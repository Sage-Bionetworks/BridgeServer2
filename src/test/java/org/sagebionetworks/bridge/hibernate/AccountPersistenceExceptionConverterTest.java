package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.Optional;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.NonUniqueObjectException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Enrollment;

import com.google.common.collect.ImmutableSet;

public class AccountPersistenceExceptionConverterTest extends Mockito {
    
    private static final String ORG_FOREIGN_KEY_ERROR = "Cannot add or update a child row: a foreign key constraint fails "
            +"(`bridgedb`.`accounts`, CONSTRAINT `accounts_ibfk_1` FOREIGN KEY (`studyId`, `orgMembership`) "
            +"REFERENCES `Organizations` (`appId`, `identifier`))";

    private AccountPersistenceExceptionConverter converter;
    
    @Mock
    private AccountDao accountDao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        RequestContext.set(RequestContext.NULL_INSTANCE);
        converter = new AccountPersistenceExceptionConverter(accountDao);
    }
    
    @AfterMethod
    public void after() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void entityAlreadyExistsForEmail() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        account.setEmail(EMAIL);
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        existing.setEmail(EMAIL);
        
        when(accountDao.getAccount(AccountId.forEmail(TEST_APP_ID, EMAIL)))
                .thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+EMAIL+"' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Email address has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), TEST_USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForPhone() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        account.setPhone(PHONE);
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        existing.setPhone(PHONE);
        
        when(accountDao.getAccount(AccountId.forPhone(TEST_APP_ID, PHONE)))
                .thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+PHONE.getNationalFormat()+"' for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Phone number has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), TEST_USER_ID);
    }

    @Test
    public void entityAlreadyExistsForExternalId() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "something", TEST_USER_ID, "ext");
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        account.setEnrollments(ImmutableSet.of(enrollment));
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        existing.setEnrollments(ImmutableSet.of(enrollment));
        
        when(accountDao.getAccount(AccountId.forExternalId(TEST_APP_ID, "ext"))).thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), TEST_USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenThereAreMultiple() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID, "externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID, "externalIdB");
        account.setEnrollments(ImmutableSet.of(en1, en2));
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        
        when(accountDao.getAccount(AccountId.forExternalId(TEST_APP_ID, "externalIdB")))
                .thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), TEST_USER_ID);
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenThereAreMultipleIgnoringStudies() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("studyB")).build());
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID, "externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID, "externalIdB");
        account.setEnrollments(ImmutableSet.of(en1, en2));
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        
        // Accept anything here, but verify that it is externalIdB still (the first that would match
        when(accountDao.getAccount(AccountId.forExternalId(TEST_APP_ID, "externalIdB")))
                .thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        assertEquals(((EntityAlreadyExistsException)result).getEntityKeys().get("userId"), TEST_USER_ID);
        
        verify(accountDao).getAccount(AccountId.forExternalId(TEST_APP_ID, "externalIdA"));
    }
    
    @Test
    public void entityAlreadyExistsForExternalIdWhenStudyOutsideOfCallerStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("studyB")).build());
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        Enrollment as1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID, "externalIdA");
        account.setEnrollments(ImmutableSet.of(as1));
        
        Account existing = Account.create();
        existing.setId(TEST_USER_ID);
        existing.setAppId(TEST_APP_ID);
        
        // Accept anything here, but verify that it is externalIdA (which won't match user calling method)
        when(accountDao.getAccount(AccountId.forExternalId(TEST_APP_ID, "externalIdA")))
                .thenReturn(Optional.of(existing));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        
        verify(accountDao).getAccount(AccountId.forExternalId(TEST_APP_ID, "externalIdA"));
    }

    // This should not happen, we're testing that not finding an account with this message doesn't break the converter.
    @Test
    public void entityAlreadyExistsIfAccountCannotBeFound() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        account.setEnrollments(ImmutableSet.of());

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
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        account.setId(TEST_USER_ID);
        account.setAppId(TEST_APP_ID);
        
        when(accountDao.getAccount(AccountId.forSynapseUserId(TEST_APP_ID, SYNAPSE_USER_ID)))
                .thenReturn(Optional.of(account));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'tim.powers' for key 'Accounts-StudyId-SynapseUserId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "Synapse User ID has already been used by another account.");
    }
    
    @Test
    public void entityAlreadyExistsForDuplicateExternalIdInApp() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setAppId(TEST_APP_ID);
        
        when(accountDao.getAccount(AccountId.forExternalId(TEST_APP_ID, "CCC")))
                .thenReturn(Optional.of(account));
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'api-CCC' for key 'unique_extId'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(result.getClass(), EntityAlreadyExistsException.class);
        assertEquals(result.getMessage(), "External ID has already been used by another account.");
        Map<String,Object> keys = ((EntityAlreadyExistsException)result).getEntityKeys();
        assertEquals(keys.get("userId"), "userId");
        assertEquals(keys.size(), 1);
    }
    
    @Test
    public void constraintViolationExceptionMessageIsHidden() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
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
        account.setAppId(TEST_APP_ID);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, account);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "Account has the wrong version number; it may have been saved in the background.");
    }
    
    @Test
    public void nonUniqueObjectException() {
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        NonUniqueObjectException nuoe = new NonUniqueObjectException("message", null, null);
        
        RuntimeException result = converter.convert(nuoe, account);
        assertEquals(result.getClass(), ConstraintViolationException.class);
        assertEquals(result.getMessage(), AccountPersistenceExceptionConverter.NON_UNIQUE_MSG);
    }
    
    @Test
    public void organizationForeignKeyConstraint() { 
        HibernateAccount account = new HibernateAccount();
        
        SQLIntegrityConstraintViolationException ex = mock(SQLIntegrityConstraintViolationException.class);
        when(ex.getMessage()).thenReturn(ORG_FOREIGN_KEY_ERROR);
        
        org.hibernate.exception.ConstraintViolationException cve = 
                new org.hibernate.exception.ConstraintViolationException(ex.getMessage(), ex, "");
        
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException e = converter.convert(pe, account);
        assertEquals(e.getClass(), EntityNotFoundException.class);
    }
}