package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;
import org.sagebionetworks.bridge.validators.DemographicValuesValidationType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "DemographicValuesValidationConfig")
@BridgeTypeName("DemographicValuesValidationConfig")
public class DynamoDemographicValuesValidationConfig implements DemographicValuesValidationConfig {
    @JsonIgnore
    private String appId;
    @JsonIgnore
    private String studyId;
    @JsonIgnore
    private String categoryName;
    private DemographicValuesValidationType validationType;
    private JsonNode validationRules;

    @DynamoDBHashKey
    @JsonIgnore
    public String getHashKey() {
        if (studyId == null) {
            return appId + ":";
        }
        return appId + ":" + studyId;
    }

    public void setHashKey(String key) {
        if (key != null) {
            String[] parts = key.split(":", 2);
            if (parts.length == 2) {
                appId = parts[0];
                studyId = parts[1];
            } else if (parts.length == 1) {
                appId = parts[0];
                studyId = null;
            }
        }
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    @Override
    @DynamoDBRangeKey
    public String getCategoryName() {
        return categoryName;
    }

    @Override
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    @Override
    @DynamoDBTypeConverted(converter = EnumMarshaller.class)
    public DemographicValuesValidationType getValidationType() {
        return validationType;
    }

    @Override
    @DynamoDBTypeConverted(converter = EnumMarshaller.class)
    public void setValidationType(DemographicValuesValidationType validationType) {
        this.validationType = validationType;
    }

    @Override
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public JsonNode getValidationRules() {
        return validationRules;
    }

    @Override
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public void setValidationRules(JsonNode validationRules) {
        this.validationRules = validationRules;
    }
}
