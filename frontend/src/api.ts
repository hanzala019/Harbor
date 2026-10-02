export async function api<T>(path:string, method='GET', body?:unknown):Promise<T> {
  const token=sessionStorage.getItem('hospital-token');
  const response=await fetch('/api'+path,{method,headers:{...(body!==undefined?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok){let message=`Request failed (${response.status})`;try {const data=await response.json();message=data.message|| (response.status===401?'Please log in again':message);}catch {/* Empty error response. */}throw new Error(message);}
  const text=await response.text();return text?JSON.parse(text):undefined;
}
export function today(offset=0){const d=new Date();d.setDate(d.getDate()+offset);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
export function time(value:string){return value.slice(0,5);}
export function arrivalTime(value:string){const [h,m]=value.split(':').map(Number);const minutes=Math.max(0,h*60+m-10);return `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`;}
export function dateLabel(value:string){return new Date(value+'T12:00:00').toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric',year:'numeric'});}
export const active=(a:{status:string})=>['CONFIRMED','WAITING','RESCHEDULED','RESCHEDULE_PENDING'].includes(a.status);
export function roundRobin<T extends {doctorId:number}>(rows:T[]):T[]{const groups=new Map<number,T[]>();rows.forEach(a=>groups.set(a.doctorId,[...(groups.get(a.doctorId)||[]),a]));const result:T[]=[];for(let i=0;Array.from(groups.values()).some(g=>g.length>i);i++)groups.forEach(g=>{if(g[i])result.push(g[i]);});return result;}
