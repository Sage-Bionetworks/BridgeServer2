package org.sagebionetworks.bridge.hibernate;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.NonUniqueResultException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;

@SuppressWarnings("unchecked")
public class HibernateHelperTest {
    private static final String QUERY = "from DummyTable";
    private static final Map<String, Object> PARAMETERS = new ImmutableMap.Builder<String, Object>().put("id", 10L)
            .put("appId", TEST_APP_ID).build();
    private static final RuntimeException TEST_EXCEPTION = new RuntimeException();

    private HibernateHelper helper;
    @Mock
    private Session mockSession;
    @Mock
    private SessionFactory mockSessionFactory;
    @Mock
    private PersistenceExceptionConverter mockExceptionConverter;
    @Mock
    private Transaction mockTransaction;
    
    @BeforeMethod
    public void setup(Method method) {
        MockitoAnnotations.initMocks(this);
        // Spy Hibernate helper. This allows us to mock execute() and test it
        // independently later.
        helper = spy(new HibernateHelper(mockSessionFactory, mockExceptionConverter));
        if (!method.getName().toLowerCase().contains("nosetup")) {
            doAnswer(invocation -> {
                Function<Session, ?> function = invocation.getArgument(0);
                return function.apply(mockSession);
            }).when(helper).execute(any());
        }
    }

    @Test
    public void createSuccess() {
        Object testObj = new Object();
        helper.create(testObj);
        verify(mockSession).save(testObj);
    }
    
