package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoStudyDao implements StudyDao {
    static final String STUDY_WHITELIST_PROPERTY = "study.whitelist";
    
    private Set<String> studyWhitelist;

    private DynamoDBMapper mapper;

    @Autowired
    final void setDynamoDbClient(AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoApp.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }
    
    @Autowired
    final void setBridgeConfig(BridgeConfig config) {
        studyWhitelist = ImmutableSet.copyOf(config.getPropertyAsList(STUDY_WHITELIST_PROPERTY));
    }

    @Override
    public boolean doesIdentifierExist(String identifier) {
        DynamoApp study = new DynamoApp();
        study.setIdentifier(identifier);
        return (mapper.load(study) != null);
    }
    
    @Override
    public App getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        DynamoApp app = new DynamoApp();
        app.setIdentifier(identifier);
        app = mapper.load(app);
        if (app == null) {
            throw new EntityNotFoundException(App.class, "Study '"+identifier+"' not found.");
        }
        return app;
    }
    
    @Override
    public List<App> getStudies() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        // get all apps including deactivated ones
        List<DynamoApp> mappings = mapper.scan(DynamoApp.class, scan);

        return new ArrayList<App>(mappings);
    }

    @Override
    public App createStudy(App app) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "study");
        checkArgument(app.getVersion() == null, "%s has a version; may not be new", "study");
        try {
            mapper.save(app);
        } catch(ConditionalCheckFailedException e) { // in the create scenario, this should be a hash key clash.
            throw new EntityAlreadyExistsException(App.class, "identifier", app.getIdentifier());
        }
        return app;
    }

    @Override
    public App updateStudy(App app) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(app.getVersion(), Validate.CANNOT_BE_NULL, "study version");
        try {
            mapper.save(app);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(app);
        }
        return app;
    }

    @Override
    public void deleteStudy(App app) {
        checkNotNull(app, Validate.CANNOT_BE_BLANK, "study");

        String studyId = app.getIdentifier();
        if (studyWhitelist.contains(studyId)) {
            throw new UnauthorizedException(studyId + " is protected by whitelist.");
        }

        mapper.delete(app);
    }

    @Override
    public void deactivateStudy(String studyId) {
        checkNotNull(studyId, Validate.CANNOT_BE_BLANK, "study");

        if (studyWhitelist.contains(studyId)) {
            throw new UnauthorizedException(studyId + " is protected by whitelist.");
        }

        App app = getStudy(studyId);
        app.setActive(false);

        updateStudy(app);
    }
}
