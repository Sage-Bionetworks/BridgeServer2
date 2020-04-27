package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.sql.SQLException;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;

public class BasicPersistenceExceptionConverterTest {
    
    private BasicPersistenceExceptionConverter converter;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        this.converter = new BasicPersistenceExceptionConverter();
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateTemplate template = new HibernateTemplate();
        template.setAppId(TEST_APP_ID);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, template);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "Template has the wrong version number; it may have been saved in the background.");
    }
    
    @Test
    public void hibernateConstraintViolationException() {
        SQLException sqe = new SQLException();
        org.hibernate.exception.ConstraintViolationException cve = 
                new org.hibernate.exception.ConstraintViolationException(
                        "message", sqe, "constraintName");
        PersistenceException ep = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(ep, null);
        assertTrue(result instanceof ConstraintViolationException);
        
        ConstraintViolationException bridgeCVE = (ConstraintViolationException)result;
        assertEquals(bridgeCVE.getMessage(), "message");
    }
}
