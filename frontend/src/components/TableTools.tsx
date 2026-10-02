import {useEffect,useState} from 'react';
import {Button} from './ui/button';
import {useRememberedState} from '../hooks/useRememberedState';
export type SortColumn<T>={key:string;label:string;value:(row:T)=>string|number};
export function useTable<T>(rows:T[],columns:SortColumn<T>[],name:string,ordered=false){
  const [sort,setSort]=useRememberedState(name+':sort',columns[0]?.key||''),[descending,setDescending]=useRememberedState(name+':descending',false),[size,setSize]=useRememberedState(name+':size',15),[page,setPage]=useState(0);
  useEffect(()=>setPage(0),[rows.length,sort,descending,size]);
  const column=columns.find(c=>c.key===sort)||columns[0];
  const sorted=ordered?rows:[...rows].sort((a,b)=>{const av=column.value(a),bv=column.value(b);return (typeof av==='number'&&typeof bv==='number'?av-bv:String(av).localeCompare(String(bv)))*(descending?-1:1);});
  const pages=Math.max(1,Math.ceil(rows.length/size)),current=Math.min(page,pages-1);
  const controls=<div className="flex flex-wrap items-center justify-between gap-3 my-3 text-sm"><div className="flex flex-wrap items-center gap-2">{!ordered&&<><label>Sort by<select aria-label={`Sort ${name}`} className="!mt-0 w-auto" value={sort} onChange={e=>setSort(e.target.value)}>{columns.map(c=><option key={c.key} value={c.key}>{c.label}</option>)}</select></label><Button size="sm" variant="ghost" onClick={()=>setDescending(!descending)}>{descending?'Descending ↓':'Ascending ↑'}</Button></>}<label>Rows<select className="!mt-0 w-auto" aria-label={`Rows ${name}`} value={size} onChange={e=>setSize(Number(e.target.value))}>{[15,30,50].map(s=><option key={s}>{s}</option>)}</select></label></div><div className="flex items-center gap-2"><span>{rows.length} results · Page {current+1}/{pages}</span><Button variant="outline" size="sm" disabled={!current} onClick={()=>setPage(current-1)}>Previous</Button><Button variant="outline" size="sm" disabled={current>=pages-1} onClick={()=>setPage(current+1)}>Next</Button></div></div>;
  return {rows:sorted.slice(current*size,(current+1)*size),controls};
}
