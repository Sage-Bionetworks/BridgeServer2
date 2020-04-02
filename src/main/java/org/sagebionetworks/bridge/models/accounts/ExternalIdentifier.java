package org.sagebionetworks.bridge.models.accounts;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(as=DynamoExternalIdentifier.class)
public interface ExternalIdentifier extends BridgeEntity {

    static ExternalIdentifier create(String studyId, String identifier) {
        if (isBlank(studyId)) {
            throw new BadRequestException("studyId cannot be null or blank");
        }
        return new DynamoExternalIdentifier(studyId, identifier);
    }
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getSubstudyId();
    void setSubstudyId(String substudyId);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getHealthCode();
    void setHealthCode(String healthCode);
}
