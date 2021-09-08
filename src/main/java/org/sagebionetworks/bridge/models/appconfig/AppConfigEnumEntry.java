package org.sagebionetworks.bridge.models.appconfig;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// This is a nicety so the “value” and “label” properties aren’t separated by the “labels” prop.
@JsonPropertyOrder({"value", "label", "labels", "type"})
public final class AppConfigEnumEntry {

    private String value;
    private List<Label> labels;
    
    public String getValue() {
        return value;
    }
    public String getLabel() {
        if (!BridgeUtils.isEmpty(labels)) {
            List<String> langs = RequestContext.get().getCallerLanguages();
            Label label = BridgeUtils.selectByLang(labels, langs, new Label("en", null));
            if (label != null) {
                return label.getValue();
            }
        }
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public List<Label> getLabels() {
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
    @Override
    public int hashCode() {
        return Objects.hash(labels, value);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AppConfigEnumEntry other = (AppConfigEnumEntry) obj;
        return Objects.equals(labels, other.labels) && Objects.equals(value, other.value);
    }
}
