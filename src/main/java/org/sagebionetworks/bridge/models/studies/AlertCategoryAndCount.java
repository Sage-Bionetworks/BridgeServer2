package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;

/**
 * Represents an alert category and the number of alerts within that category
 * for a particular study. Used as the result from a database query.
 */
@BridgeTypeName("AlertCategoryAndCount")
public class AlertCategoryAndCount implements BridgeEntity {
    private AlertCategory category;
    private long count;

    public AlertCategoryAndCount() {
    }

    public AlertCategoryAndCount(AlertCategory category, long count) {
        this.category = category;
        this.count = count;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public void setCategory(AlertCategory category) {
        this.category = category;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
