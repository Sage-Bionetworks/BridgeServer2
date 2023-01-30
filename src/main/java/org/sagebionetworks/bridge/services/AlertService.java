package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.AlertIdCollectionValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertService {
    private AlertDao alertDao;
    private AccountService accountService;

    @Autowired
    public final void setAlertDao(AlertDao alertDao) {
        this.alertDao = alertDao;
    }

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Creates an alert.
     * 
     * This is for INTERNAL USE ONLY. There is no validation.
     * 
     * @param alert The alert to create.
     */
    public void createAlert(Alert alert) {
        checkNotNull(alert.getStudyId());
        checkNotNull(alert.getAppId());
        checkNotNull(alert.getUserId());
        checkNotNull(alert.getCategory());

        if (alertDao.getAlert(alert.getStudyId(), alert.getAppId(), alert.getUserId(), alert.getCategory())
                .isPresent()) {
            // alert already exists (don't want duplicate alerts for the same topic)
            return;
        }
        alert.setId(generateGuid());
        alert.setCreatedOn(DateUtils.getCurrentDateTime());
        alertDao.createAlert(alert);
    }

    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);

        PagedResourceList<Alert> alerts = alertDao.getAlerts(appId, studyId, offsetBy, pageSize);
        for (Alert alert : alerts.getItems()) {
            injectAccountRef(alert);
        }
        return alerts;
    }

    public void deleteAlerts(String appId, String studyId, AlertIdCollection alertsToDelete)
            throws EntityNotFoundException {
        checkNotNull(appId);
        checkNotNull(studyId);

        Validate.entityThrowingException(AlertIdCollectionValidator.INSTANCE, alertsToDelete);

        for (String alertId : alertsToDelete.getAlertIds()) {
            Alert alert = alertDao.getAlertById(alertId).orElseThrow(() -> new EntityNotFoundException(Alert.class));
            if (!appId.equals(alert.getAppId()) || !studyId.equals(alert.getStudyId())) {
                // trying to delete alert outside this study
                throw new EntityNotFoundException(Alert.class);
            }
        }
        alertDao.deleteAlerts(alertsToDelete.getAlertIds());
    }

    public void deleteAlertsForStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);

        alertDao.deleteAlertsForStudy(appId, studyId);
    }

    public void deleteAlertsForUserInApp(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);

        alertDao.deleteAlertsForUserInApp(appId, userId);
    }

    public void deleteAlertsForUserInStudy(String appId, String studyId, String userId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(userId);

        alertDao.deleteAlertsForUserInStudy(appId, studyId, userId);
    }

    private void injectAccountRef(Alert alert) {
        AccountId accountId = BridgeUtils.parseAccountId(alert.getAppId(), alert.getUserId());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        alert.setParticipant(new AccountRef(account, alert.getStudyId()));
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
