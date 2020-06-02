package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.config.Environment.LOCAL;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

public final class AssessmentReference {
    
    private final String guid;
    private final String id;
    private final String sharedId;
    
    @JsonCreator
    public AssessmentReference(@JsonProperty("guid") String guid, @JsonProperty("id") String id,
            @JsonProperty("sharedId") String sharedId) {
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
        Environment env = BridgeConfigFactory.getConfig().getEnvironment();
        String baseUrl = BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws");
        String protocol = (env == LOCAL) ? "http" : "https";
        return protocol + "://" + baseUrl + "/v1/assessments/" + guid + "/config";
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
        return Objects.hash(id, guid, sharedId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AssessmentReference other = (AssessmentReference) obj;
        return (Objects.equals(id, other.id) &&
                Objects.equals(guid, other.guid) && 
                Objects.equals(guid, other.guid) &&
                Objects.equals(sharedId, other.sharedId));
    }

    @Override
    public String toString() {
        return "AssessmentReference [id=" + id + ", sharedId=" + sharedId + ", guid=" + guid + "]";
    }
}
