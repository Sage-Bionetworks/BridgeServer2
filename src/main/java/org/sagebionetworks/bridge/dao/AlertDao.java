package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;
import org.sagebionetworks.bridge.models.studies.AlertCategoriesAndCounts;

public interface AlertDao {
    /**
     * Creates an alert.
     */
    void createAlert(Alert alert);

    /**
     * Deletes a specific alert.
     */
    void deleteAlert(Alert alert);

    /**
     * Fetches a single alert with filters.
     */
    Optional<Alert> getAlert(String studyId, String appId, String userId, AlertCategory category);

    /**
     * Fetches a single alert by id.
     */
    Optional<Alert> getAlertById(String alertId);

    /**
     * Fetches alerts for a study.
     */
    PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize,
            Set<AlertCategory> alertCategories);

    /**
     * Batch deletes alerts given a list of IDs of alerts to delete.
     */
    void deleteAlerts(List<String> alertIds);

    /**
     * Deletes all alerts for all users in a study.
     */
    void deleteAlertsForStudy(String appId, String studyId);

    /**
     * Deletes all alerts for a specific user in an app.
     */
    void deleteAlertsForUserInApp(String appId, String userId);

    /**
     * Deletes all alerts for a specific user in a study.
     */
    void deleteAlertsForUserInStudy(String appId, String studyId, String userId);

    /**
     * Fetches a list of alert categories and the number of alerts within that
     * category for a study.
     */
    AlertCategoriesAndCounts getAlertCategoriesAndCounts(String appId, String studyId);

    /**
     * Marks all specified alerts as read or unread, as specified.
     */
    void setAlertsReadState(List<String> alertIds, boolean read);
}
