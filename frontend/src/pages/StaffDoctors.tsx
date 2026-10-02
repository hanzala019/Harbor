import {useEffect,useRef,useState} from 'react';
import {ArrowRight,CalendarDays,Plus} from 'lucide-react';
import {api,dateLabel,time,today} from '../api';
import {useAuth} from '../auth';
import type {Appointment,Doctor,Permission,ScheduleView} from '../types';
import {Button} from '../components/ui/button';
import {Dialog,DialogContent,DialogDescription,DialogTitle} from '../components/ui/dialog';
import {Badge,ConfirmDialog,Empty,ErrorBox} from '../components/common';
import {WeeklyScheduleDialog} from '../components/WeeklyScheduleDialog';

type SessionDraft={id?:number;startsAt:string;endsAt:string;minutes:number;available:boolean};
type Confirmation={title:string;description:string;run:()=>Promise<void>};

export function StaffDoctors(){
  const {user}=useAuth();
  const [doctors,setDoctors]=useState<Doctor[]>([]),[permissions,setPermissions]=useState<Permission[]>([]),[selected,setSelected]=useState<Doctor|null>(null),[search,setSearch]=useState('');
  const [date,setDate]=useState(today()),[sessions,setSessions]=useState<ScheduleView[]>([]),[loading,setLoading]=useState(false),[counts,setCounts]=useState({today:0,future:0,total:0});
  const [error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[editDoctor,setEditDoctor]=useState<Partial<Doctor>|null>(null),[schedule,setSchedule]=useState<SessionDraft|null>(null);
  const [affected,setAffected]=useState<{session:ScheduleView;patients:Appointment[]}|null>(null),[targetDate,setTargetDate]=useState(today(1)),[targets,setTargets]=useState<ScheduleView[]>([]),[target,setTarget]=useState(''),[confirm,setConfirm]=useState<Confirmation|null>(null);
  const sessionRequest=useRef(0);
  const loadDoctors=async()=>{const ds=await api<Doctor[]>('/staff/doctors');setDoctors(ds);setSelected(current=>current?ds.find(d=>d.id===current.id)||null:null);};
  const loadSessions=async()=>{
    if(!selected)return;
    const request=++sessionRequest.current;setLoading(true);
    try{const rows=await api<ScheduleView[]>(`/schedules?doctorId=${selected.id}&date=${date}`);if(request===sessionRequest.current)setSessions(rows);}
    finally{if(request===sessionRequest.current)setLoading(false);}
  };
  useEffect(()=>{loadDoctors().catch(e=>setError(e.message));api<Permission[]>('/staff/permissions').then(setPermissions).catch(e=>setError(e.message));},[]);
  useEffect(()=>{
    let live=true;setError('');setSessions([]);loadSessions().catch(e=>{if(live)setError(e.message);});
    if(selected)api<Appointment[]>('/staff/appointments?from=1900-01-01&to=2100-12-31').then(rows=>{
      if(!live)return;const mine=rows.filter(a=>a.doctorId===selected.id);setCounts({today:mine.filter(a=>a.date===today()).length,future:mine.filter(a=>a.date>today()).length,total:mine.length});
    }).catch(e=>{if(live)setError(e.message);});
    return()=>{live=false;sessionRequest.current++;};
  },[selected?.id,date]);
  useEffect(()=>{
    let live=true;setTargets([]);setTarget('');
    if(affected&&selected)api<ScheduleView[]>(`/schedules?doctorId=${selected.id}&date=${targetDate}`).then(rows=>{if(live)setTargets(rows);}).catch(e=>{if(live)setError(e.message);});
    return()=>{live=false;};
  },[affected?.session.schedule.id,targetDate]);
  const permission=permissions.find(p=>p.doctorId===selected?.id);
  const can=(area:'editInfo'|'editSchedule'|'editAvailability')=>user?.role==='ADMIN'||!!permission?.[area];
  const filteredDoctors=doctors.filter(d=>[d.name,d.specialty,d.department,d.room].some(v=>v.toLowerCase().includes(search.trim().toLowerCase())));
  async function review(s:ScheduleView){setError('');try{setAffected({session:s,patients:await api<Appointment[]>(`/staff/schedules/${s.schedule.id}/affected`)});}catch(e){setError((e as Error).message);}}

  return <div className="page">
    <div className="flex flex-wrap justify-between gap-3"><div><span className="eyebrow">DOCTOR OPERATIONS</span><h1>Doctors & schedules</h1></div>{user?.role==='ADMIN'&&<Button onClick={()=>{setError('');setEditDoctor({});}}><Plus size={16}/> Add doctor</Button>}</div>
    <ErrorBox message={error}/>{message&&<p role="status" className="bg-teal-50 rounded p-3 my-4">{message}</p>}
    <div className="grid gap-6 md:grid-cols-[260px_1fr] mt-7">
      <aside className="space-y-2"><label>Find a doctor<input type="search" placeholder="Name, specialty, or room" value={search} onChange={e=>setSearch(e.target.value)}/></label><p className="py-2 text-xs text-slate-500">{filteredDoctors.length} of {doctors.length} doctors</p>{filteredDoctors.map(d=><button className={'doctor-select '+(selected?.id===d.id?'selected':'')} key={d.id} onClick={()=>{setSelected(d);setMessage('');}}><strong>{d.name}</strong><small>{d.specialty} · Room {d.room}</small></button>)}{!filteredDoctors.length&&<Empty>{doctors.length?'No doctors match this search.':'No doctors assigned.'}</Empty>}</aside>
      <section>{!selected?<Empty>Select a doctor to view their calendar and permissions.</Empty>:<>
        <div className="card mb-5"><div className="flex flex-wrap justify-between gap-4"><div><h2>{selected.name}</h2><p className="text-slate-500">{selected.department} · Room {selected.room}</p></div>{can('editInfo')&&<Button variant="outline" onClick={()=>{setError('');setEditDoctor(selected);}}>Edit profile</Button>}</div><p className="my-4">{selected.biography}</p><div className="grid grid-cols-3 gap-3 border-t pt-4">{Object.entries(counts).map(([label,n])=><div key={label}><strong className="text-2xl">{n}</strong><small className="block text-slate-500">{label} appointments</small></div>)}</div></div>
        <div className="card"><div className="flex flex-wrap justify-between gap-3"><h2 className="flex gap-2 items-center"><CalendarDays/> Daily schedules</h2>{can('editSchedule')&&<div className="flex flex-wrap gap-2"><WeeklyScheduleDialog key={selected.id} doctorId={selected.id} doctorName={selected.name} initialDate={date} onSaved={async created=>{await loadSessions();setMessage(`${created} weekly sessions created. Choose a calendar date to view them.`);}}/><Button onClick={()=>{setError('');setSchedule({startsAt:'09:00',endsAt:'17:00',minutes:20,available:true});}}><Plus size={16}/> Add session</Button></div>}</div>
          <label className="my-5">Calendar date<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label>
          {loading?<p>Loading sessions…</p>:sessions.map(s=><div key={s.schedule.id} className="session-row"><div><strong>{dateLabel(s.schedule.date)}</strong><p>{time(s.schedule.startsAt)} – {time(s.schedule.endsAt)}</p><small>{s.schedule.minutes} min visits · {s.appointmentCount} bookings · {s.slots.length} free slots</small><Badge status={s.schedule.available?'AVAILABLE':'UNAVAILABLE'}/></div><div className="flex flex-wrap gap-2">{can('editSchedule')&&!s.appointmentCount&&<Button size="sm" variant="outline" onClick={()=>{setError('');setSchedule({...s.schedule});}}>Edit times</Button>}<Button size="sm" variant="outline" onClick={()=>review(s)}>Review affected patients</Button></div></div>)}
          {!loading&&!sessions.length&&<Empty>No sessions on this date. Use Weekly schedule to create several days at once.</Empty>}
        </div>
      </>}</section>
    </div>

    <Dialog open={!!editDoctor} onOpenChange={v=>{if(!v)setEditDoctor(null);}}><DialogContent><DialogTitle className="text-xl font-bold">{editDoctor?.id?'Edit doctor':'Add doctor'}</DialogTitle><DialogDescription className="my-3">Doctor information is visible to patients.</DialogDescription><form className="space-y-4" onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');try{await api(editDoctor?.id?`/staff/doctors/${editDoctor.id}`:'/admin/doctors',editDoctor?.id?'PUT':'POST',editDoctor);await loadDoctors();setEditDoctor(null);}catch(err){setError((err as Error).message);}finally{setBusy(false);}}}>{(['name','specialty','department','room','biography'] as const).map(k=><label key={k}>{k}<input required={k!=='biography'} maxLength={k==='biography'?4000:150} value={editDoctor?.[k]||''} onChange={e=>setEditDoctor({...editDoctor,[k]:e.target.value})}/></label>)}<ErrorBox message={error}/><Button disabled={busy}>Save doctor</Button></form></DialogContent></Dialog>

    <Dialog open={!!schedule} onOpenChange={v=>{if(!v)setSchedule(null);}}><DialogContent><DialogTitle className="text-xl font-bold">{schedule?.id?'Edit':'Add'} session · {dateLabel(date)}</DialogTitle><DialogDescription className="my-3">Sessions cannot overlap. For booked sessions, review and transfer affected patients instead of changing times.</DialogDescription><form className="space-y-4" onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');try{await api(schedule?.id?`/staff/schedules/${schedule.id}`:'/staff/schedules',schedule?.id?'PUT':'POST',{...schedule,doctorId:selected!.id,date});await loadSessions();setSchedule(null);}catch(err){setError((err as Error).message);}finally{setBusy(false);}}}>{(['startsAt','endsAt'] as const).map(k=><label key={k}>{k==='startsAt'?'Start time':'End time'}<input type="time" required value={schedule?.[k]||''} onChange={e=>setSchedule({...schedule!,[k]:e.target.value})}/></label>)}<label>Minutes per visit<input required type="number" min={5} max={120} value={schedule?.minutes||20} onChange={e=>setSchedule({...schedule!,minutes:Number(e.target.value)})}/></label><ErrorBox message={error}/><Button disabled={busy}>Save session</Button></form></DialogContent></Dialog>

    <Dialog open={!!affected} onOpenChange={v=>{if(!v)setAffected(null);}}><DialogContent className="max-w-2xl"><DialogTitle className="text-xl font-bold">Affected appointments</DialogTitle><DialogDescription className="my-3">Review these patients before changing availability, notifying them, or transferring the active queue. Transfer only patients who have agreed to move.</DialogDescription>
      <div className="max-h-48 overflow-auto border rounded-lg mb-4">{affected?.patients.length?affected.patients.map(a=><div key={a.id} className="p-3 border-b flex justify-between"><strong>#{a.serial} · {a.name}</strong><span>{a.phone}</span></div>):<Empty>No active appointments affected.</Empty>}</div>
      <div className="space-y-4">
        {can('editAvailability')&&<Button variant="outline" onClick={()=>setConfirm({title:affected?.session.schedule.available?'Mark session unavailable?':'Restore availability?',description:`${affected?.patients.length} active appointment(s) are affected. Emails are sent only when you choose Notify.`,run:async()=>{await api(`/staff/schedules/${affected!.session.schedule.id}/availability`,'PATCH',{available:!affected!.session.schedule.available});setAffected(null);await loadSessions();}})}>{affected?.session.schedule.available?'Mark unavailable':'Restore availability'}</Button>}
        <label>Next available date<input type="date" min={today()} value={targetDate} onChange={e=>setTargetDate(e.target.value)}/></label>
        <label>Destination session<select value={target} onChange={e=>setTarget(e.target.value)}><option value="">Select a session</option>{targets.filter(s=>s.schedule.available&&s.schedule.id!==affected?.session.schedule.id).map(s=><option value={s.schedule.id} key={s.schedule.id}>{time(s.schedule.startsAt)} – {time(s.schedule.endsAt)} · {s.slots.length} free slots</option>)}</select></label>
        <div className="flex gap-3 flex-wrap">
          {can('editAvailability')&&<Button variant="outline" onClick={()=>setConfirm({title:'Notify affected patients?',description:'Each patient will receive a separate schedule-change email with the next available session, if selected.',run:async()=>{await api(`/staff/schedules/${affected!.session.schedule.id}/notify`,'POST',{targetId:target?Number(target):null});setMessage('Schedule-change notifications queued.');}})}>Notify patients</Button>}
          {can('editSchedule')&&<Button disabled={!target} onClick={()=>setConfirm({title:'Transfer the active queue?',description:'Confirm that these patients agreed to move. Relative ordering is preserved; cancelled, visited, and no-show patients stay in history. Destination serials and times are recalculated.',run:async()=>{await api(`/staff/schedules/${affected!.session.schedule.id}/transfer`,'POST',{targetId:Number(target)});setAffected(null);setMessage('Patients transferred and rescheduling emails queued.');await loadSessions();}})}>Transfer queue <ArrowRight size={16}/></Button>}
        </div><ErrorBox message={error}/>
      </div>
    </DialogContent></Dialog>
    <ConfirmDialog open={!!confirm} onClose={()=>setConfirm(null)} title={confirm?.title||''} description={confirm?.description||''} busy={busy} onConfirm={async()=>{setBusy(true);try{await confirm!.run();setConfirm(null);}catch(e){setError((e as Error).message);setConfirm(null);}finally{setBusy(false);}}}/>
  </div>;
}
