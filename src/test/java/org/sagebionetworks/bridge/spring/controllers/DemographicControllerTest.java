package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.DemographicService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class DemographicControllerTest {
    private static final String TEST_DEMOGRAPHIC_ID = "test-demographic-id";

    @Spy
    @InjectMocks
    DemographicController controller = new DemographicController();

    @Mock
    DemographicService demographicService;

    @Mock
    AccountService accountService;

    @Mock
    ParticipantService participantService;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        doReturn(session).when(controller).getAdministrativeSession();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession(any());
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(DemographicController.class);
        assertPost(DemographicController.class, "saveDemographicUser");
        assertPost(DemographicController.class, "saveDemographicUserAssessment");
        assertDelete(DemographicController.class, "deleteDemographic");
        assertDelete(DemographicController.class, "deleteDemographicUser");
        assertGet(DemographicController.class, "getDemographicUser");
        assertGet(DemographicController.class, "getDemographicUsers");
    }

    @Test
    public void saveDemographicUser() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.empty(), Optional.of(TEST_USER_ID));

        verify(controller).getAdministrativeSession();
        verify(controller).parseJson(DemographicUser.class);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void saveDemographicUserNotInStudy() throws EntityNotFoundException, MismatchedInputException {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
    }

    @Test
    public void saveDemographicUserSelf() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserSelfAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.empty(), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserNull() throws MismatchedInputException {
        doReturn(null).when(controller).parseJson(DemographicUser.class);

        try {
            controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
            fail("should have an exception");
        } catch (BadRequestException e) {
        }

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        // can't use expectedExceptions because we need to test this after
        verify(controller).parseJson(DemographicUser.class);
    }

    @Test(expectedExceptions = { MismatchedInputException.class })
    public void saveDemographicUserInvalid() throws MismatchedInputException {
        doAnswer((invocation) -> {
            throw MismatchedInputException.from(new JsonFactory().createParser("[]"), DemographicUser.class,
                    "bad json");
        }).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.empty(), Optional.of(TEST_USER_ID));
    }

    @Test
    public void saveDemographicUserAssessment() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserAssessmentAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);

        controller.saveDemographicUserAssessment(Optional.empty(), Optional.of(TEST_USER_ID));

        verify(controller).getAdministrativeSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void saveDemographicUserAssessmentNotInStudy() throws EntityNotFoundException, MismatchedInputException {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
    }

    @Test
    public void saveDemographicUserSelfAssessment() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserSelfAssessmentAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);

        controller.saveDemographicUserAssessment(Optional.empty(), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    @Test
    public void saveDemographicUserAssessmentNull() throws MismatchedInputException {
        doReturn(null).when(controller).parseJson(DemographicUserAssessment.class);

        try {
            controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
            fail("should have an exception");
        } catch (BadRequestException e) {
        }

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        // can't use expectedExceptions because we need to test this after
        verify(controller).parseJson(DemographicUserAssessment.class);
    }

    @Test(expectedExceptions = { MismatchedInputException.class })
    public void saveDemographicUserAssessmentInvalid() throws MismatchedInputException {
        // doThrow wasn't working here; the exception was thrown immediately when this
        // line was run
        // instead of when the mocked method was called, maybe because controller is a
        // spy?
        doAnswer((invocation) -> {
            throw MismatchedInputException.from(new JsonFactory().createParser("[]"), DemographicUserAssessment.class,
                    "bad json");
        }).when(controller).parseJson(DemographicUserAssessment.class);

        controller.saveDemographicUserAssessment(Optional.empty(), Optional.of(TEST_USER_ID));
    }

    @Test
    public void deleteDemographic() {

        StatusMessage message = controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).deleteDemographic(TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
        assertEquals(message.getMessage(), "Demographic successfully deleted");
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicNotFound() {
        doThrow(new EntityNotFoundException(Demographic.class)).when(demographicService).deleteDemographic(any(),
                any());

        controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
    }

    @Test
    public void deleteDemographicAppLevel() {

        StatusMessage message = controller.deleteDemographic(Optional.empty(), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);

        verify(controller).getAdministrativeSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).deleteDemographic(TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
        assertEquals(message.getMessage(), "Demographic successfully deleted");
    }

    @Test
    public void deleteDemographicUser() {

        StatusMessage message = controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(message.getMessage(), "Demographic user successfully deleted");
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicUserNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicUserNotFound() {
        doThrow(new EntityNotFoundException(DemographicUser.class)).when(demographicService)
                .deleteDemographicUser(any(), any(), any());

        controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    @Test
    public void deleteDemographicUserAppLevel() {

        StatusMessage message = controller.deleteDemographicUser(Optional.empty(), TEST_USER_ID);

        verify(controller).getAdministrativeSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).deleteDemographicUser(TEST_APP_ID, null, TEST_USER_ID);
        assertEquals(message.getMessage(), "Demographic user successfully deleted");
    }

    @Test
    public void getDemographicUser() {

        controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void getDemographicUserNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void getDemographicUserNotFound() {
        doThrow(new EntityNotFoundException(Demographic.class)).when(demographicService).getDemographicUser(any(),
                any(), any());

        controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    @Test
    public void getDemographicUserAppLevel() {

        controller.getDemographicUser(Optional.empty(), TEST_USER_ID);

        verify(controller).getAdministrativeSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).getDemographicUser(TEST_APP_ID, null, TEST_USER_ID);
    }

    @Test
    public void getDemographicUsers() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), "0", "10");

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(demographicService).getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 10);
    }

    @Test
    public void getDemographicUsersAppLevel() {

        controller.getDemographicUsers(Optional.empty(), "0", "10");

        verify(controller).getAdministrativeSession();
        verify(demographicService).getDemographicUsers(TEST_APP_ID, null, 0, 10);
    }

    @Test
    public void getDemographicUsersBlankParams() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), null, null);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(demographicService).getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, API_DEFAULT_PAGE_SIZE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getDemographicUsersInvalidParams() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), "foo", "7.2");
    }
}
