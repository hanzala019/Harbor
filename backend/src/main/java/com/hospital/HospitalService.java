package com.hospital;

import com.hospital.Models.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class HospitalService {
    @PersistenceContext EntityManager em;
    final PasswordEncoder encoder;
    final ObjectMapper json;
    @Value("${app.time-zone}") String timeZone;
    @Value("${app.public-url}") String publicUrl;
    public HospitalService(PasswordEncoder encoder,ObjectMapper json) { this.encoder = encoder;this.json=json; }
    LocalDate today() {return LocalDate.now(ZoneId.of(timeZone));}
    LocalDateTime now() {return LocalDateTime.now(ZoneId.of(timeZone));}
    public Map<String,Object> config(){return Map.of("timeZone",timeZone,"today",today(),"time",now().toLocalTime());}
    public record Patient(@NotBlank @Size(max=150) String name, @NotBlank @Email @Size(max=200) String email,
        @NotNull @Min(0) @Max(130) Integer age, @NotBlank @Size(max=40) String gender, @NotBlank @Size(max=40) String phone) {}
    public record Registration(@jakarta.validation.Valid @NotNull Patient patient, @NotBlank @Size(min=10,max=72) String password) {}
    public record Booking(@NotNull Long scheduleId, @NotNull @Min(0) Integer slotIndex, @jakarta.validation.Valid Patient patient,boolean allowDuplicate) {}
    public record DoctorInput(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=150) String specialty,
        @NotBlank @Size(max=150) String department, @NotBlank @Size(max=100) String room, @Size(max=4000) String biography) {}
    public record ScheduleInput(@NotNull Long doctorId, @NotNull LocalDate date, @NotNull LocalTime startsAt,
        @NotNull LocalTime endsAt, @Min(5) @Max(120) int minutes, boolean available) {}
    public record PermissionInput(@NotNull Long accountId, @NotNull Long doctorId, boolean editInfo, boolean editSchedule, boolean editAvailability) {}
    public record BulkPermissionInput(@NotNull Long accountId,
        @NotEmpty @Size(max=500) List<@NotNull @Positive Long> doctorIds,
        boolean editInfo, boolean editSchedule, boolean editAvailability) {}
    public record TimeRange(@NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
    public record DayHours(@Min(1) @Max(7) int weekday,
        @NotEmpty @Size(max=10) List<@NotNull @jakarta.validation.Valid TimeRange> ranges) {}
    public record WeeklyScheduleInput(@NotNull Long doctorId, @NotNull LocalDate from, @NotNull LocalDate to,
        @NotEmpty @Size(max=7) List<@NotNull @jakarta.validation.Valid DayHours> days,
        @Min(5) @Max(120) int minutes) {}
    public record WeeklyCandidate(LocalDate date, LocalTime startsAt, LocalTime endsAt, String status) {}
    public record WeeklyPreview(List<WeeklyCandidate> sessions, long toCreate, long existing, long conflicts) {}
    public record Slot(int index, LocalTime time, int estimatedSerial) {}
    public record ScheduleView(Schedule schedule, List<Slot> slots, int appointmentCount) {}
    public record AppointmentView(Long id, Long scheduleId, Long doctorId, String doctor, String specialty, String room,
        LocalDate date, LocalTime time, LocalTime arrivalTime, Integer serial, String name, String email,
        String phone, Integer age, String gender, String status, Instant consultationAt, String timeZone) {}
    static ResponseStatusException fail(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    static String random() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    <T> T find(Class<T> type, Long id) { T obj=em.find(type,id); if(obj==null) throw fail(HttpStatus.NOT_FOUND,"Record not found"); return obj; }
    Schedule lockSchedule(Long id) {Schedule s=find(Schedule.class,id);em.refresh(s,LockModeType.PESSIMISTIC_WRITE);return s;}
    Account current() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof Long id)) throw fail(HttpStatus.UNAUTHORIZED,"Please log in");
        Account account=find(Account.class,id);if(!Boolean.TRUE.equals(account.enabled))throw fail(HttpStatus.UNAUTHORIZED,"This account is disabled");return account;
    }
    Account optional() { var a=SecurityContextHolder.getContext().getAuthentication(); return a!=null && a.getPrincipal() instanceof Long ? current() : null; }
    void admin() { if(!current().role.equals("ADMIN")) throw fail(HttpStatus.FORBIDDEN,"Admin access required"); }
    Permission access(Long doctorId, String edit) {
        Account a=current(); if(a.role.equals("ADMIN")) return null;
        if(!a.role.equals("RECEPTIONIST")) throw fail(HttpStatus.FORBIDDEN,"Staff access required");
        Permission p=em.createQuery("from Permission where accountId=:a and doctorId=:d",Permission.class)
            .setParameter("a",a.id).setParameter("d",doctorId).getResultStream().findFirst().orElseThrow(() -> fail(HttpStatus.FORBIDDEN,"Doctor is not assigned to you"));
        if((edit.equals("info")&&!p.editInfo)||(edit.equals("schedule")&&!p.editSchedule)||(edit.equals("availability")&&!p.editAvailability))
            throw fail(HttpStatus.FORBIDDEN,"You have view-only access for this action");
        return p;
    }
    public Account authenticate(String raw) {
        LoginToken t=em.find(LoginToken.class,hash(raw));
        Account account=t!=null && t.expiresAt.isAfter(Instant.now()) ? em.find(Account.class,t.accountId) : null;
        return account!=null&&Boolean.TRUE.equals(account.enabled)?account:null;
    }
    public Map<String,Object> profile(Account a) {
        var m=new LinkedHashMap<String,Object>(); m.put("id",a.id);m.put("name",a.name);m.put("email",a.email);m.put("role",a.role);
        m.put("age",a.age);m.put("gender",a.gender);m.put("phone",a.phone);m.put("enabled",a.enabled); return m;
    }
    Map<String,Object> session(Account a) {
        String raw=random(); LoginToken t=new LoginToken();t.hash=hash(raw);t.accountId=a.id;t.expiresAt=Instant.now().plus(Duration.ofHours(12));em.persist(t);
        return Map.of("token",raw,"user",profile(a));
    }
    public Map<String,Object> login(String email,String password) {
        Account a=em.createQuery("from Account where email=:email",Account.class).setParameter("email",email.trim().toLowerCase(Locale.ROOT)).getResultStream().findFirst().orElse(null);
        if(a==null || !Boolean.TRUE.equals(a.enabled) || !encoder.matches(password,a.password)) throw fail(HttpStatus.UNAUTHORIZED,"Invalid email or password"); return session(a);
    }
    Account create(Patient p,String password,String role) {
        String email=p.email.trim().toLowerCase(Locale.ROOT);
        if(!em.createQuery("from Account where email=:e",Account.class).setParameter("e",email).getResultList().isEmpty()) throw fail(HttpStatus.CONFLICT,"This email is already registered. Please log in.");
        Account a=new Account();a.email=email;a.password=encodePassword(password);a.role=role;copy(a,p);em.persist(a);return a;
    }
    String encodePassword(String password){if(password.getBytes(StandardCharsets.UTF_8).length>72)throw fail(HttpStatus.BAD_REQUEST,"Password must fit within 72 UTF-8 bytes");return encoder.encode(password);}
    void copy(Account a,Patient p) {a.name=p.name.trim();a.age=p.age;a.gender=p.gender;a.phone=p.phone;}
    public Map<String,Object> register(Registration r) {return session(create(r.patient,r.password,"PATIENT"));}
    public Map<String,Object> claim(Long appointmentId,String secret,String password) {
        Appointment a=find(Appointment.class,appointmentId);em.lock(a,LockModeType.PESSIMISTIC_WRITE);
        if(a.accountId!=null || a.claimHash==null || !MessageDigest.isEqual(a.claimHash.getBytes(StandardCharsets.UTF_8),hash(secret).getBytes(StandardCharsets.UTF_8)))
            throw fail(HttpStatus.FORBIDDEN,"Invalid or already used booking registration link");
        Account user=create(new Patient(a.name,a.email,a.age,a.gender,a.phone),password,"PATIENT");a.accountId=user.id;a.claimHash=null;revokeGuestLinks(a.id);return session(user);
    }
    public Map<String,Object> me() { return profile(current()); }
    public Map<String,Object> updateProfile(Patient p) {Account a=current();copy(a,p);return profile(a);}
    public void logout(String token) {LoginToken t=em.find(LoginToken.class,hash(token));if(t!=null)em.remove(t);}
    public List<Doctor> doctors() {return em.createQuery("from Doctor order by name",Doctor.class).getResultList();}
    public Doctor doctor(Long id) {return find(Doctor.class,id);}
    public Doctor saveDoctor(Long id,DoctorInput input) {
        if(id==null) admin(); else access(id,"info");
        Doctor d=id==null?new Doctor():find(Doctor.class,id);d.name=input.name;d.specialty=input.specialty;d.department=input.department;d.room=input.room;d.biography=input.biography;
        if(id==null)em.persist(d);audit(id==null?"DOCTOR_CREATED":"DOCTOR_UPDATED","Doctor",d.id,d.id,d.name);return d;
    }
    List<Appointment> appointments(Long schedule) { return em.createQuery("from Appointment where scheduleId=:s order by serial, id",Appointment.class).setParameter("s",schedule).getResultList(); }
    static boolean active(Appointment a) { return Set.of("CONFIRMED","WAITING","RESCHEDULED","RESCHEDULE_PENDING").contains(a.status); }
    static int capacity(Schedule s) {return (int)(Duration.between(s.startsAt,s.endsAt).toMinutes()/s.minutes);}
    public List<ScheduleView> schedules(Long doctorId,LocalDate date) {
        return em.createQuery("from Schedule where doctorId=:d and date=:date order by startsAt",Schedule.class).setParameter("d",doctorId).setParameter("date",date).getResultList().stream().map(this::scheduleView).toList();
    }
    ScheduleView scheduleView(Schedule s) {
        List<Appointment> apps=appointments(s.id);Set<Integer> occupied=new HashSet<>();apps.forEach(a->{if(a.slotIndex!=null)occupied.add(a.slotIndex);});
        List<Slot> slots=new ArrayList<>();int next=apps.stream().mapToInt(a->a.serial).max().orElse(0)+1;
        if(s.available && !s.date.isBefore(today())) for(int i=0;i<capacity(s);i++) {
            LocalTime time=s.startsAt.plusMinutes((long)i*s.minutes);
            if(!occupied.contains(i) && LocalDateTime.of(s.date,time).isAfter(now())) slots.add(new Slot(i,time,next));
        }
        return new ScheduleView(s,slots,apps.size());
    }
    public Schedule saveSchedule(Long id,ScheduleInput input) {
        access(input.doctorId,"schedule");find(Doctor.class,input.doctorId);
        if(!input.date.isAfter(today().minusDays(1)) || !input.endsAt.isAfter(input.startsAt) || Duration.between(input.startsAt,input.endsAt).toMinutes()<input.minutes)
            throw fail(HttpStatus.BAD_REQUEST,"Choose a current/future date and a valid time range");
        Doctor lock=find(Doctor.class,input.doctorId);em.lock(lock,LockModeType.PESSIMISTIC_WRITE);
        Schedule s=id==null?new Schedule():find(Schedule.class,id);
        if(id!=null) {
            access(s.doctorId,"schedule");s=lockSchedule(id);
            if(!Objects.equals(s.available,input.available))access(s.doctorId,"availability");
            if(!appointments(id).isEmpty()) throw fail(HttpStatus.CONFLICT,"This session has bookings. Review affected appointments, mark it unavailable, and transfer active patients to a new session.");
        }
        if(id==null && !input.available)access(input.doctorId,"availability");
        if(!em.createQuery("from Schedule where doctorId=:d and date=:date and startsAt<:end and endsAt>:start",Schedule.class)
            .setParameter("d",input.doctorId).setParameter("date",input.date).setParameter("end",input.endsAt).setParameter("start",input.startsAt).getResultList().stream().filter(other->!Objects.equals(other.id,id)).toList().isEmpty())
            throw fail(HttpStatus.CONFLICT,"This session overlaps another session for the doctor");
        s.doctorId=input.doctorId;s.date=input.date;s.startsAt=input.startsAt;s.endsAt=input.endsAt;s.minutes=input.minutes;s.available=input.available;
        if(id==null)em.persist(s);audit(id==null?"SCHEDULE_CREATED":"SCHEDULE_UPDATED","Schedule",s.id,s.doctorId,s.date+" "+s.startsAt+"–"+s.endsAt);return s;
    }
    public Map<String,Object> book(Booking b) {
        Schedule s=lockSchedule(b.scheduleId);
        if(!s.available || b.slotIndex<0 || b.slotIndex>=capacity(s) || !LocalDateTime.of(s.date,s.startsAt.plusMinutes((long)b.slotIndex*s.minutes)).isAfter(now())) throw fail(HttpStatus.CONFLICT,"This slot is no longer available");
        List<Appointment> existing=appointments(s.id);
        if(existing.stream().anyMatch(a->Objects.equals(a.slotIndex,b.slotIndex))) throw fail(HttpStatus.CONFLICT,"Someone has already booked this slot. Choose another time.");
        Account user=optional();Patient p=b.patient;
        if(user!=null && user.role.equals("PATIENT")) p=new Patient(user.name,user.email,user.age,user.gender,user.phone);
        if(p==null)throw fail(HttpStatus.BAD_REQUEST,"Patient details are required");
        boolean duplicate=duplicateCount(s,p,user)>0;
        if(duplicate){
            boolean staff=user!=null&&Set.of("ADMIN","RECEPTIONIST").contains(user.role);
            if(!staff||!b.allowDuplicate)throw fail(HttpStatus.CONFLICT,"This patient already has an active appointment with this doctor on this date. Authorized staff may explicitly confirm an additional booking.");
            access(s.doctorId,"");
        }
        Appointment a=new Appointment();a.scheduleId=s.id;a.accountId=user!=null&&user.role.equals("PATIENT")?user.id:null;
        a.serial=existing.stream().mapToInt(x->x.serial).max().orElse(0)+1;a.slotIndex=b.slotIndex;a.originalSlotIndex=b.slotIndex;a.name=p.name;a.email=p.email.trim().toLowerCase(Locale.ROOT);a.age=p.age;a.gender=p.gender;a.phone=p.phone;a.status="CONFIRMED";a.createdAt=Instant.now();
        String secret=random();a.claimHash=a.accountId==null?hash(secret):null;em.persist(a);emailAppointment(a,"Appointment confirmed");
        audit(duplicate?"APPOINTMENT_BOOKED_DUPLICATE_OVERRIDE":"APPOINTMENT_BOOKED","Appointment",a.id,s.doctorId,"Serial "+a.serial);
        return Map.of("appointment",view(a),"claimToken",a.accountId==null?secret:"");
    }
    public WeeklyPreview previewWeekly(WeeklyScheduleInput input) {
        access(input.doctorId,"schedule");
        find(Doctor.class,input.doctorId);
        if(input.from.isBefore(today()) || input.to.isBefore(input.from)
            || Duration.between(input.from.atStartOfDay(),input.to.atStartOfDay()).toDays()>365)
            throw fail(HttpStatus.BAD_REQUEST,"Choose a current/future date window of no more than 366 days");
        Map<Integer,List<TimeRange>> weekdays=new HashMap<>();
        for(DayHours day:input.days) {
            if(weekdays.putIfAbsent(day.weekday,day.ranges)!=null)
                throw fail(HttpStatus.BAD_REQUEST,"Each weekday must appear only once");
            List<TimeRange> ordered=day.ranges.stream().sorted(Comparator.comparing(TimeRange::startsAt)).toList();
            for(int i=0;i<ordered.size();i++) {
                TimeRange range=ordered.get(i);
                if(!range.endsAt.isAfter(range.startsAt)
                    || Duration.between(range.startsAt,range.endsAt).toMinutes()<input.minutes)
                    throw fail(HttpStatus.BAD_REQUEST,"Each time range must end after its start and fit at least one visit");
                if(i>0 && ordered.get(i-1).endsAt.isAfter(range.startsAt))
                    throw fail(HttpStatus.BAD_REQUEST,"Time ranges on the same weekday cannot overlap");
            }
            weekdays.put(day.weekday,ordered);
        }
        List<Schedule> existing=em.createQuery("from Schedule where doctorId=:d and date>=:from and date<=:to",Schedule.class)
            .setParameter("d",input.doctorId).setParameter("from",input.from).setParameter("to",input.to).getResultList();
        List<WeeklyCandidate> candidates=new ArrayList<>();
        for(LocalDate date=input.from;!date.isAfter(input.to);date=date.plusDays(1)) {
            for(TimeRange range:weekdays.getOrDefault(date.getDayOfWeek().getValue(),List.of())) {
                LocalDate sessionDate=date;
                List<Schedule> overlaps=existing.stream().filter(s->s.date.equals(sessionDate)
                    && s.startsAt.isBefore(range.endsAt) && s.endsAt.isAfter(range.startsAt)).toList();
                boolean identical=overlaps.size()==1 && overlaps.get(0).startsAt.equals(range.startsAt)
                    && overlaps.get(0).endsAt.equals(range.endsAt) && overlaps.get(0).minutes==input.minutes;
                candidates.add(new WeeklyCandidate(date,range.startsAt,range.endsAt,
                    overlaps.isEmpty()?"NEW":identical?"EXISTS":"CONFLICT"));
            }
        }
        if(candidates.isEmpty())throw fail(HttpStatus.BAD_REQUEST,"No selected weekdays fall within this date window");
        return new WeeklyPreview(candidates,candidates.stream().filter(s->s.status.equals("NEW")).count(),
            candidates.stream().filter(s->s.status.equals("EXISTS")).count(),
            candidates.stream().filter(s->s.status.equals("CONFLICT")).count());
    }
    public WeeklyPreview createWeekly(WeeklyScheduleInput input) {
        access(input.doctorId,"schedule");
        // Serialize with single-session creation; re-check the preview under the same doctor lock.
        em.refresh(find(Doctor.class,input.doctorId),LockModeType.PESSIMISTIC_WRITE);
        WeeklyPreview preview=previewWeekly(input);
        if(preview.conflicts>0) {
            WeeklyCandidate conflict=preview.sessions.stream().filter(s->s.status.equals("CONFLICT")).findFirst().orElseThrow();
            throw fail(HttpStatus.CONFLICT,"An existing session overlaps on "+conflict.date+" at "+conflict.startsAt+". Adjust the date window or hours; no sessions were created.");
        }
        for(WeeklyCandidate candidate:preview.sessions)if(candidate.status.equals("NEW")) {
            Schedule s=new Schedule();s.doctorId=input.doctorId;s.date=candidate.date;
            s.startsAt=candidate.startsAt;s.endsAt=candidate.endsAt;s.minutes=input.minutes;s.available=true;em.persist(s);
        }
        audit("WEEKLY_SCHEDULE_CREATED","Doctor",input.doctorId,input.doctorId,preview.toCreate+" sessions created; "+preview.existing+" skipped");
        return preview;
    }
    AppointmentView view(Appointment a) {
        Schedule s=find(Schedule.class,a.scheduleId);Doctor d=find(Doctor.class,s.doctorId);
        LocalTime time=s.startsAt.plusMinutes((long)(a.slotIndex!=null?a.slotIndex:a.originalSlotIndex!=null?a.originalSlotIndex:a.serial-1)*s.minutes);
        LocalTime arrival=time.isBefore(LocalTime.of(0,10))?LocalTime.MIDNIGHT:time.minusMinutes(10);
        return new AppointmentView(a.id,s.id,d.id,d.name,d.specialty,d.room,s.date,time,arrival,a.serial,a.name,a.email,a.phone,a.age,a.gender,a.status,LocalDateTime.of(s.date,time).atZone(ZoneId.of(timeZone)).toInstant(),timeZone);
    }
    public List<AppointmentView> mine() {Long id=current().id;return em.createQuery("from Appointment where accountId=:a order by createdAt desc",Appointment.class).setParameter("a",id).getResultList().stream().map(this::view).toList();}
    public List<Doctor> staffDoctors() {
        Account a=current();if(a.role.equals("ADMIN"))return doctors();
        return em.createQuery("select d from Doctor d, Permission p where p.doctorId=d.id and p.accountId=:a order by d.name",Doctor.class).setParameter("a",a.id).getResultList();
    }
    public List<Permission> permissions() {Account a=current();return a.role.equals("ADMIN")?em.createQuery("from Permission",Permission.class).getResultList():em.createQuery("from Permission where accountId=:a",Permission.class).setParameter("a",a.id).getResultList();}
    public List<AppointmentView> staffAppointments(LocalDate from,LocalDate to) {
        Set<Long> ids=new HashSet<>(staffDoctors().stream().map(d->d.id).toList());
        return em.createQuery("select a from Appointment a, Schedule s where a.scheduleId=s.id and s.date>=:from and s.date<=:to order by s.date,s.startsAt,a.serial",Appointment.class)
            .setParameter("from",from).setParameter("to",to).getResultList().stream().map(this::view).filter(a->ids.contains(a.doctorId)).toList();
    }
    public AppointmentView status(Long id,String status) {
        Appointment a=find(Appointment.class,id);Schedule s=lockSchedule(a.scheduleId);em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
        Account user=current();if(user.role.equals("PATIENT")) {if(!Objects.equals(a.accountId,user.id)||!status.equals("CANCELLED"))throw fail(HttpStatus.FORBIDDEN,"This appointment is not yours");}
        else access(s.doctorId,"");
        if(!Set.of("VISITED","NO_SHOW","CANCELLED","WAITING").contains(status)||!active(a))throw fail(HttpStatus.BAD_REQUEST,"Invalid appointment status transition");
        a.status=status;if(status.equals("CANCELLED"))a.slotIndex=null;audit("APPOINTMENT_STATUS_CHANGED","Appointment",a.id,s.doctorId,status);return view(a);
    }
    public List<AppointmentView> affected(Long id) {Schedule s=find(Schedule.class,id);access(s.doctorId,"");return appointments(id).stream().filter(HospitalService::active).map(this::view).toList();}
    public Schedule availability(Long id,boolean available) {
        Schedule s=lockSchedule(id);access(s.doctorId,"availability");s.available=available;
        for(Appointment a:appointments(id)) if(active(a)) a.status=available?"CONFIRMED":"RESCHEDULE_PENDING";
        audit("AVAILABILITY_CHANGED","Schedule",s.id,s.doctorId,"Available: "+available);return s;
    }
    public void notifyAffected(Long id,Long nextId) {
        Schedule s=find(Schedule.class,id);access(s.doctorId,"availability");String next="Please contact the hospital to arrange your next appointment.";
        if(nextId!=null) {Schedule target=find(Schedule.class,nextId);if(!target.doctorId.equals(s.doctorId)||!target.available)throw fail(HttpStatus.BAD_REQUEST,"Choose an available session for the same doctor");next="Next available session: "+target.date+" at "+target.startsAt+". Contact the hospital if you wish to move your appointment.";}
        for(Appointment a:appointments(id))if(active(a))enqueue(a.email,"Doctor schedule changed","Hello "+a.name+", your appointment with "+find(Doctor.class,s.doctorId).name+" on "+s.date+" is affected by a schedule change. "+next);
    }
    public List<AppointmentView> transfer(Long sourceId,Long targetId) {return transferSelected(sourceId,targetId,null);}
    public List<AppointmentView> transferSelected(Long sourceId,Long targetId,List<Long> selection) {
        if(sourceId.equals(targetId))throw fail(HttpStatus.BAD_REQUEST,"Select a different destination session");
        // Stable lock order prevents two simultaneous transfers deadlocking.
        lockSchedule(Math.min(sourceId,targetId));lockSchedule(Math.max(sourceId,targetId));
        Schedule source=find(Schedule.class,sourceId),target=find(Schedule.class,targetId);
        access(source.doctorId,"schedule");access(target.doctorId,"schedule");
        if(!source.doctorId.equals(target.doctorId)||!target.available||target.date.isBefore(today()))throw fail(HttpStatus.BAD_REQUEST,"Destination must be an available session for the same doctor");
        List<Appointment> moving=appointments(sourceId).stream().filter(HospitalService::active).toList();
        if(selection!=null){Set<Long> ids=new HashSet<>(selection);if(ids.isEmpty()||!moving.stream().map(a->a.id).toList().containsAll(ids))throw fail(HttpStatus.BAD_REQUEST,"Select active patients from the source session");moving=moving.stream().filter(a->ids.contains(a.id)).toList();}
        List<Slot> free=scheduleView(target).slots;
        if(free.size()<moving.size())throw fail(HttpStatus.CONFLICT,"Destination has insufficient available slots; no appointments were moved");
        int serial=appointments(targetId).stream().mapToInt(a->a.serial).max().orElse(0);
        for(int i=0;i<moving.size();i++){Appointment a=moving.get(i);a.scheduleId=targetId;a.serial=++serial;a.slotIndex=free.get(i).index;a.originalSlotIndex=a.slotIndex;a.status="RESCHEDULED";emailAppointment(a,"Appointment rescheduled");}
        audit("PATIENTS_TRANSFERRED","Schedule",sourceId,source.doctorId,moving.size()+" patients moved to session "+targetId);
        return moving.stream().map(this::view).toList();
    }
    public List<Map<String,Object>> accounts(String role) {admin();return em.createQuery("from Account where role=:r order by name",Account.class).setParameter("r",role).getResultList().stream().map(this::profile).toList();}
    public Map<String,Object> receptionist(Registration r) {admin();Account a=create(r.patient,r.password,"RECEPTIONIST");audit("RECEPTIONIST_CREATED","Account",a.id,null,"Receptionist created");return profile(a);}
    public Permission permission(PermissionInput input) {
        admin();Account account=find(Account.class,input.accountId);em.refresh(account,LockModeType.PESSIMISTIC_WRITE);
        if(!account.role.equals("RECEPTIONIST"))throw fail(HttpStatus.BAD_REQUEST,"Select a receptionist");find(Doctor.class,input.doctorId);
        Permission p=em.createQuery("from Permission where accountId=:a and doctorId=:d",Permission.class).setParameter("a",input.accountId).setParameter("d",input.doctorId).getResultStream().findFirst().orElse(null);
        boolean fresh=p==null;if(fresh)p=new Permission();p.accountId=input.accountId;p.doctorId=input.doctorId;p.editInfo=input.editInfo;p.editSchedule=input.editSchedule;p.editAvailability=input.editAvailability;if(fresh)em.persist(p);audit("ACCESS_UPDATED","Permission",p.id,p.doctorId,"Receptionist "+p.accountId+" info="+p.editInfo+" schedule="+p.editSchedule+" availability="+p.editAvailability);return p;
    }
    public List<Permission> permissionsBulk(BulkPermissionInput input) {
        admin();Account account=find(Account.class,input.accountId);em.refresh(account,LockModeType.PESSIMISTIC_WRITE);
        if(!account.role.equals("RECEPTIONIST"))throw fail(HttpStatus.BAD_REQUEST,"Select a receptionist");
        Set<Long> doctorIds=new LinkedHashSet<>(input.doctorIds);
        // Validate the complete selection before applying an atomic, idempotent upsert.
        doctorIds.forEach(id->find(Doctor.class,id));
        return doctorIds.stream().map(id->permission(new PermissionInput(input.accountId,id,
            input.editInfo,input.editSchedule,input.editAvailability))).toList();
    }
    public void revoke(Long id) {admin();Permission p=find(Permission.class,id);em.refresh(find(Account.class,p.accountId),LockModeType.PESSIMISTIC_WRITE);audit("ACCESS_REVOKED","Permission",p.id,p.doctorId,"Receptionist "+p.accountId);em.remove(p);}
    MailJob enqueue(String to,String subject,String body) {MailJob j=new MailJob();j.recipient=to;j.subject=subject;j.body=body;j.sent=false;j.attempts=0;j.createdAt=Instant.now();em.persist(j);return j;}
    void emailAppointment(Appointment a,String subject) {
        AppointmentView v=view(a);String link=a.accountId==null?"\nManage this appointment privately: "+issueGuestLink(a):"";
        MailJob job=enqueue(a.email,subject,"Hello "+a.name+",\n"+subject+".\nStatus: "+a.status+"\nDoctor: "+v.doctor+"\nDate: "+v.date+"\nEstimated consultation: "+v.time+"\nPlease arrive: "+v.arrivalTime+"\nTimezone: "+timeZone+"\nSerial: "+a.serial+"\nRoom: "+v.room+link+"\nThank you for choosing Harbor Hospital.");job.appointmentId=a.id;
    }
    long duplicateCount(Schedule s,Patient p,Account account){return em.createQuery("select a from Appointment a, Schedule s where a.scheduleId=s.id and s.doctorId=:d and s.date=:date",Appointment.class).setParameter("d",s.doctorId).setParameter("date",s.date).getResultList().stream().filter(HospitalService::active).filter(a->account!=null&&account.role.equals("PATIENT")?Objects.equals(a.accountId,account.id):a.email.equalsIgnoreCase(p.email.trim())&&a.phone.replaceAll("\\D","").equals(p.phone.replaceAll("\\D",""))).count();}
    public record DuplicateInput(@NotNull Long scheduleId,@NotBlank @Email String email,@NotBlank String phone) {}
    public Map<String,Long> checkDuplicate(DuplicateInput input){Schedule s=find(Schedule.class,input.scheduleId);access(s.doctorId,"");return Map.of("matches",duplicateCount(s,new Patient("Check",input.email,0,"Other",input.phone),null));}
    void audit(String action,String type,Long id,Long doctor,String details){Account actor=optional();AuditEvent e=new AuditEvent();e.actorId=actor==null?null:actor.id;e.actorName=actor==null?"Guest":actor.name;e.action=action;e.entityType=type;e.entityId=id;e.doctorId=doctor;e.details=details;e.occurredAt=Instant.now();em.persist(e);}
    public List<AuditEvent> auditHistory(LocalDate from,LocalDate to,String search){
        Account actor=current();Set<Long> ids=new HashSet<>(staffDoctors().stream().map(d->d.id).toList());
        return em.createQuery("from AuditEvent where occurredAt>=:from and occurredAt<:to order by occurredAt desc",AuditEvent.class).setParameter("from",from.atStartOfDay(ZoneId.of(timeZone)).toInstant()).setParameter("to",to.plusDays(1).atStartOfDay(ZoneId.of(timeZone)).toInstant()).getResultList().stream().filter(e->actor.role.equals("ADMIN")||ids.contains(e.doctorId)).filter(e->(e.actorName+" "+e.action+" "+e.details+" "+e.entityType).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
    }
    public record PasswordChange(@NotBlank String currentPassword,@NotBlank @Size(min=10,max=72) String newPassword) {}
    public record AccountUpdate(Boolean enabled,@Size(min=10,max=72) String password,boolean revokeSessions) {}
    void revokeTokens(Long id){em.createQuery("delete from LoginToken where accountId=:id").setParameter("id",id).executeUpdate();}
    public void changePassword(PasswordChange input){Account a=current();em.refresh(a,LockModeType.PESSIMISTIC_WRITE);if(!encoder.matches(input.currentPassword,a.password))throw fail(HttpStatus.FORBIDDEN,"Current password is incorrect");a.password=encodePassword(input.newPassword);revokeTokens(a.id);audit("PASSWORD_CHANGED","Account",a.id,null,"Password changed; all sessions revoked");}
    public Map<String,Object> manageAccount(Long id,AccountUpdate input){admin();Account a=find(Account.class,id);em.refresh(a,LockModeType.PESSIMISTIC_WRITE);if(!a.role.equals("RECEPTIONIST"))throw fail(HttpStatus.BAD_REQUEST,"Select a receptionist");if(input.enabled!=null)a.enabled=input.enabled;if(input.password!=null)a.password=encodePassword(input.password);if(input.revokeSessions||input.password!=null||Boolean.FALSE.equals(input.enabled))revokeTokens(a.id);audit("RECEPTIONIST_ACCOUNT_UPDATED","Account",id,null,"Enabled="+a.enabled+"; password reset="+(input.password!=null)+"; session revocation requested="+input.revokeSessions);return profile(a);}
    String issueGuestLink(Appointment a){String raw=random();GuestLink link=new GuestLink();link.appointmentId=a.id;link.hash=hash(raw);link.expiresAt=Instant.now().plus(Duration.ofDays(30));link.revoked=false;em.persist(link);return publicUrl.replaceAll("/$","")+"/guest-appointment#"+raw;}
    GuestLink guestLink(String raw){return em.createQuery("from GuestLink where hash=:hash and revoked=false and expiresAt>:now",GuestLink.class).setParameter("hash",hash(raw)).setParameter("now",Instant.now()).getResultStream().findFirst().orElseThrow(()->fail(HttpStatus.NOT_FOUND,"This private appointment link is invalid, expired, or revoked"));}
    public AppointmentView guestView(String raw){return view(find(Appointment.class,guestLink(raw).appointmentId));}
    public AppointmentView guestCancel(String raw){GuestLink link=guestLink(raw);Appointment a=find(Appointment.class,link.appointmentId);Schedule s=lockSchedule(a.scheduleId);em.refresh(a,LockModeType.PESSIMISTIC_WRITE);if(!active(a)||!view(a).consultationAt.isAfter(Instant.now()))throw fail(HttpStatus.CONFLICT,"Only active future appointments can be cancelled");a.status="CANCELLED";a.slotIndex=null;revokeGuestLinks(a.id);audit("GUEST_APPOINTMENT_CANCELLED","Appointment",a.id,s.doctorId,"Cancelled through a private link");return view(a);}
    void revokeGuestLinks(Long id){em.createQuery("update GuestLink set revoked=true where appointmentId=:id").setParameter("id",id).executeUpdate();}
    public void revokeGuestAccess(Long id){Appointment a=find(Appointment.class,id);Long doctor=find(Schedule.class,a.scheduleId).doctorId;access(doctor,"");revokeGuestLinks(id);audit("GUEST_LINKS_REVOKED","Appointment",id,doctor,"Private links revoked");}
    public void resendConfirmation(Long id){Appointment a=find(Appointment.class,id);em.refresh(a,LockModeType.PESSIMISTIC_WRITE);Long doctor=find(Schedule.class,a.scheduleId).doctorId;access(doctor,"");if(em.createQuery("from MailJob where appointmentId=:id and sent=false and attempts<10",MailJob.class).setParameter("id",id).getResultStream().findAny().isPresent())throw fail(HttpStatus.CONFLICT,"An email is already queued or retrying for this appointment");emailAppointment(a,"Appointment details");audit("CONFIRMATION_RESENT","Appointment",id,doctor,"Email queued");}
    public record MailStatus(Long id,Long appointmentId,String recipient,String subject,String status,int attempts,Instant createdAt,Instant sentAt,String lastError) {}
    public List<MailStatus> mailStatus(){Account actor=current();Set<Long> doctors=new HashSet<>(staffDoctors().stream().map(d->d.id).toList());return em.createQuery("from MailJob order by id desc",MailJob.class).getResultList().stream().filter(j->actor.role.equals("ADMIN")||(j.appointmentId!=null&&doctors.contains(find(Schedule.class,find(Appointment.class,j.appointmentId).scheduleId).doctorId))).map(j->new MailStatus(j.id,j.appointmentId,j.recipient,j.subject,j.sent?"SENT":j.attempts>=10?"FAILED":j.attempts==0?"QUEUED":"RETRYING",j.attempts,j.createdAt,j.sentAt,j.lastError)).toList();}
    public List<ScheduleView> week(Long doctorId,LocalDate from){access(doctorId,"");List<ScheduleView> result=new ArrayList<>();for(int i=0;i<7;i++)result.addAll(schedules(doctorId,from.plusDays(i)));return result;}
    public record TemplateInput(@NotNull Long doctorId,@NotBlank @Size(max=150) String name,@NotEmpty @Size(max=7) List<@NotNull @jakarta.validation.Valid DayHours> days,@Min(5) @Max(120) int minutes) {}
    public record TemplateView(Long id,Long doctorId,String name,int minutes,List<DayHours> days) {}
    TemplateView templateView(ScheduleTemplate t){try{return new TemplateView(t.id,t.doctorId,t.name,t.minutes,json.readValue(t.rules,new TypeReference<List<DayHours>>(){}));}catch(Exception e){throw new IllegalStateException("Invalid template",e);}}
    public List<TemplateView> templates(Long doctor){access(doctor,"");return em.createQuery("from ScheduleTemplate where doctorId=:id order by name",ScheduleTemplate.class).setParameter("id",doctor).getResultList().stream().map(this::templateView).toList();}
    public TemplateView saveTemplate(TemplateInput input){access(input.doctorId,"schedule");previewWeekly(new WeeklyScheduleInput(input.doctorId,today(),today().plusDays(6),input.days,input.minutes));ScheduleTemplate t=new ScheduleTemplate();t.doctorId=input.doctorId;t.name=input.name.trim();t.minutes=input.minutes;try{t.rules=json.writeValueAsString(input.days);}catch(Exception e){throw fail(HttpStatus.BAD_REQUEST,"Invalid template");}em.persist(t);audit("TEMPLATE_SAVED","ScheduleTemplate",t.id,t.doctorId,t.name);return templateView(t);}
    public void deleteTemplate(Long id){ScheduleTemplate t=find(ScheduleTemplate.class,id);access(t.doctorId,"schedule");audit("TEMPLATE_DELETED","ScheduleTemplate",id,t.doctorId,t.name);em.remove(t);}
    public TemplateView copyWeek(Long doctor,LocalDate from){access(doctor,"schedule");Map<Integer,List<TimeRange>> days=new TreeMap<>();Set<Integer> minutes=new HashSet<>();for(ScheduleView v:week(doctor,from))if(v.schedule.available){Schedule s=v.schedule;minutes.add(s.minutes);days.computeIfAbsent(s.date.getDayOfWeek().getValue(),k->new ArrayList<>()).add(new TimeRange(s.startsAt,s.endsAt));}if(days.isEmpty()||minutes.size()!=1)throw fail(HttpStatus.BAD_REQUEST,"Copy requires available sessions with the same visit duration");return new TemplateView(null,doctor,"Copied week",minutes.iterator().next(),days.entrySet().stream().map(e->new DayHours(e.getKey(),e.getValue())).toList());}
    public void bootstrap(String email,String password,boolean demo) {
        if(em.createQuery("select count(a) from Account a where role='ADMIN'",Long.class).getSingleResult()==0 && password!=null && !password.isBlank()) {
            if(password.length()<10)throw new IllegalStateException("ADMIN_PASSWORD must contain at least 10 characters");
            create(new Patient("Hospital Administrator",email,30,"Other","Not provided"),password,"ADMIN");
        }
        if(demo && doctors().isEmpty()) {
            String[][] data={{"Dr. Maya Rahman","Cardiology","Heart & Vascular","201"},{"Dr. James Chen","General Medicine","Primary Care","102"},{"Dr. Sofia Ahmed","Pediatrics","Children's Health","305"}};
            for(String[] row:data){Doctor d=new Doctor();d.name=row[0];d.specialty=row[1];d.department=row[2];d.room=row[3];d.biography="Compassionate, evidence-based care with a focus on your long-term wellbeing.";em.persist(d);
                for(int day=0;day<14;day++){Schedule s=new Schedule();s.doctorId=d.id;s.date=today().plusDays(day);s.startsAt=LocalTime.of(9,0);s.endsAt=LocalTime.of(21,0);s.minutes=20;s.available=true;em.persist(s);}}
        }
    }
}
