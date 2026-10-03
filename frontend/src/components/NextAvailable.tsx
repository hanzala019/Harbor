import {useState} from 'react';
import {api,today,shiftDate} from '../api';
import type {ScheduleView} from '../types';
import {Button} from './ui/button';
import {ErrorBox} from './common';
export function NextAvailable({doctorId,from,onDate}:{doctorId:number;from:string;onDate:(date:string)=>void}){
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('');
  return <div className="my-3"><Button variant="outline" size="sm" disabled={busy} onClick={async()=>{setBusy(true);setMessage('');setError('');try{const start=from<today()?today():from;const rows=await api<ScheduleView[]>(`/availability?doctorId=${doctorId}&from=${start}&to=${shiftDate(start,89)}`);if(rows.length)onDate(rows[0].schedule.date);else setMessage('No available appointments in the next 90 days from this date. Contact reception for help.');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>{busy?'Finding appointments…':'Next available appointment'}</Button><p role="status" className="mt-2 text-sm text-slate-500">{message}</p><ErrorBox message={error}/></div>;
}
