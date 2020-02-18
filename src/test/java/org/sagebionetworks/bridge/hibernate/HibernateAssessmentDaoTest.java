package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.dao.AssessmentDao.APP_ID;
import static org.sagebionetworks.bridge.dao.AssessmentDao.GUID;
import static org.sagebionetworks.bridge.dao.AssessmentDao.IDENTIFIER;
import static org.sagebionetworks.bridge.dao.AssessmentDao.REVISION;
import static org.sagebionetworks.bridge.hibernate.HibernateAssessmentDao.GET_BY_GUID;
import static org.sagebionetworks.bridge.hibernate.HibernateAssessmentDao.GET_BY_IDENTIFIER;
import static org.testng.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.Assessment;

public class HibernateAssessmentDaoTest extends Mockito {

    private static final String APP_ID_VALUE = "appId";
    private static final String ID_VALUE = "identifier";
    private static final String GUID_VALUE = "guid";
    private static final int REV_VALUE = 3;

    @Mock
    SessionFactory mockSessionFactory;
    
    @Mock
    Session mockSession;
    
    @Mock
    EntityManager mockEntityManager;
    
    @Mock
    Transaction mockTransaction;
    
    @Mock
    PersistenceExceptionConverter mockConverter;
    
    @Mock
    Assessment mockAssessment;
    
    @Mock
    TypedQuery<Assessment> mockAssessmentQuery;
    
    @Mock
    TypedQuery<Long> mockLongQuery;
    
    @Mock
    TypedQuery<BigInteger> mockBigIntegerQuery;
    
    @Captor
    ArgumentCaptor<String> queryStringCaptor;
    
