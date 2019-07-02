package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class TemplateRevisionPersistenceExceptionConverterTest {
    
    private TemplateRevisionPersistenceExceptionConverter converter;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        this.converter = new TemplateRevisionPersistenceExceptionConverter();
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(converter.convert(ex, null), ex);
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateTemplateRevision revision = new HibernateTemplateRevision();
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, revision);
        assertEquals(result.getClass(), ConcurrentModificationException.class);
        assertEquals(result.getMessage(), "TemplateRevision has the wrong version number; it may have been saved in the background.");
    }

}
