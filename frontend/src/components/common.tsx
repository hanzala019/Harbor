import { useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from './ui/button';
import { Dialog,DialogContent,DialogTitle,DialogDescription } from './ui/dialog';
import { dateLabel,time } from '../api';
import type { Appointment,Patient } from '../types';
export const patientSchema=z.object({name:z.string().trim().min(2,'Enter a full name').max(150),email:z.string().email(),age:z.coerce.number().int().min(0).max(130),gender:z.string().min(1),phone:z.string().min(5).max(40)});
export function PatientForm({initial,onSubmit,password=false,submit='Continue',children,editableEmail=false}:{initial?:Partial<Patient>;onSubmit:(p:Patient,password?:string)=>Promise<void>;password?:boolean;submit?:string;children?:ReactNode;editableEmail?:boolean}){
  const schema=password?patientSchema.extend({password:z.string().min(10,'Use at least 10 characters').max(72)}):patientSchema;
  const {register,handleSubmit,formState:{errors,isSubmitting}}=useForm<Patient & {password?:string}>({resolver:zodResolver(schema),defaultValues:{gender:'',...initial}});
  const [error,setError]=useState('');
  return <form onSubmit={handleSubmit(async data=>{setError('');try{await onSubmit(data,data.password);}catch(e){setError((e as Error).message);}})} className="space-y-4">
    <div className="form-grid">{(['name','email','age','phone'] as const).map(key=><label key={key}>{({name:'Full name',email:'Email address',age:'Age',phone:'Phone number'})[key]}<input required readOnly={key==='email'&&!!initial?.email&&!password&&!editableEmail} {...register(key)} type={key==='age'?'number':key==='email'?'email':'text'} autoComplete={key==='name'?'name':key==='email'?'email':key==='phone'?'tel':'off'}/>{errors[key]&&<small className="text-red-600">{errors[key]?.message}</small>}</label>)}</div>
    <label>Gender<select aria-label="Gender" required {...register('gender')}><option value="">Select gender</option><option>Female</option><option>Male</option><option>Other</option><option>Prefer not to say</option></select>{errors.gender&&<small className="text-red-600">{errors.gender.message}</small>}</label>
    {password&&<label>Password<input type="password" autoComplete="new-password" {...register('password')}/>{errors.password&&<small className="text-red-600">{errors.password.message}</small>}</label>}
    {children}<ErrorBox message={error}/><Button disabled={isSubmitting} className="w-full">{isSubmitting?'Saving…':submit}</Button>
  </form>;
}
export function ErrorBox({message}:{message:string}){return message?<p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{message}</p>:null;}
export function Empty({children}:{children:ReactNode}){return <div className="rounded-xl border border-dashed p-10 text-center text-slate-500">{children}</div>;}
export function Badge({status}:{status:string}){return <span className={'badge '+(['CANCELLED','NO_SHOW','FAILED','DISABLED','UNAVAILABLE','OVERLAP'].includes(status)?'bg-red-50 text-red-700':['RESCHEDULE_PENDING','QUEUED','RETRYING'].includes(status)?'bg-amber-50 text-amber-800':'bg-teal-50 text-teal-800')}>{status.replaceAll('_',' ')}</span>;}
export function AppointmentDetails({a}:{a:Appointment}){return <div className="details"><div><span>Doctor</span><strong>{a.doctor}</strong><small>{a.specialty}</small></div><div><span>Date & consultation</span><strong>{dateLabel(a.date)}</strong><small>{time(a.time)} · Arrive {time(a.arrivalTime)}{a.timeZone?' · '+a.timeZone:''}</small></div><div><span>Your serial</span><strong>#{a.serial}</strong></div><div><span>Room</span><strong>{a.room}</strong></div></div>;}
export function ConfirmDialog({open,onClose,onConfirm,title,description,busy=false}:{open:boolean;onClose:()=>void;onConfirm:()=>void;title:string;description:string;busy?:boolean}){return <Dialog open={open} onOpenChange={v=>{if(!v)onClose();}}><DialogContent><DialogTitle className="text-xl font-bold">{title}</DialogTitle><DialogDescription className="my-4 text-slate-600">{description}</DialogDescription><div className="flex justify-end gap-3"><Button variant="outline" onClick={onClose}>Keep unchanged</Button><Button disabled={busy} onClick={onConfirm}>{busy?'Saving…':'Confirm'}</Button></div></DialogContent></Dialog>;}
