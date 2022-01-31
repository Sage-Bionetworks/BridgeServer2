package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.Map;

import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * A simple wrapper that allows us to transfer this information as a map. We don’t want
 * to have to update a JavaBean-style object every time this metadata is adjusted, nor
 * do consumers necessarily care about the separate fields over the map as a whole.
 */
@BridgeTypeName("TimelineMetadata")
public class TimelineMetadataView {

    private final Map<String,String> metadata;
    
    public TimelineMetadataView(TimelineMetadata meta) {
        this.metadata = meta.asMap();
    }
    
    public Map<String, String> getMetadata() {
        return this.metadata;
    }
    
}
