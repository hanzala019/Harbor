[CmdletBinding()]
param([switch]$CheckOnly,[switch]$SmokeTest,[switch]$NoBrowser,[int]$BackendPort=18080,[int]$WebPort=5173)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$owned=@()
function Free-Port([int]$preferred) {
    for($p=$preferred;$p -lt [Math]::Min($preferred+100,65536);$p++) {
        $listener=New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback,$p)
        try {$listener.Start();return $p} catch {} finally {$listener.Stop()}
    }
    throw "No available ports near $preferred."
}
function Wait-Url([string]$url,[System.Diagnostics.Process]$process) {
    for($i=0;$i -lt 180;$i++) {
        if($process.HasExited){throw "A server exited during startup. Check the terminal output."}
        try {
            $request=[System.Net.HttpWebRequest]::Create($url);$request.Proxy=$null;$request.Timeout=2000
            $response=$request.GetResponse();$status=[int]$response.StatusCode;$response.Close()
            if($status -eq 200){return}
        } catch {if($i % 15 -eq 0){Write-Host "Waiting for ${url}: $($_.Exception.Message)"}}
        Start-Sleep -Seconds 1
    }
    throw "Server did not become ready: $url"
}
if($BackendPort -lt 1024 -or $BackendPort -gt 65435 -or $WebPort -lt 1024 -or $WebPort -gt 65435){throw 'Choose ports between 1024 and 65435.'}
# Environment variables override repository-local .env values, just as in Spring Boot.
$envFile=Join-Path $root '.env'
if(Test-Path $envFile){Get-Content $envFile | ForEach-Object {if($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$'){if([Environment]::GetEnvironmentVariable($matches[1],'Process') -eq $null){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}}}
$candidates=@($env:JAVA_HOME)
$javaRoot=Join-Path $env:ProgramFiles 'Java'
if(Test-Path $javaRoot){$candidates+=@(Get-ChildItem $javaRoot -Directory -Filter 'jdk*' | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)}
$jdk=$null
foreach($candidate in $candidates){
    if(!$candidate){continue}
    $exe=Join-Path $candidate 'bin\java.exe'
    if(!(Test-Path $exe) -or !(Test-Path (Join-Path $candidate 'bin\javac.exe'))){continue}
    $info=New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName=$exe;$info.Arguments='-version';$info.UseShellExecute=$false;$info.RedirectStandardError=$true;$info.RedirectStandardOutput=$true;$info.CreateNoWindow=$true
    $probe=[System.Diagnostics.Process]::Start($info)
    $version=$probe.StandardError.ReadToEnd()+$probe.StandardOutput.ReadToEnd();$probe.WaitForExit();$probe.Dispose()
    if($version -match 'version "(\d+)' -and [int]$matches[1] -ge 17){$jdk=$candidate;break}
}
if(!$jdk){throw 'A JDK 17+ is required. Set JAVA_HOME to its installation directory.'}
$env:JAVA_HOME=$jdk
$node=Get-Command node -ErrorAction SilentlyContinue
$npm=Get-Command npm.cmd -ErrorAction SilentlyContinue
if(!$node -or !$npm){throw 'Install Node.js 22+ (including npm) and reopen PowerShell.'}
if([int]((& node --version).TrimStart('v').Split('.')[0]) -lt 22){throw 'Node.js 22+ is required.'}
$dbUrl=if($env:DB_URL){$env:DB_URL}else{'jdbc:postgresql://localhost:5432/harbor'}
if($dbUrl -notmatch '^jdbc:postgresql://([^/:]+):(\d+)/([^?]+)'){throw 'DB_URL must use jdbc:postgresql://host:port/database.'}
$dbHost=$matches[1];$dbPort=[int]$matches[2];$dbName=$matches[3]
$tcp=New-Object System.Net.Sockets.TcpClient
try {$connect=$tcp.BeginConnect($dbHost,$dbPort,$null,$null);if(!$connect.AsyncWaitHandle.WaitOne(3000)){throw 'Timed out'};$tcp.EndConnect($connect)} catch {throw "PostgreSQL is not reachable at ${dbHost}:${dbPort}. Start PostgreSQL before running this launcher."} finally {$tcp.Close()}
$psql=Get-Command psql.exe -ErrorAction SilentlyContinue
if(!$psql){foreach($service in @(Get-CimInstance Win32_Service -Filter "Name LIKE 'postgresql%'")){if($service.PathName -match '^"?(.+?pg_ctl.exe)"? '){$path=Join-Path (Split-Path $matches[1]) 'psql.exe';if(Test-Path $path){$psql=Get-Item $path;break}}}}
if($psql){$psqlPath=if($psql -is [System.IO.FileInfo]){$psql.FullName}else{$psql.Source};$oldPassword=$env:PGPASSWORD;try{$env:PGPASSWORD=if($env:DB_PASSWORD){$env:DB_PASSWORD}else{'postgres'};$user=if($env:DB_USER){$env:DB_USER}else{'postgres'};& $psqlPath -h $dbHost -p $dbPort -U $user -d $dbName -w -c 'SELECT current_database();';if($LASTEXITCODE -ne 0){throw 'PostgreSQL login/database check failed. Check DB_URL, DB_USER, and DB_PASSWORD in .env.'}}finally{$env:PGPASSWORD=$oldPassword}}
$BackendPort=Free-Port $BackendPort
$WebPort=Free-Port $WebPort
if($WebPort -eq $BackendPort){$WebPort=Free-Port ($WebPort+1)}
$env:PORT=[string]$BackendPort
$env:API_PROXY="http://127.0.0.1:$BackendPort"
$env:PUBLIC_URL="http://localhost:$WebPort"
Write-Host "Java: $jdk"
Write-Host "PostgreSQL: ${dbHost}:${dbPort}/$dbName"
Write-Host "Backend: http://localhost:$BackendPort | Application: $env:PUBLIC_URL"
if($CheckOnly){Write-Host 'Prerequisite checks passed. No servers were started.';exit 0}
try {
    Push-Location (Join-Path $root 'frontend')
    try {if(!(Test-Path 'node_modules\vite\bin\vite.js')){& $npm.Source ci;if($LASTEXITCODE -ne 0){throw 'npm ci failed.'}}} finally {Pop-Location}
    $jar=Join-Path $root 'backend\target\hospital-1.0.0.jar'
    $inputs=@(Get-ChildItem (Join-Path $root 'backend\src\main') -Recurse -File)+@(Get-Item (Join-Path $root 'backend\pom.xml'))
    $latest=($inputs | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
    if(!(Test-Path $jar) -or $latest -gt (Get-Item $jar).LastWriteTimeUtc){
        & (Join-Path $root 'backend\mvnw.ps1') '-DskipTests' package
        if($LASTEXITCODE -ne 0){throw 'Backend build failed.'}
    }
    $backend=Start-Process -FilePath (Join-Path $jdk 'bin\java.exe') -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $root -NoNewWindow -PassThru
    $owned+=$backend
    $vite=Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
    $frontend=Start-Process -FilePath $node.Source -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1','--port',[string]$WebPort,'--strictPort') -WorkingDirectory (Join-Path $root 'frontend') -NoNewWindow -PassThru
    $owned+=$frontend
    Wait-Url "http://127.0.0.1:$BackendPort/api/health" $backend
    Wait-Url "http://127.0.0.1:$WebPort/" $frontend
    Write-Host "Ready: $env:PUBLIC_URL - press Ctrl+C to stop both servers."
    if($SmokeTest){Write-Host 'Startup smoke check passed; shutting down owned servers.';return}
    if(!$NoBrowser){Start-Process $env:PUBLIC_URL}
    while(!$backend.HasExited -and !$frontend.HasExited){Start-Sleep -Seconds 1}
} finally {
    foreach($process in $owned){if(!$process.HasExited){& taskkill.exe /PID $process.Id /T /F | Out-Null}}
}
