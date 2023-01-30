package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Used to delete alerts; contains a list of alert IDs for batch deletion.
 */
@BridgeTypeName("AlertIdCollection")
public class AlertIdCollection implements BridgeEntity {
    private List<String> alertIds;

    public AlertIdCollection() {
    }

    public AlertIdCollection(List<String> alertIds) {
        this.alertIds = alertIds;
    }

    public List<String> getAlertIds() {
        return alertIds;
    }

    public void setAlertIds(List<String> alertIds) {
        this.alertIds = alertIds;
    }
}
