package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.hibernate.SponsorPersistenceExceptionConverter.DUPLICATE_ERROR;
import static org.sagebionetworks.bridge.hibernate.SponsorPersistenceExceptionConverter.DUPLICATE_MSG;
import static org.sagebionetworks.bridge.hibernate.SponsorPersistenceExceptionConverter.ORG_ERROR;
import static org.sagebionetworks.bridge.hibernate.SponsorPersistenceExceptionConverter.STUDY_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.sql.SQLIntegrityConstraintViolationException;

import javax.persistence.PersistenceException;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.organizations.Organization;

public class SponsorPersistenceExceptionConverterTest extends Mockito {
    
    private SponsorPersistenceExceptionConverter converter;
    
    @Mock
    private ConstraintViolationException cve;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        this.converter = new SponsorPersistenceExceptionConverter();
    }
    
    @Test
    public void repackagesConstraintViolation() { 
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException("Some error", null, "");
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException retValue = converter.convert(pe, Organization.create());
        assertEquals(retValue.getClass().getName(), "org.sagebionetworks.bridge.exceptions.ConstraintViolationException");
        assertSame(retValue.getMessage(), "Some error");
    }
    
    @Test
    public void repackagesDuplicateConstraintViolation() {
        constraintTests(org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class, DUPLICATE_ERROR, DUPLICATE_MSG);
    }

    @Test
    public void repackagesOrgNotFoundException() { 
        constraintTests(EntityNotFoundException.class, ORG_ERROR, "Organization not found.");
    }
    
    @Test
    public void repackagesStudyNotFoundException() {
        constraintTests(EntityNotFoundException.class, STUDY_ERROR, "Study not found.");
    }
    
    private void constraintTests(Class<?> clazz, String sqlError, String finalMessage) {
        SQLIntegrityConstraintViolationException sqle = new SQLIntegrityConstraintViolationException(sqlError);
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException("", sqle, "");
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException retValue = converter.convert(pe, null);
        
        assertEquals(retValue.getClass().getName(), clazz.getCanonicalName());
        assertEquals(retValue.getMessage(), finalMessage);
    }
    
    @Test
    public void passesThroughOtherExceptions() { 
        RuntimeException re = new RuntimeException("Some error");
        PersistenceException pe = new PersistenceException(re);
        
        RuntimeException retValue = converter.convert(pe, Organization.create());
        assertSame(retValue, pe);
    }    
}
