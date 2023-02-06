package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * A list of alert categories and the number of alerts within those categories
 * for a particular study.
 * 
 * We use list of objects containing category and count instead of map of category to count
 * - so that we can control the order in JSON (alphabetical by category)
 * - so that we can use category as an enum in the SDK
 */
@BridgeTypeName("AlertCategoriesAndCounts")
public class AlertCategoriesAndCounts implements BridgeEntity {
    private List<AlertCategoryAndCount> alertCategoriesAndCounts;

    public AlertCategoriesAndCounts() {
    }

    public AlertCategoriesAndCounts(List<AlertCategoryAndCount> alertCategoriesAndCounts) {
        this.alertCategoriesAndCounts = alertCategoriesAndCounts;
    }

    public List<AlertCategoryAndCount> getAlertCategoriesAndCounts() {
        return alertCategoriesAndCounts;
    }

    public void setAlertCategoriesAndCounts(List<AlertCategoryAndCount> alertCategoriesAndCounts) {
        this.alertCategoriesAndCounts = alertCategoriesAndCounts;
    }
}
