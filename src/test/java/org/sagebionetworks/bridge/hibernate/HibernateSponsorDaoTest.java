package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

public class HibernateSponsorDaoTest extends Mockito {

    @Mock
    HibernateHelper mockHelper;
    
    @InjectMocks
    HibernateSponsorDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getStudySponsors() {
        int total = 100;
        List<HibernateOrganization> list = ImmutableList.of(new HibernateOrganization(), new HibernateOrganization());

        when(mockHelper.nativeQueryCount(queryCaptor.capture(), paramsCaptor.capture())).thenReturn(total);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), any(), 
                eq(10), eq(60), eq(HibernateOrganization.class))).thenReturn(list);
        
        PagedResourceList<Organization> retValue = dao.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 10, 60);
        assertEquals(retValue.getItems().size(), 2);
        assertEquals(retValue.getTotal(), (Integer)100);
        
        verify(mockHelper).nativeQueryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).nativeQueryGet(queryCaptor.capture(), any(), 
                eq(10), eq(60), eq(HibernateOrganization.class));
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), TEST_APP_ID);
        assertEquals(params.get("studyId"), TEST_STUDY_ID);
        
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) FROM Organizations o " 
                + "INNER JOIN OrganizationsStudies os ON o.identifier = os.orgId AND  o.appId = " 
                + ":appId AND os.appId = :appId  AND os.studyId = :studyId");
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * FROM Organizations o INNER " 
                + "JOIN OrganizationsStudies os ON o.identifier = os.orgId AND  o.appId = :appId " 
                + "AND os.appId = :appId  AND os.studyId = :studyId");
    }
    
    @Test
    public void getSponsoredStudies() {
        int total = 100;
        List<HibernateStudy> list = ImmutableList.of(new HibernateStudy(), new HibernateStudy());

        when(mockHelper.nativeQueryCount(queryCaptor.capture(), paramsCaptor.capture())).thenReturn(total);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), any(), 
                eq(10), eq(60), eq(HibernateStudy.class))).thenReturn(list);
        
        PagedResourceList<Study> retValue = dao.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 10, 60);
        assertEquals(retValue.getItems().size(), 2);
        assertEquals(retValue.getTotal(), (Integer)100);
        
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) FROM Substudies s INNER JOIN " 
                + "OrganizationsStudies os ON s.id = os.studyId AND  s.studyId = :appId AND os.appId = " 
                + ":appId  AND os.orgId = :orgId AND s.deleted != 1");
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * FROM Substudies s INNER JOIN " 
                + "OrganizationsStudies os ON s.id = os.studyId AND  s.studyId = :appId AND os.appId = " 
                + ":appId  AND os.orgId = :orgId AND s.deleted != 1");
    }
    
    @Test
    public void addStudySponsor() {
        dao.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockHelper).nativeQueryUpdate(queryCaptor.capture(), paramsCaptor.capture());
        
        assertEquals(queryCaptor.getValue(), 
                "INSERT INTO OrganizationsStudies (appId, studyId, orgId) VALUES (:appId, :studyId, :orgId)");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("orgId"), TEST_ORG_ID);
    }

    @Test
    public void removeStudySponsor() {
        dao.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockHelper).nativeQueryUpdate(queryCaptor.capture(), paramsCaptor.capture());
        
        assertEquals(queryCaptor.getValue(), 
            "DELETE FROM OrganizationsStudies WHERE appId = :appId  AND studyId = :studyId  AND orgId = :orgId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("orgId"), TEST_ORG_ID);
    }
    
    @Test
    public void doesOrganizationSponsorStudyTrue() {
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), paramsCaptor.capture())).thenReturn(1);
        
        assertTrue(dao.doesOrganizationSponsorStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID));
        
        assertEquals(queryCaptor.getValue(), "SELECT count(*) FROM OrganizationsStudies os WHERE " 
                + "os.appId = :appId  AND os.studyId = :studyId  AND os.orgId = :orgId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("orgId"), TEST_ORG_ID);
    }

    @Test
    public void doesOrganizationSponsorStudyFalse() {
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), paramsCaptor.capture())).thenReturn(0);
        
        assertFalse(dao.doesOrganizationSponsorStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID));
        
        assertEquals(queryCaptor.getValue(), "SELECT count(*) FROM OrganizationsStudies os WHERE " 
                + "os.appId = :appId  AND os.studyId = :studyId  AND os.orgId = :orgId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("orgId"), TEST_ORG_ID);
    }
}
