package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.sql.SQLIntegrityConstraintViolationException;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.apache.http.HttpStatus;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;

public class MySQLHibernatePersistenceExceptionConverterTest extends Mockito {

    MySQLHibernatePersistenceExceptionConverter converter;
    
    Schedule2 schedule;
    
    Organization organization;
    
    @Mock
    private ConstraintViolationException cve;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        converter = new MySQLHibernatePersistenceExceptionConverter();
        schedule = new Schedule2();
        schedule.setGuid(GUID);
        
        organization = Organization.create();
    }
    
    @Test
    public void optimisticLock() {
        // exception nesting is:
        //  javax.persistence.OptimisticLockException, wraps
        //    org.hibernate.StaleObjectStateException
        //      msg: Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect) : 

        StaleObjectStateException sose = new StaleObjectStateException("Schedule2", "key");

        OptimisticLockException ole = new OptimisticLockException();
        ole.initCause(sose);
        
        BridgeServiceException converted = (BridgeServiceException)converter.convert(ole, schedule);
        
        assertEquals(converted.getMessage(),
                "Schedule has the wrong version number; it may have been saved in the background.");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConcurrentModificationException");
    }
    
    @Test
    public void nonUniqueObject() {
        // exception nesting is:
        //   org.hibernate.NonUniqueObjectException
        //     msg: A different object with the same identifier value was already associated with the session
        
        NonUniqueObjectException nuoe = new NonUniqueObjectException(
                "Duplicate entry 'Rf4L4IdSgEL27fAeBSFTi-CJ' for key 'PRIMARY'", GUID, "Schedule");

        BridgeServiceException converted = (BridgeServiceException)converter.convert(nuoe, schedule);
        
        assertEquals(converted.getMessage(), "Another schedule has already used a value which must be unique: " + GUID);
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }

    @Test
    public void sqlIntegrityConstraintViolation_UnknownForeignKeyConstraint() {
        PersistenceException pe = buildIntegrityConstraintViolation("Unknown-Constraint",
                "a foreign key constraint fails ... `Unknown-Constraint`");
        
        BridgeServiceException converted = (BridgeServiceException)converter.convert(pe, schedule);
        
        assertEquals(converted.getMessage(), "Cannot update or delete this item because it is in use.");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }

    @Test
    public void sqlIntegrityConstraintViolation_Unknown() {
        PersistenceException pe = buildIntegrityConstraintViolation("Unknown-Constraint",
                "Not known ... `Unknown-Constraint`");
        
        BridgeServiceException converted = (BridgeServiceException)converter.convert(pe, schedule);
        
        assertEquals(converted.getMessage(), "Cannot update or delete this item because it is in use.");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }

    @Test
    public void sqlIntegrityConstraintViolation_AssessmentRefAssessmentConstraint() {
        PersistenceException pe = buildIntegrityConstraintViolation("AssessmentRef-Assessment-Cosntraint",
                "a foreign key constraint fails ... AssessmentRef-Assessment-Constraint`");
        
        HibernateAssessment assessment = new HibernateAssessment();
        BridgeServiceException converted = (BridgeServiceException)converter.convert(pe, assessment);
        
        assertEquals(converted.getMessage(),
                "This assessment cannot be deleted or updated because it is referenced by a scheduling session.");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }
    
    @Test
    public void sqlIntegrityConstraintViolation_ScheduleOrganizationConstraint() {
        PersistenceException pe = buildIntegrityConstraintViolation("Schedule-Organization-Constraint",
                "a foreign key constraint fails ... Schedule-Organization-Constraint`");
        
        BridgeServiceException converted = (BridgeServiceException)converter.convert(pe, organization);
        
        assertEquals(converted.getMessage(),
                "This organization cannot be deleted or updated because it is referenced by a schedule.");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }
    
    @Test
    public void sqlIntegrityConstraintViolation_DuplicateEntry() {
        PersistenceException pe = buildIntegrityConstraintViolation("PRIMARY",
                "Duplicate entry ... PRIMARY");
        
        BridgeServiceException converted = (BridgeServiceException)converter.convert(pe, organization);
        
        assertEquals(converted.getMessage(),
                "Cannot update this organization because it has duplicate primary keys");
        assertEquals(converted.getStatusCode(), HttpStatus.SC_CONFLICT);
        assertEquals(converted.getClass().getSimpleName(), "ConstraintViolationException");
    }
    
    @Test
    public void passthrough() { 
        PersistenceException pe = new PersistenceException("Nothing known to the converter, pass it through");
        
        RuntimeException converted = converter.convert(pe, organization);
        assertSame(converted, pe);
    }
    
    private PersistenceException buildIntegrityConstraintViolation(String constraintName, String message) {
        // exception nesting is:
        // javax.persistence.PersistenceException
        //   org.hibernate.exception.ConstraintViolationException
        //     java.sql.SQLIntegrityConstraintViolationException
        //       msg: Cannot add or update a child row: a foreign key constraint fails (`bridgedb`.`sessionassessments`, CONSTRAINT 
        //          `AssessmentRef-Assessment-Constraint` FOREIGN KEY (`guid`) REFERENCES `Assessments` (`guid`))
        SQLIntegrityConstraintViolationException sicve = new SQLIntegrityConstraintViolationException(message);
        org.hibernate.exception.ConstraintViolationException cve = new ConstraintViolationException(
                "could not execute query", sicve, constraintName);
        
        PersistenceException pe = new PersistenceException(cve);
        
        return pe;
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateStudy study = new HibernateStudy();
        study.setAppId(TEST_APP_ID);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, study);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "Study has the wrong version number; it may have been saved in the background.");
    }
    
    @Test
    public void genericConstraintViolationException() {
        HibernateStudy study = new HibernateStudy();
        study.setAppId(TEST_APP_ID);

        SQLIntegrityConstraintViolationException icve = 
                new SQLIntegrityConstraintViolationException(
                        "abc a foreign key constraint fails");
        
        PersistenceException ex = new PersistenceException(icve);

        RuntimeException result = converter.convert(ex, study);

        assertEquals(result.getClass(), org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class);
        assertEquals(result.getMessage(), "Cannot update or delete this item because it is in use.");
    }
    
    @Test
    public void usedByAccountsConstraintViolationException() {
        HibernateStudy study = new HibernateStudy();
        study.setAppId(TEST_APP_ID);

        SQLIntegrityConstraintViolationException icve = 
                new SQLIntegrityConstraintViolationException(
                        "abc a foreign key constraint fails `fk_substudy`");
        PersistenceException ex = new PersistenceException(icve);

        RuntimeException result = converter.convert(ex, study);

        assertEquals(result.getClass(), 
            org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class);
        assertEquals(result.getMessage(), 
            "This study cannot be deleted or updated because it is referenced by an account.");
    }
}
