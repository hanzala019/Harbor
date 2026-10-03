import React from 'react';
import ReactDOM from 'react-dom/client';
import {BrowserRouter,Link,NavLink,Navigate,Route,Routes,useNavigate} from 'react-router-dom';
import {HeartPulse,LogOut,UserRound} from 'lucide-react';
import {AuthProvider,useAuth} from './auth';
import {Home,Doctors,DoctorDetail} from './pages/Public';
import {Login,Register,Profile,MyAppointments} from './pages/Account';
import {StaffAppointments,StaffDoctors,Admin} from './pages/Staff';
import {Settings} from './pages/Settings';
import {GuestAppointment} from './pages/GuestAppointment';
import {StaffTools} from './pages/StaffTools';
import {StaffBooking} from './pages/StaffBooking';
import {ForgotPassword,ResetPassword} from './pages/PasswordRecovery';
import {VisitInformation} from './components/VisitInfo';
import {Notifications} from './components/Notifications';
import {Button} from './components/ui/button';
import {api,hospitalInfo,hospitalTimeZone,setHospitalInfo,setHospitalTimeZone} from './api';
import './styles.css';
function Protected({roles,children}:{roles:string[];children:React.ReactNode}){const {user,loading}=useAuth();if(loading)return <div className="page">Loading account…</div>;if(!user)return <Navigate to="/login" replace/>;if(!roles.includes(user.role))return <Navigate to="/" replace/>;return children;}
function App(){const {user,logout}=useAuth(),navigate=useNavigate();return <>
  <header className="site-header"><div className="nav-inner"><Link to="/" className="brand"><span><HeartPulse size={25}/></span><div>HARBOR<small>HOSPITAL & CARE</small></div></Link><nav aria-label="Main navigation"><NavLink to="/" end>Home</NavLink><NavLink to="/doctors">Find a doctor</NavLink>{user?.role==='PATIENT'&&<NavLink to="/my-appointments">My appointments</NavLink>}{user&&user.role!=='PATIENT'&&<><NavLink to="/staff/appointments">Appointments</NavLink><NavLink to="/staff/doctors">Doctors</NavLink><NavLink to="/staff/tools">Tools & history</NavLink></>}{user?.role==='ADMIN'&&<NavLink to="/admin">Team & access</NavLink>}</nav>{user?<details className="account-menu"><summary><span className="avatar">{user.name.charAt(0)}</span><span className="hidden sm:block">{user.name}</span></summary><div><Link to="/profile"><UserRound size={16}/> My profile</Link><Link to="/settings">Account security</Link><button onClick={async()=>{await logout().catch(()=>{});navigate('/');}}><LogOut size={16}/> Log out</button></div></details>:<Button asChild variant="outline" size="sm"><Link to="/login">Login</Link></Button>}</div></header>
  <main><Routes><Route path="/" element={<Home/>}/><Route path="/doctors" element={<Doctors/>}/><Route path="/doctors/:id" element={<DoctorDetail/>}/><Route path="/guest-appointment" element={<GuestAppointment/>}/><Route path="/login" element={<Login/>}/><Route path="/forgot-password" element={<ForgotPassword/>}/><Route path="/reset-password" element={<ResetPassword/>}/><Route path="/visit-info" element={<VisitInformation/>}/><Route path="/register" element={<Register/>}/><Route path="/my-appointments" element={<Protected roles={['PATIENT']}><MyAppointments/></Protected>}/><Route path="/profile" element={<Protected roles={['PATIENT','ADMIN','RECEPTIONIST']}><Profile/></Protected>}/><Route path="/settings" element={<Protected roles={['PATIENT','ADMIN','RECEPTIONIST']}><Settings/></Protected>}/><Route path="/staff/appointments" element={<Protected roles={['ADMIN','RECEPTIONIST']}><StaffAppointments/></Protected>}/><Route path="/staff/book" element={<Protected roles={['ADMIN','RECEPTIONIST']}><StaffBooking/></Protected>}/><Route path="/staff/doctors" element={<Protected roles={['ADMIN','RECEPTIONIST']}><StaffDoctors/></Protected>}/><Route path="/staff/tools" element={<Protected roles={['ADMIN','RECEPTIONIST']}><StaffTools/></Protected>}/><Route path="/admin" element={<Protected roles={['ADMIN']}><Admin/></Protected>}/><Route path="*" element={<div className="page"><h1>Page not found</h1><Link to="/">Return home</Link></div>}/></Routes></main>
  <footer><Link to="/" className="brand"><HeartPulse/> HARBOR HOSPITAL</Link><p>Appointment times: <strong>{hospitalTimeZone}</strong></p><Link to="/visit-info" className="text-primary">Visit & contact information</Link><button onClick={()=>{const prefix=`harbor:${user?.id??'guest'}:`;try{Object.keys(localStorage).filter(k=>k.startsWith(prefix)).forEach(k=>localStorage.removeItem(k));}catch{}window.dispatchEvent(new Event('harbor:prefs-reset'));window.dispatchEvent(new CustomEvent('harbor:notice',{detail:'Saved filters and table preferences reset'}));}}>Reset saved filters</button><small>For a medical emergency, contact your local emergency services.</small></footer><Notifications/>
</>;}
async function boot(){try{const config=await api<{timeZone:string}&typeof hospitalInfo>('/config');setHospitalTimeZone(config.timeZone);setHospitalInfo(config);}catch{}ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><AuthProvider><App/></AuthProvider></BrowserRouter></React.StrictMode>);}
boot();
