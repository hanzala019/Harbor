package com.hospital;

import jakarta.persistence.*;
import java.time.*;

public final class Models {
    @Entity(name="Account") @Table(name="accounts")
    public static class Account {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public String name, email, password, role, phone, gender;
        public Integer age;
        public Boolean enabled=true;
    }
    @Entity(name="LoginToken") @Table(name="login_tokens")
    public static class LoginToken {
        @Id public String hash;
        public Long accountId;
        public Instant expiresAt;
    }
    @Entity(name="Doctor") @Table(name="doctors")
    public static class Doctor {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public String name, specialty, department, room;
        @Column(length=4000) public String biography;
    }
    @Entity(name="PasswordReset") @Table(name="password_resets")
    public static class PasswordReset {
        @Id public Long accountId;
        public String hash;
        public Instant createdAt, expiresAt;
        public Boolean used;
    }
    @Entity(name="Schedule") @Table(name="schedules")
    public static class Schedule {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long doctorId;
        public LocalDate date;
        public LocalTime startsAt, endsAt;
        public Integer minutes;
        public Boolean available;
    }
    @Entity(name="Appointment") @Table(name="appointments")
    public static class Appointment {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long scheduleId, accountId;
        public Integer serial, slotIndex, originalSlotIndex, age;
        public String name, email, phone, gender, status, claimHash;
        public Instant createdAt;
    }
    @Entity(name="Permission") @Table(name="permissions")
    public static class Permission {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long accountId, doctorId;
        public Boolean editInfo, editSchedule, editAvailability;
    }
    @Entity(name="MailJob") @Table(name="mail_jobs")
    public static class MailJob {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public String recipient, subject;
        @Column(length=8000) public String body;
        public Boolean sent;
        public Integer attempts;
        public Long appointmentId;
        public Instant createdAt, sentAt;
        @Column(length=1000) public String lastError;
    }
    @Entity(name="GuestLink") @Table(name="guest_links")
    public static class GuestLink {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long appointmentId;
        public String hash;
        public Instant expiresAt;
        public Boolean revoked;
    }
    @Entity(name="ScheduleTemplate") @Table(name="schedule_templates")
    public static class ScheduleTemplate {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long doctorId;
        @Column(length=150) public String name;
        public Integer minutes;
        @Column(length=12000) public String rules;
    }
    @Entity(name="AuditEvent") @Table(name="audit_events")
    public static class AuditEvent {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public Long actorId, entityId, doctorId;
        public String actorName, action, entityType;
        @Column(length=4000) public String details;
        public Instant occurredAt;
    }
}
