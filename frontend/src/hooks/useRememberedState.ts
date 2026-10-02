import {useEffect,useState,type Dispatch,type SetStateAction} from 'react';
import {useAuth} from '../auth';
export function useRememberedState<T>(name:string,initial:T):[T,Dispatch<SetStateAction<T>>]{
  const {user}=useAuth();const key=`harbor:${user?.id??'guest'}:${name}`;
  const read=()=>{try{const raw=localStorage.getItem(key);return raw?JSON.parse(raw) as T:initial;}catch{return initial;}};
  const [entry,setEntry]=useState(()=>({key,value:read()}));const value=entry.key===key?entry.value:read();
  useEffect(()=>{const reset=()=>setEntry({key,value:initial});window.addEventListener('harbor:prefs-reset',reset);return()=>window.removeEventListener('harbor:prefs-reset',reset);},[key]);
  useEffect(()=>{if(entry.key!==key)setEntry({key,value:read()});else try{localStorage.setItem(key,JSON.stringify(entry.value));}catch{}},[key,entry]);
  return [value,next=>setEntry({key,value:typeof next==='function'?(next as (p:T)=>T)(value):next})];
}
