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
import org.sagebionetworks.bridge.dao.AppDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoAppDao implements AppDao {
    static final String APP_WHITELIST_PROPERTY = "app.whitelist";
    
    private Set<String> appWhitelist;

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
        appWhitelist = ImmutableSet.copyOf(config.getPropertyAsList(APP_WHITELIST_PROPERTY));
    }

    @Override
    public boolean doesIdentifierExist(String appId) {
        DynamoApp app = new DynamoApp();
        app.setIdentifier(appId);
        return (mapper.load(app) != null);
    }
    
    @Override
    public App getApp(String appId) {
        checkArgument(isNotBlank(appId), Validate.CANNOT_BE_BLANK, "appId");
        
        DynamoApp app = new DynamoApp();
        app.setIdentifier(appId);
        app = mapper.load(app);
        if (app == null) {
            throw new EntityNotFoundException(App.class, "App '"+appId+"' not found.");
        }
        
        return app;
    }
    
    @Override
    public List<App> getApps() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        // get all apps including deactivated ones
        List<DynamoApp> mappings = mapper.scan(DynamoApp.class, scan);
        
        return new ArrayList<App>(mappings);
    }
    
    @Override
    public App createApp(App app) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "app");
        checkArgument(app.getVersion() == null, "%s has a version; may not be new", "app");
        try {
            mapper.save(app);
        } catch(ConditionalCheckFailedException e) { // in the create scenario, this should be a hash key clash.
            throw new EntityAlreadyExistsException(App.class, "identifier", app.getIdentifier());
        }
        return app;
    }

    @Override
    public App updateApp(App app) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "app");
        checkNotNull(app.getVersion(), Validate.CANNOT_BE_NULL, "app version");
        try {
            mapper.save(app);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(app);
        }
        return app;
    }

    @Override
    public void deleteApp(App app) {
        checkNotNull(app, Validate.CANNOT_BE_BLANK, "app");

        String appId = app.getIdentifier();
        if (appWhitelist.contains(appId)) {
            throw new UnauthorizedException(appId + " is protected by whitelist.");
        }

        mapper.delete(app);
    }

    @Override
    public void deactivateApp(String appId) {
        checkNotNull(appId, Validate.CANNOT_BE_BLANK, "appId");

        if (appWhitelist.contains(appId)) {
            throw new UnauthorizedException(appId + " is protected by whitelist.");
        }

        App app = getApp(appId);
        app.setActive(false);

        updateApp(app);
    }
}
