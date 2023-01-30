package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;

public interface AlertDao {
    /**
     * Creates an alert.
     */
    void createAlert(Alert alert);

    /**
     * Fetches a single alert.
     */
    Optional<Alert> getAlert(String studyId, String appId, String userId, AlertCategory category);

    /**
     * Fetches a single alert by id.
     */
    Optional<Alert> getAlertById(String alertId);

    /**
     * Fetches alerts for a study.
     */
    PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize);

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
}
