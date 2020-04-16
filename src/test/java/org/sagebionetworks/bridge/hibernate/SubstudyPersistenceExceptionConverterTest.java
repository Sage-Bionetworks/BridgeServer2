package org.sagebionetworks.bridge.hibernate;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.exception.ConstraintViolationException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class SubstudyPersistenceExceptionConverterTest {
    
    private SubstudyPersistenceExceptionConverter converter;
    
    @Mock
    private ConstraintViolationException cve;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        this.converter = new SubstudyPersistenceExceptionConverter();
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TEST_APP_ID);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, substudy);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "Substudy has the wrong version number; it may have been saved in the background.");
    }
    
    @Test
    public void genericConstraintViolationException() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TEST_APP_ID);

        PersistenceException ex = new PersistenceException(cve);
        when(cve.getMessage()).thenReturn("This is some generic constraint violation message");

        RuntimeException result = converter.convert(ex, substudy);

        assertEquals(result.getClass(), org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class);
        assertEquals(result.getMessage(), "Substudy table constraint prevented save or update.");
    }
    
    @Test
    public void usedByAccountsConstraintViolationException() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TEST_APP_ID);

        PersistenceException ex = new PersistenceException(cve);
        when(cve.getMessage()).thenReturn("abc a foreign key constraint fails abc REFERENCES `Substudies`abc");

        RuntimeException result = converter.convert(ex, substudy);

        assertEquals(result.getClass(), org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class);
        assertEquals(result.getMessage(), "Substudy cannot be deleted, it is referenced by an account");
    }
}
