package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.config.Environment.PROD;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

public class AssessmentReference {
    
    private final String id;
    private final String guid;
    private final String sharedId;
    
    @JsonCreator
    public AssessmentReference(@JsonProperty("id") String id, @JsonProperty("sharedId") String sharedId,
            @JsonProperty("guid") String guid) {
        this.id = id;
        this.guid = guid;
        this.sharedId = sharedId;
    }

    public String getId() {
        return id;
    }
    public String getGuid() {
        return guid;
    }
    public String getConfigHref() {
        if (guid == null) {
            return null;
        }
        Environment env = BridgeConfigFactory.getConfig().getEnvironment();
        String baseUrl = BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws");
        String protocol = (env == PROD) ? "https" : "http";
        return protocol + "://" + baseUrl + "/v1/assessments/" + guid + "/config";
    }
    /**
     * If this assessment was derived from a shared assessment, the shared assessment's
     * identifier may help to identifier it. We do not provide a GUID or revision because
     * we don't anticipate trying to retrieve an actual shared assessment.
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
