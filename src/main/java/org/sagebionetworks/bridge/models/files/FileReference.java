package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.models.appconfig.ConfigResolver.INSTANCE;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

public final class FileReference {
    
    private final ConfigResolver resolver;
    private final String guid;
    private final DateTime createdOn;

    public FileReference(ConfigResolver resolver, String guid, DateTime createdOn) {
        this.resolver = resolver;
        this.guid = guid;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
    }

    @JsonCreator
    public FileReference(@JsonProperty("guid") String guid, @JsonProperty("createdOn") DateTime createdOn) {
        this(INSTANCE, guid, createdOn);
    }

    public String getGuid() {
        return guid;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public String getHref() {
        if (guid == null || createdOn == null) {
            return null;
        }
        return resolver.url("docs", "/" + guid + "." + createdOn.getMillis());
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
        FileReference other = (FileReference) obj;
        return (Objects.equals(createdOn, other.createdOn) && 
                Objects.equals(guid, other.guid));
    }

    @Override
    public String toString() {
        return "FileReference [guid=" + guid + ", createdOn=" + createdOn + ", href=" + getHref() + "]";
    }
}
