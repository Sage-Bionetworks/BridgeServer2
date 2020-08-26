package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.persistence.PersistenceException;

import org.hibernate.exception.ConstraintViolationException;
import org.testng.annotations.Test;

public class PersistenceExceptionConverterTest {
    
    @Test
    public void getConstraintViolationWrappedWithPersistenceException() {
        ConstraintViolationException cve = new ConstraintViolationException("Some error", null, "");
        PersistenceException exception = new PersistenceException(cve);
        
        ConstraintViolationException retValue = PersistenceExceptionConverter.getConstraintViolation(exception);
        assertEquals(retValue.getClass(), ConstraintViolationException.class);
        assertEquals(retValue.getMessage(), "Some error");
    }
    
    @Test
    public void getConstraintViolationRawConstraintViolationException() {
        ConstraintViolationException cve = new ConstraintViolationException("Some error", null, "");
        
        ConstraintViolationException retValue = PersistenceExceptionConverter.getConstraintViolation(cve);
        assertEquals(retValue.getClass(), ConstraintViolationException.class);
        assertEquals(retValue.getMessage(), "Some error");
    }
    
    // This one is silly but just to try it
    @Test
    public void getConstraintViolationDeeplyNestedStillWorks() { 
        ConstraintViolationException cve = new ConstraintViolationException("Some error", null, "");
        PersistenceException exception = new PersistenceException(cve);
        for (int i=0; i < 5; i++) {
            exception = new PersistenceException(exception);
        }
        ConstraintViolationException retValue = PersistenceExceptionConverter.getConstraintViolation(exception);
        assertEquals(retValue.getClass(), ConstraintViolationException.class);
        assertEquals(retValue.getMessage(), "Some error");
    }
    
    @Test
    public void getConstraintViolationNonConstraintException() {
        java.sql.SQLException sqlException = new java.sql.SQLException("Some error");
        PersistenceException exception = new PersistenceException(sqlException);
        
        PersistenceException retValue = PersistenceExceptionConverter.getConstraintViolation(exception);
        assertNull(retValue);
    }
}
