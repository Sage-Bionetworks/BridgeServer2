package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
import org.sagebionetworks.bridge.dao.DemographicValidationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;
import org.sagebionetworks.bridge.validators.DemographicValuesValidationType;
import org.slf4j.Logger;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DemographicServiceTest {
    private static final String DEMOGRAPHIC_ID = "test-demographic-id";
    private static final String DEMOGRAPHIC_USER_ID = "test-demographic-user-id";
    private static final String INVALID_appVALIDATION_CONFIGURATION = "invalid demographics validation configuration";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";

    @Mock
    DemographicDao demographicDao;

    @Mock
    DemographicValidationDao demographicValidationDao;

    @Mock
    ParticipantVersionService participantVersionService;

    @InjectMocks
    @Spy
    DemographicService demographicService;

    @Mock
    Account account;

    @Mock
    Logger logger;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        demographicService.setLogger(logger);

        doReturn("0", IntStream.range(1, 1000).mapToObj(Integer::toString).toArray()).when(demographicService)
                .generateGuid();
    }

    private void assertAllInvalidity(Demographic demographic, String errorMessage) {
        for (DemographicValue value : demographic.getValues()) {
            assertEquals(value.getInvalidity(), errorMessage);
        }
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
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID,
                "category-name1");
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID,
                "category-name2");
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
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any()))
                .thenReturn(Optional.empty());

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
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, null, "category-name1");
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, null, "category-name2");
    }

    @Test
    public void saveDemographicUser_performsInputValidation() {
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any()))
                .thenReturn(Optional.empty());
        DemographicUser demographicUser = new DemographicUser(null, null, null, null,
                new HashMap<>());
        
        // execute
        try {
            demographicService.saveDemographicUser(demographicUser, account);
            fail("should have thrown InvalidEntityException");
        } catch (InvalidEntityException e) {
        }

        // verify
        // we should not have gotten to custom validation
        verifyZeroInteractions(demographicValidationDao);
    }

    @Test
    public void saveDemographicUser_appValidation_noDemographics() {
        when(demographicDao.saveDemographicUser(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any())).thenReturn(Optional.empty());

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, new HashMap<>());

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verifyZeroInteractions(demographicValidationDao);
    }

    @Test
    public void saveDemographicUser_studyValidation_noDemographics() {
        when(demographicDao.saveDemographicUser(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any())).thenReturn(Optional.empty());

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, new HashMap<>());

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verifyZeroInteractions(demographicValidationDao);
    }

    // tests case where configuration does not exist and also case where
    // configuration is for another category
    @Test
    public void saveDemographicUser_appValidation_noConfiguration()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, null, "category"))
                .thenReturn(Optional.of(config));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "bar", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("some-other-category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, null, "some-other-category");
        // nothing should be invalid because the category with validation rules does not
        // match the category name of the demographic
        assertAllInvalidity(demographic, null);
    }

    // tests case where configuration does not exist and also case where
    // configuration is for another category
    @Test
    public void saveDemographicUser_studyValidation_noConfiguration()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category"))
                .thenReturn(Optional.of(config));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID,
                null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "bar", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("some-other-category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID,
                "some-other-category");
        // nothing should be invalid because the category with validation rules does not
        // match the category name of the demographic
        assertAllInvalidity(demographic, null);
    }

    @Test
    public void saveDemographicUser_appValidation_rulesDeserializationException()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, null, "category"))
                .thenReturn(Optional.of(config));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        DemographicUser savedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);
        assertAllInvalidity(savedDemographicUser.getDemographics().get("category"),
                INVALID_appVALIDATION_CONFIGURATION);
        verify(logger)
                .error(eq(
                        "Demographic validation rules failed deserialization when attempting to validate a demographic "
                                + "but rules should have been validated for deserialization already,"
                                + " appId " + demographicUser.getAppId()
                                + " studyId " + demographicUser.getStudyId()
                                + " userId " + demographicUser.getUserId()
                                + " demographics categoryName " + demographic.getCategoryName()
                                + " demographics validationType " + config.getValidationType().toString()),
                        (Throwable) any());
    }

    @Test
    public void saveDemographicUser_studyValidation_rulesDeserializationException()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category"))
                .thenReturn(Optional.of(config));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID,
                null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        DemographicUser savedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);
        assertAllInvalidity(savedDemographicUser.getDemographics().get("category"),
                INVALID_appVALIDATION_CONFIGURATION);
        verify(logger)
                .error(eq(
                        "Demographic validation rules failed deserialization when attempting to validate a demographic "
                                + "but rules should have been validated for deserialization already,"
                                + " appId " + demographicUser.getAppId()
                                + " studyId " + demographicUser.getStudyId()
                                + " userId " + demographicUser.getUserId()
                                + " demographics categoryName " + demographic.getCategoryName()
                                + " demographics validationType " + config.getValidationType().toString()),
                        (Throwable) any());
    }

    @Test
    public void saveDemographicUser_appValidation_invalidDemographic()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, null, "category"))
                .thenReturn(Optional.of(config));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        DemographicUser savedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);
        assertAllInvalidity(savedDemographicUser.getDemographics().get("category"), INVALID_ENUM_VALUE);
    }

    @Test
    public void saveDemographicUser_studyValidation_invalidDemographic()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category"))
                .thenReturn(Optional.of(config));
        when(demographicDao.saveDemographicUser(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID,
                null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("random value")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        DemographicUser savedDemographicUser = demographicService.saveDemographicUser(demographicUser, account);
        assertAllInvalidity(savedDemographicUser.getDemographics().get("category"), INVALID_ENUM_VALUE);
    }

    @Test
    public void saveDemographicUser_appValidation_validDemographic()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, null, "category"))
                .thenReturn(Optional.of(config));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, null, TEST_USER_ID, null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("foo")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, null, "category");
        // all valid
        assertAllInvalidity(demographic, null);
    }

    @Test
    public void saveDemographicUser_studyValidation_validDemographic()
            throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category"))
                .thenReturn(Optional.of(config));

        DemographicUser demographicUser = new DemographicUser("test-id", TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID,
                null);
        Demographic demographic = new Demographic(TEST_APP_ID, demographicUser, "category", false,
                ImmutableList.of(new DemographicValue("foo")), null);
        demographicUser.setDemographics(ImmutableMap.of("category", demographic));

        // execute
        demographicService.saveDemographicUser(demographicUser, account);

        // verify
        verify(demographicValidationDao).getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category");
        // all valid
        assertAllInvalidity(demographic, null);
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

    @Test
    public void saveValidationConfig() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"" +
                "    ]" +
                "}", JsonNode.class));
        when(demographicValidationDao.saveDemographicValuesValidationConfig(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DemographicValuesValidationConfig returnedConfig = demographicService.saveValidationConfig(config);

        assertSame(returnedConfig, config);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void saveValidationConfig_validates() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();

        when(demographicValidationDao.saveDemographicValuesValidationConfig(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        demographicService.saveValidationConfig(config);
    }

    @Test
    public void getValidationConfig() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any()))
                .thenReturn(Optional.of(config));

        Optional<DemographicValuesValidationConfig> returnedConfig = demographicService.getValidationConfig(TEST_APP_ID,
                TEST_STUDY_ID, "category");

        assertTrue(returnedConfig.isPresent());
        assertSame(returnedConfig.get(), config);
    }

    @Test
    public void getValidationConfig_empty() {
        when(demographicValidationDao.getDemographicValuesValidationConfig(any(), any(), any())).thenReturn(Optional.empty());
        
        Optional<DemographicValuesValidationConfig> returnedConfig = demographicService.getValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category");
        
        assertFalse(returnedConfig.isPresent());
    }

    @Test
    public void deleteValidationConfig() {
        demographicService.deleteValidationConfig(TEST_APP_ID, TEST_STUDY_ID, "category");

        verify(demographicValidationDao).deleteDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID,
                "category");
    }

    @Test
    public void deleteAllValidationConfigs() {
        demographicService.deleteAllValidationConfigs(TEST_APP_ID, TEST_STUDY_ID);

        verify(demographicValidationDao).deleteAllValidationConfigs(TEST_APP_ID, TEST_STUDY_ID);
    }
}
