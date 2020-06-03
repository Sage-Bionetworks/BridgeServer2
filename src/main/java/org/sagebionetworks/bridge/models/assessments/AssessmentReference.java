package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.models.appconfig.ConfigResolver.INSTANCE;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

public final class AssessmentReference {
    
    private final ConfigResolver resolver;
    private final String guid;
    private final String identifier;
    private final String sharedId;
    
    @JsonCreator
    public AssessmentReference(@JsonProperty("guid") String guid,
            @JsonProperty("identifier") String identifier, @JsonProperty("sharedId") String sharedId) {
        this(INSTANCE, guid, identifier, sharedId);
    }

    public AssessmentReference(ConfigResolver resolver, String guid, String identifier, String sharedId) {
        this.resolver = resolver;
        this.guid = guid;
        this.identifier = identifier;
        this.sharedId = sharedId;
    }
    
    public String getGuid() {
        return guid;
    }
    public String getIdentifier() {
        return identifier;
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
        return "AssessmentReference [identifier=" + identifier + ", sharedId=" + sharedId + ", guid=" + guid
                + ", configHref=" + getConfigHref() + "]";
    }
}
