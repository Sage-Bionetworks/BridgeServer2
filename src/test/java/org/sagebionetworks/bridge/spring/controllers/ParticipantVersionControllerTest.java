package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ParticipantVersionService;

public class ParticipantVersionControllerTest {
    private static final int PARTICIPANT_VERSION = 23;

    @Mock
    private AccountService mockAccountService;

    @Mock
    private ParticipantVersionService mockParticipantVersionService;

    @InjectMocks
    @Spy
    private ParticipantVersionController controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        UserSession mockSession = new UserSession();
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ParticipantVersionController.class);
        assertDelete(ParticipantVersionController.class, "deleteParticipantVersionsForUser");
        assertGet(ParticipantVersionController.class, "getAllParticipantVersionsForUser");
        assertGet(ParticipantVersionController.class, "getParticipantVersion");
    }

    @Test
    public void deleteForHealthCode() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));

        // Execute and validate.
        StatusMessage response = controller.deleteParticipantVersionsForUser(TestConstants.TEST_APP_ID,
                TestConstants.TEST_USER_ID);
        assertNotNull(response);

        verify(mockParticipantVersionService).deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);

    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteForHealthCode_AccountNotFound() {
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.empty());
        controller.deleteParticipantVersionsForUser(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
    }

    @Test
    public void getAllForHealthCode() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));

        List<ParticipantVersion> participantVersionList = ImmutableList.of(ParticipantVersion.create());
        when(mockParticipantVersionService.getAllParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(participantVersionList);

        // Execute and validate.
        ResourceList<ParticipantVersion> resourceList = controller.getAllParticipantVersionsForUser(
                TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
        assertSame(resourceList.getItems(), participantVersionList);

        verify(mockParticipantVersionService).getAllParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAllForHealthCode_AccountNotFound() {
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.empty());
        controller.getAllParticipantVersionsForUser(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
    }

    @Test
    public void getParticipantVersion() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));

        ParticipantVersion participantVersion = ParticipantVersion.create();
        when(mockParticipantVersionService.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE,
                PARTICIPANT_VERSION)).thenReturn(participantVersion);

        // Execute and validate.
        ParticipantVersion result = controller.getParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.TEST_USER_ID, PARTICIPANT_VERSION);
        assertSame(result, participantVersion);

        verify(mockParticipantVersionService).getParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE, PARTICIPANT_VERSION);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantVersion_AccountNotFound() {
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.empty());
        controller.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID, PARTICIPANT_VERSION);
    }
}