    @InjectMocks
    @Spy
    HibernateAssessmentDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        doReturn(mockEntityManager).when(dao).getEntityManager();
        doReturn(mockSession).when(mockSessionFactory).openSession();
        doReturn(mockTransaction).when(mockSession).beginTransaction();
    }
    
    @Test
    public void getAssessmentsExcludeDeleted() {
        Set<String> cats = ImmutableSet.of("A", "B");
        Set<String> tags = ImmutableSet.of("C", "D");
        executeNativePagedCall(false, cats, tags, () -> dao.getAssessments(APP_ID_VALUE, 2, 20, cats, tags, false));
    }
    
    @Test
    public void getAssessmentsIncludeDeleted() {
        executeNativePagedCall(true, null, null,
                () -> dao.getAssessments(APP_ID_VALUE, 2, 20, ImmutableSet.of(), ImmutableSet.of(), true));
    }
    
    @Test
    public void getAssessmentsWithCategoriesAndTags() {
        Set<String> categories = ImmutableSet.of("A", "B");
        Set<String> tags = ImmutableSet.of("C", "D");
        executeNativePagedCall(true, categories, tags, () -> dao.getAssessments(APP_ID_VALUE, 2, 20, categories, tags, true));
    }
    
    private void executeNativePagedCall(boolean includeDeleted, Set<String> categories, Set<String> tags, Supplier<PagedResourceList<Assessment>> supplier) {
        Set<Tag> c = BridgeUtils.toTagSet(categories, "category");
        when(mockAssessment.getCategories()).thenReturn(c);

        Set<Tag> t = BridgeUtils.toTagSet(tags, "tag");
        when(mockAssessment.getTags()).thenReturn(t);
            
        when(mockEntityManager.createNativeQuery(queryStringCaptor.capture())).thenReturn(mockBigIntegerQuery);
        when(mockEntityManager.createNativeQuery(queryStringCaptor.capture(), eq(Assessment.class)))
                .thenReturn(mockAssessmentQuery);

        when(mockAssessmentQuery.getResultList()).thenReturn(
                ImmutableList.of(mockAssessment, mockAssessment, mockAssessment, mockAssessment, mockAssessment));

        PagedResourceList<Assessment> page = supplier.get();
        assertEquals(page.getTotal(), Integer.valueOf(5));
        assertEquals(page.getItems(), ImmutableList.of(mockAssessment, mockAssessment, mockAssessment));
        
        verify(mockAssessmentQuery).setParameter(APP_ID, APP_ID_VALUE);
    }
    
    @Test
    public void getAssessmentRevisionsIncludeDeleted() {
        executePagedCall(true, () -> dao.getAssessmentRevisions(APP_ID_VALUE, ID_VALUE, 10, 20, true));
    }
    
    @Test
    public void getAssessmentRevisionsExcludeDeleted() {
        executePagedCall(false, () -> dao.getAssessmentRevisions(APP_ID_VALUE, ID_VALUE, 10, 20, false));
    }
    
    private void executePagedCall(boolean includeDeleted, Supplier<PagedResourceList<Assessment>> supplier) {
        when(mockEntityManager.createQuery(any(), eq(Long.class))).thenReturn(mockLongQuery);
        when(mockEntityManager.createQuery(any(), eq(Assessment.class))).thenReturn(mockAssessmentQuery);
        when(mockLongQuery.getResultList()).thenReturn(ImmutableList.of(Long.valueOf(10)));
        when(mockAssessmentQuery.getResultList()).thenReturn(ImmutableList.of(mockAssessment));
        
        PagedResourceList<Assessment> page = supplier.get(); 
        assertEquals(page.getTotal(), Integer.valueOf(10));
        assertEquals(page.getItems(), ImmutableList.of(mockAssessment));
        
        verify(mockLongQuery).setParameter(APP_ID, APP_ID_VALUE);
        verify(mockLongQuery).setParameter(IDENTIFIER, ID_VALUE);
        verify(mockAssessmentQuery).setParameter(APP_ID, APP_ID_VALUE);
        verify(mockAssessmentQuery).setParameter(IDENTIFIER, ID_VALUE);
        verify(mockAssessmentQuery).setFirstResult(10);
        verify(mockAssessmentQuery).setMaxResults(20);
    }

    @Test
    public void getAssessmentByGuid() {
        when(mockEntityManager.createQuery(GET_BY_GUID, Assessment.class)).thenReturn(mockAssessmentQuery);
        when(mockAssessmentQuery.getResultList()).thenReturn(ImmutableList.of(mockAssessment));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertEquals(retValue.get(), mockAssessment);
        
        verify(mockAssessmentQuery).setParameter(APP_ID, APP_ID_VALUE);
        verify(mockAssessmentQuery).setParameter(GUID, GUID_VALUE);
    }

    @Test
    public void getAssessmentByIdAndRevision() {
        when(mockEntityManager.createQuery(GET_BY_IDENTIFIER, Assessment.class)).thenReturn(mockAssessmentQuery);
        when(mockAssessmentQuery.getResultList()).thenReturn(ImmutableList.of(mockAssessment));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertEquals(retValue.get(), mockAssessment);
        
        verify(mockAssessmentQuery).setParameter(APP_ID, APP_ID_VALUE);
        verify(mockAssessmentQuery).setParameter(IDENTIFIER, ID_VALUE);
        verify(mockAssessmentQuery).setParameter(REVISION, REV_VALUE);
    }
    
    @Test
    public void createAssessment() throws Exception {
        when(mockSession.merge(mockAssessment)).thenReturn(mockAssessment);
        
        Assessment returnValue = dao.createAssessment(mockAssessment);
        assertEquals(returnValue, mockAssessment);
        
        InOrder inOrder = inOrder(mockSession, mockTransaction, mockEntityManager);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).merge(mockAssessment);
        inOrder.verify(mockTransaction).commit();
    }

    @Test
    public void updateAssessment() throws Exception {
        when(mockSession.merge(mockAssessment)).thenReturn(mockAssessment);
        
        Assessment returnValue = dao.updateAssessment(mockAssessment);
        
        assertEquals(returnValue, mockAssessment);
        
        InOrder inOrder = inOrder(mockSession, mockTransaction, mockEntityManager);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).merge(mockAssessment);
        inOrder.verify(mockTransaction).commit();
    }

    @Test
    public void deleteAssessment() throws Exception {
        dao.deleteAssessment(mockAssessment);
        
        InOrder inOrder = inOrder(mockSession, mockTransaction, mockEntityManager);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).remove(mockAssessment);
        inOrder.verify(mockTransaction).commit();
    }  
    
    @Test
    public void publishAssessment() throws Exception {
        Assessment original = new Assessment();
        Assessment assessmentToPublish = new Assessment();
        
        dao.publishAssessment(original, assessmentToPublish);
        
        InOrder inOrder = inOrder(mockSession, mockTransaction, mockEntityManager);
        inOrder.verify(mockSession).beginTransaction();
        verify(mockSession).saveOrUpdate(assessmentToPublish);
        verify(mockSession).merge(original);
        inOrder.verify(mockTransaction).commit();
    }
    
    @Test(expectedExceptions = RuntimeException.class, 
            expectedExceptionsMessageRegExp = ".*Converted exception.*")
    public void persistenceExceptionConverted() {
        PersistenceException pe = new PersistenceException("Process me");
        doThrow(pe).when(mockSession).remove(any());
        
        RuntimeException re = new RuntimeException("Converted exception");
        when(mockConverter.convert(pe, mockAssessment)).thenReturn(re);
        
        dao.deleteAssessment(mockAssessment);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, 
            expectedExceptionsMessageRegExp = ".*Do not process me.*")
    public void persistenceExceptionWrapped() {
        PersistenceException pe = new PersistenceException("Do not process me");
        doThrow(pe).when(mockSession).remove(any());
        
        when(mockConverter.convert(pe, mockAssessment)).thenReturn(pe);
        
        dao.deleteAssessment(mockAssessment);
    }    
}
