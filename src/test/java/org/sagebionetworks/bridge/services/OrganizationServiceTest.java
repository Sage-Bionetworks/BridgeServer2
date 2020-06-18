package org.sagebionetworks.bridge.services;

import org.mockito.Mock;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Optional;

import com.google.common.collect.ImmutableList;

import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationServiceTest extends Mockito {

    private static final String NAME = "aName";
    private static final String IDENTIFIER = "an-identifier";

    @Mock
    OrganizationDao mockDao;
    
    @InjectMocks
    @Spy
    OrganizationService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @Test
    public void getOrganizations() {
        PagedResourceList<Organization> page = new PagedResourceList<>(
                ImmutableList.of(Organization.create(), Organization.create()), 10);
        when(mockDao.getOrganizations(TEST_APP_ID, 100, 20)).thenReturn(page);
        
        PagedResourceList<Organization> retList = service.getOrganizations(TEST_APP_ID, 100, 20);
        assertEquals(retList.getRequestParams().get("offsetBy"), 100);
        assertEquals(retList.getRequestParams().get("pageSize"), 20);
        assertEquals(retList.getTotal(), Integer.valueOf(10));
        assertEquals(retList.getItems().size(), 2);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getOrganizationsNegativeOffset() {
        service.getOrganizations(TEST_APP_ID, -5, 0);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getOrganizationsPageTooSmall() {
        service.getOrganizations(TEST_APP_ID, 0, 0);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getOrganizationsPageTooLarge() {
        service.getOrganizations(TEST_APP_ID, 0, 1000);
    }
    
    @Test
    public void createOrganization() {
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        org.setVersion(3L);
        
        when(mockDao.createOrganization(org)).thenReturn(org);

        Organization retValue = service.createOrganization(org);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), CREATED_ON);
        assertNull(retValue.getVersion());
        
        verify(mockDao).createOrganization(org);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createOrganizationAlreadyExists() {
        Organization existing = Organization.create();
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(existing));
        
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);

        service.createOrganization(org);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrganizationNotValid() {
        Organization org = Organization.create();
        service.createOrganization(org);
    }
    
    @Test
    public void updateOrganization() {
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        
        Organization existing = Organization.create();
        existing.setAppId(TEST_APP_ID);
        existing.setIdentifier(IDENTIFIER);
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(existing));
        
        when(mockDao.updateOrganization(org)).thenReturn(org);
        
        Organization retValue = service.updateOrganization(org);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getName(), NAME);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void updateOrganizationNotFound() {
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.empty());
        
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        
        service.updateOrganization(org);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateOrganizationNotValid() {
        Organization org = Organization.create();
        
        service.updateOrganization(org);
    }
    
    @Test
    public void getOrganization() {
        Organization org = Organization.create();
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER))
            .thenReturn(Optional.of(org));
        
        Organization retValue = service.getOrganization(TEST_APP_ID, IDENTIFIER);
        assertSame(retValue, org);
        
        verify(mockDao).getOrganization(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void getOrganizationNotFound() {
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER))
            .thenReturn(Optional.empty());
        
        service.getOrganization(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test
    public void deleteOrganization() {
        Organization org = Organization.create();
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(org));
        
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
        
        verify(mockDao).deleteOrganization(org);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void deleteOrganizationNotFound() {
        when(mockDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.empty());
        
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
    }
}
