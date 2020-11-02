package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class HibernateExternalIdDaoTest extends Mockito {
    
    private static final String EXTERNAL_ID = "anExternalId";
    
    private static final String FULL_QUERY = "SELECT en from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL AND en.externalId LIKE :idFilter ORDER BY en.externalId";
    private static final String FULL_COUNT_QUERY = "SELECT count(en) from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL AND en.externalId LIKE :idFilter ORDER BY en.externalId";
    private static final String QUERY = "SELECT en from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL ORDER BY en.externalId";
    private static final String COUNT_QUERY = "SELECT count(en) from HibernateEnrollment as en WHERE en.appId = :appId AND en.studyId = :studyId AND en.externalId IS NOT NULL ORDER BY en.externalId";
    
    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    AccountDao mockAccountDao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String, Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @InjectMocks
    HibernateExternalIdDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getPagedExternalIds() {
        HibernateEnrollment en1 = new HibernateEnrollment();
        en1.setAppId(TEST_APP_ID);
        en1.setStudyId(TEST_STUDY_ID);
        en1.setExternalId("extId1");
        
        HibernateEnrollment en2 = new HibernateEnrollment();
        en2.setAppId(TEST_APP_ID);
        en2.setStudyId(TEST_STUDY_ID);
        en2.setExternalId("extId2");
        
        List<HibernateEnrollment> list = ImmutableList.of(en1, en2);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class)))
            .thenReturn(list);
        
        when(mockHelper.queryCount(any(), any())).thenReturn(100);
        
        PagedResourceList<ExternalIdentifierInfo> retValue = dao.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, "idFilter", 100, 50);
        assertEquals(retValue.getTotal(), new Integer(100));
        
        ExternalIdentifierInfo info1 = retValue.getItems().get(0);
        assertEquals(info1.getIdentifier(), "extId1");
        assertEquals(info1.getStudyId(), TEST_STUDY_ID);
        assertTrue(info1.isAssigned());
        
        ExternalIdentifierInfo info2 = retValue.getItems().get(1);
        assertEquals(info2.getIdentifier(), "extId2");
        assertEquals(info2.getStudyId(), TEST_STUDY_ID);
        assertTrue(info2.isAssigned());
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(100), eq(50), eq(HibernateEnrollment.class));
        verify(mockHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        
        String query1 = queryCaptor.getAllValues().get(0);
        assertEquals(query1, FULL_QUERY);
        Map<String,Object> params1 = paramsCaptor.getAllValues().get(0);
        assertEquals(params1.get("appId"), TEST_APP_ID);
        assertEquals(params1.get("studyId"), TEST_STUDY_ID);
        assertEquals(params1.get("idFilter"), "idFilter%");
        
        String query2 = queryCaptor.getAllValues().get(1);
        assertEquals(query2, FULL_COUNT_QUERY);
        Map<String,Object> params2 = paramsCaptor.getAllValues().get(1);
        assertEquals(params2.get("appId"), TEST_APP_ID);
        assertEquals(params2.get("studyId"), TEST_STUDY_ID);
        assertEquals(params2.get("idFilter"), "idFilter%");
    }
    
    @Test
    public void getPagedExternalIdsNoIdFilter() {
        List<HibernateEnrollment> list = ImmutableList.of();
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateEnrollment.class)))
            .thenReturn(list);
        when(mockHelper.queryCount(any(), any())).thenReturn(100);
        
        dao.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, null, 100, 50);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(100), eq(50), eq(HibernateEnrollment.class));
        verify(mockHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        
        String query1 = queryCaptor.getAllValues().get(0);
        assertEquals(query1, QUERY);
        Map<String,Object> params1 = paramsCaptor.getAllValues().get(0);
        assertEquals(params1.get("appId"), TEST_APP_ID);
        assertEquals(params1.get("studyId"), TEST_STUDY_ID);
        assertNull(params1.get("idFilter"));
        
        String query2 = queryCaptor.getAllValues().get(1);
        assertEquals(query2, COUNT_QUERY);
        Map<String,Object> params2 = paramsCaptor.getAllValues().get(1);
        assertEquals(params2.get("appId"), TEST_APP_ID);
        assertEquals(params2.get("studyId"), TEST_STUDY_ID);
        assertNull(params1.get("idFilter"));        
    }

    @Test
    public void deleteExternalId() {
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
        en.setExternalId(EXTERNAL_ID);
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(en));
        
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setStudyId(TEST_STUDY_ID);
        
        dao.deleteExternalId(extId);
        
        verify(mockAccountDao).updateAccount(accountCaptor.capture(), eq(null));
        
        Account captured = accountCaptor.getValue();
        assertNull(Iterables.getFirst(captured.getEnrollments(), null).getExternalId());
    }
    
    @Test
    public void deleteExternalIdAccountNotFound() {
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.empty());
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setStudyId(TEST_STUDY_ID);
        
        dao.deleteExternalId(extId);
        
        verify(mockAccountDao, never()).updateAccount(any(), any());
    }

    @Test
    public void deleteExternalIdEnrollmentNotFound() {
        Enrollment en = Enrollment.create(TEST_APP_ID, "anotherStudy", USER_ID);
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(en));
        
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));
        
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setStudyId(TEST_STUDY_ID);
        
        dao.deleteExternalId(extId);
        
        verify(mockAccountDao, never()).updateAccount(any(), any());
    }
}
