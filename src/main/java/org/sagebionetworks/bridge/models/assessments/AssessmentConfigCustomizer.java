package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.BridgeConstants.ID_FIELD_NAME;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AssessmentConfigCustomizer implements BiConsumer<String, JsonNode> {

    private final Map<String, Set<PropertyInfo>> fields;
    private final Map<String, Map<String, JsonNode>> updates;
    private boolean updated;
    
    public AssessmentConfigCustomizer(Map<String, Set<PropertyInfo>> fields,
            Map<String, Map<String, JsonNode>> updates) {
        this.fields = fields;
        this.updates = updates;
        this.updated = false;
    }
    
    public boolean hasUpdated() {
        return updated;
    }
    
    @Override
    public void accept(String fieldName, JsonNode node) {
        if (node.isObject() && node.has(ID_FIELD_NAME)) {
            ObjectNode object = (ObjectNode)node;
            String identifier = object.get(ID_FIELD_NAME).textValue();
            
            // Can this node be updated?
            if (fields.containsKey(identifier)) {
                // Does this node actually have updates?
                Map<String, JsonNode> nodeUpdates = updates.get(identifier);
                if (nodeUpdates != null) {
                    // examine each property that is allowed to change
                    Set<PropertyInfo> infos = fields.get(identifier);
                    for (PropertyInfo info : infos) {
                        // Does this property actually have an update?
                        if (nodeUpdates.containsKey(info.getPropName())) {
                            JsonNode propUpdate = nodeUpdates.get(info.getPropName());
                            if (propUpdate == null || propUpdate.isNull()) {
                                object.remove(info.getPropName());
                            } else {
                                object.set(info.getPropName(), propUpdate);
                            }
                            updated = true;
                        }
                    }
                }
            }
        }
    }
}