    @Test
    public void createCallsConsumer() { 
        reset(helper); // clear @Before setup
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        
        Account testObj = Account.create();
        
        helper.create(testObj);
        
        InOrder inOrder = Mockito.inOrder(mockSession, mockTransaction);
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockSession).save(testObj);
        inOrder.verify(mockTransaction).commit();
    }
    
    @Test
    public void createOtherException() {
        PersistenceException ex = new PersistenceException();
        when(mockSession.save(any())).thenThrow(ex);
        Object testObj = new Object();
        
        when(mockExceptionConverter.convert(ex, testObj)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.create(testObj);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, testObj);
    }

    @Test
    public void delete() {
        // set up
        Object hibernateOutput = new Object();
        when(mockSession.get(Object.class, "test-id")).thenReturn(hibernateOutput);

        // execute and validate
        helper.deleteById(Object.class, "test-id");
        verify(mockSession).delete(hibernateOutput);
    }
    
    @Test
    public void deleteThrowingException() {
        PersistenceException ex = new PersistenceException();
        // set up
        Object hibernateOutput = new Object();
        when(mockSession.get(Object.class, "test-id")).thenReturn(hibernateOutput);
        doThrow(ex).when(mockSession).delete(any());
        when(mockExceptionConverter.convert(any(), any())).thenReturn(ex);

        // execute and validate
        try {
            helper.deleteById(Object.class, "test-id");
            fail("Should have thrown exception.");
        } catch(BridgeServiceException e) {}
        verify(mockExceptionConverter).convert(ex, hibernateOutput);
    }

    @Test
    public void getById() {
        // set up
        Object hibernateOutput = new Object();
        when(mockSession.get(Object.class, "test-id")).thenReturn(hibernateOutput);

        // execute and validate
        Object helperOutput = helper.getById(Object.class, "test-id");
        assertSame(helperOutput, hibernateOutput);
    }

    @Test
    public void queryCountSuccess() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(42L);

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, null);
        assertEquals(count, 42);
    }

    @Test
    public void queryCountNull() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(null);

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, null);
        assertEquals(count, 0);
    }
    
    @Test
    public void queryCountWithParameters() {
        // mock query
        Query<Long> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(42L);

        when(mockSession.createQuery(QUERY, Long.class)).thenReturn(mockQuery);

        // execute and validate
        int count = helper.queryCount(QUERY, PARAMETERS);
        assertEquals(count, 42);
        
        verify(mockQuery).setParameter("appId", TEST_APP_ID);
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test
    public void queryGetSuccess() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.queryGet(QUERY, null, null, null, Object.class);
        assertSame(helperOutputList, hibernateOutputList);
    }

    @Test
    public void queryGetOffsetAndLimit() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(ImmutableList.of());

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and verify we pass through the offset and limit
        helper.queryGet(QUERY, null, 100, 25, Object.class);
        verify(mockQuery).setFirstResult(100);
        verify(mockQuery).setMaxResults(25);
    }
    
    @Test
    public void queryGetWithParameters() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.queryGet(QUERY, PARAMETERS, null, null, Object.class);
        assertSame(helperOutputList, hibernateOutputList);
        
        verify(mockQuery).setParameter("appId", TEST_APP_ID);
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test
    public void queryGetOne() {
        // mock query
        Object hibernateOutput = new Object();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResultOptional()).thenReturn(Optional.of(hibernateOutput));

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        Optional<Object> helperOutput = helper.queryGetOne(QUERY, null, Object.class);
        assertSame(helperOutput.get(), hibernateOutput);
    }

    @Test
    public void queryGetOneWithParameters() {
        // mock query
        Object hibernateOutput = new Object();
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResultOptional()).thenReturn(Optional.of(hibernateOutput));

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        Optional<Object> helperOutput = helper.queryGetOne(QUERY, PARAMETERS, Object.class);
        assertSame(helperOutput.get(), hibernateOutput);

        verify(mockQuery).setParameter("appId", TEST_APP_ID);
        verify(mockQuery).setParameter("id", 10L);
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void queryGetOneNonUnique() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResultOptional()).thenThrow(new NonUniqueResultException(2));
        when(mockExceptionConverter.convert(any(), any())).thenAnswer((invocation) -> invocation.getArgument(0));

        when(mockSession.createQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute
        helper.queryGetOne(QUERY, PARAMETERS, Object.class);
    }

    @Test
    public void queryUpdate() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.executeUpdate()).thenReturn(7);

        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);

        // execute and validate
        int numRows = helper.queryUpdate(QUERY, null);
        assertEquals(numRows, 7);
    }
    
    @Test
    public void queryUpdateWithParameters() {
        // mock query
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.executeUpdate()).thenReturn(7);

        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);

        // execute and validate
        int numRows = helper.queryUpdate(QUERY, PARAMETERS);
        assertEquals(numRows, 7);
        
        verify(mockQuery).setParameter("appId", TEST_APP_ID);
        verify(mockQuery).setParameter("id", 10L);
    }
    
    @Test
    public void query() {
        Query<Object> mockQuery = mock(Query.class);
        when(mockSession.createQuery(QUERY)).thenReturn(mockQuery);
        
        helper.query(QUERY, PARAMETERS);
        
        verify(mockSession).createQuery(QUERY);
        verify(mockQuery).setParameter("id", 10L);
        verify(mockQuery).setParameter("appId", TEST_APP_ID);
        verify(mockQuery).executeUpdate();
    }
    
    @Test
    public void saveOrUpdate() {
        Object testObj = new Object();
        Object received = helper.saveOrUpdate(testObj);
        assertSame(received, testObj);
        verify(mockSession).saveOrUpdate(testObj);
    }

    @Test
    public void update() {
        Object testObj = new Object();
        Object received = helper.update(testObj);
        assertSame(received, testObj);
        verify(mockSession).update(testObj);
    }

    @Test
    public void execute() {
        // mock session to produce transaction
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);

        // mock session factory
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        when(mockSessionFactory.openSession()).thenReturn(mockSession);

        helper = new HibernateHelper(mockSessionFactory, mockExceptionConverter);

        // mock function, so we can verify that it was called, and with the session we expect.
        Object functionOutput = new Object();
        Function<Session, Object> mockFunction = mock(Function.class);
        when(mockFunction.apply(any())).thenReturn(functionOutput);

        // We need to verify mocks in order.
        InOrder inOrder = inOrder(mockSessionFactory, mockSession, mockTransaction, mockFunction);

        // execute and validate
        Object helperOutput = helper.executeWithExceptionHandling(null, mockFunction);
        assertSame(helperOutput, functionOutput);

        inOrder.verify(mockSessionFactory).openSession();
        inOrder.verify(mockSession).beginTransaction();
        inOrder.verify(mockFunction).apply(mockSession);
        inOrder.verify(mockTransaction).commit();
        inOrder.verify(mockSession).close();
    }
    
    // These methods verify that the helper is using the exception converter. The exact behavior of the
    // converter is tested separately.
    
    @Test
    public void createConvertsExceptions() throws Exception {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, account)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.create(account);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, account);
    }

    @Test
    public void deleteByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.deleteById(HibernateAccount.class, "whatever");
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void getByIdConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.getById(HibernateAccount.class, "whatever");
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryCountConvertsExceptions() { 
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryCount("query string", ImmutableMap.of());
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryGetConvertsExceptions() {
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        OptimisticLockException ex = new OptimisticLockException();
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryGet("query string", ImmutableMap.of(), 0, 20, HibernateAccount.class);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void queryUpdateConvertsExceptions() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry 'studyTest-email@email.com' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        when(mockExceptionConverter.convert(ex, null)).thenReturn(TEST_EXCEPTION);
        
        try {
            helper.queryUpdate("query string", ImmutableMap.of());
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, null);
    }
    
    @Test
    public void updateConvertsExceptions() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry 'studyTest-email@email.com' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        
        
        reset(helper); // clear the doAnswer set up in @before 
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        doThrow(ex).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);

        when(mockExceptionConverter.convert(ex, account)).thenReturn(TEST_EXCEPTION);

        try {
            helper.update(account);
            fail("Should have thrown exception");
        } catch(Exception e) {
            assertSame(e, TEST_EXCEPTION);
        }
        verify(mockExceptionConverter).convert(ex, account);
    }
    
    @Test
    public void returnsUnconvertedExceptionAsServiceException() throws Exception {
        reset(helper); // clear the doAnswer set up in @before
        
        // this isn't exactly when the exception is thrown, but it's close enough to simulate
        PersistenceException pe = new PersistenceException(TEST_EXCEPTION);
        doThrow(pe).when(mockSessionFactory).openSession();
        
        HibernateAccount account = new HibernateAccount();
        account.setAppId(TEST_APP_ID);
        
        // This does not convert the exception, it hands it back.
        when(mockExceptionConverter.convert(pe, account)).thenReturn(pe);
        
        try {
            helper.update(account);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            // So we wrap it with a BridgeServiceException
            assertSame(e.getCause(), pe);
        }
    }
 
    @SuppressWarnings("rawtypes")
    @Test
    public void nativeQueryCountSuccess() {
        Object object = new Object();
        NativeQuery mockQuery = mock(NativeQuery.class);
        Map<String,Object> params = ImmutableMap.of("param", object);
        
        when(mockSession.createNativeQuery(QUERY)).thenReturn(mockQuery);
        when(mockQuery.uniqueResult()).thenReturn(BigInteger.valueOf(50L));
        
        int retValue = helper.nativeQueryCount(QUERY, params);
        assertEquals(retValue, 50);
        
        verify(mockQuery).setParameter("param", object);        
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void nativeQueryCountNoParams() {
        NativeQuery mockQuery = mock(NativeQuery.class);
        
        when(mockSession.createNativeQuery(QUERY)).thenReturn(mockQuery);
        when(mockQuery.uniqueResult()).thenReturn(BigInteger.valueOf(50L));
        
        int retValue = helper.nativeQueryCount(QUERY, ImmutableMap.of());
        assertEquals(retValue, 50);
        
        verify(mockQuery, never()).setParameter(Mockito.anyString(), any());        
    }

    
    @SuppressWarnings("rawtypes")
    @Test
    public void nativeQueryCountFailure() {
        NativeQuery mockQuery = mock(NativeQuery.class);
        
        when(mockSession.createNativeQuery(QUERY)).thenReturn(mockQuery);
        
        int retValue = helper.nativeQueryCount(QUERY, null);
        assertEquals(retValue, 0);
    }    
    
    @Test
    public void nativeQueryGetSuccess() {
        // mock query
        List<Object> hibernateOutputList = ImmutableList.of();
        NativeQuery<Object> mockQuery = mock(NativeQuery.class);
        when(mockQuery.list()).thenReturn(hibernateOutputList);

        when(mockSession.createNativeQuery(QUERY, Object.class)).thenReturn(mockQuery);

        // execute and validate
        List<Object> helperOutputList = helper.nativeQueryGet(QUERY, null, null, null, Object.class);
        assertSame(helperOutputList, hibernateOutputList);
        
        verify(mockQuery, never()).setFirstResult(Mockito.anyInt());
        verify(mockQuery, never()).setMaxResults(Mockito.anyInt());
        verify(mockQuery, never()).setParameter(Mockito.anyString(), any());
    }

    @Test
    public void nativeQueryGetSetsParameters() {
        NativeQuery<Object> mockQuery = mock(NativeQuery.class);
        when(mockSession.createNativeQuery(QUERY, Object.class)).thenReturn(mockQuery);
        
        Object object = new Object();
        Map<String, Object> params = ImmutableMap.of("param", object);

        // execute and validate
        helper.nativeQueryGet(QUERY, params, null, null, Object.class);
        
        verify(mockQuery).setParameter("param", object);
    }
    
    @Test
    public void nativeQueryGetSetsOffsetAndLimit() {
        // mock query
        NativeQuery<Object> mockQuery = mock(NativeQuery.class);
        when(mockSession.createNativeQuery(QUERY, Object.class)).thenReturn(mockQuery);
        
        // execute and validate
        helper.nativeQueryGet(QUERY, ImmutableMap.of(), 100, 25, Object.class);
        
        verify(mockQuery).setFirstResult(100);
        verify(mockQuery).setMaxResults(25);
    }
    
    @Test
    public void nativeQueryUpdate() {
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        
        NativeQuery<Object> mockQuery = mock(NativeQuery.class);
        when(mockSession.createNativeQuery(QUERY)).thenReturn(mockQuery);
        when(mockQuery.executeUpdate()).thenReturn(3);
        
        int retValue = helper.nativeQueryUpdate(QUERY, ImmutableMap.of("a", "b", "c", "d"));
        assertEquals(retValue, 3);
        
        verify(mockQuery).setParameter("a", "b");
        verify(mockQuery).setParameter("c", "d");
        verify(mockQuery).executeUpdate();
    }
    
    @Test
    public void nativeQuery() {
        List<Object> resultset = new ArrayList<>();
        resultset.add(new Object[8]);
        resultset.add(new Object[8]);
        
        NativeQuery<Object> mockQuery = mock(NativeQuery.class);
        when(mockSession.createNativeQuery(QUERY)).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(resultset);
        
        List<Object[]> retValue = helper.nativeQuery(QUERY, ImmutableMap.of("a", "b", "c", "d"));
        assertSame(retValue, resultset);
        
        verify(mockQuery).setParameter("a", "b");
        verify(mockQuery).setParameter("c", "d");
        verify(mockQuery).getResultList();
    }

    @Test
    public void executeRollbackNoSetup() {
        when(mockSession.beginTransaction()).thenReturn(mockTransaction);
        when(mockSessionFactory.openSession()).thenReturn(mockSession);

        try {
            helper.execute((session) -> {
                throw new PersistenceException();
            });
            fail("should have thrown an exception");
        } catch (PersistenceException e) {
        }

        verify(mockTransaction).rollback();
    }
}
