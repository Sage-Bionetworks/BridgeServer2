package org.sagebionetworks.bridge.models.schedules2;

import javax.persistence.Embeddable;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Embeddable
@Table(name = "SessionMessages")
public class Message implements Localized {

    private final String lang;
    private final String subject;
    private final String body;
    
    @JsonCreator
    public Message(@JsonProperty("lang") String lang, @JsonProperty("subject") String subject,
            @JsonProperty("body") String body) {
        this.lang = lang;
        this.subject = subject;
        this.body = body;
    }
    
    public String getLang() {
        return lang;
    }
    public String getSubject() {
        return subject;
    }
    public String getBody() {
        return body;
    }
}
