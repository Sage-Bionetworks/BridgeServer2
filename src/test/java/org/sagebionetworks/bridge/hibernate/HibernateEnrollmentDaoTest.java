package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;

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
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class HibernateEnrollmentDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @InjectMocks
    HibernateEnrollmentDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getEnrollmentsForStudy() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAccountId("id1");
        en1.setEnrolledBy("id2");
        en1.setWithdrawnBy("id3");
        HibernateEnrollment en2 = new HibernateEnrollment();
        List<HibernateEnrollment> page = ImmutableList.of(en1, en2);
        
        when(mockHelper.queryCount(any(), any())).thenReturn(20);
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class))).thenReturn(page);
        
        PagedResourceList<Enrollment> retValue = dao.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, true, 10, 75);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));
        assertEquals(retValue.getItems(), page);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(75),
                eq(HibernateEnrollment.class));
        assertEquals(queryCaptor.getValue(), "SELECT h FROM HibernateEnrollment AS h WHERE h.appId = :appId AND h.studyId = :studyId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
    }
    
    @Test
    public void getEnrollmentsForStudyWithArguments() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAccountId("id1");
        en1.setEnrolledBy("id2");
        en1.setWithdrawnBy("id3");
        HibernateEnrollment en2 = new HibernateEnrollment();
        List<HibernateEnrollment> page = ImmutableList.of(en1, en2);
        
        when(mockHelper.queryCount(any(), any())).thenReturn(20);
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class))).thenReturn(page);
        
        
        PagedResourceList<Enrollment> retValue = dao.getEnrollmentsForStudy(
                TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 10, 75);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));
        assertEquals(retValue.getItems(), page);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(75),
                eq(HibernateEnrollment.class));
        assertEquals(queryCaptor.getValue(), "SELECT h FROM HibernateEnrollment AS h WHERE h.appId = "
                + ":appId AND h.studyId = :studyId AND withdrawnOn IS NULL");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
    }
    
    @Test
    public void getEnrollmentsForStudyExcludesTestUsers() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAccountId("id1");
        en1.setEnrolledBy("id2");
        en1.setWithdrawnBy("id3");
        HibernateEnrollment en2 = new HibernateEnrollment();
        List<HibernateEnrollment> page = ImmutableList.of(en1, en2);
        
        when(mockHelper.queryCount(any(), any())).thenReturn(20);
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class))).thenReturn(page);
        
        PagedResourceList<Enrollment> retValue = dao.getEnrollmentsForStudy(
                TEST_APP_ID, TEST_STUDY_ID, ENROLLED, false, 10, 75);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));
        assertEquals(retValue.getItems(), page);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(75),
                eq(HibernateEnrollment.class));
        assertEquals(queryCaptor.getValue(), "SELECT h FROM HibernateEnrollment AS h INNER JOIN "
                + "org.sagebionetworks.bridge.hibernate.HibernateAccount AS acct ON acct.id = " 
                + "h.accountId WHERE h.appId = :appId AND h.studyId = :studyId AND withdrawnOn IS NULL " 
                + "AND (:NOTIN1 NOT IN elements(acct.dataGroups))");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("NOTIN1"), TEST_USER_GROUP);
    }
    
    @Test
    public void getEnrollmentsForUser() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAccountId("id1");
        en1.setEnrolledBy("id2");
        en1.setWithdrawnBy("id3");
        HibernateEnrollment en2 = new HibernateEnrollment();
        List<HibernateEnrollment> page = ImmutableList.of(en1, en2);
        
        when(mockHelper.queryGet(any(), any(), isNull(), isNull(), eq(HibernateEnrollment.class))).thenReturn(page);
        
        List<Enrollment> retValue = dao.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID);
        assertEquals(retValue, page);

        verify(mockHelper).queryGet(queryCaptor.capture(),
                paramsCaptor.capture(), isNull(), isNull(), eq(HibernateEnrollment.class));
        
        assertEquals(queryCaptor.getValue(), "FROM HibernateEnrollment WHERE appId = :appId AND accountId = :userId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("userId"), TEST_USER_ID);
    }
}
