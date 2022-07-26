package org.sagebionetworks.bridge.hibernate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class HibernateDemographicDaoTest {
    private static final String DEMOGRAPHIC_USER_ID = "test-demographic-user-id";
    private static final String DEMOGRAPHIC_ID = "test-demographic-id";

    @Mock
    HibernateHelper hibernateHelper;

    @InjectMocks
    HibernateDemographicDao hibernateDemographicDao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void saveDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        when(hibernateHelper.saveOrUpdate(demographicUser)).thenReturn(demographicUser);

        DemographicUser returnedDemographicUser = hibernateDemographicDao.saveDemographicUser(demographicUser);

        verify(hibernateHelper).saveOrUpdate(demographicUser);
        assertSame(returnedDemographicUser, demographicUser);
    }

    @Test
    public void deleteDemographic() {
        hibernateDemographicDao.deleteDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).deleteById(Demographic.class, DEMOGRAPHIC_ID);
    }

    @Test
    public void deleteDemographicUser() {
        hibernateDemographicDao.deleteDemographicUser(DEMOGRAPHIC_USER_ID);

        verify(hibernateHelper).deleteById(DemographicUser.class, DEMOGRAPHIC_USER_ID);
    }

    @Test
    public void getDemographicUserId() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(String.class))).thenReturn(DEMOGRAPHIC_USER_ID);

        Optional<String> returnedDemographicUserId = hibernateDemographicDao.getDemographicUserId(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "SELECT du.id FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID), String.class);
        assertEquals(returnedDemographicUserId.get(), DEMOGRAPHIC_USER_ID);
    }

    @Test
    public void getDemographicUserIdNull() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(String.class))).thenReturn(null);

        Optional<String> returnedDemographicUserId = hibernateDemographicDao.getDemographicUserId(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "SELECT du.id FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID), String.class);
        assertTrue(!returnedDemographicUserId.isPresent());
    }

    @Test
    public void getDemographic() {
        Demographic demographic = new Demographic();
        when(hibernateHelper.queryGetOne(any(), any(), eq(Demographic.class))).thenReturn(demographic);

        Optional<Demographic> returnedDemographic = hibernateDemographicDao.getDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).queryGetOne("FROM Demographic d WHERE d.id = :demographicId",
                ImmutableMap.of("demographicId", DEMOGRAPHIC_ID),
                Demographic.class);
        assertSame(returnedDemographic.get(), demographic);
    }

    @Test
    public void getDemographicNull() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(Demographic.class))).thenReturn(null);

        Optional<Demographic> returnedDemographic = hibernateDemographicDao.getDemographic(DEMOGRAPHIC_ID);

        verify(hibernateHelper).queryGetOne("FROM Demographic d WHERE d.id = :demographicId",
                ImmutableMap.of("demographicId", DEMOGRAPHIC_ID),
                Demographic.class);
        assertTrue(!returnedDemographic.isPresent());
    }

    @Test
    public void getDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        when(hibernateHelper.queryGetOne(any(), any(), eq(DemographicUser.class))).thenReturn(demographicUser);

        Optional<DemographicUser> returnedDemographicUser = hibernateDemographicDao.getDemographicUser(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                DemographicUser.class);
        assertSame(returnedDemographicUser.get(), demographicUser);
    }

    @Test
    public void getDemographicUserNull() {
        when(hibernateHelper.queryGetOne(any(), any(), eq(DemographicUser.class))).thenReturn(null);

        Optional<DemographicUser> returnedDemographicUser = hibernateDemographicDao.getDemographicUser(TEST_APP_ID,
                TEST_STUDY_ID, TEST_USER_ID);

        verify(hibernateHelper).queryGetOne(
                "FROM DemographicUser du WHERE du.appId = :appId AND du.studyId = :studyId AND du.userId = :userId",
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID, "appId", TEST_APP_ID),
                DemographicUser.class);
        assertTrue(!returnedDemographicUser.isPresent());
    }

    @Test
    public void getDemographicUsers() {
        DemographicUser demographicUser1 = new DemographicUser();
        DemographicUser demographicUser2 = new DemographicUser();
        List<DemographicUser> demographicUsers = new ArrayList<>();
        demographicUsers.add(demographicUser1);
        demographicUsers.add(demographicUser2);
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
}
