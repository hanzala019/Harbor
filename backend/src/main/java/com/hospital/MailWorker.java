package com.hospital;

import com.hospital.Models.MailJob;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

@Service
public class MailWorker {
    @PersistenceContext EntityManager em;
    final JavaMailSender sender;
    @Value("${app.mail-enabled}") boolean enabled;
    @Value("${app.mail-from}") String from;
    public MailWorker(JavaMailSender sender){this.sender=sender;}
    @Scheduled(fixedDelay=30000) @Transactional
    public void deliver(){
        if(!enabled)return;
        for(MailJob j:em.createQuery("from MailJob where sent=false and attempts<10 order by id",MailJob.class).setMaxResults(20).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()) {
            j.attempts++;
            try {SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(j.recipient);m.setSubject(j.subject);m.setText(j.body);sender.send(m);j.sent=true;j.sentAt=java.time.Instant.now();j.lastError=null;}
            catch(Exception e){j.lastError="SMTP delivery failed; check SMTP configuration and connectivity.";LoggerFactory.getLogger(MailWorker.class).warn("Mail job {} could not be delivered (attempt {}, {})",j.id,j.attempts,e.getClass().getSimpleName());}
        }
    }
}
