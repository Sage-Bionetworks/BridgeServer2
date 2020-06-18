package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateOrganizationDao.GET_QUERY;
import static org.sagebionetworks.bridge.hibernate.HibernateOrganizationDao.GET_SUMMARY_QUERY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;

import java.util.Map;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.organizations.OrganizationId;

public class HibernateOrganizationDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<Map<String, Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<OrganizationId> idCaptor;
    
    @InjectMocks
    HibernateOrganizationDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getOrganizations() {
        dao.getOrganizations(TEST_APP_ID, 5, 25);
        
        verify(mockHelper).queryCount(eq("SELECT count(*) " + GET_QUERY), paramsCaptor.capture());
        verify(mockHelper).queryGet(eq(GET_SUMMARY_QUERY), paramsCaptor.capture(), 
                eq(5), eq(25), eq(HibernateOrganization.class));
        
        assertEquals(paramsCaptor.getAllValues().get(0).get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getAllValues().get(1).get("appId"), TEST_APP_ID);
    }

    @Test
    public void createOrganization() {
        Organization org = Organization.create();
        dao.createOrganization(org);
        verify(mockHelper).create(org, null);
    }

    @Test
    public void updateOrganization() {
        Organization org = Organization.create();
        dao.updateOrganization(org);
        verify(mockHelper).update(org, null);
    }

    @Test
    public void getOrganization() {
        Organization org = Organization.create();
        when(mockHelper.getById(any(), any())).thenReturn(org);
        
        Optional<Organization> retValue = dao.getOrganization(TEST_APP_ID, "anIdentifier");
        assertSame(retValue.get(), org);
        
        verify(mockHelper).getById(eq(HibernateOrganization.class), idCaptor.capture());
        assertEquals(idCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(idCaptor.getValue().getIdentifier(), "anIdentifier");
    }
    
    @Test
    public void getOrganizationNotFound() {
        Optional<Organization> retValue = dao.getOrganization(TEST_APP_ID, "anIdentifier");
        assertFalse(retValue.isPresent());
    }
    
    @Test
    public void deleteOrganization() {
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier("anIdentifier");
        
        dao.deleteOrganization(org);
        
        verify(mockHelper).deleteById(eq(HibernateOrganization.class), idCaptor.capture());
        assertEquals(idCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(idCaptor.getValue().getIdentifier(), "anIdentifier");
    }
}
