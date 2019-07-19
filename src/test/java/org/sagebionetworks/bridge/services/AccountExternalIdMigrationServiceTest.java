package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.HibernateException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.Substudy;

public class AccountExternalIdMigrationServiceTest extends Mockito {
    private static final String SUBSTUDY_ID = "oneSubstudyId";
    private static final String USER_ID = "userId";
    private static final String EXTERNAL_ID_STRING = "external-id";
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    
    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    SubstudyDao mockSubstudyDao;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    ExternalIdService mockExternalIdService;
    
    @Mock
    Account account;
    
    @InjectMocks
    AccountExternalIdMigrationService service;
    
    @Captor
    ArgumentCaptor<ExternalIdentifier> createExtIdCaptor;
    
    @Captor
    ArgumentCaptor<ExternalIdentifier> updateExtIdCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void succeedsNoSubstudySpecified() {
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(new HashSet<>());
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Substudy substudy = Substudy.create();
        substudy.setId(SUBSTUDY_ID);
        when(mockSubstudyDao.getSubstudies(TEST_STUDY, false)).thenReturn(ImmutableList.of(substudy));
        
        when(mockExternalIdService.getExternalId(TEST_STUDY, EXTERNAL_ID_STRING)).thenReturn(Optional.empty());

        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(invocation.getArgument(0));
            }
            return null;
        }).when(mockAccountDao).updateAccount(any(), any());
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_STUDY, EXTERNAL_ID_STRING);
        when(mockParticipantService.beginAssignExternalId(account, EXTERNAL_ID_STRING)).thenReturn(extId);
        
        String result = service.migrate(TEST_STUDY, USER_ID, null);
        assertEquals(result, SUBSTUDY_ID);
        
        verify(mockExternalIdService).createExternalId(createExtIdCaptor.capture(), eq(true));
        assertEquals(createExtIdCaptor.getValue().getIdentifier(), EXTERNAL_ID_STRING);
        assertEquals(createExtIdCaptor.getValue().getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createExtIdCaptor.getValue().getSubstudyId(), SUBSTUDY_ID);
        
        verify(mockParticipantService).beginAssignExternalId(account, EXTERNAL_ID_STRING);
        verify(mockAccountDao).updateAccount(eq(account), any());
        verify(mockExternalIdService).commitAssignExternalId(updateExtIdCaptor.capture());
        assertSame(updateExtIdCaptor.getValue(), extId);
    }
    
    @Test
    public void succeedsSubstudySpecified() {
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(new HashSet<>());
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);

        when(mockExternalIdService.getExternalId(TEST_STUDY, EXTERNAL_ID_STRING)).thenReturn(Optional.empty());

        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(invocation.getArgument(0));
            }
            return null;
        }).when(mockAccountDao).updateAccount(any(), any());
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_STUDY, EXTERNAL_ID_STRING);
        when(mockParticipantService.beginAssignExternalId(account, EXTERNAL_ID_STRING)).thenReturn(extId);
        
        service.migrate(TEST_STUDY, USER_ID, "twoSubstudyId");
        
        verify(mockExternalIdService).createExternalId(createExtIdCaptor.capture(), eq(true));
        assertEquals(createExtIdCaptor.getValue().getIdentifier(), EXTERNAL_ID_STRING);
        assertEquals(createExtIdCaptor.getValue().getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createExtIdCaptor.getValue().getSubstudyId(), "twoSubstudyId");
        
        verify(mockParticipantService).beginAssignExternalId(account, EXTERNAL_ID_STRING);
        verify(mockAccountDao).updateAccount(eq(account), any());
        verify(mockExternalIdService).commitAssignExternalId(updateExtIdCaptor.capture());
        assertSame(updateExtIdCaptor.getValue(), extId);        
    }
    
    @Test
    public void externalIdAlreadyManaged() { 
        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        acctSubstudy.setExternalId(EXTERNAL_ID_STRING);
        
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(ImmutableSet.of(acctSubstudy));
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);

        String result = service.migrate(TEST_STUDY, USER_ID, null);
        assertNull(result);
        
        verifyNoMoreInteractions(mockExternalIdService);
        verifyNoMoreInteractions(mockParticipantService);
    }
    
    @Test
    public void thisExternalIdIsNotManaged() {
        AccountSubstudy acctSub = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        acctSub.setExternalId("someOtherExternalId");
        
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(ImmutableSet.of(acctSub));
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Substudy substudy = Substudy.create();
        substudy.setId(SUBSTUDY_ID);
        when(mockSubstudyDao.getSubstudies(TEST_STUDY, false)).thenReturn(ImmutableList.of(substudy));
        
        when(mockExternalIdService.getExternalId(TEST_STUDY, EXTERNAL_ID_STRING)).thenReturn(Optional.empty());

        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(invocation.getArgument(0));
            }
            return null;
        }).when(mockAccountDao).updateAccount(any(), any());
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_STUDY, EXTERNAL_ID_STRING);
        when(mockParticipantService.beginAssignExternalId(account, EXTERNAL_ID_STRING)).thenReturn(extId);
        
        String result = service.migrate(TEST_STUDY, USER_ID, null);
        assertEquals(result, SUBSTUDY_ID);
        
        verify(mockExternalIdService).createExternalId(createExtIdCaptor.capture(), eq(true));
        assertEquals(createExtIdCaptor.getValue().getIdentifier(), EXTERNAL_ID_STRING);
        assertEquals(createExtIdCaptor.getValue().getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(createExtIdCaptor.getValue().getSubstudyId(), SUBSTUDY_ID);
        
        verify(mockParticipantService).beginAssignExternalId(account, EXTERNAL_ID_STRING);
        verify(mockAccountDao).updateAccount(eq(account), any());
        verify(mockExternalIdService).commitAssignExternalId(updateExtIdCaptor.capture());
        assertSame(updateExtIdCaptor.getValue(), extId);        
    }
    
    @Test
    public void externalIdNull() { 
        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        acctSubstudy.setExternalId(EXTERNAL_ID_STRING);
        
        when(account.getAccountSubstudies()).thenReturn(ImmutableSet.of(acctSubstudy));
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);

        String result = service.migrate(TEST_STUDY, USER_ID, null);
        assertNull(result);
        
        verifyNoMoreInteractions(mockExternalIdService);
        verifyNoMoreInteractions(mockParticipantService);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = ".*no substudies are defined for study.*")
    public void failsNoSubstudyExists() {
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(new HashSet<>());
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        when(mockSubstudyDao.getSubstudies(TEST_STUDY, false)).thenReturn(ImmutableList.of());

        service.migrate(TEST_STUDY, USER_ID, null);
    }
    
    @Test
    public void succeedsExternalIdRecordAlreadyExists() {
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(new HashSet<>());
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Substudy substudy = Substudy.create();
        substudy.setId(SUBSTUDY_ID);
        when(mockSubstudyDao.getSubstudies(TEST_STUDY, false)).thenReturn(ImmutableList.of(substudy));
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_STUDY, EXTERNAL_ID_STRING);
        when(mockExternalIdService.getExternalId(TEST_STUDY, EXTERNAL_ID_STRING)).thenReturn(Optional.of(extId));

        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(invocation.getArgument(0));
            }
            return null;
        }).when(mockAccountDao).updateAccount(any(), any());
        
        when(mockParticipantService.beginAssignExternalId(account, EXTERNAL_ID_STRING)).thenReturn(extId);
        
        String result = service.migrate(TEST_STUDY, USER_ID, null);
        assertEquals(result, SUBSTUDY_ID);
        
        verify(mockExternalIdService, never()).createExternalId(any(), eq(true));
        
        verify(mockParticipantService).beginAssignExternalId(account, EXTERNAL_ID_STRING);
        verify(mockAccountDao).updateAccount(eq(account), any());
        verify(mockExternalIdService).commitAssignExternalId(updateExtIdCaptor.capture());
        assertSame(updateExtIdCaptor.getValue(), extId);
    }
    
    @Test
    public void failsOnAccountUpdateAndUnassignsExtId() {
        when(account.getExternalId()).thenReturn(EXTERNAL_ID_STRING);
        when(account.getAccountSubstudies()).thenReturn(new HashSet<>());
        when(account.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Substudy substudy = Substudy.create();
        substudy.setId(SUBSTUDY_ID);
        when(mockSubstudyDao.getSubstudies(TEST_STUDY, false)).thenReturn(ImmutableList.of(substudy));
        
        when(mockExternalIdService.getExternalId(TEST_STUDY, EXTERNAL_ID_STRING)).thenReturn(Optional.empty());

        doThrow(new HibernateException("Something bad happened")).when(mockAccountDao).updateAccount(any(), any());
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_STUDY, EXTERNAL_ID_STRING);
        when(mockParticipantService.beginAssignExternalId(account, EXTERNAL_ID_STRING)).thenReturn(extId);
        try {
            service.migrate(TEST_STUDY, USER_ID, null);
            fail("Should have thrown exception");
        } catch(Exception e) {
        }
        
        verify(mockExternalIdService).createExternalId(createExtIdCaptor.capture(), eq(true));
        verify(mockParticipantService).beginAssignExternalId(account, EXTERNAL_ID_STRING);
        verify(mockExternalIdService).unassignExternalId(account, EXTERNAL_ID_STRING);
    }
}
