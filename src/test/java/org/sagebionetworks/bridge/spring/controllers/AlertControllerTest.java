package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.AlertFilter;
import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.sagebionetworks.bridge.services.AlertService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AlertControllerTest {
    @Spy
    @InjectMocks
    AlertController alertController;

    @Mock
    AlertService alertService;

    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        doReturn(session).when(alertController).getAuthenticatedAndConsentedSession();
        doReturn(session).when(alertController).getAuthenticatedSession(ArgumentMatchers.<Roles>any());
    }

    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AlertController.class);
        assertPost(AlertController.class, "getAlerts");
        assertPost(AlertController.class, "deleteAlerts");
    }

    @Test
    public void getAlerts() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(Roles.RESEARCHER)).build());
        PagedResourceList<Alert> alerts = new PagedResourceList<>(ImmutableList.of(new Alert()), 1);
        when(alertService.getAlerts(any(), any(), anyInt(), anyInt(), any())).thenReturn(alerts);
        AlertFilter alertFilter = new AlertFilter(ImmutableSet.of());
        doReturn(alertFilter).when(alertController).parseJson(AlertFilter.class);

        PagedResourceList<Alert> returnedAlerts = alertController.getAlerts(TEST_STUDY_ID, "1", "23");

        verify(alertController).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(alertController).parseJson(AlertFilter.class);
        verify(alertService).getAlerts(TEST_APP_ID, TEST_STUDY_ID, 1, 23, alertFilter);
        assertSame(returnedAlerts, alerts);
    }

    @Test
    public void getAlerts_blankParams() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());
        PagedResourceList<Alert> alerts = new PagedResourceList<>(ImmutableList.of(new Alert()), 1);
        when(alertService.getAlerts(any(), any(), anyInt(), anyInt(), any())).thenReturn(alerts);
        AlertFilter alertFilter = new AlertFilter(ImmutableSet.of());
        doReturn(alertFilter).when(alertController).parseJson(AlertFilter.class);

        PagedResourceList<Alert> returnedAlerts = alertController.getAlerts(TEST_STUDY_ID, null, null);

        verify(alertController).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(alertController).parseJson(AlertFilter.class);
        verify(alertService).getAlerts(TEST_APP_ID, TEST_STUDY_ID, 0, API_DEFAULT_PAGE_SIZE, alertFilter);
        assertSame(returnedAlerts, alerts);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAlerts_cannotEditStudyParticipants_noRoles() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());

        alertController.getAlerts(TEST_STUDY_ID, null, null);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAlerts_cannotEditStudyParticipants_wrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("wrong study id"))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());

        alertController.getAlerts(TEST_STUDY_ID, null, null);
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void getAlerts_wrongSchema() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());
        doAnswer((invocation) -> {
            throw MismatchedInputException.from(new JsonFactory().createParser("[]"), AlertFilter.class,
                    "bad json");
        }).when(alertController).parseJson(AlertFilter.class);

        alertController.getAlerts(TEST_STUDY_ID, null, null);
    }

    @Test
    public void deleteAlerts() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());
        AlertIdCollection alertIdCollection = new AlertIdCollection(ImmutableList.of("foo"));
        doReturn(alertIdCollection).when(alertController).parseJson(AlertIdCollection.class);

        StatusMessage message = alertController.deleteAlerts(TEST_STUDY_ID);

        verify(alertController).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(alertController).parseJson(AlertIdCollection.class);
        verify(alertService).deleteAlerts(eq(TEST_APP_ID), eq(TEST_STUDY_ID), same(alertIdCollection));
        assertEquals(message.getMessage(), "Alerts successfully deleted");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteAlerts_cannotEditStudyParticipants_noRoles() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());

        alertController.deleteAlerts(TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteAlerts_cannotEditStudyParticipants_wrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("wrong study id"))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());

        alertController.deleteAlerts(TEST_STUDY_ID);
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deleteAlerts_wrongSchema() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(Roles.STUDY_COORDINATOR)).build());
        doAnswer((invocation) -> {
            throw MismatchedInputException.from(new JsonFactory().createParser("[]"), AlertIdCollection.class,
                    "bad json");
        }).when(alertController).parseJson(AlertIdCollection.class);

        alertController.deleteAlerts(TEST_STUDY_ID);
    }
}
