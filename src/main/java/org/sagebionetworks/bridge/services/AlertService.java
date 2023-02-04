package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;
import org.sagebionetworks.bridge.models.studies.AlertCategoriesAndCounts;
import org.sagebionetworks.bridge.models.studies.AlertFilter;
import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.AlertFilterValidator;
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

        Optional<Alert> existingAlert = alertDao.getAlert(alert.getStudyId(), alert.getAppId(), alert.getUserId(),
                alert.getCategory());
        if (existingAlert.isPresent()) {
            // alert already exists: overwrite
            alertDao.deleteAlert(existingAlert.get());
        }
        alert.setId(generateGuid());
        alert.setCreatedOn(DateUtils.getCurrentDateTime());
        alertDao.createAlert(alert);
    }

    /**
     * Fetches alerts for a study.
     */
    public PagedResourceList<Alert> getAlerts(String appId, String studyId, int offsetBy, int pageSize,
            AlertFilter alertFilter) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(alertFilter);

        Validate.entityThrowingException(AlertFilterValidator.INSTANCE, alertFilter);

        // if no filters applied, get all alerts
        if (alertFilter.getAlertCategories().isEmpty()) {
            alertFilter.setAlertCategories(
                    Arrays.stream(AlertCategory.values()).collect(Collectors.toSet()));
        }

        PagedResourceList<Alert> alerts = alertDao.getAlerts(appId, studyId, offsetBy, pageSize,
                alertFilter.getAlertCategories());
        // alerts are only stored with the userId; we need to insert the AccountRef so
        // alerts can be displayed with external id or other data
        for (Alert alert : alerts.getItems()) {
            injectAccountRef(alert);
        }
        return alerts;
    }

    /**
     * Batch deletes alerts given a list of IDs of alerts to delete.
     */
    public void deleteAlerts(String appId, String studyId, AlertIdCollection alertsToDelete)
            throws EntityNotFoundException {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(alertsToDelete);

        Validate.entityThrowingException(AlertIdCollectionValidator.INSTANCE, alertsToDelete);

        // don't modify alerts outside study
        verifyAlertsInStudy(appId, studyId, alertsToDelete);

        alertDao.deleteAlerts(alertsToDelete.getAlertIds());
    }

    /**
     * Deletes all alerts for all users in a study.
     */
    public void deleteAlertsForStudy(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);

        alertDao.deleteAlertsForStudy(appId, studyId);
    }

    /**
     * Deletes all alerts for a specific user in an app.
     */
    public void deleteAlertsForUserInApp(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);

        alertDao.deleteAlertsForUserInApp(appId, userId);
    }

    /**
     * Deletes all alerts for a specific user in a study.
     */
    public void deleteAlertsForUserInStudy(String appId, String studyId, String userId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(userId);

        alertDao.deleteAlertsForUserInStudy(appId, studyId, userId);
    }

    /**
     * Marks an alert as read.
     */
    public void markAlertsRead(String appId, String studyId, AlertIdCollection alertsToMarkRead)
            throws EntityNotFoundException {
        setAlertsReadState(appId, studyId, alertsToMarkRead, true);
    }

    /**
     * Marks an alert as unread.
     */
    public void markAlertsUnread(String appId, String studyId, AlertIdCollection alertsToMarkUnread)
            throws EntityNotFoundException {
        setAlertsReadState(appId, studyId, alertsToMarkUnread, false);
    }

    /**
     * Marks an alert as read or unread.
     */
    private void setAlertsReadState(String appId, String studyId, AlertIdCollection alertIds, boolean read)
            throws EntityNotFoundException {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(alertIds);

        Validate.entityThrowingException(AlertIdCollectionValidator.INSTANCE, alertIds);

        // don't modify alerts outside study
        verifyAlertsInStudy(appId, studyId, alertIds);

        alertDao.setAlertsReadState(alertIds.getAlertIds(), read);
    }

    /**
     * Calculates and returns a list of alert categories and the number of alerts
     * within that category for a study.
     */
    public AlertCategoriesAndCounts getAlertCategoriesAndCounts(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);

        return alertDao.getAlertCategoriesAndCounts(appId, studyId);
    }

    /**
     * Ensures that all alerts belong to the specified study.
     * 
     * @throws EntityNotFoundException if any alert is not part of the specified
     *                                 study or if the alert does not exist.
     */
    private void verifyAlertsInStudy(String appId, String studyId, AlertIdCollection alertIds)
            throws EntityNotFoundException {
        for (String alertId : alertIds.getAlertIds()) {
            Alert alert = alertDao.getAlertById(alertId).orElseThrow(() -> new EntityNotFoundException(Alert.class));
            if (!appId.equals(alert.getAppId()) || !studyId.equals(alert.getStudyId())) {
                // trying to modify alert outside this study
                throw new EntityNotFoundException(Alert.class);
            }
        }
    }

    /**
     * Inserts an AccountRef into an alert. The AccountRef is fetched using the
     * alert's userId.
     */
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
