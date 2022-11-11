package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertService {
    private AlertDao alertDao;

    @Autowired
    public final void setAlertDao(AlertDao alertDao) {
        this.alertDao = alertDao;
    }

    /**
     * Creates an alert.
     * 
     * This is for INTERNAL USE ONLY. There is no validation.
     * 
     * @param alert The alert to create.
     */
    public void createAlert(Alert alert) {
        if (alertDao.getAlert(alert.getStudyId(), alert.getAppId(), alert.getParticipant().getIdentifier(),
                alert.getCategory()).isPresent()) {
            // alert already exists (don't want duplicate alerts for the same topic)
            return;
        }
        alert.setId(generateGuid());
        alert.setCreatedOn(DateUtils.getCurrentDateTime());
        alertDao.createAlert(alert);
    }

    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize) {
        return alertDao.getAlerts(appId, studyId, offsetBy, pageSize);
    }

    public void deleteAlerts(String appId, String studyId, List<String> alertIds) throws EntityNotFoundException {
        for (String alertId : alertIds) {
            Alert alert = alertDao.getAlertById(alertId).orElseThrow(() -> new EntityNotFoundException(Alert.class));
            if (!appId.equals(alert.getAppId()) || !studyId.equals(alert.getStudyId())) {
                // trying to delete alert outside this study
                throw new EntityNotFoundException(Alert.class);
            }
        }
        alertDao.deleteAlerts(alertIds);
    }

    /**
     * Generates a guid.
     * 
     * @return a generated guid.
     */
    public String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
