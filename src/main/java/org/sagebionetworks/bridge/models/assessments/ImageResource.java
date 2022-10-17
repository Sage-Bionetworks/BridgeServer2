package org.sagebionetworks.bridge.models.assessments;

import java.util.List;

import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.sagebionetworks.bridge.hibernate.LabelListConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Label;

@Embeddable
@BridgeTypeName("ImageResource")
public class ImageResource implements BridgeEntity {
    private String name;
    private String module;
    @Convert(converter = LabelListConverter.class)
    private List<Label> labels;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    @Convert(converter = LabelListConverter.class)
    public List<Label> getLabels() {
        return labels;
    }

    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
}
