package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.appconfig.ConfigResolver.INSTANCE;

import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
public final class SurveyReference {

    private final ConfigResolver resolver;
    private final String identifier;
    private final String guid;
    private final DateTime createdOn;

    public SurveyReference(ConfigResolver resolver, String identifier, String guid, DateTime createdOn) {
        this.resolver = resolver;
        this.identifier = identifier;
        this.guid = guid;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
    }

    @JsonCreator
    public SurveyReference(@JsonProperty("identifier") String identifier, @JsonProperty("guid") String guid,
            @JsonProperty("createdOn") DateTime createdOn) {
        this(INSTANCE, identifier, guid, createdOn);
    }

    public String getIdentifier() {
        return identifier;
    }
    public String getGuid() {
        return guid;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public String getHref() {
        if (createdOn == null) {
            return resolver.url("ws", "/v3/surveys/" + guid + "/revisions/published");
        }
        return resolver.url("ws", "/v3/surveys/" + guid + "/revisions/" + 
                createdOn.toString(ISODateTimeFormat.dateTime()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, createdOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyReference other = (SurveyReference) obj;
        return Objects.equals(createdOn, other.createdOn) && Objects.equals(guid, other.guid);
    }

    public boolean equalsSurvey(GuidCreatedOnVersionHolder keys) {
        if (keys == null) {
            return false;
        }
        return (keys.getGuid().equals(guid) && createdOn != null && keys.getCreatedOn() == createdOn.getMillis());
    }
    
    @Override
    public String toString() {
        return String.format("SurveyReference [identifier=%s, guid=%s, createdOn=%s, href=%s]",
            identifier, guid, createdOn, getHref());
    }
}
