import {describe,it,expect} from 'vitest';
import {active,roundRobin,upcoming,setHospitalTimeZone,today} from './api';
describe('receptionist queue',()=>{
  it('interleaves each doctor’s next patient rather than globally sorting serials',()=>{
    const rows=[{doctorId:1,serial:1,status:'CONFIRMED'},{doctorId:1,serial:2,status:'WAITING'},{doctorId:2,serial:1,status:'CONFIRMED'},{doctorId:2,serial:2,status:'CONFIRMED'},{doctorId:3,serial:10,status:'CONFIRMED'},{doctorId:3,serial:11,status:'NO_SHOW'}];
    expect(roundRobin(rows.filter(active)).map(a=>[a.doctorId,a.serial])).toEqual([[1,1],[2,1],[3,10],[1,2],[2,2]]);
  });
  it('keeps inactive patients out while retaining reschedule-pending patients',()=>{
    expect(['CANCELLED','VISITED','NO_SHOW'].some(status=>active({status}))).toBe(false);
    expect(active({status:'RESCHEDULE_PENDING'})).toBe(true);
  });
});
describe('hospital time and appointment history',()=>{
  it('keeps past active appointments in history and excludes future cancelled appointments',()=>{
    expect(upcoming({status:'CONFIRMED',date:'2000-01-01',time:'09:00',consultationAt:'2000-01-01T03:00:00Z'})).toBe(false);
    expect(upcoming({status:'CONFIRMED',date:'2099-01-01',time:'09:00',consultationAt:'2099-01-01T03:00:00Z'})).toBe(true);
    expect(upcoming({status:'CANCELLED',date:'2099-01-01',time:'09:00',consultationAt:'2099-01-01T03:00:00Z'})).toBe(false);
  });
  it('calculates date offsets across months in the configured hospital timezone',()=>{
    setHospitalTimeZone('Asia/Dhaka');const first=new Date(today()+'T12:00:00Z');first.setUTCDate(first.getUTCDate()+31);expect(today(31)).toBe(first.toISOString().slice(0,10));
  });
});
