package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.List;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UserAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * This bootstrapper creates DynamoDB and S3 buckets that are needed by Bridge, as well as
 * two initial apps and administrative accounts. Bootstrapping occurs on startup by default 
 * unless you start the Spring Boot application with the "nonit" profile enabled 
 * (mvn spring-boot:run -Dspring.profiles.active=noinit). The "noinit" profile will also 
 * disable the database migrations that we run through Liquibase. 
 */
@Component
@Profile("default")
public class DefaultAppBootstrapper implements ApplicationListener<ContextRefreshedEvent> {

    static final SubpopulationGuid SHARED_SUBPOP = SubpopulationGuid.create(SHARED_APP_ID);
    static final SubpopulationGuid API_SUBPOP = SubpopulationGuid.create(API_APP_ID);
    
    /**
     * The data group set in the test (api) app. This includes groups that are required for the SDK integration tests.
     */
    public static final Set<String> TEST_DATA_GROUPS = ImmutableSet.of("sdk-int-1", "sdk-int-2", "group1", TEST_USER_GROUP);

    /**
     * The task identifiers set in the test (api) app. This includes task identifiers that are required for the SDK
     * integration tests.
     */
    public static final Set<String> TEST_TASK_IDENTIFIERS = ImmutableSet.of("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

    private final BridgeConfig bridgeConfig;
    private final UserAdminService userAdminService;
    private final AppService appService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;
    private final S3Initializer s3Initializer;

    @Autowired
    public DefaultAppBootstrapper(BridgeConfig bridgeConfig, UserAdminService userAdminService, 
            AppService appService, AnnotationBasedTableCreator annotationBasedTableCreator, 
            DynamoInitializer dynamoInitializer, S3Initializer s3Initializer) {
        this.bridgeConfig = bridgeConfig;
        this.userAdminService = userAdminService;
        this.appService = appService;
        this.dynamoInitializer = dynamoInitializer;
        this.annotationBasedTableCreator = annotationBasedTableCreator;
        this.s3Initializer = s3Initializer;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<TableDescription> tables = annotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb");
        dynamoInitializer.init(tables);
        
        s3Initializer.initBuckets();
        
        RequestContext.set(new RequestContext.Builder().withCallerAppId(API_APP_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN, SUPERADMIN, DEVELOPER, RESEARCHER))
                .withCallerUserId("DefaultStudyBootstrapper").build());

        // Create the "api" app if it doesn't exist. This is used for local testing and integ tests.
        try {
            appService.getApp(API_APP_ID);
        } catch (EntityNotFoundException e) {
            App app = App.create();
            app.setName("Test App");
            app.setShortName("TestApp");
            app.setIdentifier(API_APP_ID);
            app.setReauthenticationEnabled(false);
            app.setSponsorName("Sage Bionetworks");
            app.setMinAgeOfConsent(18);
            app.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
            app.setTechnicalEmail("bridge-testing+technical@sagebase.org");
            app.setSupportEmail("support@sagebridge.org");
            app.setDataGroups(Sets.newHashSet(TEST_DATA_GROUPS));
            app.setTaskIdentifiers(Sets.newHashSet(TEST_TASK_IDENTIFIERS));
            app.setUserProfileAttributes(Sets.newHashSet("can_be_recontacted"));
            app.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            app.setEmailVerificationEnabled(true);
            app.setVerifyChannelOnSignInEnabled(true);
            app = appService.createApp(app);
            
            StudyParticipant admin = new StudyParticipant.Builder()
                    .withEmail(bridgeConfig.get("admin.email"))
                    .withPassword(bridgeConfig.get("admin.password"))
                    .withRoles(ImmutableSet.of(SUPERADMIN)).build();
            userAdminService.createUser(app, admin, API_SUBPOP, false, false);

            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(bridgeConfig.get("api.developer.email"))
                    .withPassword(bridgeConfig.get("api.developer.password"))
                    .withRoles(ImmutableSet.of(DEVELOPER)).build();
            userAdminService.createUser(app, dev, API_SUBPOP, false, false);
        }

        // Create the "shared" app if it doesn't exist. This is used for the Shared Module Library.
        try {
            appService.getApp(SHARED_APP_ID);
        } catch (EntityNotFoundException e) {
            App app = App.create();
            app.setName("Shared Module Library");
            app.setReauthenticationEnabled(false);
            app.setShortName("SharedLib");
            app.setSponsorName("Sage Bionetworks");
            app.setIdentifier(SHARED_APP_ID);
            app.setSupportEmail(bridgeConfig.get("admin.email"));
            app.setTechnicalEmail(bridgeConfig.get("admin.email"));
            app.setConsentNotificationEmail(bridgeConfig.get("admin.email"));
            app.setEmailVerificationEnabled(true);
            app.setVerifyChannelOnSignInEnabled(true);
            app = appService.createApp(app);
            
            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(bridgeConfig.get("shared.developer.email"))
                    .withPassword(bridgeConfig.get("shared.developer.password"))
                    .withRoles(ImmutableSet.of(DEVELOPER)).build();
            userAdminService.createUser(app, dev, SHARED_SUBPOP, false, false);
        }
    }
}
