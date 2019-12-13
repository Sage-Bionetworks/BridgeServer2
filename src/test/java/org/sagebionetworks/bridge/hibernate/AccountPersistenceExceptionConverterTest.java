package org.sagebionetworks.bridge.hibernate;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.mockito.Mockito.verify;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.NonUniqueObjectException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.google.common.collect.ImmutableSet;

public class AccountPersistenceExceptionConverterTest {

    private AccountPersistenceExceptionConverter converter;
    
    @Mock
    private AccountDao accountDao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        converter = new AccountPersistenceExceptionConverter(accountDao);
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void entityAlreadyExistsForEmail() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setEmail(EMAIL);
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setEmail(EMAIL);
        
        when(accountDao.getAccount(AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL)))
                .thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+EMAIL+"' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Email address has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForPhone() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setPhone(PHONE);
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setPhone(PHONE);
        
        when(accountDao.getAccount(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE)))
                .thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+PHONE.getNationalFormat()+"' for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Phone number has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), USER_ID);
    }

    @Test
    public void entityAlreadyExistsForExternalId() {
        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "something", USER_ID);
        acctSubstudy.setExternalId("ext");
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy));
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setAccountSubstudies(ImmutableSet.of(acctSubstudy));
        
        when(accountDao.getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "ext"))).thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenThereAreMultiple() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyB", USER_ID);
        as2.setExternalId("externalIdB");
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        
        when(accountDao.getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "externalIdB"))).thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenThereAreMultipleIgnoringSubstudies() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        as1.setExternalId("externalIdA");
        HibernateAccountSubstudy as2 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyB", USER_ID);
        as2.setExternalId("externalIdB");
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        
        // Accept anything here, but verify that it is externalIdB still (the first that would match
        when(accountDao.getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "externalIdB")))
                .thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), USER_ID);
        
        verify(accountDao).getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "externalIdA"));
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenSubstudyOutsideOfCallerSubstudy() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        HibernateAccountSubstudy as1 = (HibernateAccountSubstudy) AccountSubstudy
                .create(TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        as1.setExternalId("externalIdA");
        account.setAccountSubstudies(ImmutableSet.of(as1));
        
        Account existing = Account.create();
        existing.setId(USER_ID);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        
        // Accept anything here, but verify that it is externalIdA (which won't match user calling method)
        when(accountDao.getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "externalIdA")))
                .thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        
        verify(accountDao).getAccount(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "externalIdA"));
    }

    // This should not happen, we're testing that not finding an account with this message doesn't break the converter.
    @Test
    public void entityAlreadyExistsIfAccountCannotBeFound() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setAccountSubstudies(ImmutableSet.of());

        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        // It is converted to a generic constraint violation exception
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), ConstraintViolationException.class);
        assertEquals(result.getMessage(), "Accounts table constraint prevented save or update.");
    }    
    
    // This scenario should not happen, but were it to happen, it would not generate an NPE exception.
    @Test
    public void entityAlreadyExistsIfAccountIsSomehowNullIsGenericConstraintViolation() {
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, null);
        assertEquals(result.getClass(), ConstraintViolationException.class);
    }
    
    @Test
    public void entityAlreadyExistsForSynapseUserId() {
        HibernateAccount account = new HibernateAccount();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        account.setId(USER_ID);
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        
        when(accountDao.getAccount(AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, SYNAPSE_USER_ID)))
                .thenReturn(account);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'tim.powers' for key 'Accounts-StudyId-SynapseUserId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Synapse User ID has already been used by another account.");
    }
    
    @Test
    public void constraintViolationExceptionMessageIsHidden() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "This is a generic constraint violation.", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), ConstraintViolationException.class);
        assertEquals(result.getMessage(), "Accounts table constraint prevented save or update.");
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, account);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "Account has the wrong version number; it may have been saved in the background.");
    }
    
    @Test
    public void nonUniqueObjectException() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        
        NonUniqueObjectException nuoe = new NonUniqueObjectException("message", null, null);
        
        RuntimeException result = converter.convert(nuoe, account);
        assertEquals(result.getClass(), ConstraintViolationException.class);
        assertEquals(result.getMessage(), AccountPersistenceExceptionConverter.NON_UNIQUE_MSG);
    }
    
}