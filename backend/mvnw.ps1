# Maven bootstrap for Windows. Uses the project's Java release 17 target (JDK 17+).
$ErrorActionPreference = 'Stop'
$version = '3.9.9'
$cache = Join-Path $env:LOCALAPPDATA 'HospitalAppointmentSystem'
$mavenHome = Join-Path $cache "apache-maven-$version"
$maven = Join-Path $mavenHome 'bin\mvn.cmd'
if (!(Test-Path $maven)) {
    New-Item -ItemType Directory -Force $cache | Out-Null
    $zip = Join-Path $cache 'maven.zip'
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
    Invoke-WebRequest $url -OutFile $zip
    $expected = (Invoke-WebRequest "$url.sha512" -UseBasicParsing).Content.Trim()
    $actual = (Get-FileHash $zip -Algorithm SHA512).Hash
    if ($actual.ToLowerInvariant() -ne $expected.ToLowerInvariant()) { throw 'Maven archive checksum did not match.' }
    Expand-Archive $zip -DestinationPath $cache -Force
    Remove-Item $zip
}
Push-Location $PSScriptRoot
try { & $maven @args; $result = $LASTEXITCODE } finally { Pop-Location }
exit $result
