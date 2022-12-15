package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.DemographicValidationDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

@Component
public class DynamoDemographicValidationDao implements DemographicValidationDao {
    private DynamoDBMapper mapper;

    @Resource(name = "demographicValidationDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DemographicValuesValidationConfig saveDemographicValuesValidationConfig(
            DemographicValuesValidationConfig validationConfig) {
        checkNotNull(validationConfig);
        mapper.save(validationConfig);
        return validationConfig;
    }

    @Override
    public Optional<DemographicValuesValidationConfig> getDemographicValuesValidationConfig(String appId,
            String studyId, String categoryName) {
        // studyId can be null for app demographics validation
        checkNotNull(appId);
        checkNotNull(categoryName);

        DynamoDemographicValuesValidationConfig key = new DynamoDemographicValuesValidationConfig();
        key.setAppId(appId);
        key.setStudyId(studyId);
        key.setCategoryName(categoryName);

        DemographicValuesValidationConfig validationConfig = mapper.load(key);
        return Optional.ofNullable(validationConfig);
    }

    @Override
    public void deleteDemographicValuesValidationConfig(String appId, String studyId, String categoryName)
            throws EntityNotFoundException {
        // studyId can be null for app demographics validation
        checkNotNull(appId);
        checkNotNull(categoryName);

        DemographicValuesValidationConfig validationConfig = getDemographicValuesValidationConfig(appId, studyId,
                categoryName).orElseThrow(() -> new EntityNotFoundException(DemographicValuesValidationConfig.class));
        mapper.delete(validationConfig);
    }

    @Override
    public void deleteAllValidationConfigs(String appId, String studyId) {
        // studyId can be null for app demographics validation
        checkNotNull(appId);

        DynamoDemographicValuesValidationConfig key = new DynamoDemographicValuesValidationConfig();
        key.setAppId(appId);
        key.setStudyId(studyId);

        // fetch all matching hash key of appId + studyId
        DynamoDBQueryExpression<DynamoDemographicValuesValidationConfig> query = new DynamoDBQueryExpression<>();
        query.setHashKeyValues(key);
        PaginatedQueryList<DynamoDemographicValuesValidationConfig> validationConfigsToDelete = mapper
                .query(DynamoDemographicValuesValidationConfig.class, query);

        // then delete all that were fetched
        mapper.batchDelete(validationConfigsToDelete);
    }
}
