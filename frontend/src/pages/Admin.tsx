import {useEffect,useRef,useState} from 'react';
import {Plus,X} from 'lucide-react';
import {api} from '../api';
import type {Doctor,Permission,User} from '../types';
import {Button} from '../components/ui/button';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from '../components/ui/dialog';
import {ConfirmDialog,Empty,ErrorBox,PatientForm} from '../components/common';

const matches=(query:string,...values:(string|number|undefined)[])=>values.some(value=>String(value??'').toLowerCase().includes(query.trim().toLowerCase()));
const unique=(values:string[])=>Array.from(new Set(values)).sort();

export function Admin(){
  const [receptionists,setReceptionists]=useState<User[]>([]),[patients,setPatients]=useState<User[]>([]),[doctors,setDoctors]=useState<Doctor[]>([]),[permissions,setPermissions]=useState<Permission[]>([]);
  const [error,setError]=useState(''),[message,setMessage]=useState(''),[loading,setLoading]=useState(true),[create,setCreate]=useState(false),[busy,setBusy]=useState(false),[revoke,setRevoke]=useState<Permission|null>(null);
  const [accountId,setAccountId]=useState(''),[selected,setSelected]=useState<number[]>([]),[info,setInfo]=useState(false),[schedule,setSchedule]=useState(false),[availability,setAvailability]=useState(false);
  const [receptionistSearch,setReceptionistSearch]=useState(''),[doctorSearch,setDoctorSearch]=useState(''),[specialty,setSpecialty]=useState(''),[department,setDepartment]=useState('');
  const [assignmentSearch,setAssignmentSearch]=useState(''),[assignmentStaff,setAssignmentStaff]=useState(''),[assignmentDoctor,setAssignmentDoctor]=useState(''),[assignmentAccess,setAssignmentAccess]=useState('');
  const [patientSearch,setPatientSearch]=useState(''),[patientGender,setPatientGender]=useState(''),[ageFrom,setAgeFrom]=useState(''),[ageTo,setAgeTo]=useState('');
  const assignmentForm=useRef<HTMLElement>(null);

  const load=async()=>{
    const [r,p,d,a]=await Promise.all([api<User[]>('/admin/accounts?role=RECEPTIONIST'),api<User[]>('/admin/accounts?role=PATIENT'),api<Doctor[]>('/staff/doctors'),api<Permission[]>('/staff/permissions')]);
    setReceptionists(r);setPatients(p);setDoctors(d);setPermissions(a);
  };
  useEffect(()=>{load().catch(e=>setError(e.message)).finally(()=>setLoading(false));},[]);
  const chooseReceptionist=(id:string)=>{setAccountId(id);setSelected([]);setMessage('');};
  const toggleDoctor=(id:number)=>setSelected(ids=>ids.includes(id)?ids.filter(i=>i!==id):[...ids,id]);
  const doctorById=new Map(doctors.map(d=>[d.id,d]));
  const staffById=new Map(receptionists.map(r=>[r.id,r]));
  const visibleStaff=receptionists.filter(r=>matches(receptionistSearch,r.name,r.email,r.phone));
  const visibleDoctors=doctors.filter(d=>matches(doctorSearch,d.name,d.room,d.specialty,d.department)&&(!specialty||d.specialty===specialty)&&(!department||d.department===department));
  const filteredAssignments=permissions.filter(p=>{
    const doctor=doctorById.get(p.doctorId),staff=staffById.get(p.accountId);
    return matches(assignmentSearch,doctor?.name,doctor?.specialty,doctor?.department,doctor?.room,staff?.name,staff?.email)
      &&(!assignmentStaff||String(p.accountId)===assignmentStaff)&&(!assignmentDoctor||String(p.doctorId)===assignmentDoctor)
      &&(!assignmentAccess||(assignmentAccess==='VIEW'?!p.editInfo&&!p.editSchedule&&!p.editAvailability:assignmentAccess==='INFO'?p.editInfo:assignmentAccess==='SCHEDULE'?p.editSchedule:p.editAvailability));
  });
  const filteredPatients=patients.filter(p=>matches(patientSearch,p.name,p.email,p.phone)&&(!patientGender||p.gender===patientGender)
    &&(ageFrom===''||p.age>=Number(ageFrom))&&(ageTo===''||p.age<=Number(ageTo)));
  const existingSelected=permissions.filter(p=>String(p.accountId)===accountId&&selected.includes(p.doctorId)).length;
  const editAccess=(p:Permission)=>{
    setAccountId(String(p.accountId));setSelected([p.doctorId]);setInfo(p.editInfo);setSchedule(p.editSchedule);setAvailability(p.editAvailability);
    setDoctorSearch('');setSpecialty('');setDepartment('');setError('');setMessage('');assignmentForm.current?.scrollIntoView({behavior:'smooth',block:'start'});
  };

  return <div className="page">
    <span className="eyebrow">HOSPITAL ADMINISTRATION</span><h1>Team & access</h1>
    <p className="page-subtitle">Find your team, select several doctors, and manage their access together.</p>
    <div className="grid gap-4 md:grid-cols-3 mb-6">{[['Doctors',doctors.length],['Receptionists',receptionists.length],['Registered patients',patients.length]].map(([label,count])=><div className="card" key={label}><p className="text-slate-500">{label}</p><strong className="text-3xl">{count}</strong></div>)}</div>
    <ErrorBox message={error}/>{message&&<p role="status" className="rounded bg-teal-50 p-3 my-4">{message}</p>}
    {loading&&<p className="my-4 text-slate-500">Loading team and assignments…</p>}
    <div className="grid gap-6 lg:grid-cols-[1fr_1.5fr]">
      <section className="card">
        <div className="flex flex-wrap justify-between gap-3 mb-5"><h2>Receptionists</h2><Button onClick={()=>setCreate(true)} size="sm"><Plus size={16}/> Create account</Button></div>
        <label>Find a receptionist<input type="search" placeholder="Name, email, or phone" value={receptionistSearch} onChange={e=>setReceptionistSearch(e.target.value)}/></label>
        <p className="my-3 text-xs text-slate-500">{visibleStaff.length} of {receptionists.length} receptionists</p>
        {visibleStaff.map(r=><div key={r.id} className="flex items-center justify-between gap-3 py-3 border-b"><div className="min-w-0"><strong>{r.name}</strong><small className="block break-all text-slate-500">{r.email} · {r.phone}</small><small className="text-slate-500">{permissions.filter(p=>p.accountId===r.id).length} assigned doctors</small></div><Button variant={accountId===String(r.id)?'default':'outline'} size="sm" onClick={()=>{chooseReceptionist(String(r.id));assignmentForm.current?.scrollIntoView({behavior:'smooth'});}}>Select</Button></div>)}
        {!visibleStaff.length&&<Empty>{receptionists.length?'No receptionists match your search.':'No receptionist accounts yet.'}</Empty>}
      </section>
      <section className="card scroll-mt-6" ref={assignmentForm}>
        <h2>Assign doctor access</h2>
        <form className="space-y-4 mt-5" onSubmit={async e=>{
          e.preventDefault();setBusy(true);setError('');setMessage('');
          try{await api('/admin/permissions/bulk','PUT',{accountId:Number(accountId),doctorIds:selected,editInfo:info,editSchedule:schedule,editAvailability:availability});await load();setMessage(`Access saved for ${selected.length} doctor(s) assigned to ${staffById.get(Number(accountId))?.name}.`);setSelected([]);}
          catch(err){setError((err as Error).message);}finally{setBusy(false);}
        }}>
          <fieldset disabled={busy} className="space-y-4">
            <label>Receptionist<select required value={accountId} onChange={e=>chooseReceptionist(e.target.value)}><option value="">Select receptionist</option>{receptionists.filter(r=>visibleStaff.includes(r)||String(r.id)===accountId).map(r=><option value={r.id} key={r.id}>{r.name} · {r.email}</option>)}</select></label>
            <div className="grid gap-3 sm:grid-cols-2"><label className="sm:col-span-2">Find doctors<input type="search" placeholder="Name, room, specialty, or department" value={doctorSearch} onChange={e=>setDoctorSearch(e.target.value)}/></label><label>Specialty<select value={specialty} onChange={e=>setSpecialty(e.target.value)}><option value="">All specialties</option>{unique(doctors.map(d=>d.specialty)).map(s=><option key={s}>{s}</option>)}</select></label><label>Department<select value={department} onChange={e=>setDepartment(e.target.value)}><option value="">All departments</option>{unique(doctors.map(d=>d.department)).map(s=><option key={s}>{s}</option>)}</select></label></div>
            <div className="flex flex-wrap gap-2 items-center"><Button type="button" variant="outline" size="sm" disabled={!visibleDoctors.length} onClick={()=>setSelected(ids=>Array.from(new Set([...ids,...visibleDoctors.map(d=>d.id)])))}>Select all filtered</Button><Button type="button" variant="ghost" size="sm" onClick={()=>setSelected(ids=>ids.filter(id=>!visibleDoctors.some(d=>d.id===id)))}>Deselect filtered</Button><Button type="button" variant="ghost" size="sm" onClick={()=>setSelected([])}>Clear selection</Button></div>
            <div className="max-h-64 overflow-auto rounded-lg border" aria-label="Doctor selection">
              {visibleDoctors.map(d=>{const assigned=permissions.some(p=>String(p.accountId)===accountId&&p.doctorId===d.id);return <label key={d.id} className="flex cursor-pointer items-center gap-3 border-b p-3 last:border-b-0"><input type="checkbox" aria-label={`Assign ${d.name}`} checked={selected.includes(d.id)} onChange={()=>toggleDoctor(d.id)}/><span><strong className="block text-sm">{d.name}</strong><small className="block text-slate-500">{d.specialty} · {d.department} · Room {d.room}{assigned?' · Already assigned':''}</small></span></label>;})}
              {!visibleDoctors.length&&<Empty>No doctors match these filters.</Empty>}
            </div>
            <p className="text-sm font-semibold">{selected.length} doctor(s) selected · {visibleDoctors.length} shown</p>
            {selected.length>0&&<div className="flex flex-wrap gap-2">{selected.map(id=><Button key={id} type="button" variant="outline" size="sm" aria-label={`Remove ${doctorById.get(id)?.name} from selection`} onClick={()=>toggleDoctor(id)}>{doctorById.get(id)?.name}<X size={12}/></Button>)}</div>}
            <p className="text-sm text-slate-500">View access is included. Apply these editing permissions to every selected doctor:</p>
            <label className="checkbox"><input type="checkbox" checked={info} onChange={e=>setInfo(e.target.checked)}/>Doctor information</label>
            <label className="checkbox"><input type="checkbox" checked={schedule} onChange={e=>setSchedule(e.target.checked)}/>Schedules / calendar</label>
            <label className="checkbox"><input type="checkbox" checked={availability} onChange={e=>setAvailability(e.target.checked)}/>Availability</label>
            {existingSelected>0&&<p className="text-xs text-slate-500">The chosen permissions will replace existing permissions for {existingSelected} selected assignment(s).</p>}
            <Button disabled={busy||!accountId||!selected.length}>{busy?'Saving…':`Save access for ${selected.length} doctor(s)`}</Button>
          </fieldset>
        </form>
      </section>
    </div>
    <section className="card mt-6">
      <h2>Current assignments</h2>
      <div className="filter-grid my-5">
        <label>Search assignments<input type="search" placeholder="Receptionist, doctor, or specialty" value={assignmentSearch} onChange={e=>setAssignmentSearch(e.target.value)}/></label>
        <label>Filter receptionist<select value={assignmentStaff} onChange={e=>setAssignmentStaff(e.target.value)}><option value="">All receptionists</option>{receptionists.map(r=><option value={r.id} key={r.id}>{r.name}</option>)}</select></label>
        <label>Filter doctor<select value={assignmentDoctor} onChange={e=>setAssignmentDoctor(e.target.value)}><option value="">All doctors</option>{doctors.map(d=><option value={d.id} key={d.id}>{d.name}</option>)}</select></label>
        <label>Permission filter<select value={assignmentAccess} onChange={e=>setAssignmentAccess(e.target.value)}><option value="">All permissions</option><option value="VIEW">View only</option><option value="INFO">Can edit doctor information</option><option value="SCHEDULE">Can edit schedules</option><option value="AVAILABILITY">Can edit availability</option></select></label>
      </div>
      <div className="flex justify-between items-center mb-4"><p className="text-xs text-slate-500">{filteredAssignments.length} of {permissions.length} assignments</p><Button variant="ghost" size="sm" onClick={()=>{setAssignmentSearch('');setAssignmentStaff('');setAssignmentDoctor('');setAssignmentAccess('');}}>Clear assignment filters</Button></div>
      <div className="table-wrap"><table><thead><tr><th>Receptionist</th><th>Doctor</th><th>Info</th><th>Schedules</th><th>Availability</th><th>Actions</th></tr></thead><tbody>{filteredAssignments.map(p=><tr key={p.id}><td>{staffById.get(p.accountId)?.name}<small>{staffById.get(p.accountId)?.email}</small></td><td>{doctorById.get(p.doctorId)?.name}<small>{doctorById.get(p.doctorId)?.specialty}</small></td><td>{p.editInfo?'Edit':'View'}</td><td>{p.editSchedule?'Edit':'View'}</td><td>{p.editAvailability?'Edit':'View'}</td><td><div className="flex gap-2"><Button variant="outline" size="sm" onClick={()=>editAccess(p)}>Edit access</Button><Button variant="outline" size="sm" onClick={()=>setRevoke(p)}>Revoke</Button></div></td></tr>)}</tbody></table>{!filteredAssignments.length&&<Empty>No assignments match these filters.</Empty>}</div>
    </section>
    <section className="card mt-6">
      <h2>Patient directory</h2>
      <div className="filter-grid my-5"><label>Search patients<input type="search" placeholder="Name, email, or phone" value={patientSearch} onChange={e=>setPatientSearch(e.target.value)}/></label><label>Gender filter<select value={patientGender} onChange={e=>setPatientGender(e.target.value)}><option value="">All genders</option>{unique(patients.map(p=>p.gender)).map(g=><option key={g}>{g}</option>)}</select></label><label>Minimum age<input type="number" min={0} max={130} value={ageFrom} onChange={e=>setAgeFrom(e.target.value)}/></label><label>Maximum age<input type="number" min={0} max={130} value={ageTo} onChange={e=>setAgeTo(e.target.value)}/></label></div>
      <div className="flex justify-between items-center mb-4"><p className="text-xs text-slate-500">{filteredPatients.length} of {patients.length} patients</p><Button variant="ghost" size="sm" onClick={()=>{setPatientSearch('');setPatientGender('');setAgeFrom('');setAgeTo('');}}>Clear patient filters</Button></div>
      <div className="table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Age</th><th>Gender</th></tr></thead><tbody>{filteredPatients.map(p=><tr key={p.id}><td>{p.name}</td><td>{p.email}</td><td>{p.phone}</td><td>{p.age}</td><td>{p.gender}</td></tr>)}</tbody></table>{!filteredPatients.length&&<Empty>{patients.length?'No patients match these filters.':'No registered patients yet. Guest patients appear in appointment records.'}</Empty>}</div>
    </section>
    <Dialog open={create} onOpenChange={setCreate}><DialogContent><DialogTitle className="text-xl font-bold">Create Receptionist account</DialogTitle><DialogDescription className="my-3">Create the account, then assign several doctors at once using the access form.</DialogDescription><PatientForm password submit="Create Receptionist" onSubmit={async(patient,password)=>{const staff=await api<User>('/admin/receptionists','POST',{patient,password});await load();setCreate(false);chooseReceptionist(String(staff.id));setMessage('Receptionist created. Select doctors to assign access.');}}/></DialogContent></Dialog>
    <ConfirmDialog open={!!revoke} onClose={()=>setRevoke(null)} title="Revoke doctor access?" description="This receptionist will immediately lose access to this doctor's information and appointments." busy={busy} onConfirm={async()=>{setBusy(true);setError('');try{await api(`/admin/permissions/${revoke!.id}`,'DELETE');await load();setRevoke(null);setMessage('Doctor access revoked.');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}/>
  </div>;
}
