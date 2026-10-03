import {useEffect,useRef,useState} from 'react';
import {Check,X} from 'lucide-react';
import {Link} from 'react-router-dom';
import {api,active,dateLabel,roundRobin,time,today,hospitalTimeZone} from '../api';
import type {Appointment,Doctor} from '../types';
import {Button} from '../components/ui/button';
import {Badge,ConfirmDialog,Empty,ErrorBox} from '../components/common';
import {SearchSelect} from '../components/SearchSelect';
import {useRememberedState} from '../hooks/useRememberedState';
import {useTable} from '../components/TableTools';
export {Admin} from './Admin';
export {StaffDoctors} from './StaffDoctors';

export function StaffAppointments(){
  const [rows,setRows]=useState<Appointment[]>([]),[doctors,setDoctors]=useState<Doctor[]>([]),[from,setFrom]=useRememberedState('queue:from',today()),[to,setTo]=useRememberedState('queue:to',today());
  const [doctor,setDoctor]=useRememberedState('queue:doctor',''),[search,setSearch]=useRememberedState('queue:search',''),[tab,setTab]=useRememberedState('queue:tab','ONGOING'),[status,setStatus]=useRememberedState('queue:status',''),[error,setError]=useState(''),[loading,setLoading]=useState(true);
  const [lastUpdate,setLastUpdate]=useState(''),[live,setLive]=useRememberedState('queue:live',true),[mail,setMail]=useState<{appointmentId:number;status:string}[]>([]);
  const [action,setAction]=useState<{a:Appointment;status:string}|null>(null),[busy,setBusy]=useState(false);
  const requestId=useRef(0);
  const load=async(silent=false)=>{
    const request=++requestId.current;if(!silent)setLoading(true);
    try{const [data,mail]=await Promise.all([api<Appointment[]>(`/staff/appointments?from=${from}&to=${to}`),api<{appointmentId:number;status:string}[]>('/staff/mail')]);if(request===requestId.current){setRows(data);setMail(mail);setError('');setLastUpdate(new Date().toLocaleTimeString(undefined,{timeZone:hospitalTimeZone}));}}
    catch(e){if(request===requestId.current)setError((e as Error).message);}
    finally{if(request===requestId.current)setLoading(false);}
  };
  useEffect(()=>{api<Doctor[]>('/staff/doctors').then(setDoctors).catch(e=>setError(e.message));},[]);
  useEffect(()=>{load();return()=>{requestId.current++;};},[from,to]);
  useEffect(()=>{if(!live)return;const timer=setInterval(()=>{if(document.visibilityState==='visible')load(true);},30000);return()=>clearInterval(timer);},[live,from,to]);
  const pending=roundRobin(rows.filter(active));
  const filtered=(tab==='ONGOING'?pending:rows.filter(a=>a.status===tab)).filter(a=>(!doctor||String(a.doctorId)===doctor)&&(!status||a.status===status)&&(a.name.toLowerCase().includes(search.toLowerCase())||a.phone.includes(search)));
  const table=useTable(filtered,[{key:'date',label:'Visit date',value:a=>a.date+' '+a.time},{key:'patient',label:'Patient',value:a=>a.name},{key:'doctor',label:'Doctor',value:a=>a.doctor},{key:'serial',label:'Serial',value:a=>a.serial}],'appointments',tab==='ONGOING');

  return <div className="page wide">
    <div className="flex flex-wrap items-start justify-between gap-4"><div><span className="eyebrow">RECEPTION DESK</span><h1>Appointments</h1><p className="page-subtitle">Keep each doctor's next patient in view.</p></div><Button asChild><Link to="/staff/book">Book for a patient</Link></Button></div>
    <div className="card filter-grid"><label>From<input type="date" value={from} onChange={e=>{setFrom(e.target.value);if(e.target.value>to)setTo(e.target.value);}}/></label><label>To<input type="date" min={from} value={to} onChange={e=>setTo(e.target.value)}/></label><label>Doctor<SearchSelect label="Doctor" value={doctor} onChange={setDoctor} options={[{value:'',label:'All assigned doctors'},...doctors.map(d=>({value:String(d.id),label:d.name}))]}/></label><label>Patient name / phone<input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search patients…"/></label></div>
    <div className="flex flex-wrap gap-2 mt-3">{[['Today',0],['Yesterday',-1],['Tomorrow',1]].map(([name,offset])=><Button key={String(name)} variant="outline" size="sm" onClick={()=>{setFrom(today(Number(offset)));setTo(today(Number(offset)));}}>{name}</Button>)}<Button size="sm" variant="outline" onClick={()=>load()}>Refresh</Button><Button variant="ghost" size="sm" onClick={()=>{setFrom(today());setTo(today());setDoctor('');setSearch('');setTab('ONGOING');setStatus('');}}>Reset filters</Button><label className="checkbox"><input type="checkbox" checked={live} onChange={e=>setLive(e.target.checked)}/>Auto-refresh every 30s</label></div>
    <p className="my-3 text-xs text-slate-500">Last updated: {lastUpdate||'—'} · {hospitalTimeZone}. {tab==='ONGOING'?'Round-robin queue order is preserved across pages.':''}</p>
    <ErrorBox message={error}/>
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_280px] mt-7">
      <section className="min-w-0">
        <div className="flex flex-wrap justify-between gap-3 mb-4"><div className="tabs">{[['ONGOING','Ongoing'],['VISITED','Visited'],['NO_SHOW','Did Not Show Up'],['CANCELLED','Cancelled']].map(([value,label])=><Button key={value} size="sm" variant={tab===value?'default':'ghost'} onClick={()=>{setTab(value);setStatus('');}}>{label}</Button>)}</div>{tab==='ONGOING'&&<select aria-label="Appointment status" className="w-auto" value={status} onChange={e=>setStatus(e.target.value)}><option value="">All active statuses</option>{['CONFIRMED','WAITING','RESCHEDULED','RESCHEDULE_PENDING'].map(s=><option key={s}>{s}</option>)}</select>}</div>
        {table.controls}<div className="table-wrap"><table><thead><tr><th>Serial</th><th>Patient</th><th>Doctor</th><th>Visit</th><th>Status / email</th><th>Actions</th></tr></thead><tbody>{!loading&&table.rows.map(a=><tr key={a.id}><td><strong className="serial">#{a.serial}</strong></td><td><strong>{a.name}</strong><small>{a.phone}</small></td><td>{a.doctor}<small>Room {a.room}</small></td><td>{dateLabel(a.date)}<small>{time(a.time)}</small></td><td><Badge status={a.status}/><small>Email: {mail.find(m=>m.appointmentId===a.id)?.status||'No linked history'}</small></td><td><div className="flex flex-wrap gap-1">{active(a)&&<><Button size="icon" variant="outline" title="Mark visited" aria-label={'Mark '+a.name+' visited'} onClick={()=>setAction({a,status:'VISITED'})}><Check size={16}/></Button><Button size="icon" variant="outline" title="Mark no-show" aria-label={'Mark '+a.name+' no-show'} onClick={()=>setAction({a,status:'NO_SHOW'})}><X size={16}/></Button><Button size="sm" variant="ghost" onClick={()=>setAction({a,status:'CANCELLED'})}>Cancel</Button></>}<Button size="sm" variant="outline" onClick={async()=>{try{await api(`/staff/appointments/${a.id}/resend`,'POST');await load(true);}catch(e){setError((e as Error).message);}}}>Resend email</Button></div></td></tr>)}</tbody></table>{loading?<Empty>Loading appointments…</Empty>:!filtered.length&&<Empty>No appointments match this view.</Empty>}</div>
      </section>
      <aside className="space-y-4"><h3>Next in line</h3><p className="text-xs text-slate-500">Each session has its own serial queue.</p>{doctors.filter(d=>!doctor||String(d.id)===doctor).map(d=>{
        const queues=new Map<number,Appointment>();rows.filter(a=>a.doctorId===d.id&&active(a)).forEach(a=>{if(!queues.has(a.scheduleId))queues.set(a.scheduleId,a);});
        return <div className="card !p-4" key={d.id}><h3 className="text-base">{d.name}</h3><p className="text-xs text-slate-500">Room {d.room}</p>{queues.size?Array.from(queues.values()).map(next=><div key={next.scheduleId} className="mt-3 border-t pt-3"><span className="text-xs">{dateLabel(next.date)}</span><div className="flex items-center gap-3 mt-2"><span className="serial">#{next.serial}</span><div><strong>{next.name}</strong><small className="block text-slate-500">Current / next pending · {time(next.time)}</small></div></div></div>):<p className="mt-3 text-sm text-slate-500">Queue is clear</p>}</div>;
      })}</aside>
    </div>
    <ConfirmDialog open={!!action} onClose={()=>setAction(null)} title={'Mark appointment '+(action?.status||'').replaceAll('_',' ').toLowerCase()+'?'} description={`${action?.a.name||''} will be updated in ${action?.a.doctor||''}'s queue. Historical records are retained.`} busy={busy} onConfirm={async()=>{setBusy(true);try{await api(`/appointments/${action!.a.id}/status`,'PATCH',{status:action!.status});setAction(null);await load();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}/>
  </div>;
}
