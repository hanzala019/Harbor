package com.hospital;

import jakarta.persistence.*;
import java.time.*;

public final class Models {
    @Entity(name="Account") @Table(name="accounts")
    public static class Account {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        public String name, email, password, role, phone, gender;
        public Integer age;
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
        public Integer serial, slotIndex, age;
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
    }
}
