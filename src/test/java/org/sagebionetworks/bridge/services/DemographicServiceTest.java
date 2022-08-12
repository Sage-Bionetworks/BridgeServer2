package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class DemographicServiceTest {
    private static final String DEMOGRAPHIC_ID = "test-demographic-id";
    private static final String DEMOGRAPHIC_USER_ID = "test-demographic-user-id";

    @Mock
    DemographicDao demographicDao;

    @InjectMocks
    @Spy
    DemographicService demographicService;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        doReturn("0", IntStream.range(1, 1000).mapToObj(Integer::toString).toArray()).when(demographicService)
                .generateGuid();
    }

    @Test
    public void saveDemographicUserNew() {
        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID,
                TEST_USER_ID,
                new HashMap<>());
        demographicUser.getDemographics().put("category-name1",
                new Demographic(null, demographicUser, "category-name1", true, ImmutableList.of(),
                        null));
        demographicUser.getDemographics().put("category-name2",
                new Demographic(null, demographicUser, "category-name2", true, ImmutableList.of(),
                        null));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any())).thenAnswer((invocation) -> invocation.getArgument(0));

        DemographicUser returnedDemographicUser = demographicService.saveDemographicUser(demographicUser);

        verify(demographicDao).saveDemographicUser(demographicUser, TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(returnedDemographicUser.getId(), "0");
        Iterator<Demographic> iter = returnedDemographicUser.getDemographics().values().iterator();
        for (int i = 1; iter.hasNext(); i++) {
            assertEquals(Integer.toString(i), iter.next().getId());
        }
        assertSame(returnedDemographicUser, demographicUser);
    }

    @Test
    public void saveDemographicUserOverwrite() {
        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID,
                TEST_USER_ID,
                new HashMap<>());
        demographicUser.getDemographics().put("category-name1",
                new Demographic(null, demographicUser, "category-name1", true, ImmutableList.of(),
                        null));
        demographicUser.getDemographics().put("category-name2",
                new Demographic(null, demographicUser, "category-name2", true, ImmutableList.of(),
                        null));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any())).thenAnswer((invocation) -> invocation.getArgument(0));

        DemographicUser returnedDemographicUser = demographicService.saveDemographicUser(demographicUser);

        verify(demographicDao).saveDemographicUser(demographicUser, TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(returnedDemographicUser.getId(), "0");
        Iterator<Demographic> iter = returnedDemographicUser.getDemographics().values().iterator();
        for (int i = 1; iter.hasNext(); i++) {
            assertEquals(Integer.toString(i), iter.next().getId());
        }
        assertSame(returnedDemographicUser, demographicUser);
    }

    /**
     * Tests that having null demographics will not cause a NullPointerException and
     * also that validation is occurring, which will catch the null demographics
     */
    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserNullDemographics() {
        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID,
                TEST_USER_ID,
                null);
        when(demographicDao.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        demographicService.saveDemographicUser(demographicUser);
    }

    @Test
    public void deleteDemographic() {
        DemographicUser demographicUser = new DemographicUser();
        demographicUser.setUserId(TEST_USER_ID);
        Demographic demographic = new Demographic();
        demographic.setDemographicUser(demographicUser);
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.of(demographic));

        demographicService.deleteDemographic(TEST_USER_ID, DEMOGRAPHIC_ID);

        verify(demographicDao).deleteDemographic(DEMOGRAPHIC_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicNotFound() {
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.empty());

        demographicService.deleteDemographic(TEST_USER_ID, DEMOGRAPHIC_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicNotOwnedByUser() {
        DemographicUser demographicUser = new DemographicUser();
        demographicUser.setUserId(TEST_USER_ID);
        Demographic demographic = new Demographic();
        demographic.setDemographicUser(demographicUser);
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.of(demographic));

        demographicService.deleteDemographic("wrong user id", DEMOGRAPHIC_ID);
    }

    @Test
    public void deleteDemographicUser() {
        when(demographicDao.getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.of(DEMOGRAPHIC_USER_ID));

        demographicService.deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        verify(demographicDao).getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicDao).deleteDemographicUser(DEMOGRAPHIC_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicUserNotFound() {
        when(demographicDao.getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        demographicService.deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void getDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        when(demographicDao.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.of(demographicUser));

        DemographicUser returnedDemographicUser = demographicService.getDemographicUser(TEST_APP_ID,
                TEST_STUDY_ID,
                TEST_USER_ID);

        verify(demographicDao).getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertSame(returnedDemographicUser, demographicUser);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getDemographicUserNotFound() {
        when(demographicDao.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        demographicService.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void getDemographicUsers() {
        DemographicUser demographicUser1 = new DemographicUser();
        DemographicUser demographicUser2 = new DemographicUser();
        List<DemographicUser> demographicUsers = ImmutableList.of(demographicUser1, demographicUser2);
        PagedResourceList<DemographicUser> demographicUsersResourceList = new PagedResourceList<>(demographicUsers, 2,
                false);
        when(demographicDao.getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 5))
                .thenReturn(demographicUsersResourceList);

        PagedResourceList<DemographicUser> returnedDemographicUsersResourceList = demographicService
                .getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 5);

        verify(demographicDao).getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, 5);
        assertSame(returnedDemographicUsersResourceList, demographicUsersResourceList);
    }

    @Test
    public void getDemographicUsersBadPageSize() {
        try {
            demographicService.getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, API_MINIMUM_PAGE_SIZE - 1);
            fail("should have thrown an exception");
        } catch (BadRequestException e) {
        }

        try {
            demographicService.getDemographicUsers(TEST_APP_ID, TEST_STUDY_ID, 0, API_MAXIMUM_PAGE_SIZE + 1);
            fail("should have thrown an exception");
        } catch (BadRequestException e) {
        }
    }
}
