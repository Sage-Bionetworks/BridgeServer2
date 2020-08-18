package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.hibernate.OrganizationPersistenceExceptionConverter.CONSTRAINT_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.sql.SQLIntegrityConstraintViolationException;

import javax.persistence.PersistenceException;

import org.hibernate.exception.ConstraintViolationException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationPersistenceExceptionConverterTest extends Mockito {
    
    public static final String CONSTRAINT_MSG = "Cannot delete or update a parent row: a foreign "
            +"key constraint fails (`bridgedb`.`organizationsstudies`, CONSTRAINT `fk_os_organization` "
            +"FOREIGN KEY (`appId`, `orgId`) REFERENCES `Organizations` (`appId`, `identifier`))";

    private OrganizationPersistenceExceptionConverter converter;
    
    @Mock
    private ConstraintViolationException cve;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        this.converter = new OrganizationPersistenceExceptionConverter();
    }
    
    @Test
    public void trapsConstraint() throws Exception {
        SQLIntegrityConstraintViolationException sqle = new SQLIntegrityConstraintViolationException(CONSTRAINT_MSG);
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException("", sqle, "");
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException retValue = converter.convert(pe, Organization.create());
        
        assertEquals(retValue.getClass().getName(), "org.sagebionetworks.bridge.exceptions.ConstraintViolationException");
        assertEquals(retValue.getMessage(), CONSTRAINT_ERROR);
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
    public void passesThroughOtherExceptions() { 
        RuntimeException re = new RuntimeException("Some error");
        PersistenceException pe = new PersistenceException(re);
        
        RuntimeException retValue = converter.convert(pe, Organization.create());
        assertSame(retValue, pe);
    }
}
