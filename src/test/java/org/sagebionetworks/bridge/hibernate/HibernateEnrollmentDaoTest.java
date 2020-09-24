package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

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
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;

public class HibernateEnrollmentDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    AccountDao mockAccountDao;
    
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
        
        AccountId accountId1 = AccountId.forId(TEST_APP_ID, "id1");
        AccountId accountId2 = AccountId.forId(TEST_APP_ID, "id2");
        AccountId accountId3 = AccountId.forId(TEST_APP_ID, "id3");
        Account account1 = Account.create();
        account1.setLastName("account1");
        Account account2 = Account.create();
        account2.setLastName("account2");
        Account account3 = Account.create();
        account3.setLastName("account3");
        when(mockAccountDao.getAccount(accountId1)).thenReturn(Optional.of(account1));
        when(mockAccountDao.getAccount(accountId2)).thenReturn(Optional.of(account2));
        when(mockAccountDao.getAccount(accountId3)).thenReturn(Optional.of(account3));
        
        PagedResourceList<EnrollmentDetail> retValue = dao.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, 10, 75);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));
        assertEquals(retValue.getItems().size(), 2);
        
        EnrollmentDetail detail1 = retValue.getItems().get(0);
        assertEquals(detail1.getParticipant().getLastName(), "account1");
        assertEquals(detail1.getEnrolledBy().getLastName(), "account2");
        assertEquals(detail1.getWithdrawnBy().getLastName(), "account3");
        
        // This one is empty
        EnrollmentDetail detail2 = retValue.getItems().get(1);
        assertNull(detail2.getParticipant());
        assertNull(detail2.getEnrolledBy());
        assertNull(detail2.getWithdrawnBy());
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(75),
                eq(HibernateEnrollment.class));
        assertEquals(queryCaptor.getValue(), "FROM HibernateEnrollment WHERE appId = :appId AND studyId = :studyId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
    }
}
