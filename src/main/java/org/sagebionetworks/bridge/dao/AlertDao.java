package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;

public interface AlertDao {
    void createAlert(Alert alert);

    Optional<Alert> getAlert(String studyId, String appId, String accountIdentifier, AlertCategory category);

    Optional<Alert> getAlertById(String alertId);

    PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize);

    void deleteAlerts(List<String> alertIds);
}
