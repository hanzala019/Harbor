import {createContext,useContext,useEffect,useState,type ReactNode} from 'react';
import {api} from './api';
import type {User} from './types';
type Session={token:string;user:User};
const Context=createContext<{user:User|null;loading:boolean;accept:(s:Session)=>void;refresh:()=>Promise<void>;logout:()=>Promise<void>}>({user:null,loading:true,accept:()=>{},refresh:async()=>{},logout:async()=>{}});
export function AuthProvider({children}:{children:ReactNode}){
  const [user,setUser]=useState<User|null>(null),[loading,setLoading]=useState(true);
  useEffect(()=>{const expired=()=>setUser(null);window.addEventListener('harbor:session-expired',expired);return()=>window.removeEventListener('harbor:session-expired',expired);},[]);
  const refresh=async()=>{setUser(await api<User>('/me'));};
  useEffect(()=>{if(!sessionStorage.getItem('hospital-token')){setLoading(false);return;}refresh().catch(()=>{sessionStorage.removeItem('hospital-token');setUser(null);}).finally(()=>setLoading(false));},[]);
  const accept=(s:Session)=>{sessionStorage.setItem('hospital-token',s.token);setUser(s.user);};
  const logout=async()=>{try{await api('/auth/logout','POST');}finally{sessionStorage.removeItem('hospital-token');setUser(null);}};
  return <Context.Provider value={{user,loading,accept,refresh,logout}}>{children}</Context.Provider>;
}
export const useAuth=()=>useContext(Context);
