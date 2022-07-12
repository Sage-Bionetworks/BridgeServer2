package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import org.sagebionetworks.bridge.json.DemographicCollectionDeserializer;
import org.sagebionetworks.bridge.json.DemographicCollectionSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = DemographicCollectionSerializer.class)
@JsonDeserialize(using = DemographicCollectionDeserializer.class)
public class DemographicCollection {
    private List<Demographic> demographics;

    public DemographicCollection(List<Demographic> demographics) {
        this.demographics = demographics;
    }

    public List<Demographic> getDemographics() {
        return demographics;
    }

    public void setDemographics(List<Demographic> demographics) {
        this.demographics = demographics;
    }

    @Override
    public String toString() {
        return "DemographicCollection [demographics=" + demographics + "]";
    }
}
