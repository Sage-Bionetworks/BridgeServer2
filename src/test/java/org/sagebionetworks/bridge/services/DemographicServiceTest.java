package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
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
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DemographicServiceTest {
    private static final String DEMOGRAPHIC_ID = "test-demographic-id";
    private static final String DEMOGRAPHIC_USER_ID = "test-demographic-user-id";
    private static final String DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX = "bridge-validation-demographics-values-";

    @Mock
    DemographicDao demographicDao;

    @Mock
    ParticipantVersionService participantVersionService;

    @Mock
    AppConfigElementService appConfigElementService;

    @InjectMocks
    @Spy
    DemographicService demographicService;

    @Mock
    Account account;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        doReturn("0", IntStream.range(1, 1000).mapToObj(Integer::toString).toArray()).when(demographicService)
                .generateGuid();
    }

    /**
     * Tests saving a DemographicUser at the study level.
     */
    @Test
    public void saveDemographicUserStudy() {
        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID,
                TEST_USER_ID,
                new HashMap<>());
        demographicUser.getDemographics().put("category-name1",
                new Demographic(null, demographicUser, "category-name1", true, ImmutableList.of(),
                        null));
        demographicUser.getDemographics().put("category-name2",
                new Demographic(null, demographicUser, "category-name2", true, ImmutableList.of(),
                        null));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        // execute
        DemographicUser returnedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicDao).saveDemographicUser(demographicUser, TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(returnedDemographicUser.getId(), "0");
        Iterator<Demographic> iter = returnedDemographicUser.getDemographics().values().iterator();
        for (int i = 1; iter.hasNext(); i++) {
            Demographic next = iter.next();
            assertEquals(Integer.toString(i), next.getId());
            assertSame(next.getDemographicUser(), demographicUser);
        }
        assertSame(returnedDemographicUser, demographicUser);
        verify(participantVersionService).createParticipantVersionFromAccount(account);
        verifyZeroInteractions(appConfigElementService);
    }

    /**
     * Tests saving a DemographicUser at the app level.
     */
    @Test
    public void saveDemographicUserApp() {
        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID,
                new HashMap<>());
        demographicUser.getDemographics().put("category-name1",
                new Demographic(null, demographicUser, "category-name1", true, ImmutableList.of(),
                        null));
        demographicUser.getDemographics().put("category-name2",
                new Demographic(null, demographicUser, "category-name2", true, ImmutableList.of(),
                        null));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        // execute
        DemographicUser returnedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicDao).saveDemographicUser(demographicUser, TEST_APP_ID, null, TEST_USER_ID);
        assertEquals(returnedDemographicUser.getId(), "0");
        Iterator<Demographic> iter = returnedDemographicUser.getDemographics().values().iterator();
        for (int i = 1; iter.hasNext(); i++) {
            Demographic next = iter.next();
            assertEquals(Integer.toString(i), next.getId());
            assertSame(next.getDemographicUser(), demographicUser);
        }
        assertSame(returnedDemographicUser, demographicUser);
        verify(participantVersionService).createParticipantVersionFromAccount(account);
        verify(appConfigElementService).getMostRecentElements(TEST_APP_ID, false);
    }

    @Test
    public void saveDemographicUserAppValidation_noDemographics() {
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(AppConfigElement.create()));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, new HashMap<>());

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(appConfigElementService).getMostRecentElements(TEST_APP_ID, false);
    }

    @Test
    public void saveDemographicUserAppValidation_wrongValidationKey() {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "foo");
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "bar", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("bar", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(appConfigElementService).getMostRecentElements(TEST_APP_ID, false);
        // nothing else should happen because the app config element key does nkot match
        // the category name of the demographic
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_configurationJsonErrorIOException()
            throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        element.setData(BridgeObjectMapper.get().createArrayNode());
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_configurationJsonErrorIllegalArgumentException() {
        AppConfigElement element = mock(AppConfigElement.class);
        when(element.getId()).thenReturn(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        when(element.getData()).thenThrow(new IllegalArgumentException());
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_nullConfiguration() {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        element.setData(BridgeObjectMapper.get().nullNode());
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_blankType() throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        JsonNode config = BridgeObjectMapper.get().readValue("{" +
                "    \"validationType\": \"\"," +
                "    \"validationRules\": {" +
                "        \"en\": [" +
                "            \"foo\"," +
                "            \"bar\"" +
                "        ]" +
                "    }" +
                "}", JsonNode.class);
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_invalidConfigurationNullType()
            throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        JsonNode config = BridgeObjectMapper.get().readValue("{" +
                "    \"validationType\": null," +
                "    \"validationRules\": {" +
                "        \"en\": [" +
                "            \"foo\"," +
                "            \"bar\"" +
                "        ]" +
                "    }" +
                "}", JsonNode.class);
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_invalidConfigurationNullRules()
            throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        JsonNode config = BridgeObjectMapper.get().readValue("{" +
                "    \"validationType\": \"enum\"," +
                "    \"validationRules\": null" +
                "}", JsonNode.class);
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_invalidConfigurationNullTypeAndRules() {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        ObjectNode config = BridgeObjectMapper.get().createObjectNode();
        config.set("validationType", BridgeObjectMapper.get().nullNode());
        config.set("validationRules", BridgeObjectMapper.get().nullNode());
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveDemographicUserAppValidation_invalidDemographic()
            throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        JsonNode config = BridgeObjectMapper.get().readValue("{" +
                "    \"validationType\": \"enum\"," +
                "    \"validationRules\": {" +
                "        \"en\": [" +
                "            \"foo\"," +
                "            \"bar\"" +
                "        ]" +
                "    }" +
                "}", JsonNode.class);
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);
    }

    @Test
    public void saveDemographicUserAppValidation_validDemographic()
            throws JsonMappingException, JsonProcessingException {
        AppConfigElement element = AppConfigElement.create();
        element.setId(DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + "category");
        JsonNode config = BridgeObjectMapper.get().readValue("{" +
                "    \"validationType\": \"enum\"," +
                "    \"validationRules\": {" +
                "        \"en\": [" +
                "            \"foo\"," +
                "            \"bar\"" +
                "        ]" +
                "    }" +
                "}", JsonNode.class);
        element.setData(config);
        when(appConfigElementService.getMostRecentElements(TEST_APP_ID, false)).thenReturn(ImmutableList.of(element));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("foo")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(appConfigElementService).getMostRecentElements(TEST_APP_ID, false);
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

        demographicService.saveDemographicUser(demographicUser, account);
    }

    /**
     * Tests deleting a Demographic.
     */
    @Test
    public void deleteDemographic() {
        DemographicUser demographicUser = new DemographicUser();
        demographicUser.setUserId(TEST_USER_ID);
        Demographic demographic = new Demographic();
        demographic.setDemographicUser(demographicUser);
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.of(demographic));

        demographicService.deleteDemographic(TEST_USER_ID, DEMOGRAPHIC_ID, account);

        verify(demographicDao).deleteDemographic(DEMOGRAPHIC_ID);
        verify(participantVersionService).createParticipantVersionFromAccount(account);
    }

    /**
     * Tests that attempting to delete a Demographic that does not exist results in
     * an error.
     */
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicNotFound() {
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.empty());

        demographicService.deleteDemographic(TEST_USER_ID, DEMOGRAPHIC_ID, account);
    }

    /**
     * Tests that attempting to delete a Demographic that is not owned by the user
     * who is trying to delete it results in a not found error
     */
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicNotOwnedByUser() {
        DemographicUser demographicUser = new DemographicUser();
        demographicUser.setUserId(TEST_USER_ID);
        Demographic demographic = new Demographic();
        demographic.setDemographicUser(demographicUser);
        when(demographicDao.getDemographic(DEMOGRAPHIC_ID)).thenReturn(Optional.of(demographic));

        demographicService.deleteDemographic("wrong user id", DEMOGRAPHIC_ID, account);
    }

    /**
     * Tests deleting a DemographicUser.
     */
    @Test
    public void deleteDemographicUser() {
        when(demographicDao.getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.of(DEMOGRAPHIC_USER_ID));

        demographicService.deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, account);

        verify(demographicDao).getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicDao).deleteDemographicUser(DEMOGRAPHIC_USER_ID);
        verify(participantVersionService).createParticipantVersionFromAccount(account);
    }

    /**
     * Tests that attempting to delete a DemographicUser that does not exist results
     * in an error.
     */
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteDemographicUserNotFound() {
        when(demographicDao.getDemographicUserId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        demographicService.deleteDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, account);
    }

    /**
     * Tests fetching a DemographicUser.
     */
    @Test
    public void getDemographicUser() {
        DemographicUser demographicUser = new DemographicUser();
        when(demographicDao.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.of(demographicUser));

        Optional<DemographicUser> returnedDemographicUser = demographicService.getDemographicUser(TEST_APP_ID,
                TEST_STUDY_ID,
                TEST_USER_ID);

        verify(demographicDao).getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertSame(returnedDemographicUser.get(), demographicUser);
    }

    /**
     * Tests that attempting to fetch a DemographicUser that does not exist results
     * in an Optional.empty.
     */
    @Test
    public void getDemographicUserNotFound() {
        when(demographicDao.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        Optional<DemographicUser> returnedDemographicUser = demographicService.getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        verify(demographicDao).getDemographicUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertTrue(!returnedDemographicUser.isPresent());
    }

    /**
     * Tests fetching DemographicUsers.
     */
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

    /**
     * Tests that attempting to fetch DemographicUsers with a bad pageSize results
     * in an error.
     */
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
