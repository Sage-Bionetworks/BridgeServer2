package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.FPHSExternalIdentifierDao;
import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class FPHSServiceTest {

    private static final String EXTERNAL_ID = "BBB";
    
    private FPHSService service;
    @Mock
    private FPHSExternalIdentifierDao mockDao;
    @Mock
    private AccountService mockAccountService;
    @Mock
    private Account mockAccount;
    
    private ExternalIdentifier externalId;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        externalId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        service = new FPHSService();
        service.setFPHSExternalIdentifierDao(mockDao);
        service.setAccountService(mockAccountService);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void validateIdThrowsException() throws Exception {
        service.verifyExternalIdentifier(ExternalIdentifier.create(TEST_APP_ID, ""));
    }
    
    @Test
    public void verifyExternalIdentifierSucceeds() throws Exception {
        service.verifyExternalIdentifier(externalId);
        verify(mockDao).verifyExternalId(externalId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void verifyExternalIdentifierFailsOnNotFound() throws Exception {
        doThrow(new EntityNotFoundException(ExternalIdentifier.class)).when(mockDao).verifyExternalId(externalId);
        
        service.verifyExternalIdentifier(externalId);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void registerIdThrowsException() throws Exception {
        service.registerExternalIdentifier(TEST_APP_ID, EXTERNAL_ID, ExternalIdentifier.create(TEST_APP_ID, null));
    }
    
    @Test
    public void registerExternalIdentifier() throws Exception {
        TestUtils.mockEditAccount(mockAccountService, mockAccount);
        Set<String> dataGroups = Sets.newHashSet();
        Set<AccountSubstudy> accountSubstudies = Sets.newHashSet();
        when(mockAccount.getDataGroups()).thenReturn(dataGroups);
        when(mockAccount.getAccountSubstudies()).thenReturn(accountSubstudies);
        when(mockAccount.getId()).thenReturn("userId");
        
        service.registerExternalIdentifier(TEST_APP_ID, EXTERNAL_ID, externalId);
        verify(mockDao).registerExternalId(externalId);
        assertEquals(dataGroups, ImmutableSet.of("football_player"));
        assertEquals(accountSubstudies.size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(accountSubstudies, null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), "harvard");
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
    }
    
    @Test
    public void failureOfDaoDoesNotSetExternalId() throws Exception {
        // Mock this, throw exception afterward
        doThrow(new EntityNotFoundException(ExternalIdentifier.class, "Not found")).when(mockDao).registerExternalId(externalId);
        try {
            service.registerExternalIdentifier(TEST_APP_ID, EXTERNAL_ID, externalId);
            fail("Exception should have been thrown");
        } catch(EntityNotFoundException e) {
            verify(mockDao).verifyExternalId(externalId);
            verify(mockDao).registerExternalId(externalId);
            verifyNoMoreInteractions(mockDao);
            verifyNoMoreInteractions(mockAccountService);
        }
    }
    
    @Test
    public void failureToSetExternalIdRollsBackRegistration() throws Exception {
        doThrow(new RuntimeException()).when(mockDao).verifyExternalId(any());
        try {
            service.registerExternalIdentifier(TEST_APP_ID, EXTERNAL_ID, externalId);
            fail("Exception should have been thrown");
        } catch(RuntimeException e) {
            verify(mockDao).verifyExternalId(externalId);
            verifyNoMoreInteractions(mockDao);
            verifyNoMoreInteractions(mockAccountService);
        }
    }
    
    @Test
    public void getExternalIdentifiers() throws Exception {
        List<FPHSExternalIdentifier> externalIds = Lists.newArrayList(
                new DynamoFPHSExternalIdentifier("foo"), new DynamoFPHSExternalIdentifier("bar"));
        when(mockDao.getExternalIds()).thenReturn(externalIds);
        
        List<FPHSExternalIdentifier> identifiers = service.getExternalIdentifiers();
        
        assertEquals(identifiers, externalIds);
        verify(mockDao).getExternalIds();
    }
    
    @Test
    public void addExternalIdentifiers() throws Exception {
        List<FPHSExternalIdentifier> identifiers = Lists.newArrayList(FPHSExternalIdentifier.create("AAA"),
                FPHSExternalIdentifier.create(EXTERNAL_ID), FPHSExternalIdentifier.create("CCC"));
        
        service.addExternalIdentifiers(identifiers);
        verify(mockDao).addExternalIds(identifiers);
    }
    
}
