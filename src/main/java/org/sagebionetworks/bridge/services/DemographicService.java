package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DemographicDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.validators.DemographicUserValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service for Demographic related operations.
 */
@Component
public class DemographicService {
    private static final String DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX = "bridge-demographics-";
    private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";

    private DemographicDao demographicDao;

    private ParticipantVersionService participantVersionService;

    private AppConfigElementService appConfigElementService;

    @Autowired
    public final void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    @Autowired
    public final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    @Autowired
    public final void setAppConfigElementService(AppConfigElementService appConfigElementService) {
        this.appConfigElementService = appConfigElementService;
    }

    /**
     * Saves or overwrites a DemographicUser.
     * 
     * @param demographicUser The DemographicUser to save/overwrite.
     * @param account         The account of the user owning the demographics.
     * @return The DemographicUser that was saved.
     * @throws InvalidEntityException if demographicUser is invalid.
     */
    public DemographicUser saveDemographicUser(DemographicUser demographicUser, Account account)
            throws InvalidEntityException {
        demographicUser.setId(generateGuid());
        if (demographicUser.getDemographics() != null) {
            for (Demographic demographic : demographicUser.getDemographics().values()) {
                demographic.setId(generateGuid());
                demographic.setDemographicUser(demographicUser);
            }
        }
        Validate.entityThrowingException(DemographicUserValidator.INSTANCE, demographicUser);
        if (demographicUser.getStudyId() == null) {
            // app-level demographics
            validateAppLevelDemographics(demographicUser);
        }
        DemographicUser savedDemographicUser = demographicDao.saveDemographicUser(demographicUser,
                demographicUser.getAppId(),
                demographicUser.getStudyId(), demographicUser.getUserId());
        participantVersionService.createParticipantVersionFromAccount(account);
        return savedDemographicUser;
    }

    /**
     * For every Demographic, checks the app config elements for keys in the form
     * "bridge-demographics-{categoryName}". If it exists, the list of values with
     * key "en" is selected (currently validation only supports English). All values
     * in the Demographic are checked to see if they are present in the list of
     * values in the app config. If any of them are not, an exception is thrown.
     * 
     * @param demographicUser The DemographicUser whose values should be validated
     * @throws InvalidEntityException if any value in any Demographic is not valid
     */
    private void validateAppLevelDemographics(DemographicUser demographicUser) throws InvalidEntityException {
        // map of ids to the elements for constant lookup
        Map<String, AppConfigElement> elementIdToElement = appConfigElementService
                .getMostRecentElements(demographicUser.getAppId(), false)
                .stream()
                .collect(Collectors.toMap(AppConfigElement::getId, Function.identity()));
        for (Demographic demographic : demographicUser.getDemographics().values()) {
            // calculate what the key would be if it exists
            String appConfigElementId = DEMOGRAPHICS_APP_CONFIG_KEY_PREFIX + demographic.getCategoryName();
            if (elementIdToElement.containsKey(appConfigElementId)) {
                DemographicAppConfigValidator demographicValidationJsonDeserializerTarget;
                try {
                    demographicValidationJsonDeserializerTarget = BridgeObjectMapper.get().treeToValue(
                            elementIdToElement.get(appConfigElementId).getData(),
                            DemographicAppConfigValidator.class);
                    demographicValidationJsonDeserializerTarget.validate(demographic);
                } catch (IOException | IllegalArgumentException e) {
                    throw new InvalidEntityException(demographicUser,
                            "error validating demographics: " + e.getMessage());
                }
            }
        }
    }

    private static class DemographicAppConfigValidator {
        private ValidationType validationType;
        private JsonNode validationRules;

        private static enum ValidationType {
            @JsonProperty("numberRange")
            NUMBER_RANGE,
            @JsonProperty("enum")
            ENUM
        }

        public void validate(Demographic demographic) throws JsonParseException, JsonMappingException, IOException {
            switch (validationType) {
                case ENUM:
                    validateEnum(demographic);
                case NUMBER_RANGE:
                    validateNumberRange(demographic);
                default:
                    break;
            }
        }

        private void validateEnum(Demographic demographic)
                throws JsonParseException, JsonMappingException, IOException {
            // workaround because ObjectMapper does not have treeToValue method that accepts
            // a TypeReference
            JsonParser tokens = BridgeObjectMapper.get().treeAsTokens(validationRules);
            JavaType type = BridgeObjectMapper.get().getTypeFactory()
                    .constructType(new TypeReference<Map<String, Set<String>>>() {
                    });
            Map<String, Set<String>> enumValidationRules = BridgeObjectMapper.get().readValue(tokens, type);
            // currently only English supported
            Set<String> allowedValues = enumValidationRules.get(DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE);
            // validate all values in the Demographic against the values in the
            // AppConfigElement
            for (DemographicValue demographicValue : demographic.getValues()) {
                if (!allowedValues.contains(demographicValue.getValue())) {
                    throw new InvalidEntityException(demographic, "category " + demographic.getCategoryName()
                            + " has an invalid enum value " + demographicValue.getValue());
                }
            }
        }

