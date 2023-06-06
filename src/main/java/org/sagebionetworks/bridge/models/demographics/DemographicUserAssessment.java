package org.sagebionetworks.bridge.models.demographics;

import org.sagebionetworks.bridge.json.DemographicUserAssessmentDeserializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Class used as the deserialization target for deserializing a demographic user
 * from the assessment format
 */
@JsonDeserialize(using = DemographicUserAssessmentDeserializer.class)
public class DemographicUserAssessment {
    private DemographicUser demographicUser;

    public DemographicUserAssessment(DemographicUser demographicUser) {
        this.demographicUser = demographicUser;
    }

    public DemographicUserAssessment() {
    }

    public DemographicUser getDemographicUser() {
        return demographicUser;
    }

    public void setDemographicUser(DemographicUser demographicUser) {
        this.demographicUser = demographicUser;
    }
}
