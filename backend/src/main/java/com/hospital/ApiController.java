package com.hospital;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import static com.hospital.HospitalService.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    final HospitalService service;
    public ApiController(HospitalService service){this.service=service;}
    @Bean ApplicationRunner bootstrap(@Value("${app.admin-email}")String email,@Value("${app.admin-password}")String password,@Value("${app.seed}")boolean seed) {return args->service.bootstrap(email,password,seed);}
    public record Login(@NotBlank @Email String email,@NotBlank String password) {}
    public record Claim(@NotNull Long appointmentId,@NotBlank String claimToken,@Size(min=10,max=72) @NotBlank String password) {}
    public record Status(@NotBlank String status) {}
    public record Availability(boolean available) {}
    public record Destination(Long targetId,List<Long> appointmentIds) {}
    public record GuestSecret(@NotBlank String token) {}
    @GetMapping("/health") Map<String,String> health(){return Map.of("status","ok");}
    @GetMapping("/config") Object config(){return service.config();}
    @PostMapping("/guest/view") Object guestView(@Valid @RequestBody GuestSecret input){return service.guestView(input.token);}
    @PostMapping("/guest/cancel") Object guestCancel(@Valid @RequestBody GuestSecret input){return service.guestCancel(input.token);}
    @PutMapping("/me/password") void password(@Valid @RequestBody PasswordChange input){service.changePassword(input);}
    @PatchMapping("/admin/accounts/{id}") Object account(@PathVariable Long id,@Valid @RequestBody AccountUpdate input){return service.manageAccount(id,input);}
    @GetMapping("/staff/audit") Object audit(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="") String search){return service.auditHistory(from,to,search);}
    @GetMapping("/staff/mail") Object mail(){return service.mailStatus();}
    @PostMapping("/staff/appointments/{id}/resend") void resend(@PathVariable Long id){service.resendConfirmation(id);}
    @DeleteMapping("/staff/appointments/{id}/guest-links") void revokeGuest(@PathVariable Long id){service.revokeGuestAccess(id);}
    @PostMapping("/staff/appointments/check-duplicate") Object duplicate(@Valid @RequestBody DuplicateInput input){return service.checkDuplicate(input);}
    @GetMapping("/staff/schedules/week") Object week(@RequestParam Long doctorId,@RequestParam LocalDate from){return service.week(doctorId,from);}
    @GetMapping("/staff/templates") Object templates(@RequestParam Long doctorId){return service.templates(doctorId);}
    @PostMapping("/staff/templates") Object template(@Valid @RequestBody TemplateInput input){return service.saveTemplate(input);}
    @DeleteMapping("/staff/templates/{id}") void templateDelete(@PathVariable Long id){service.deleteTemplate(id);}
    @GetMapping("/staff/schedules/copy-week") Object copyWeek(@RequestParam Long doctorId,@RequestParam LocalDate from){return service.copyWeek(doctorId,from);}
    @PostMapping("/auth/login") Object login(@Valid @RequestBody Login input){return service.login(input.email,input.password);}
    @PostMapping("/auth/register") Object register(@Valid @RequestBody Registration input){return service.register(input);}
    @PostMapping("/auth/claim") Object claim(@Valid @RequestBody Claim input){return service.claim(input.appointmentId,input.claimToken,input.password);}
    @PostMapping("/auth/logout") void logout(@RequestHeader("Authorization")String token){service.logout(token.substring(7));}
    @GetMapping("/me") Object me(){return service.me();}
    @PutMapping("/me") Object profile(@Valid @RequestBody Patient input){return service.updateProfile(input);}
    @GetMapping("/doctors") Object doctors(){return service.doctors();}
    @GetMapping("/doctors/{id}") Object doctor(@PathVariable Long id){return service.doctor(id);}
    @GetMapping("/schedules") Object schedules(@RequestParam Long doctorId,@RequestParam LocalDate date){return service.schedules(doctorId,date);}
    @PostMapping("/appointments") Object book(@Valid @RequestBody Booking input){return service.book(input);}
    @GetMapping("/appointments/mine") Object mine(){return service.mine();}
    @PatchMapping("/appointments/{id}/status") Object status(@PathVariable Long id,@Valid @RequestBody Status input){return service.status(id,input.status);}
    @GetMapping("/staff/doctors") Object staffDoctors(){return service.staffDoctors();}
    @GetMapping("/staff/permissions") Object permissions(){return service.permissions();}
    @GetMapping("/staff/appointments") Object staffAppointments(@RequestParam LocalDate from,@RequestParam LocalDate to){return service.staffAppointments(from,to);}
    @PostMapping("/admin/doctors") Object createDoctor(@Valid @RequestBody DoctorInput input){return service.saveDoctor(null,input);}
    @PutMapping("/staff/doctors/{id}") Object doctor(@PathVariable Long id,@Valid @RequestBody DoctorInput input){return service.saveDoctor(id,input);}
    @PostMapping("/staff/schedules") Object schedule(@Valid @RequestBody ScheduleInput input){return service.saveSchedule(null,input);}
    @PostMapping("/staff/schedules/weekly/preview") Object weeklyPreview(@Valid @RequestBody WeeklyScheduleInput input){return service.previewWeekly(input);}
    @PostMapping("/staff/schedules/weekly") Object weeklySchedule(@Valid @RequestBody WeeklyScheduleInput input){return service.createWeekly(input);}
    @PutMapping("/staff/schedules/{id}") Object schedule(@PathVariable Long id,@Valid @RequestBody ScheduleInput input){return service.saveSchedule(id,input);}
    @GetMapping("/staff/schedules/{id}/affected") Object affected(@PathVariable Long id){return service.affected(id);}
    @PatchMapping("/staff/schedules/{id}/availability") Object availability(@PathVariable Long id,@RequestBody Availability input){return service.availability(id,input.available);}
    @PostMapping("/staff/schedules/{id}/notify") void notify(@PathVariable Long id,@RequestBody Destination input){service.notifyAffected(id,input.targetId);}
    @PostMapping("/staff/schedules/{id}/transfer") Object transfer(@PathVariable Long id,@RequestBody Destination input){if(input.targetId==null)throw fail(org.springframework.http.HttpStatus.BAD_REQUEST,"Choose a destination session");return service.transferSelected(id,input.targetId,input.appointmentIds);}
    @GetMapping("/admin/accounts") Object accounts(@RequestParam String role){return service.accounts(role);}
    @PostMapping("/admin/receptionists") Object receptionist(@Valid @RequestBody Registration input){return service.receptionist(input);}
    @PutMapping("/admin/permissions") Object permission(@Valid @RequestBody PermissionInput input){return service.permission(input);}
    @PutMapping("/admin/permissions/bulk") Object bulkPermissions(@Valid @RequestBody BulkPermissionInput input){return service.permissionsBulk(input);}
    @DeleteMapping("/admin/permissions/{id}") void revoke(@PathVariable Long id){service.revoke(id);}
}