        private void validateNumberRange(Demographic demographic)
                throws JsonProcessingException, IllegalArgumentException {
            NumberRangeValidationRules numberRangeValidationRules = BridgeObjectMapper.get()
                    .treeToValue(validationRules, NumberRangeValidationRules.class);
            for (DemographicValue demographicValue : demographic.getValues()) {
                double actualValue;
                try {
                    actualValue = Double.parseDouble(demographicValue.getValue());
                } catch (NumberFormatException e) {
                    // this value is not a double
                    continue;
                }
                if (numberRangeValidationRules.getMin() != null && actualValue < numberRangeValidationRules.getMin()) {
                    throw new InvalidEntityException(demographic, "category " + demographic.getCategoryName()
                            + " has an invalid number value " + demographicValue.getValue());
                }
                if (numberRangeValidationRules.getMax() != null && actualValue > numberRangeValidationRules.getMax()) {
                    throw new InvalidEntityException(demographic, "category " + demographic.getCategoryName()
                            + " has an invalid number value " + demographicValue.getValue());
                }
            }
        }

        private static class NumberRangeValidationRules {
            private Double min;
            private Double max;

            public Double getMin() {
                return min;
            }

            public Double getMax() {
                return max;
            }
        }
    }

    /**
     * Deletes a Demographic.
     * 
     * @param userId        The userId of the user who owns the Demographic to
     *                      delete.
     * @param demographicId The id of the Demographic to delete.
     * @param account       The account of the user owning the demographics.
     * @throws EntityNotFoundException if the Demographic does not exist or the
     *                                 specified user does not own the specified
     *                                 Demographic.
     */
    public void deleteDemographic(String userId, String demographicId, Account account) throws EntityNotFoundException {
        Demographic existingDemographic = demographicDao.getDemographic(demographicId)
                .orElseThrow(() -> new EntityNotFoundException(Demographic.class));
        if (!existingDemographic.getDemographicUser().getUserId().equals(userId)) {
            // user does not own this demographic
            // just give them a 404 because we don't want to expose the existence of another
            // user's demographic data
            throw new EntityNotFoundException(Demographic.class);
        }
        demographicDao.deleteDemographic(demographicId);
        participantVersionService.createParticipantVersionFromAccount(account);
    }

    /**
     * Deletes a DemographicUser (all demographics for a user).
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                delete.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                delete. Can be null if the demographics are app-level.
     * @param account The account of the user owning the demographics.
     * @param userId  The userId of the user to delete demographics for.
     * @throws EntityNotFoundException if the DemographicUser to delete does not
     *                                 exist based on the provided appId, studyId,
     *                                 and userId.
     */
    public void deleteDemographicUser(String appId, String studyId, String userId, Account account)
            throws EntityNotFoundException {
        String existingDemographicUserId = demographicDao.getDemographicUserId(appId, studyId, userId)
                .orElseThrow(() -> new EntityNotFoundException(DemographicUser.class));
        demographicDao.deleteDemographicUser(existingDemographicUserId);
        participantVersionService.createParticipantVersionFromAccount(account);
    }

    /**
     * Fetches a DemographicUser.
     * 
     * @param appId   The appId of the app which contains the DemographicUser to
     *                fetch.
     * @param studyId The studyId of the study which contains the DemographicUser to
     *                fetch. Can be null if the demographics are app-level.
     * @param userId  The userId of the user to fetch demographics for.
     * @return The fetched DemographicUser.
     */
    public Optional<DemographicUser> getDemographicUser(String appId, String studyId, String userId) {
        return demographicDao.getDemographicUser(appId, studyId, userId);
    }

    /**
     * Fetches all app-level DemographicUsers for an app or all study-level
     * DemographicUsers for a study.
     * 
     * @param appId    The appId of the app which contains the DemographicUsers to
     *                 fetch.
     * @param studyId  The studyId of the study which contains the DemographicUsers
     *                 to fetch. Can be null if the demographics are app-level.
     * @param offsetBy The offset at which the returned list of DemographicUsers
     *                 should begin.
     * @param pageSize The maximum number of entries in the returned list of
     *                 DemographicUsers.
     * @return a paged list of fetched DemographicUsers.
     * @throws BadRequestException if the pageSize is invalid.
     */
    public PagedResourceList<DemographicUser> getDemographicUsers(String appId, String studyId, int offsetBy,
            int pageSize) throws BadRequestException {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return demographicDao.getDemographicUsers(appId, studyId, offsetBy, pageSize);
    }

    /**
     * Generates a guid.
     * 
     * @return a generated guid.
     */
    public String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
