package org.sagebionetworks.bridge.models.schedules2;

import javax.persistence.Embeddable;
import javax.persistence.Table;

@Embeddable
@Table(name = "ScheduleSessionMessages")
public class Message {

    private String language;
    private String subject;
    private String body;
    
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }
}
