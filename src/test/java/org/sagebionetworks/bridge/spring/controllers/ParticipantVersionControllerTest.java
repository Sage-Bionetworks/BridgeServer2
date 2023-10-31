package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ParticipantVersionService;

public class ParticipantVersionControllerTest {
    private static final int PARTICIPANT_VERSION = 23;
    private static final String PARTICIPANT_VERSION_STRING = "23";

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
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ParticipantVersionController.class);
        assertDelete(ParticipantVersionController.class, "deleteParticipantVersionsForUser");
        assertGet(ParticipantVersionController.class, "getAllParticipantVersionsForUser");
        assertGet(ParticipantVersionController.class, "getLatestParticipantVersion");
        assertGet(ParticipantVersionController.class, "getParticipantVersion");
    }

    @Test
    public void deleteForHealthCode() {
        // Mock dependencies.
        Account account = Account.create();
        account.setHealthCode(TestConstants.HEALTH_CODE);
        account.setDataGroups(ImmutableSet.of(BridgeConstants.TEST_USER_GROUP));
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Execute and validate.
        StatusMessage response = controller.deleteParticipantVersionsForUser(TestConstants.TEST_USER_ID);
        assertNotNull(response);

        verify(mockParticipantVersionService).deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);

        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        verify(mockAccountService).getAccount(accountIdCaptor.capture());

        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(accountId.getId(), TestConstants.TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteForHealthCode_AccountNotFound() {
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());
        controller.deleteParticipantVersionsForUser(TestConstants.TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteForHealthCode_NotTestUser() {
        // Mock dependencies.
        Account account = Account.create();
        account.setHealthCode(TestConstants.HEALTH_CODE);
        account.setDataGroups(ImmutableSet.of());
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        // Execute. This will throw.
        controller.deleteParticipantVersionsForUser(TestConstants.TEST_USER_ID);
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
    public void getLatestParticipantVersion() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));

        ParticipantVersion participantVersion = ParticipantVersion.create();
        when(mockParticipantVersionService.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.of(participantVersion));

        // Execute and validate.
        ParticipantVersion result = controller.getLatestParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.TEST_USER_ID);
        assertSame(result, participantVersion);

        verify(controller).getAuthenticatedSession(WORKER);
        verify(mockParticipantVersionService).getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);
    }

    @Test
    public void getLatestParticipantVersion_AccountNotFound() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Execute - This throws.
        try {
            controller.getLatestParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getMessage(), "StudyParticipant not found.");
        }
    }

    @Test
    public void getLatestParticipantVersion_ParticipantVersionNotFound() {
        // Mock dependencies.
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));
        when(mockParticipantVersionService.getLatestParticipantVersionForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE)).thenReturn(Optional.empty());

        // Execute - This throws.
        try {
            controller.getLatestParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getMessage(), "ParticipantVersion not found.");
        }
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
                TestConstants.TEST_USER_ID, PARTICIPANT_VERSION_STRING);
        assertSame(result, participantVersion);

        verify(mockParticipantVersionService).getParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE, PARTICIPANT_VERSION);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantVersion_AccountNotFound() {
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.empty());
        controller.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID,
                PARTICIPANT_VERSION_STRING);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantVersion_InvalidVersion() {
        when(mockAccountService.getAccountHealthCode(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID))
                .thenReturn(Optional.of(TestConstants.HEALTH_CODE));
        controller.getParticipantVersion(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID, "not an int");
    }
}
