package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_EXTERNAL_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AlertDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;
import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AlertServiceTest {
    private static final String ALERT_ID = "test-alert-id";

    @Mock
    AlertDao alertDao;

    @Mock
    AccountService accountService;

    @Captor
    ArgumentCaptor<Alert> alertCaptor;

    @InjectMocks
    AlertService alertService;

    Alert alert;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        alert = new Alert(null, null, TEST_STUDY_ID, TEST_APP_ID, TEST_USER_ID, AlertCategory.NEW_ENROLLMENT,
                BridgeObjectMapper.get().nullNode());
    }

    @Test
    public void createAlert() {
        when(alertDao.getAlert(TEST_STUDY_ID, TEST_APP_ID, TEST_USER_ID, AlertCategory.NEW_ENROLLMENT)).thenReturn(Optional.empty());

        alertService.createAlert(alert);

        verify(alertDao).getAlert(TEST_STUDY_ID, TEST_APP_ID, TEST_USER_ID, AlertCategory.NEW_ENROLLMENT);
        verify(alertDao).createAlert(alertCaptor.capture());
        assertSame(alertCaptor.getValue(), alert);
        assertNotNull(alert.getId());
        assertNotNull(alert.getCreatedOn());
    }

    @Test
    public void createAlert_alreadyExists() {
        when(alertDao.getAlert(TEST_STUDY_ID, TEST_APP_ID, TEST_USER_ID, AlertCategory.NEW_ENROLLMENT)).thenReturn(Optional.of(new Alert()));

        alertService.createAlert(alert);
        verify(alertDao).getAlert(TEST_STUDY_ID, TEST_APP_ID, TEST_USER_ID, AlertCategory.NEW_ENROLLMENT);
        verifyNoMoreInteractions(alertDao);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createAlert_nullStudyId() {
        alert.setStudyId(null);

        alertService.createAlert(alert);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createAlert_nullAppId() {
        alert.setAppId(null);

        alertService.createAlert(alert);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createAlert_nullUserId() {
        alert.setUserId(null);

        alertService.createAlert(alert);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void createAlert_nullCategory() {
        alert.setCategory(null);

        alertService.createAlert(alert);
    }

    @Test
    public void getAlerts() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId(TEST_EXTERNAL_ID);
        account.setEnrollments(ImmutableSet.of(enrollment));
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        PagedResourceList<Alert> alertsPage = new PagedResourceList<>(ImmutableList.of(alert), 1);
        when(alertDao.getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, 100)).thenReturn(alertsPage);

        PagedResourceList<Alert> returnedAlerts = alertService.getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, 100);

        verify(alertDao).getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, 100);
        verify(accountService).getAccount(BridgeUtils.parseAccountId(TEST_APP_ID, TEST_USER_ID));
        assertSame(returnedAlerts, alertsPage);
        assertNotNull(alert.getParticipant());
        assertEquals(alert.getParticipant().getIdentifier(), TEST_USER_ID);
        assertEquals(alert.getParticipant().getExternalId(), TEST_EXTERNAL_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAlerts_noAccount() {
        when(accountService.getAccount(any())).thenReturn(Optional.empty());
        PagedResourceList<Alert> alertsPage = new PagedResourceList<>(ImmutableList.of(alert), 1);
        when(alertDao.getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, 100)).thenReturn(alertsPage);

        alertService.getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, 100);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void getAlerts_nullAppId() {
        alertService.getAlerts(null, TEST_STUDY_ID, 0, 0);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void getAlerts_nullStudyId() {
        alertService.getAlerts(TEST_APP_ID, null, 0, 0);
    }

    @Test
    public void deleteAlerts() {
        alert.setId(ALERT_ID);
        AlertIdCollection alertIds = new AlertIdCollection(ImmutableList.of(ALERT_ID));
        when(alertDao.getAlertById(ALERT_ID)).thenReturn(Optional.of(alert));

        alertService.deleteAlerts(TEST_APP_ID, TEST_STUDY_ID, alertIds);

        verify(alertDao).getAlertById(ALERT_ID);
        verify(alertDao).deleteAlerts(ImmutableList.of(ALERT_ID));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAlerts_alertDoesNotExist() {
        alert.setId(ALERT_ID);
        AlertIdCollection alertIds = new AlertIdCollection(ImmutableList.of(ALERT_ID));
        when(alertDao.getAlertById(ALERT_ID)).thenReturn(Optional.empty());

        alertService.deleteAlerts(TEST_APP_ID, TEST_STUDY_ID, alertIds);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAlerts_wrongApp() {
        alert.setId(ALERT_ID);
        AlertIdCollection alertIds = new AlertIdCollection(ImmutableList.of(ALERT_ID));
        when(alertDao.getAlertById(ALERT_ID)).thenReturn(Optional.of(alert));

        alertService.deleteAlerts("wrong app id", TEST_STUDY_ID, alertIds);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAlerts_wrongStudy() {
        alert.setId(ALERT_ID);
        AlertIdCollection alertIds = new AlertIdCollection(ImmutableList.of(ALERT_ID));
        when(alertDao.getAlertById(ALERT_ID)).thenReturn(Optional.of(alert));

        alertService.deleteAlerts(TEST_APP_ID, "wrong study id", alertIds);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlerts_nullAppId() {
        alertService.deleteAlerts(null, TEST_STUDY_ID, new AlertIdCollection(ImmutableList.of(ALERT_ID)));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlerts_nullStudyId() {
        alertService.deleteAlerts(TEST_APP_ID, null, new AlertIdCollection(ImmutableList.of(ALERT_ID)));
    }

    @Test
    public void deleteAlertsForStudy() {
        alertService.deleteAlertsForStudy(TEST_APP_ID, TEST_STUDY_ID);

        verify(alertDao).deleteAlertsForStudy(TEST_APP_ID, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForStudy_nullAppId() {
        alertService.deleteAlertsForStudy(null, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForStudy_nullStudyId() {
        alertService.deleteAlertsForStudy(TEST_APP_ID, null);
    }

    @Test
    public void deleteAlertsForUserInApp() {
        alertService.deleteAlertsForUserInApp(TEST_APP_ID, TEST_USER_ID);

        verify(alertDao).deleteAlertsForUserInApp(TEST_APP_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForUserInApp_nullAppId() {
        alertService.deleteAlertsForUserInApp(null, TEST_USER_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForUserInApp_nullUserId() {
        alertService.deleteAlertsForUserInApp(TEST_APP_ID, null);
    }

    @Test
    public void deleteAlertsForUserInStudy() {
        alertService.deleteAlertsForUserInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        verify(alertDao).deleteAlertsForUserInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForUserInStudy_nullAppId() {
        alertService.deleteAlertsForUserInStudy(null, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForUserInStudy_nullStudyId() {
        alertService.deleteAlertsForUserInStudy(TEST_APP_ID, null, TEST_USER_ID);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAlertsForUserInStudy_nullUserId() {
        alertService.deleteAlertsForUserInStudy(TEST_APP_ID, TEST_STUDY_ID, null);
    }
}
