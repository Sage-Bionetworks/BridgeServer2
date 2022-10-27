package org.sagebionetworks.bridge.hibernate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.hibernate.Session;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class HibernateDemographicDaoTest {
    private static final String DEMOGRAPHIC_USER_ID = "test-demographic-user-id";
    private static final String DEMOGRAPHIC_ID = "test-demographic-id";

    @Mock
    HibernateHelper hibernateHelper;

    @Spy
    @InjectMocks
    HibernateDemographicDao hibernateDemographicDao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Tests whether a new DemographicUser is saved correctly.
     */
    @Test
    public void saveDemographicUserNew() {
        DemographicUser demographicUser = new DemographicUser();
        doReturn(Optional.empty()).when(hibernateDemographicDao).getDemographicUser(any(), any(), any());
        when(hibernateHelper.saveOrUpdate(demographicUser)).thenReturn(demographicUser);

        DemographicUser returnedDemographicUser = hibernateDemographicDao.saveDemographicUser(demographicUser,
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).saveOrUpdate(demographicUser);
        assertSame(returnedDemographicUser, demographicUser);
    }

    /**
     * Tests whether a DemographicUser overwrites a saved DemographicUser correctly.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void saveDemographicUserOverwrite() {
        Session session = mock(Session.class);
        DemographicUser demographicUser = new DemographicUser();
        DemographicUser existingDemographicUser = new DemographicUser();
        doReturn(Optional.of(existingDemographicUser)).when(hibernateDemographicDao).getDemographicUser(any(), any(),
                any());
        when(hibernateHelper.executeWithExceptionHandling(any(), any())).thenAnswer(invocation -> {
            ((Function<Session, DemographicUser>) invocation.getArgument(1)).apply(session);
            return demographicUser;
        });

        DemographicUser returnedDemographicUser = hibernateDemographicDao.saveDemographicUser(demographicUser,
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).executeWithExceptionHandling(eq(null), any());
        verify(session).delete(existingDemographicUser);
        verify(session).flush();
        verify(session).saveOrUpdate(demographicUser);
        assertSame(returnedDemographicUser, demographicUser);
    }

    /**
     * Tests whether a Demographic is deleted correctly.
     */
    @Test
    public void deleteDemographic() {
        hibernateDemographicDao.deleteDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).deleteById(Demographic.class, DEMOGRAPHIC_ID);
    }

    /**
     * Tests whether a DemographicUser is deleted correctly.
     */
    @Test
    public void deleteDemographicUser() {
        hibernateDemographicDao.deleteDemographicUser(DEMOGRAPHIC_USER_ID);

        verify(hibernateHelper).deleteById(DemographicUser.class, DEMOGRAPHIC_USER_ID);
    }

    /**
     * Tests whether a DemographicUser's id is fetched correctly.
     */
    @Test
    public void getDemographicUserId() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(String.class)))
                .thenReturn(Optional.of(DEMOGRAPHIC_USER_ID));

        Optional<String> returnedDemographicUserId = hibernateDemographicDao.getDemographicUserId(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "SELECT du.id FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                String.class);
        assertEquals(returnedDemographicUserId.get(), DEMOGRAPHIC_USER_ID);
    }

    /**
     * Tests whether a DemographicUser's id is fetched correctly with null studyId.
     */
    @Test
    public void getDemographicUserIdApp() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(String.class)))
                .thenReturn(Optional.of(DEMOGRAPHIC_USER_ID));

        Optional<String> returnedDemographicUserId = hibernateDemographicDao.getDemographicUserId(TEST_APP_ID,
                null, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "SELECT du.id FROM DemographicUser du WHERE du.appId = :appId AND du.studyId IS NULL AND du.userId = :userId",
                ImmutableMap.of("userId", TEST_USER_ID, "appId", TEST_APP_ID),
                String.class);
        assertEquals(returnedDemographicUserId.get(), DEMOGRAPHIC_USER_ID);
    }

    /**
     * Tests whether empty is returned when a fetching a DemographicUser's id but it
     * does not exist.
     */
    @Test
    public void getDemographicUserIdEmpty() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(String.class))).thenReturn(Optional.empty());

        Optional<String> returnedDemographicUserId = hibernateDemographicDao.getDemographicUserId(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "SELECT du.id FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                String.class);
        assertTrue(!returnedDemographicUserId.isPresent());
    }

    /**
     * Tests whether a Demographic is fetched correctly.
     */
    @Test
    public void getDemographic() {
        Demographic demographic = new Demographic();
        when(hibernateHelper.queryGetOne(any(), any(), eq(Demographic.class)))
                .thenReturn(Optional.of(demographic));

        Optional<Demographic> returnedDemographic = hibernateDemographicDao.getDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).queryGetOne("FROM Demographic d WHERE d.id = :demographicId",
                ImmutableMap.of("demographicId", DEMOGRAPHIC_ID),
                Demographic.class);
        assertSame(returnedDemographic.get(), demographic);
    }

    /**
     * Tests whether empty is returned when fetching a Demographic but it does not
     * exist.
     */
    @Test
    public void getDemographicEmpty() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(Demographic.class))).thenReturn(Optional.empty());

        Optional<Demographic> returnedDemographic = hibernateDemographicDao.getDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).queryGetOne("FROM Demographic d WHERE d.id = :demographicId",
                ImmutableMap.of("demographicId", DEMOGRAPHIC_ID),
                Demographic.class);
        assertTrue(!returnedDemographic.isPresent());
    }

    /**
     * Tests whether a DemographicUser is fetched correctly.
     */
    @Test
    public void getDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        when(hibernateHelper.queryGetOne(any(), any(), eq(DemographicUser.class)))
                .thenReturn(Optional.of(demographicUser));

        Optional<DemographicUser> returnedDemographicUser = hibernateDemographicDao.getDemographicUser(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                DemographicUser.class);
        assertSame(returnedDemographicUser.get(), demographicUser);
    }

    /**
     * Tests whether a DemographicUser is fetched correctly with null studyId.
     */
    @Test
    public void getDemographicUserApp() {
        DemographicUser demographicUser = new DemographicUser();
        when(hibernateHelper.queryGetOne(any(), any(), eq(DemographicUser.class)))
                .thenReturn(Optional.of(demographicUser));

        Optional<DemographicUser> returnedDemographicUser = hibernateDemographicDao.getDemographicUser(
                TEST_APP_ID, null, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId IS NULL AND du.userId = :userId",
                ImmutableMap.of("userId", TEST_USER_ID, "appId", TEST_APP_ID),
                DemographicUser.class);
        assertSame(returnedDemographicUser.get(), demographicUser);
    }

    /**
     * Tests whether empty is returned when fetching a DemographicUser but it does
     * not exist.
     */
    @Test
    public void getDemographicUserNull() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(DemographicUser.class))).thenReturn(Optional.empty());

        Optional<DemographicUser> returnedDemographicUser = hibernateDemographicDao.getDemographicUser(
                TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                DemographicUser.class);
        assertTrue(!returnedDemographicUser.isPresent());
    }

    /**
     * Tests whether multiple DemographicUsers are fetched correctly.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void getDemographicUsers() {
        DemographicUser demographicUser1 = new DemographicUser();
        DemographicUser demographicUser2 = new DemographicUser();
        List<DemographicUser> demographicUsers = ImmutableList.of(demographicUser1, demographicUser2);
        when(hibernateHelper.queryCount(any(), any())).thenReturn(2);
        when(hibernateHelper.queryGet(any(), any(), any(), any(), eq(DemographicUser.class)))
                .thenReturn(demographicUsers);

        PagedResourceList<DemographicUser> returnedDemographicUsersResourceList = hibernateDemographicDao
                .getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 5);

        String query = "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId";
        String countQuery = "SELECT COUNT(*) " + query;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("studyId", TEST_STUDY_ID);
        parameters.put("appId", TEST_APP_ID);
        verify(hibernateHelper).queryCount(countQuery, parameters);
        verify(hibernateHelper).queryGet(query, parameters, 0, 5, DemographicUser.class);
        assertSame(returnedDemographicUsersResourceList.getItems(), demographicUsers);
        assertEquals(returnedDemographicUsersResourceList.getRequestParams(),
                ImmutableMap.of("pageSize", 5, "offsetBy", 0, "type", "RequestParams"));
        // ensure suppressDeprecated is enabled by ensuring null return from deprecated
        // methods
        assertNull(returnedDemographicUsersResourceList.getPageSize());
        assertNull(returnedDemographicUsersResourceList.getOffsetBy());
    }

    /**
     * Tests whether multiple DemographicUsers are fetched correctly with null
     * studyId.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void getDemographicUsersApp() {
        DemographicUser demographicUser1 = new DemographicUser();
        DemographicUser demographicUser2 = new DemographicUser();
        List<DemographicUser> demographicUsers = ImmutableList.of(demographicUser1, demographicUser2);
        when(hibernateHelper.queryCount(any(), any())).thenReturn(2);
        when(hibernateHelper.queryGet(any(), any(), any(), any(), eq(DemographicUser.class)))
                .thenReturn(demographicUsers);

        PagedResourceList<DemographicUser> returnedDemographicUsersResourceList = hibernateDemographicDao
                .getDemographicUsers(TEST_APP_ID, null, 0, 5);

        String query = "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId IS NULL";
        String countQuery = "SELECT COUNT(*) " + query;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("appId", TEST_APP_ID);
        verify(hibernateHelper).queryCount(countQuery, parameters);
        verify(hibernateHelper).queryGet(query, parameters, 0, 5, DemographicUser.class);
        assertSame(returnedDemographicUsersResourceList.getItems(), demographicUsers);
        assertEquals(returnedDemographicUsersResourceList.getRequestParams(),
                ImmutableMap.of("pageSize", 5, "offsetBy", 0, "type", "RequestParams"));
        // ensure suppressDeprecated is enabled by ensuring null return from deprecated
        // methods
        assertNull(returnedDemographicUsersResourceList.getPageSize());
        assertNull(returnedDemographicUsersResourceList.getOffsetBy());
    }
}
