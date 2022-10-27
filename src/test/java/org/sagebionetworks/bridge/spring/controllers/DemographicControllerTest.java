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
import static org.testng.Assert.assertSame;
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
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicUserAssessment;
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

    @Mock
    Account account;

    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(Roles.ADMIN);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession(any());
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    /**
     * Tests that the mapping annotations on controller methods are correct.
     */
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

    /**
     * Tests saving a DemographicUser.
     */
    @Test
    public void saveDemographicUser() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests saving a DemographicUser at the app level.
     */
    @Test
    public void saveDemographicUserAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUser(Optional.empty(), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(controller).parseJson(DemographicUser.class);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests that attempting to save a DemographicUser for a user who is not in the
     * study specified throws an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void saveDemographicUserNotInStudy() throws EntityNotFoundException, MismatchedInputException {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
    }

    /**
     * Tests saving a DemographicUser by the user themself.
     */
    @Test
    public void saveDemographicUserSelf() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests saving a DemographicUser by the user themself at the app level.
     */
    @Test
    public void saveDemographicUserSelfAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(demographicUser).when(controller).parseJson(DemographicUser.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUser(Optional.empty(), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests that attempting to post null for a DemographicUser results in an error.
     */
    @Test
    public void saveDemographicUserNull() throws MismatchedInputException {
        doReturn(null).when(controller).parseJson(DemographicUser.class);

        try {
            controller.saveDemographicUser(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
            fail("should have an exception");
        } catch (BadRequestException e) {
        }

        // can't use expectedExceptions because we need to test these after
        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUser.class);
    }

    /**
     * Tests that attempting to post an array for a DemographicUser results in an
     * error.
     */
    @Test(expectedExceptions = { MismatchedInputException.class })
    public void saveDemographicUserInvalid() throws MismatchedInputException {
        doAnswer((invocation) -> {
            throw MismatchedInputException.from(new JsonFactory().createParser("[]"), DemographicUser.class,
                    "bad json");
        }).when(controller).parseJson(DemographicUser.class);

        controller.saveDemographicUser(Optional.empty(), Optional.of(TEST_USER_ID));
    }

    /**
     * Tests saving a DemographicUser in the assessment format.
     */
    @Test
    public void saveDemographicUserAssessment() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests saving a DemographicUser in the assessment format at the app level.
     */
    @Test
    public void saveDemographicUserAssessmentAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUserAssessment(Optional.empty(), Optional.of(TEST_USER_ID));

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests that attempting to save a DemographicUser for a user who is not in the
     * specified study results in an error when using the assessment format.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void saveDemographicUserAssessmentNotInStudy() throws EntityNotFoundException, MismatchedInputException {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.of(TEST_USER_ID));
    }

    /**
     * Test saving a DemographicUser by the user themself using the assessment
     * format.
     */
    @Test
    public void saveDemographicUserSelfAssessment() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUserAssessment(Optional.of(TEST_STUDY_ID), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), TEST_STUDY_ID);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Test saving a DemographicUser by the user themself using the assessment
     * format at the app level.
     */
    @Test
    public void saveDemographicUserSelfAssessmentAppLevel() throws MismatchedInputException {
        DemographicUser demographicUser = new DemographicUser();
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        doReturn(demographicUserAssessment).when(controller).parseJson(DemographicUserAssessment.class);
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        controller.saveDemographicUserAssessment(Optional.empty(), Optional.empty());

        verify(controller).getAuthenticatedAndConsentedSession();
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(controller).parseJson(DemographicUserAssessment.class);
        verify(demographicService).saveDemographicUser(demographicUser, account);
        assertEquals(demographicUser.getAppId(), TEST_APP_ID);
        assertEquals(demographicUser.getStudyId(), null);
        assertEquals(demographicUser.getUserId(), TEST_USER_ID);
    }

    /**
     * Tests that attempting to post null for a DemographicUser results in an error
     * when using the asssessment format.
     */
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

    /**
     * Tests that attempting to post an array for a DemographicUser results in an
     * error when using the assessment format.
     */
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

    /**
     * Tests deleting a Demographic.
     */
    @Test
    public void deleteDemographic() {
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        StatusMessage message = controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID,
                TEST_DEMOGRAPHIC_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).deleteDemographic(TEST_USER_ID, TEST_DEMOGRAPHIC_ID, account);
        assertEquals(message.getMessage(), "Demographic successfully deleted");
    }

    /**
     * Tests that attempting to delete a Demographic for a user who is not in the
     * specified study throws an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
    }

    /**
     * Tests that attempting to delete a Demographic that does not exist throws an
     * error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicNotFound() {
        doThrow(new EntityNotFoundException(Demographic.class)).when(demographicService).deleteDemographic(any(),
                any(), any());

        controller.deleteDemographic(Optional.of(TEST_STUDY_ID), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);
    }

    /**
     * Tests deleting a Demographic at the app level.
     */
    @Test
    public void deleteDemographicAppLevel() {
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        StatusMessage message = controller.deleteDemographic(Optional.empty(), TEST_USER_ID, TEST_DEMOGRAPHIC_ID);

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).deleteDemographic(TEST_USER_ID, TEST_DEMOGRAPHIC_ID, account);
        assertEquals(message.getMessage(), "Demographic successfully deleted");
    }

    /**
     * Tests deleting a DemographicUser.
     */
    @Test
    public void deleteDemographicUser() {
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        StatusMessage message = controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, account);
        assertEquals(message.getMessage(), "Demographic user successfully deleted");
    }

    /**
     * Tests that attempting to delete a DemographicUser for a user who is not in
     * the specified study throws an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicUserNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    /**
     * Tests that attempting to delete a DemographicUser that does not exist throws
     * an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void deleteDemographicUserNotFound() {
        doThrow(new EntityNotFoundException(DemographicUser.class)).when(demographicService)
                .deleteDemographicUser(any(), any(), any(), any());

        controller.deleteDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    /**
     * Tests deleting a DemographicUser at the app level.
     */
    @Test
    public void deleteDemographicUserAppLevel() {
        doReturn(account).when(participantService).getAccountInStudy(any(), any(), any());

        StatusMessage message = controller.deleteDemographicUser(Optional.empty(), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).deleteDemographicUser(TEST_APP_ID, null, TEST_USER_ID, account);
        assertEquals(message.getMessage(), "Demographic user successfully deleted");
    }

    /**
     * Tests fetching a DemographicUser.
     */
    @Test
    public void getDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(Optional.of(demographicUser)).when(demographicService).getDemographicUser(any(), any(), any());

        DemographicUser returnedDemographicUser = controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(participantService).getAccountInStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicService).getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertSame(returnedDemographicUser, demographicUser);
    }

    /**
     * Tests that attempting to fetch a DemographicUser for a user who is not in the
     * specified study throws an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void getDemographicUserNotInStudy() {
        doThrow(new EntityNotFoundException(Account.class)).when(participantService).getAccountInStudy(any(), any(),
                any());

        controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    /**
     * Tests that attempting to fetch a DemographicUser that does not exist throws
     * an error.
     */
    @Test(expectedExceptions = { EntityNotFoundException.class })
    public void getDemographicUserNotFound() {
        doReturn(Optional.empty()).when(demographicService).getDemographicUser(any(),
                any(), any());

        controller.getDemographicUser(Optional.of(TEST_STUDY_ID), TEST_USER_ID);
    }

    /**
     * Tests fetching a DemographicUser at the app level.
     */
    @Test
    public void getDemographicUserAppLevel() {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(Optional.of(demographicUser)).when(demographicService).getDemographicUser(any(), any(), any());

        DemographicUser returnedDemographicUser = controller.getDemographicUser(Optional.empty(), TEST_USER_ID);

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(participantService).getAccountInStudy(TEST_APP_ID, null, TEST_USER_ID);
        verify(demographicService).getDemographicUser(TEST_APP_ID, null, TEST_USER_ID);
        assertSame(returnedDemographicUser, demographicUser);
    }

    /**
     * Tests fetching multiple DemographicUsers.
     */
    @Test
    public void getDemographicUsers() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), "0", "10");

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(demographicService).getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 10);
    }

    /**
     * Tests fetching multiple DemographicUsers at the app level.
     */
    @Test
    public void getDemographicUsersAppLevel() {

        controller.getDemographicUsers(Optional.empty(), "0", "10");

        verify(controller).getAuthenticatedSession(Roles.ADMIN);
        verify(demographicService).getDemographicUsers(TEST_APP_ID, null, 0, 10);
    }

    /**
     * Tests fetching multiple DemographicUsers with blank offsetBy and pageSize
     * succeeds and uses the default values..
     */
    @Test
    public void getDemographicUsersBlankParams() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), null, null);

        verify(controller).getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        verify(demographicService).getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, API_DEFAULT_PAGE_SIZE);
    }

    /**
     * Tests that attempting to fetch multiple DemographicUsers using an invalid
     * offsetBy and pageSize throws an error.
     */
    @Test(expectedExceptions = BadRequestException.class)
    public void getDemographicUsersInvalidParams() {

        controller.getDemographicUsers(Optional.of(TEST_STUDY_ID), "foo", "7.2");
    }
}
