package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.config.Environment.LOCAL;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

public class FileReference {
    
    private static final Environment ENV = BridgeConfigFactory.getConfig().getEnvironment();
    private static final String BASE_URL = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");
    
    private final String guid;
    private final DateTime createdOn;
    
    @JsonCreator
    public FileReference(@JsonProperty("guid") String guid, @JsonProperty("createdOn") DateTime createdOn) {
        this.guid = guid;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
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
        String protocol = (ENV == LOCAL) ? "http" : "https";
        return protocol + "://" + BASE_URL + "/" + guid + "." + createdOn.getMillis();
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
        return (Objects.equals(createdOn, other.createdOn) && Objects.equals(guid, other.guid));
    }

    @Override
    public String toString() {
        return "FileReference [guid=" + guid + ", createdOn=" + createdOn + ", href=" + getHref() + "]";
    }
}
