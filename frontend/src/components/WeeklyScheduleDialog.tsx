import {useEffect,useId,useRef,useState} from 'react';
import {Plus,Trash2} from 'lucide-react';
import {api,dateLabel,time,today} from '../api';
import {Button} from './ui/button';
import {Dialog,DialogContent,DialogDescription,DialogTitle} from './ui/dialog';
import {Badge,ErrorBox} from './common';
import {SearchSelect} from './SearchSelect';

const weekdays=['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
type Hours={startsAt:string;endsAt:string};
type Day={weekday:number;enabled:boolean;ranges:Hours[]};
type Preview={sessions:{date:string;startsAt:string;endsAt:string;status:string}[];toCreate:number;existing:number;conflicts:number};
export type SchedulePattern={id?:number;name:string;minutes:number;days:{weekday:number;ranges:Hours[]}[]};

export function WeeklyScheduleDialog({doctorId,doctorName,initialDate,onSaved,pattern,onPatternUsed}:{doctorId:number;doctorName:string;initialDate:string;onSaved:(created:number)=>Promise<void>;pattern?:SchedulePattern|null;onPatternUsed?:()=>void}){
  const [open,setOpen]=useState(false),[from,setFrom]=useState(initialDate<today()?today():initialDate),[to,setTo]=useState(today(90)),[minutes,setMinutes]=useState(20);
  const [days,setDays]=useState<Day[]>(weekdays.map((_,i)=>({weekday:i+1,enabled:i<5,ranges:[{startsAt:'09:00',endsAt:'17:00'}]})));
  const [preview,setPreview]=useState<Preview|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[saving,setSaving]=useState(false);
  const formId=useId(),previewSection=useRef<HTMLElement>(null);
  const [templates,setTemplates]=useState<SchedulePattern[]>([]),[templateId,setTemplateId]=useState(''),[templateName,setTemplateName]=useState('');
  const applyPattern=(p:SchedulePattern)=>{setDays(weekdays.map((_,i)=>({weekday:i+1,enabled:p.days.some(d=>d.weekday===i+1),ranges:p.days.find(d=>d.weekday===i+1)?.ranges.map(r=>({...r}))||[{startsAt:'09:00',endsAt:'17:00'}]})));setMinutes(p.minutes);setPreview(null);setError('');};
  useEffect(()=>{if(open)api<SchedulePattern[]>(`/staff/templates?doctorId=${doctorId}`).then(setTemplates).catch(e=>setError(e.message));},[open,doctorId]);
  useEffect(()=>{if(pattern){applyPattern(pattern);setOpen(true);onPatternUsed?.();}},[pattern]);
  useEffect(()=>{if(preview)previewSection.current?.scrollIntoView({behavior:'smooth',block:'start'});},[preview]);
  const invalidate=()=>{setPreview(null);setError('');};
  const changeDay=(index:number,update:Partial<Day>)=>{invalidate();setDays(days.map((d,i)=>i===index?{...d,...update}:d));};
  const payload=()=>({doctorId,from,to,minutes,days:days.filter(d=>d.enabled).map(({weekday,ranges})=>({weekday,ranges}))});
  const openDialog=()=>{setFrom(initialDate<today()?today():initialDate);setTo(initialDate>today(90)?initialDate:today(90));invalidate();setOpen(true);};

  return <>
    <Button variant="outline" onClick={openDialog}>Weekly schedule</Button>
    <Dialog open={open} onOpenChange={value=>{if(!busy)setOpen(value);}}>
      <DialogContent className="flex h-[90vh] max-w-4xl flex-col overflow-hidden">
        <div className="shrink-0 pr-6">
          <DialogTitle className="text-xl font-bold">Weekly schedule · {doctorName}</DialogTitle>
          <DialogDescription className="my-3">Choose weekdays and their working hours once, then generate dated sessions through the end date. Existing sessions and bookings are preserved.</DialogDescription>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto pr-2">
        <form id={formId} className="space-y-5" onSubmit={async e=>{
          e.preventDefault();setBusy(true);setError('');
          try{setPreview(await api<Preview>('/staff/schedules/weekly/preview','POST',payload()));}
          catch(err){setError((err as Error).message);}finally{setBusy(false);}
        }}>
          <fieldset disabled={busy} className="space-y-5">
            <div className="grid gap-3 sm:grid-cols-2"><label>Saved template<SearchSelect label="Saved template" value={templateId} options={[{value:'',label:'Choose a saved template'},...templates.map(t=>({value:String(t.id),label:t.name}))]} onChange={id=>{setTemplateId(id);const p=templates.find(t=>String(t.id)===id);if(p)applyPattern(p);}}/></label><label>Save these hours as a template<input placeholder="Template name" maxLength={150} value={templateName} onChange={e=>setTemplateName(e.target.value)}/></label></div>
            <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" size="sm" disabled={!templateName.trim()||!days.some(d=>d.enabled)} onClick={async()=>{setBusy(true);try{const t=await api<SchedulePattern>('/staff/templates','POST',{...payload(),name:templateName});setTemplates([...templates,t]);setTemplateId(String(t.id));setTemplateName('');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Save template</Button><Button type="button" variant="ghost" size="sm" disabled={!templateId} onClick={async()=>{setBusy(true);try{await api(`/staff/templates/${templateId}`,'DELETE');setTemplates(templates.filter(t=>String(t.id)!==templateId));setTemplateId('');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Delete selected template</Button></div>
            <div className="grid gap-4 sm:grid-cols-3">
              <label>From date<input required type="date" min={today()} value={from} onChange={e=>{invalidate();setFrom(e.target.value);if(e.target.value>to)setTo(e.target.value);}}/></label>
              <label>Through date<input required type="date" min={from} value={to} onChange={e=>{invalidate();setTo(e.target.value);}}/></label>
              <label>Minutes per visit<input required type="number" min={5} max={120} value={minutes} onChange={e=>{invalidate();setMinutes(Number(e.target.value));}}/></label>
            </div>
            <p className="text-xs text-slate-500">Maximum date window: 366 days. Add separate time ranges to leave a break between sessions.</p>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={()=>{invalidate();setDays(days.map(d=>({...d,enabled:d.weekday<=5})));}}>Monday–Friday</Button>
              <Button type="button" variant="ghost" size="sm" onClick={()=>{invalidate();setDays(days.map(d=>({...d,enabled:true})));}}>Every day</Button>
              <Button type="button" variant="ghost" size="sm" onClick={()=>{invalidate();setDays(days.map(d=>({...d,enabled:false})));}}>Clear days</Button>
            </div>
            <div className="space-y-3">
              {days.map((day,index)=><div key={day.weekday} className="rounded-xl border p-4">
                <label className="checkbox"><input type="checkbox" checked={day.enabled} onChange={e=>changeDay(index,{enabled:e.target.checked})}/>{weekdays[index]}</label>
                {day.enabled&&<div className="mt-3 space-y-3">
                  {day.ranges.map((range,rangeIndex)=><div key={rangeIndex} className="grid grid-cols-[1fr_1fr_auto] items-end gap-3">
                    <label>Start time<input aria-label={`${weekdays[index]} start ${rangeIndex+1}`} required type="time" value={range.startsAt} onChange={e=>changeDay(index,{ranges:day.ranges.map((r,i)=>i===rangeIndex?{...r,startsAt:e.target.value}:r)})}/></label>
                    <label>End time<input aria-label={`${weekdays[index]} end ${rangeIndex+1}`} required type="time" value={range.endsAt} onChange={e=>changeDay(index,{ranges:day.ranges.map((r,i)=>i===rangeIndex?{...r,endsAt:e.target.value}:r)})}/></label>
                    <Button type="button" variant="ghost" size="icon" disabled={day.ranges.length===1} aria-label={`Remove ${weekdays[index]} range ${rangeIndex+1}`} onClick={()=>changeDay(index,{ranges:day.ranges.filter((_,i)=>i!==rangeIndex)})}><Trash2 size={16}/></Button>
                  </div>)}
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" variant="outline" size="sm" disabled={day.ranges.length>=10} onClick={()=>changeDay(index,{ranges:[...day.ranges,{startsAt:'18:00',endsAt:'21:00'}]})}><Plus size={14}/> Add time range</Button>
                    <Button type="button" variant="ghost" size="sm" onClick={()=>{invalidate();setDays(days.map(d=>d.enabled?{...d,ranges:day.ranges.map(r=>({...r}))}:d));}}>Copy hours to selected days</Button>
                  </div>
                </div>}
              </div>)}
            </div>
          </fieldset>
        </form>
        {preview&&<section ref={previewSection} className="mt-6 space-y-4 border-t pt-5 pb-4">
          <h3>Schedule preview</h3>
          <p className="text-sm"><strong>{preview.toCreate}</strong> new sessions · {preview.existing} already scheduled · <span className={preview.conflicts?'text-red-700':''}>{preview.conflicts} overlaps</span></p>
          <p className="text-xs text-slate-500">Identical sessions are skipped, including any marked unavailable. Overlaps must be resolved before creation; the complete batch saves together.</p>
          <div className="table-wrap max-h-60"><table><thead><tr><th>Date / weekday</th><th>Time range</th><th>Result</th></tr></thead><tbody>{preview.sessions.map((s,i)=><tr key={i}><td>{dateLabel(s.date)}</td><td>{time(s.startsAt)} – {time(s.endsAt)}</td><td><Badge status={s.status==='NEW'?'NEW':s.status==='EXISTS'?'ALREADY SCHEDULED':'OVERLAP'}/></td></tr>)}</tbody></table></div>
          {preview.conflicts>0&&<ErrorBox message="Adjust the date window or weekday hours to resolve overlapping sessions."/>}
        </section>}
        </div>
        <footer className="!m-0 mt-4 flex shrink-0 flex-col items-stretch gap-3 border-t !px-0 !pb-0 !pt-4">
          <ErrorBox message={error}/>
          <p role="status" className={'text-sm '+(preview?.conflicts?'text-red-700':'text-slate-600')}>
            {!preview?'Preview sessions to check the dates, then save your weekly schedule.'
              :preview.conflicts?`${preview.conflicts} overlapping session(s). Adjust the dates or hours and preview again before saving.`
              :preview.toCreate===0?'All matching sessions are already scheduled. There are no new sessions to save.'
              :`${preview.toCreate} new session(s) ready to save${preview.existing?`; ${preview.existing} existing session(s) will be skipped`:''}.`}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" disabled={busy} onClick={()=>setOpen(false)}>Cancel</Button>
          <Button type="submit" form={formId} variant="outline" disabled={busy||!days.some(d=>d.enabled)}>{busy&&!saving?'Checking…':'Preview sessions'}</Button>
          <Button type="button" disabled={busy||!preview||preview.conflicts>0||preview.toCreate===0} onClick={async()=>{
            setBusy(true);setSaving(true);setError('');
            try{const result=await api<Preview>('/staff/schedules/weekly','POST',payload());await onSaved(result.toCreate);setOpen(false);}
            catch(err){setError((err as Error).message);setPreview(null);}finally{setBusy(false);setSaving(false);}
          }}>{saving?'Saving…':'Save weekly schedule'}</Button>
          </div>
        </footer>
      </DialogContent>
    </Dialog>
  </>;
}
