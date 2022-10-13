package org.sagebionetworks.bridge.models.assessments;

import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.sagebionetworks.bridge.hibernate.LabelConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Label;

@Embeddable
@BridgeTypeName("ImageResource")
public class ImageResource implements BridgeEntity {
    private String name;
    private String module;
    @Convert(converter = LabelConverter.class)
    private Label label;

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
    @Convert(converter = LabelConverter.class)
    public Label getLabel() {
        return label;
    }
    public void setLabel(Label label) {
        this.label = label;
    }
}
