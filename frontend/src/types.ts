export type User = {id:number;name:string;email:string;role:'PATIENT'|'RECEPTIONIST'|'ADMIN';age:number;gender:string;phone:string};
export type Patient = Pick<User,'name'|'email'|'age'|'gender'|'phone'>;
export type Doctor = {id:number;name:string;specialty:string;department:string;room:string;biography:string};
export type Schedule = {id:number;doctorId:number;date:string;startsAt:string;endsAt:string;minutes:number;available:boolean};
export type ScheduleView = {schedule:Schedule;slots:{index:number;time:string;estimatedSerial:number}[];appointmentCount:number};
export type Appointment = Patient & {id:number;scheduleId:number;doctorId:number;doctor:string;specialty:string;room:string;date:string;time:string;arrivalTime:string;serial:number;status:string};
export type Permission = {id:number;accountId:number;doctorId:number;editInfo:boolean;editSchedule:boolean;editAvailability:boolean};
