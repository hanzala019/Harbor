package com.hospital;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HospitalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    String admin;
    long doctor, session;
    Map<String,Object> patient(String email){return Map.of("name","Test Patient","email",email,"age",35,"gender","Other","phone","0123456789");}
    String email(){return UUID.randomUUID()+"@hospital.test";}
    MvcResult request(String method,String path,Object body,String token) throws Exception {
        var req=switch(method){case "POST"->post("/api"+path);case "PUT"->put("/api"+path);case "PATCH"->patch("/api"+path);case "DELETE"->delete("/api"+path);default->get("/api"+path);};
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req).andReturn();
    }
    JsonNode success(String method,String path,Object body,String token) throws Exception {
        var r=request(method,path,body,token);assertTrue(r.getResponse().getStatus()>=200&&r.getResponse().getStatus()<300,r.getResponse().getContentAsString());
        String text=r.getResponse().getContentAsString();return text.isBlank()?json.nullNode():json.readTree(text);
    }
    long schedule(String date,int endHour) throws Exception {return success("POST","/staff/schedules",Map.of("doctorId",doctor,"date",date,"startsAt","09:00","endsAt",String.format("%02d:00",endHour),"minutes",20,"available",true),admin).get("id").asLong();}
    JsonNode book(long s,int slot,String mail) throws Exception{return success("POST","/appointments",Map.of("scheduleId",s,"slotIndex",slot,"patient",patient(mail)),null);}
    String login(String email,String password) throws Exception{return success("POST","/auth/login",Map.of("email",email,"password",password),null).get("token").asText();}
    @BeforeEach void setup() throws Exception {
        admin=login("integration-admin@hospital.test","integration-admin-password");
        doctor=success("POST","/admin/doctors",Map.of("name","Doctor "+UUID.randomUUID(),"specialty","General","department","Primary","room","12","biography","Test doctor"),admin).get("id").asLong();
        session=schedule("2099-05-01",12);
    }
    @Test void guestBookingIsConfirmedWithoutRegistrationAndClaimIsOneUse() throws Exception {
        var b=book(session,0,email());long id=b.path("appointment").path("id").asLong();
        assertEquals("CONFIRMED",b.path("appointment").path("status").asText());assertEquals(1,b.path("appointment").path("serial").asInt());
        assertEquals(401,request("GET","/appointments/mine",null,null).getResponse().getStatus());
        var payload=Map.of("appointmentId",id,"claimToken",b.get("claimToken").asText(),"password","patient-password");
        var claim=success("POST","/auth/claim",payload,null);assertEquals("PATIENT",claim.path("user").path("role").asText());
        assertEquals(id,success("GET","/appointments/mine",null,claim.get("token").asText()).get(0).get("id").asLong());
        assertEquals(403,request("POST","/auth/claim",payload,null).getResponse().getStatus());
    }
    @Test void simultaneousBookingsCannotReserveTheSameDoctorSlot() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{start.await();return request("POST","/appointments",Map.of("scheduleId",session,"slotIndex",0,"patient",patient(email())),null).getResponse().getStatus();};
            Future<Integer> a=pool.submit(call),b=pool.submit(call);start.countDown();
            var results=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));Collections.sort(results);assertEquals(List.of(200,409),results);
        }finally{pool.shutdownNow();}
    }
    @Test void cancelledSlotsAreReusableAndSerialsRemainDoctorLocal() throws Exception {
        var first=book(session,0,email());success("PATCH","/appointments/"+first.path("appointment").path("id").asLong()+"/status",Map.of("status","CANCELLED"),admin);
        assertEquals(2,book(session,0,email()).path("appointment").path("serial").asInt());
        long other=schedule("2099-05-02",12);assertEquals(1,book(other,0,email()).path("appointment").path("serial").asInt());
    }
    @Test void transfersPreserveOrderSkipInactiveAndRecalculateSerials() throws Exception {
        var a=book(session,0,email());var cancelled=book(session,1,email());var c=book(session,2,email());
        success("PATCH","/appointments/"+cancelled.path("appointment").path("id").asLong()+"/status",Map.of("status","CANCELLED"),admin);
        long target=schedule("2099-05-02",12);
        var moved=success("POST","/staff/schedules/"+session+"/transfer",Map.of("targetId",target),admin);
        assertEquals(2,moved.size());assertEquals(a.path("appointment").path("id").asLong(),moved.get(0).get("id").asLong());assertEquals(c.path("appointment").path("id").asLong(),moved.get(1).get("id").asLong());
        assertEquals(1,moved.get(0).get("serial").asInt());assertEquals(2,moved.get(1).get("serial").asInt());assertEquals("09:20:00",moved.get(1).get("time").asText());
        assertEquals(0,success("GET","/staff/schedules/"+session+"/affected",null,admin).size());
    }
    @Test void insufficientTransferCapacityLeavesOriginalAppointmentsIntact() throws Exception {
        book(session,0,email());book(session,1,email());book(session,2,email());book(session,3,email());long target=schedule("2099-05-02",10);
        assertEquals(409,request("POST","/staff/schedules/"+session+"/transfer",Map.of("targetId",target),admin).getResponse().getStatus());
        assertEquals(4,success("GET","/staff/schedules/"+session+"/affected",null,admin).size());
        assertEquals(0,success("GET","/staff/schedules/"+target+"/affected",null,admin).size());
    }
    @Test void receptionistAssignmentsAndGranularPermissionsAreEnforced() throws Exception {
        String mail=email();long staff=success("POST","/admin/receptionists",Map.of("patient",patient(mail),"password","staff-password"),admin).get("id").asLong();String token=login(mail,"staff-password");
        assertEquals(0,success("GET","/staff/doctors",null,token).size());assertEquals(403,request("GET","/staff/schedules/"+session+"/affected",null,token).getResponse().getStatus());
        long permission=success("PUT","/admin/permissions",Map.of("accountId",staff,"doctorId",doctor,"editInfo",false,"editSchedule",true,"editAvailability",false),admin).get("id").asLong();
        assertEquals(1,success("GET","/staff/doctors",null,token).size());assertEquals(403,request("PATCH","/staff/schedules/"+session+"/availability",Map.of("available",false),token).getResponse().getStatus());
        assertEquals(403,request("PUT","/staff/schedules/"+session,Map.of("doctorId",doctor,"date","2099-05-01","startsAt","09:00","endsAt","12:00","minutes",20,"available",false),token).getResponse().getStatus());
        assertEquals(403,request("POST","/admin/receptionists",Map.of("patient",patient(email()),"password","staff-password"),token).getResponse().getStatus());
        success("DELETE","/admin/permissions/"+permission,null,admin);assertEquals(0,success("GET","/staff/doctors",null,token).size());
    }
    @Test void publicRegistrationCannotEscalateRoleAndLogoutRevokesToken() throws Exception {
        var s=success("POST","/auth/register",Map.of("patient",patient(email()),"password","patient-password","role","ADMIN"),null);
        assertEquals("PATIENT",s.path("user").path("role").asText());String token=s.get("token").asText();assertEquals(403,request("GET","/admin/accounts?role=PATIENT",null,token).getResponse().getStatus());
        success("POST","/auth/logout",null,token);assertEquals(401,request("GET","/me",null,token).getResponse().getStatus());
    }
    @Test void registeredPatientsUseSavedDetailsAndCannotReadOtherBookings() throws Exception {
        var s=success("POST","/auth/register",Map.of("patient",patient(email()),"password","patient-password"),null);String token=s.get("token").asText();
        var a=success("POST","/appointments",Map.of("scheduleId",session,"slotIndex",0),token);
        assertEquals("Test Patient",a.path("appointment").path("name").asText());
        var other=success("POST","/auth/register",Map.of("patient",patient(email()),"password","patient-password"),null).get("token").asText();
        assertEquals(0,success("GET","/appointments/mine",null,other).size());assertEquals(403,request("PATCH","/appointments/"+a.path("appointment").path("id").asLong()+"/status",Map.of("status","CANCELLED"),other).getResponse().getStatus());
    }
    @Test void cancellationPreservesConsultationTimeWhileFreeingTheSlot() throws Exception {
        var appointment=book(session,2,email()).path("appointment");long id=appointment.path("id").asLong();
        var cancelled=success("PATCH","/appointments/"+id+"/status",Map.of("status","CANCELLED"),admin);
        assertEquals(appointment.get("time").asText(),cancelled.get("time").asText());assertEquals(appointment.get("consultationAt").asText(),cancelled.get("consultationAt").asText());
        assertEquals("09:40:00",book(session,2,email()).path("appointment").path("time").asText());
    }
    String guestToken(long id){String body=jdbc.queryForObject("select body from mail_jobs where appointment_id=? order by id desc limit 1",String.class,id);var matcher=java.util.regex.Pattern.compile("guest-appointment#([A-Za-z0-9_-]+)").matcher(body);assertTrue(matcher.find());return matcher.group(1);}
    @Test void guestLinksAreScopedHashedRevocableAndExpire() throws Exception {
        var booked=book(session,0,email());long id=booked.path("appointment").path("id").asLong();String raw=guestToken(id);
        assertEquals(id,success("POST","/guest/view",Map.of("token",raw,"appointmentId",999),null).get("id").asLong());
        assertNotEquals(raw,jdbc.queryForObject("select hash from guest_links where appointment_id=?",String.class,id));
        assertEquals(404,request("POST","/guest/view",Map.of("token","invalid"),null).getResponse().getStatus());
        jdbc.update("update guest_links set expires_at=? where appointment_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),id);
        assertEquals(404,request("POST","/guest/view",Map.of("token",raw),null).getResponse().getStatus());
        long other=book(session,1,email()).path("appointment").path("id").asLong();String otherToken=guestToken(other);
        assertEquals("CANCELLED",success("POST","/guest/cancel",Map.of("token",otherToken),null).get("status").asText());
        assertEquals(404,request("POST","/guest/view",Map.of("token",otherToken),null).getResponse().getStatus());
    }
    @Test void disablingResettingAndRevokingReceptionistSessionsWorks() throws Exception {
        String mail=email();long id=success("POST","/admin/receptionists",Map.of("patient",patient(mail),"password","staff-password"),admin).get("id").asLong();String token=login(mail,"staff-password");
        success("PATCH","/admin/accounts/"+id,Map.of("enabled",false),admin);
        assertEquals(401,request("GET","/me",null,token).getResponse().getStatus());assertEquals(401,request("POST","/auth/login",Map.of("email",mail,"password","staff-password"),null).getResponse().getStatus());
        success("PATCH","/admin/accounts/"+id,Map.of("enabled",true,"password","new-staff-password","revokeSessions",true),admin);
        String fresh=login(mail,"new-staff-password");success("PATCH","/admin/accounts/"+id,Map.of("revokeSessions",true),admin);assertEquals(401,request("GET","/me",null,fresh).getResponse().getStatus());
    }
    @Test void passwordChangeRequiresCurrentPasswordAndRevokesAllSessions() throws Exception {
        String mail=email();var registration=success("POST","/auth/register",Map.of("patient",patient(mail),"password","patient-password"),null);String token=registration.get("token").asText();
        assertEquals(403,request("PUT","/me/password",Map.of("currentPassword","wrong","newPassword","changed-password"),token).getResponse().getStatus());
        success("PUT","/me/password",Map.of("currentPassword","patient-password","newPassword","changed-password"),token);
        assertEquals(401,request("GET","/me",null,token).getResponse().getStatus());assertEquals(401,request("POST","/auth/login",Map.of("email",mail,"password","patient-password"),null).getResponse().getStatus());assertNotNull(login(mail,"changed-password"));
    }
    @Test void duplicateBookingRequiresExplicitAuthorizedOverride() throws Exception {
        String mail=email();book(session,0,mail);
        var input=Map.of("scheduleId",session,"slotIndex",1,"patient",patient(mail));assertEquals(409,request("POST","/appointments",input,null).getResponse().getStatus());
        assertEquals(1,success("POST","/staff/appointments/check-duplicate",Map.of("scheduleId",session,"email",mail,"phone","0123456789"),admin).get("matches").asInt());
        assertEquals(409,request("POST","/appointments",input,admin).getResponse().getStatus());
        assertEquals(2,success("POST","/appointments",Map.of("scheduleId",session,"slotIndex",1,"patient",patient(mail),"allowDuplicate",true),admin).path("appointment").path("serial").asInt());
    }
    @Test void selectiveTransfersKeepUnselectedPatientsAndOnlyNotifySelectedPatients() throws Exception {
        long a=book(session,0,email()).path("appointment").path("id").asLong(),b=book(session,1,email()).path("appointment").path("id").asLong(),c=book(session,2,email()).path("appointment").path("id").asLong();long target=schedule("2099-05-02",12);
        var moved=success("POST","/staff/schedules/"+session+"/transfer",Map.of("targetId",target,"appointmentIds",List.of(c,a)),admin);
        assertEquals(a,moved.get(0).get("id").asLong());assertEquals(c,moved.get(1).get("id").asLong());assertEquals(2,moved.get(1).get("serial").asInt());
        assertEquals(b,success("GET","/staff/schedules/"+session+"/affected",null,admin).get(0).get("id").asLong());
        assertEquals(1,jdbc.queryForObject("select count(*) from mail_jobs where appointment_id=?",Integer.class,b));assertEquals(2,jdbc.queryForObject("select count(*) from mail_jobs where appointment_id=?",Integer.class,a));
    }
    @Test void templatesCopyWeeksApplySafelyAndCanBeDeleted() throws Exception {
        var copied=success("GET","/staff/schedules/copy-week?doctorId="+doctor+"&from=2099-05-01",null,admin);
        var template=success("POST","/staff/templates",Map.of("doctorId",doctor,"name","Morning template","minutes",20,"days",copied.get("days")),admin);
        assertEquals(1,success("GET","/staff/templates?doctorId="+doctor,null,admin).size());
        success("POST","/staff/schedules/weekly",Map.of("doctorId",doctor,"from","2099-05-08","to","2099-05-14","minutes",20,"days",template.get("days")),admin);
        assertEquals(1,success("GET","/schedules?doctorId="+doctor+"&date=2099-05-08",null,null).size());
        success("DELETE","/staff/templates/"+template.get("id").asLong(),null,admin);assertEquals(0,success("GET","/staff/templates?doctorId="+doctor,null,admin).size());
    }
    @Test void emailStatusDoesNotExposeBodiesOrPrivateLinksAndResendsFailedJobs() throws Exception {
        long id=book(session,0,email()).path("appointment").path("id").asLong();String raw=guestToken(id);
        var jobs=success("GET","/staff/mail",null,admin);assertFalse(jobs.toString().contains(raw));assertFalse(jobs.toString().contains("\"body\""));
        assertEquals(409,request("POST","/staff/appointments/"+id+"/resend",null,admin).getResponse().getStatus());
        jdbc.update("update mail_jobs set attempts=10 where appointment_id=?",id);success("POST","/staff/appointments/"+id+"/resend",null,admin);
        assertEquals(2,jdbc.queryForObject("select count(*) from mail_jobs where appointment_id=?",Integer.class,id));
    }
    @Test void auditRecordsMutationsAndAppointmentInstantsUseHospitalTimezone() throws Exception {
        var a=book(session,0,email()).get("appointment");assertEquals("Asia/Dhaka",a.get("timeZone").asText());assertEquals("2099-05-01T03:00:00Z",a.get("consultationAt").asText());
        success("PATCH","/appointments/"+a.get("id").asLong()+"/status",Map.of("status","VISITED"),admin);
        assertTrue(jdbc.queryForObject("select count(*) from audit_events where action='APPOINTMENT_STATUS_CHANGED' and entity_id=?",Integer.class,a.get("id").asLong())>0);
        var history=success("GET","/staff/audit?from="+LocalDate.now().minusDays(1)+"&to="+LocalDate.now().plusDays(1)+"&search=APPOINTMENT_STATUS",null,admin);assertTrue(history.size()>0);assertFalse(history.toString().contains("integration-admin-password"));
    }
    Map<String,String> hours(String start,String end){return Map.of("startsAt",start,"endsAt",end);}
    Map<String,Object> weekly(LocalDate from,LocalDate to,List<Map<String,Object>> days){return Map.of("doctorId",doctor,"from",from.toString(),"to",to.toString(),"minutes",20,"days",days);}
    @Test void weeklySchedulesGenerateSelectedWeekdaysAndMultipleRangesWithoutDuplicates() throws Exception {
        LocalDate monday=LocalDate.of(2099,6,1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        var input=weekly(monday,monday.plusDays(13),List.of(
            Map.of("weekday",1,"ranges",List.of(hours("09:00","12:00"),hours("14:00","16:00"))),
            Map.of("weekday",3,"ranges",List.of(hours("10:00","12:00")))));
        var preview=success("POST","/staff/schedules/weekly/preview",input,admin);
        assertEquals(6,preview.get("toCreate").asInt());assertEquals(0,preview.get("conflicts").asInt());
        success("POST","/staff/schedules/weekly",input,admin);
        var mondaySessions=success("GET","/schedules?doctorId="+doctor+"&date="+monday,null,null);
        assertEquals(2,mondaySessions.size());assertEquals(0,success("GET","/schedules?doctorId="+doctor+"&date="+monday.plusDays(1),null,null).size());
        long first=mondaySessions.get(0).path("schedule").path("id").asLong();
        success("PATCH","/staff/schedules/"+first+"/availability",Map.of("available",false),admin);
        var repeated=success("POST","/staff/schedules/weekly",input,admin);
        assertEquals(0,repeated.get("toCreate").asInt());assertEquals(6,repeated.get("existing").asInt());
        var unchanged=success("GET","/schedules?doctorId="+doctor+"&date="+monday,null,null);
        assertEquals(2,unchanged.size());assertFalse(unchanged.get(0).path("schedule").path("available").asBoolean());
    }
    @Test void weeklyConflictsFailTheWholeBatchAndPreserveBookedSessions() throws Exception {
        var booked=book(session,0,email());
        LocalDate date=LocalDate.of(2099,5,1);int weekday=date.getDayOfWeek().getValue();
        var input=weekly(date,date.plusDays(1),List.of(
            Map.of("weekday",weekday,"ranges",List.of(hours("10:00","13:00"))),
            Map.of("weekday",date.plusDays(1).getDayOfWeek().getValue(),"ranges",List.of(hours("09:00","12:00")))));
        assertEquals(1,success("POST","/staff/schedules/weekly/preview",input,admin).get("conflicts").asInt());
        assertEquals(409,request("POST","/staff/schedules/weekly",input,admin).getResponse().getStatus());
        assertEquals(0,success("GET","/schedules?doctorId="+doctor+"&date="+date.plusDays(1),null,null).size());
        var affected=success("GET","/staff/schedules/"+session+"/affected",null,admin);
        assertEquals(booked.path("appointment").path("id").asLong(),affected.get(0).get("id").asLong());
        assertEquals("CONFIRMED",affected.get(0).get("status").asText());
    }
    @Test void weeklySchedulesValidateDateWindowsAndTimeRanges() throws Exception {
        LocalDate date=LocalDate.of(2099,6,1);int day=date.getDayOfWeek().getValue();
        var overlap=weekly(date,date.plusDays(6),List.of(Map.of("weekday",day,"ranges",List.of(hours("09:00","12:00"),hours("11:00","14:00")))));
        assertEquals(400,request("POST","/staff/schedules/weekly/preview",overlap,admin).getResponse().getStatus());
        var empty=weekly(date,date.plusDays(6),List.of());
        assertEquals(400,request("POST","/staff/schedules/weekly",empty,admin).getResponse().getStatus());
        var longWindow=weekly(date,date.plusDays(366),List.of(Map.of("weekday",day,"ranges",List.of(hours("09:00","12:00")))));
        assertEquals(400,request("POST","/staff/schedules/weekly",longWindow,admin).getResponse().getStatus());
        var duplicates=weekly(date,date.plusDays(6),List.of(Map.of("weekday",day,"ranges",List.of(hours("09:00","12:00"))),Map.of("weekday",day,"ranges",List.of(hours("14:00","16:00")))));
        assertEquals(400,request("POST","/staff/schedules/weekly",duplicates,admin).getResponse().getStatus());
        assertEquals(0,success("GET","/schedules?doctorId="+doctor+"&date="+date,null,null).size());
    }
    @Test void weeklyCreationRequiresScheduleEditPermission() throws Exception {
        String mail=email();long id=success("POST","/admin/receptionists",Map.of("patient",patient(mail),"password","staff-password"),admin).get("id").asLong();
        String token=login(mail,"staff-password");
        success("PUT","/admin/permissions",Map.of("accountId",id,"doctorId",doctor,"editInfo",true,"editSchedule",false,"editAvailability",true),admin);
        LocalDate date=LocalDate.of(2099,6,1);var input=weekly(date,date.plusDays(6),List.of(Map.of("weekday",date.getDayOfWeek().getValue(),"ranges",List.of(hours("09:00","12:00")))));
        assertEquals(403,request("POST","/staff/schedules/weekly/preview",input,token).getResponse().getStatus());
        assertEquals(403,request("POST","/staff/schedules/weekly",input,token).getResponse().getStatus());
        success("PUT","/admin/permissions",Map.of("accountId",id,"doctorId",doctor,"editInfo",false,"editSchedule",true,"editAvailability",false),admin);
        assertEquals(1,success("POST","/staff/schedules/weekly",input,token).get("toCreate").asInt());
    }
    @Test void availabilityFindsOnlyFutureFreeSlotsWithoutPatientData() throws Exception {
        long full=schedule("2099-05-02",10);for(int i=0;i<3;i++)book(full,i,email());
        long unavailable=schedule("2099-05-03",12);success("PATCH","/staff/schedules/"+unavailable+"/availability",Map.of("available",false),admin);
        book(session,0,email());var results=success("GET","/availability?doctorId="+doctor+"&from=2099-05-01&to=2099-05-04",null,null);
        assertEquals(1,results.size());assertEquals(session,results.get(0).path("schedule").path("id").asLong());assertEquals(1,results.get(0).path("slots").get(0).path("index").asInt());
        assertFalse(results.toString().contains("email"));assertFalse(results.toString().contains("phone"));
        assertEquals(400,request("GET","/availability?from=2099-05-01&to=2100-05-02",null,null).getResponse().getStatus());
    }
    @Test void patientReschedulingIsOwnedAtomicAndPreservesHistoryAndSerials() throws Exception {
        var registered=success("POST","/auth/register",Map.of("patient",patient(email()),"password","patient-password"),null);String token=registered.get("token").asText();
        var booked=success("POST","/appointments",Map.of("scheduleId",session,"slotIndex",2),token).get("appointment");long id=booked.get("id").asLong(),target=schedule("2099-05-02",12);book(target,0,email());
        var input=Map.of("scheduleId",target,"slotIndex",0);
        assertEquals(409,request("POST","/appointments/"+id+"/reschedule",input,token).getResponse().getStatus());
        assertEquals(session,success("GET","/appointments/mine",null,token).get(0).get("scheduleId").asLong());
        String stranger=success("POST","/auth/register",Map.of("patient",patient(email()),"password","patient-password"),null).get("token").asText();
        assertEquals(403,request("POST","/appointments/"+id+"/reschedule",Map.of("scheduleId",target,"slotIndex",1),stranger).getResponse().getStatus());
        var moved=success("POST","/appointments/"+id+"/reschedule",Map.of("scheduleId",target,"slotIndex",1),token);
        assertEquals(id,moved.get("id").asLong());assertEquals(2,moved.get("serial").asInt());assertEquals("RESCHEDULED",moved.get("status").asText());assertEquals("09:20:00",moved.get("time").asText());
        assertEquals(0,success("GET","/staff/schedules/"+session+"/affected",null,admin).size());assertNotNull(book(session,2,email()));
        assertEquals(2,jdbc.queryForObject("select count(*) from mail_jobs where appointment_id=?",Integer.class,id));
        assertTrue(jdbc.queryForObject("select details from audit_events where action='PATIENT_APPOINTMENT_RESCHEDULED' and entity_id=?",String.class,id).contains("2099-05-01"));
        success("POST","/appointments/"+id+"/reschedule",Map.of("scheduleId",target,"slotIndex",2),token);
        assertEquals(2,success("GET","/appointments/mine",null,token).get(0).get("serial").asInt());
    }
    @Test void guestReschedulingKeepsPrivateAccessAndRejectsRevokedOrInactiveLinks() throws Exception {
        long id=book(session,0,email()).path("appointment").path("id").asLong(),target=schedule("2099-05-02",12);String raw=guestToken(id);
        var result=success("POST","/guest/reschedule",Map.of("token",raw,"scheduleId",target,"slotIndex",0),null);
        assertEquals(id,result.get("id").asLong());assertEquals(target,success("POST","/guest/view",Map.of("token",raw),null).get("scheduleId").asLong());
        success("DELETE","/staff/appointments/"+id+"/guest-links",null,admin);
        assertEquals(404,request("POST","/guest/reschedule",Map.of("token",raw,"scheduleId",session,"slotIndex",0),null).getResponse().getStatus());
        long other=book(session,1,email()).path("appointment").path("id").asLong();String privateLink=guestToken(other);success("PATCH","/appointments/"+other+"/status",Map.of("status","VISITED"),admin);
        assertEquals(409,request("POST","/guest/reschedule",Map.of("token",privateLink,"scheduleId",target,"slotIndex",1),null).getResponse().getStatus());
    }
    @Test void twoReschedulesCannotTakeTheSameSlotAndTheLosingBookingSurvives() throws Exception {
        long a=book(session,0,email()).path("appointment").path("id").asLong(),b=book(session,1,email()).path("appointment").path("id").asLong(),target=schedule("2099-05-02",12);String first=guestToken(a),second=guestToken(b);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{var one=pool.submit(()->{start.await();return request("POST","/guest/reschedule",Map.of("token",first,"scheduleId",target,"slotIndex",0),null).getResponse().getStatus();});var two=pool.submit(()->{start.await();return request("POST","/guest/reschedule",Map.of("token",second,"scheduleId",target,"slotIndex",0),null).getResponse().getStatus();});start.countDown();var statuses=new ArrayList<>(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}finally{pool.shutdownNow();}
        assertEquals(1,success("GET","/staff/schedules/"+session+"/affected",null,admin).size());assertEquals(1,success("GET","/staff/schedules/"+target+"/affected",null,admin).size());
    }
    @Test void reschedulingRejectsDuplicatesOtherDoctorsAndPastAppointments() throws Exception {
        String mail=email();long id=book(session,0,mail).path("appointment").path("id").asLong(),target=schedule("2099-05-02",12);String raw=guestToken(id);book(target,0,mail);
        assertEquals(409,request("POST","/guest/reschedule",Map.of("token",raw,"scheduleId",target,"slotIndex",1),null).getResponse().getStatus());
        long otherDoctor=success("POST","/admin/doctors",Map.of("name","Other","specialty","General","department","Primary","room","13"),admin).get("id").asLong();
        long otherSession=success("POST","/staff/schedules",Map.of("doctorId",otherDoctor,"date","2099-05-02","startsAt","09:00","endsAt","12:00","minutes",20,"available",true),admin).get("id").asLong();
        assertEquals(400,request("POST","/guest/reschedule",Map.of("token",raw,"scheduleId",otherSession,"slotIndex",0),null).getResponse().getStatus());
        jdbc.update("update schedules set date=? where id=?",java.sql.Date.valueOf(LocalDate.now().minusDays(1)),session);
        assertEquals(409,request("POST","/guest/reschedule",Map.of("token",raw,"scheduleId",target,"slotIndex",2),null).getResponse().getStatus());
    }
    String resetToken(String mail){String body=jdbc.queryForObject("select body from mail_jobs where recipient=? and subject='Reset your hospital account password' order by id desc limit 1",String.class,mail);var matcher=java.util.regex.Pattern.compile("reset-password#([A-Za-z0-9_-]+)").matcher(body);assertTrue(matcher.find());return matcher.group(1);}
    @Test void passwordRecoveryIsGenericHashedSingleUseAndRevokesSessions() throws Exception {
        String mail=email();var registration=success("POST","/auth/register",Map.of("patient",patient(mail),"password","patient-password"),null);String token=registration.get("token").asText();long id=registration.path("user").path("id").asLong();
        var known=success("POST","/auth/forgot-password",Map.of("email",mail),null);var unknown=success("POST","/auth/forgot-password",Map.of("email",email()),null);assertEquals(known,unknown);
        String raw=resetToken(mail);assertNotEquals(raw,jdbc.queryForObject("select hash from password_resets where account_id=?",String.class,id));
        success("POST","/auth/forgot-password",Map.of("email",mail),null);assertEquals(1,jdbc.queryForObject("select count(*) from mail_jobs where recipient=?",Integer.class,mail));
        success("POST","/auth/reset-password",Map.of("token",raw,"newPassword","recovered-password"),null);
        assertEquals(401,request("GET","/me",null,token).getResponse().getStatus());assertNotNull(login(mail,"recovered-password"));
        assertEquals(400,request("POST","/auth/reset-password",Map.of("token",raw,"newPassword","another-password"),null).getResponse().getStatus());
        assertFalse(success("GET","/staff/mail",null,admin).toString().contains(raw));
    }
    @Test void recoveryRejectsExpiredReplacedAndDisabledAccountTokens() throws Exception {
        String mail=email();var staff=success("POST","/admin/receptionists",Map.of("patient",patient(mail),"password","staff-password"),admin);long id=staff.get("id").asLong();
        success("POST","/auth/forgot-password",Map.of("email",mail),null);String raw=resetToken(mail);
        jdbc.update("update password_resets set expires_at=?,created_at=? where account_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),java.sql.Timestamp.from(Instant.now().minusSeconds(120)),id);
        assertEquals(400,request("POST","/auth/reset-password",Map.of("token",raw,"newPassword","recovered-password"),null).getResponse().getStatus());
        success("POST","/auth/forgot-password",Map.of("email",mail),null);String fresh=resetToken(mail);assertNotEquals(raw,fresh);
        assertEquals(400,request("POST","/auth/reset-password",Map.of("token",raw,"newPassword","recovered-password"),null).getResponse().getStatus());
        success("PATCH","/admin/accounts/"+id,Map.of("enabled",false),admin);
        assertEquals(400,request("POST","/auth/reset-password",Map.of("token",fresh,"newPassword","recovered-password"),null).getResponse().getStatus());
        success("POST","/auth/forgot-password",Map.of("email",mail),null);assertEquals(2,jdbc.queryForObject("select count(*) from mail_jobs where recipient=?",Integer.class,mail));
    }
    @Test void staffBookingAndLookupAreScopedAndCanLinkRegisteredPatients() throws Exception {
        String staffMail=email();long staff=success("POST","/admin/receptionists",Map.of("patient",patient(staffMail),"password","staff-password"),admin).get("id").asLong();String staffToken=login(staffMail,"staff-password");
        String mail=email();var registered=success("POST","/auth/register",Map.of("patient",patient(mail),"password","patient-password"),null);long account=registered.path("user").path("id").asLong();String patientToken=registered.get("token").asText();
        success("POST","/appointments",Map.of("scheduleId",session,"slotIndex",0),patientToken);
        assertEquals(0,success("GET","/staff/patients?search="+mail,null,staffToken).size());
        assertEquals(403,request("POST","/appointments",Map.of("scheduleId",session,"slotIndex",1,"patient",patient(email())),staffToken).getResponse().getStatus());
        success("PUT","/admin/permissions",Map.of("accountId",staff,"doctorId",doctor,"editInfo",false,"editSchedule",false,"editAvailability",false),admin);
        var choices=success("GET","/staff/patients?search="+mail,null,staffToken);assertEquals(account,choices.get(0).get("accountId").asLong());assertFalse(choices.toString().contains("password"));
        long target=schedule("2099-05-02",12);var linked=success("POST","/staff/appointments",Map.of("scheduleId",target,"slotIndex",0,"patientAccountId",account),staffToken).get("appointment");
        assertEquals(mail,linked.get("email").asText());assertEquals(2,success("GET","/appointments/mine",null,patientToken).size());
        assertEquals(403,request("POST","/appointments",Map.of("scheduleId",target,"slotIndex",1,"patientAccountId",account,"patient",patient(email())),null).getResponse().getStatus());
        String unrelated=email();long unknownAccount=success("POST","/auth/register",Map.of("patient",patient(unrelated),"password","patient-password"),null).path("user").path("id").asLong();
        assertEquals(403,request("POST","/staff/appointments",Map.of("scheduleId",target,"slotIndex",1,"patientAccountId",unknownAccount),staffToken).getResponse().getStatus());
    }
    @Test void bulkAssignmentsAreAtomicIdempotentAndAdminOnly() throws Exception {
        String mail=email();long staff=success("POST","/admin/receptionists",Map.of("patient",patient(mail),"password","staff-password"),admin).get("id").asLong();String token=login(mail,"staff-password");
        long second=success("POST","/admin/doctors",Map.of("name","Second doctor "+UUID.randomUUID(),"specialty","General","department","Primary","room","13","biography","Test"),admin).get("id").asLong();
        var invalid=Map.of("accountId",staff,"doctorIds",List.of(doctor,Long.MAX_VALUE),"editInfo",true,"editSchedule",true,"editAvailability",false);
        assertEquals(404,request("PUT","/admin/permissions/bulk",invalid,admin).getResponse().getStatus());
        assertEquals(0,success("GET","/staff/permissions",null,token).size());
        var selection=Map.of("accountId",staff,"doctorIds",List.of(doctor,second,doctor),"editInfo",false,"editSchedule",true,"editAvailability",false);
        assertEquals(403,request("PUT","/admin/permissions/bulk",selection,token).getResponse().getStatus());
        assertEquals(2,success("PUT","/admin/permissions/bulk",selection,admin).size());
        assertEquals(2,success("PUT","/admin/permissions/bulk",selection,admin).size());
        assertEquals(2,success("GET","/staff/doctors",null,token).size());
        success("PUT","/admin/permissions/bulk",Map.of("accountId",staff,"doctorIds",List.of(doctor),"editInfo",true,"editSchedule",false,"editAvailability",true),admin);
        var permissions=success("GET","/staff/permissions",null,token);assertEquals(2,permissions.size());
        for(JsonNode p:permissions)if(p.get("doctorId").asLong()==doctor){assertTrue(p.get("editInfo").asBoolean());assertFalse(p.get("editSchedule").asBoolean());assertTrue(p.get("editAvailability").asBoolean());}
    }
}
