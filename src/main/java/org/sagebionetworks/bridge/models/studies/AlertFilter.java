package org.sagebionetworks.bridge.models.studies;

import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;

/**
 * Used to filter alerts. A set of alert categories can be provided which will
 * be used to filter a call for fetching alerts.
 */
@BridgeTypeName("AlertFilter")
public class AlertFilter implements BridgeEntity {
    private Set<AlertCategory> alertCategories;

    public AlertFilter() {
    }

    public AlertFilter(Set<AlertCategory> alertCategories) {
        this.alertCategories = alertCategories;
    }

    public Set<AlertCategory> getAlertCategories() {
        return alertCategories;
    }

    public void setAlertCategories(Set<AlertCategory> alertCategories) {
        this.alertCategories = alertCategories;
    }
}
