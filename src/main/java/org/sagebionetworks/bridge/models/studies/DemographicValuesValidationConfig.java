package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.dynamodb.DynamoDemographicValuesValidationConfig;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.DemographicValuesValidationType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Demographics are validated to ensure the inputs are not malformed, but they
 * can also be validated to ensure that their values conform to user-specified
 * restrictions (app-level demographics only). These restrictions are specified
 * using this class. It is stored as JSON in an app config element under the key
 * "bridge-validation-demographics-values-{categoryName}". This validation can
 * ensure that values are in a specified set of allowed values ("enum"
 * validation), or that values are numbers which fall within a certain range,
 * inclusive ("number range" validation).
 * 
 * The configuration consists of a validationType and validationRules.
 * validationType specifies the type of validation and determines the schema for
 * the validationRules. validationRules schemas are as follows:
 * 
 * enum: object mapping language codes to arrays of allowed values.
 * number_range: object containing up to 2 keys, "min" and "max", specifying the
 * ends of the allowed range, both of which are optional.
 */
@JsonDeserialize(as = DynamoDemographicValuesValidationConfig.class)
public interface DemographicValuesValidationConfig extends BridgeEntity {
    public static DemographicValuesValidationConfig create() {
        return new DynamoDemographicValuesValidationConfig();
    }

    public String getAppId();
    public void setAppId(String appId);

    public String getStudyId();
    public void setStudyId(String studyId);

    public String getCategoryName();
    public void setCategoryName(String categoryName);

    public DemographicValuesValidationType getValidationType();
    public void setValidationType(DemographicValuesValidationType validationType);

    public JsonNode getValidationRules();
    public void setValidationRules(JsonNode validationRules);
}
