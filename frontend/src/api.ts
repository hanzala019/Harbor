export async function api<T>(path:string, method='GET', body?:unknown):Promise<T> {
  const token=sessionStorage.getItem('hospital-token');
  const response=await fetch('/api'+path,{method,headers:{...(body!==undefined?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok){if(response.status===401&&token){sessionStorage.removeItem('hospital-token');window.dispatchEvent(new Event('harbor:session-expired'));}let message=`Request failed (${response.status})`;try {const data=await response.json();message=data.message|| (response.status===401?'Please log in again':message);}catch {}throw new Error(message);}
  const text=await response.text(),data=text?JSON.parse(text):undefined;
  if(method!=='GET'&&!path.includes('/auth/')&&!path.includes('/preview')&&!path.includes('/guest/view')&&!path.includes('check-duplicate')){
    const message=path.endsWith('/resend')?'Confirmation email queued':path.endsWith('/reschedule')?'Appointment rescheduled. Updated confirmation email queued.':path.endsWith('/notify')?'Patient notifications queued':path.endsWith('/weekly')?`${data?.toCreate??0} sessions created`:path.endsWith('/bulk')?`Access saved for ${data?.length??0} doctors`:path.endsWith('/transfer')?`${data?.length??0} patients transferred`:path.endsWith('/password')?'Password changed. Please log in again.':path==='/appointments'||path==='/staff/appointments'?'Appointment confirmed':path.includes('/templates')?'Schedule template updated':path.includes('/cancel')?'Appointment cancelled':'Changes saved successfully';window.dispatchEvent(new CustomEvent('harbor:notice',{detail:message}));
  }return data;
}
export let hospitalTimeZone='UTC';
export let hospitalInfo={hospitalAddress:'',receptionPhone:'',directionsUrl:'',visitInstructions:'Bring your appointment confirmation and relevant medical records. Check in at reception before going to your consultation room.'};
export function setHospitalInfo(info:Partial<typeof hospitalInfo>){hospitalInfo={...hospitalInfo,...info};}
export function setHospitalTimeZone(zone:string){hospitalTimeZone=zone;}
export function today(offset=0){const date=new Intl.DateTimeFormat('sv-SE',{timeZone:hospitalTimeZone,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());const d=new Date(date+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10);}
export function hospitalNowTime(){return new Intl.DateTimeFormat('en-GB',{timeZone:hospitalTimeZone,hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(new Date());}
export function shiftDate(date:string,days:number){const d=new Date(date+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10);}
export function upcoming(a:{status:string;consultationAt?:string;date:string;time:string}){return active(a)&&(a.consultationAt?new Date(a.consultationAt).getTime()>Date.now():a.date>today()||(a.date===today()&&a.time>hospitalNowTime()));}
export function time(value:string){return value.slice(0,5);}
export function arrivalTime(value:string){const [h,m]=value.split(':').map(Number);const minutes=Math.max(0,h*60+m-10);return `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`;}
export function dateLabel(value:string){return new Date(value+'T12:00:00').toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric',year:'numeric'});}
export const active=(a:{status:string})=>['CONFIRMED','WAITING','RESCHEDULED','RESCHEDULE_PENDING'].includes(a.status);
export function roundRobin<T extends {doctorId:number}>(rows:T[]):T[]{const groups=new Map<number,T[]>();rows.forEach(a=>groups.set(a.doctorId,[...(groups.get(a.doctorId)||[]),a]));const result:T[]=[];for(let i=0;Array.from(groups.values()).some(g=>g.length>i);i++)groups.forEach(g=>{if(g[i])result.push(g[i]);});return result;}
