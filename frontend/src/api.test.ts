import {describe,it,expect} from 'vitest';
import {active,roundRobin} from './api';
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
