import {useEffect,useState} from 'react';
import {api,dateLabel,time,today,shiftDate} from '../api';
import type {ScheduleView} from '../types';
import {Button} from './ui/button';
import {Empty,ErrorBox} from './common';
import {NextAvailable} from './NextAvailable';
export type SlotSelection={s:ScheduleView;slot:ScheduleView['slots'][number]};
export function AppointmentSlots({doctorId,date,onDate,selected,onSelect,refresh=0,disabled=false}:{doctorId:number;date:string;onDate:(date:string)=>void;selected:SlotSelection|null;onSelect:(slot:SlotSelection|null)=>void;refresh?:number;disabled?:boolean}){
  const [sessions,setSessions]=useState<ScheduleView[]>([]),[days,setDays]=useState<string[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState('');
  useEffect(()=>{let live=true;setLoading(true);setError('');setSessions([]);api<ScheduleView[]>(`/schedules?doctorId=${doctorId}&date=${date}`).then(rows=>{if(live)setSessions(rows);}).catch(e=>{if(live)setError(e.message);}).finally(()=>{if(live)setLoading(false);});return()=>{live=false;};},[doctorId,date,refresh]);
  useEffect(()=>{let live=true;setDays([]);const start=date<today()?today():date;api<ScheduleView[]>(`/availability?doctorId=${doctorId}&from=${start}&to=${shiftDate(start,89)}`).then(rows=>{if(live)setDays([...new Set(rows.map(s=>s.schedule.date))]);}).catch(e=>{if(live)setError(e.message);});return()=>{live=false;};},[doctorId,date,refresh]);
  return <fieldset disabled={disabled} className="min-w-0"><label className="my-4">Visit date<input type="date" required min={today()} value={date} onChange={e=>{onSelect(null);onDate(e.target.value);}}/></label><NextAvailable doctorId={doctorId} from={date} onDate={value=>{onSelect(null);onDate(value);}}/>
    {!!days.length&&<details className="my-4"><summary className="cursor-pointer text-sm text-primary">Available dates · next 90 days</summary><div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mt-3">{days.map(day=><Button key={day} size="sm" variant={day===date?'default':'outline'} onClick={()=>{onSelect(null);onDate(day);}}>{dateLabel(day)}</Button>)}</div></details>}
    <ErrorBox message={error}/>{loading?<p role="status">Loading available times…</p>:sessions.filter(s=>s.slots.length).map(s=><div key={s.schedule.id} className="mb-5"><h3 className="text-base mb-3">{time(s.schedule.startsAt)} – {time(s.schedule.endsAt)}</h3><div className="flex flex-wrap gap-2">{s.slots.map(slot=><Button key={slot.index} size="sm" aria-pressed={selected?.s.schedule.id===s.schedule.id&&selected.slot.index===slot.index} variant={selected?.s.schedule.id===s.schedule.id&&selected.slot.index===slot.index?'default':'outline'} onClick={()=>onSelect({s,slot})}>{time(slot.time)}</Button>)}</div></div>)}{!loading&&!error&&!sessions.some(s=>s.slots.length)&&<Empty>No available slots on this date. Use Next available appointment or choose a highlighted available date.</Empty>}
  </fieldset>;
}
