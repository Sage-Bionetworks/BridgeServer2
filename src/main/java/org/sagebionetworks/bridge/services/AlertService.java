package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.alerts.Alert;
import org.sagebionetworks.bridge.validators.AlertValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertService {
    private AlertDao alertDao;

    @Autowired
    public final void setAlertDao(AlertDao alertDao) {
        this.alertDao = alertDao;
    }

    public void createAlert(Alert alert) {
        alert.setId(generateGuid());
        Validate.entityThrowingException(AlertValidator.INSTANCE, alert);
        alertDao.createAlert(alert);
    }

    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize) {
        return alertDao.getAlerts(appId, studyId, offsetBy, pageSize);
    }

    public void deleteAlerts(String appId, String studyId, List<String> alertIds) throws EntityNotFoundException {
        for (String alertId : alertIds) {
            Alert alert = alertDao.getAlert(alertId).orElseThrow(() -> new EntityNotFoundException(Alert.class));
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
