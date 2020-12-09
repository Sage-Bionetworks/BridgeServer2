package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.models.appconfig.ConfigResolver.INSTANCE;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

public final class AssessmentReference {
    
    private final ConfigResolver resolver;
    private final String guid;
    private final String id;
    private final String sharedId;
    
    @JsonCreator
    public AssessmentReference(@JsonProperty("guid") String guid,
            @JsonProperty("id") String id, @JsonProperty("sharedId") String sharedId) {
        this(INSTANCE, guid, id, sharedId);
    }

    public AssessmentReference(ConfigResolver resolver, String guid, String id, String sharedId) {
        this.resolver = resolver;
        this.guid = guid;
        this.id = id;
        this.sharedId = sharedId;
    }
    
    public String getGuid() {
        return guid;
    }
    public String getId() {
        return id;
    }
    public String getConfigHref() {
        if (guid == null) {
            return null;
        }
        return resolver.url("ws", "/v1/assessments/" + guid + "/config");
    }
    /**
     * If this assessment was derived from a shared assessment, the shared assessment's
     * identifier may help to find it in config. We do not provide a GUID or revision 
     * because we don't anticipate clients will want to retrieve the original shared
     * assessment.
     */
    public String getSharedId() {
        return sharedId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AssessmentReference other = (AssessmentReference) obj;
        return Objects.equals(guid, other.guid);
    }

    @Override
    public String toString() {
        return "AssessmentReference [id=" + id + ", sharedId=" + sharedId + ", guid=" + guid + ", configHref="
                + getConfigHref() + "]";
    }
}
