package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_2_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SAGE_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.OrganizationService;
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

    /**
     * The data group set in the test (api) app. This includes groups that are required for the SDK integration tests.
     */
    static final Set<String> TEST_DATA_GROUPS = ImmutableSet.of("sdk-int-1", "sdk-int-2", "group1");

    /**
     * The task identifiers set in the test (api) app. This includes task identifiers that are required for the SDK
     * integration tests.
     */
    static final Set<String> TEST_TASK_IDENTIFIERS = ImmutableSet.of("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

    private final BridgeConfig bridgeConfig;
    private final UserAdminService userAdminService;
    private final AccountService accountService;
    private final AppService appService;
    private final OrganizationService orgService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;
    private final S3Initializer s3Initializer;

    @Autowired
    public DefaultAppBootstrapper(BridgeConfig bridgeConfig, UserAdminService userAdminService,
            AccountService accountService, AppService appService, OrganizationService orgService,
            AnnotationBasedTableCreator annotationBasedTableCreator, DynamoInitializer dynamoInitializer,
            S3Initializer s3Initializer) {
        this.bridgeConfig = bridgeConfig;
        this.userAdminService = userAdminService;
        this.accountService = accountService;
        this.appService = appService;
        this.orgService = orgService;
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
                .withCallerRoles(ImmutableSet.of(SUPERADMIN))
                .withCallerUserId("DefaultStudyBootstrapper").build());
        
        // The integration bootstrap account should be ADMIN in production, and SUPERADMIN in other environments.
        // It should have the Synapse user ID of the synapse.user set for the integration tests.
        String adminEmail = bridgeConfig.get("admin.email");
        String adminSynUserId = bridgeConfig.get("admin.synapse.user.id");
        Roles adminRole = (bridgeConfig.getEnvironment() == PROD) ? ADMIN : SUPERADMIN;
        boolean bootstrapUserConfigured = (adminEmail != null && adminSynUserId != null);
        
        StudyParticipant admin = new StudyParticipant.Builder()
                .withEmail(adminEmail)
                .withSynapseUserId(adminSynUserId)
                .withDataGroups(Sets.newHashSet(TEST_USER_GROUP))
                .withSharingScope(NO_SHARING)
                .withRoles(Sets.newHashSet(adminRole)).build();

        App app = createApp(API_APP_ID, "Test App", provided -> {
            provided.setMinAgeOfConsent(18);
            provided.setDataGroups(Sets.newHashSet(TEST_DATA_GROUPS));
            provided.setTaskIdentifiers(Sets.newHashSet(TEST_TASK_IDENTIFIERS));
            provided.setUserProfileAttributes(Sets.newHashSet("can_be_recontacted"));
        });
        if (bootstrapUserConfigured) {
            createAccount(app, admin);    
        }
        App app2 = createApp(API_2_APP_ID, "Test App 2", null);
        if (bootstrapUserConfigured) {
            createAccount(app2, admin);    
        }
        App shared = createApp(SHARED_APP_ID, "Shared App", null);
        if (bootstrapUserConfigured && bridgeConfig.getEnvironment() != Environment.PROD) {
            createAccount(shared, admin);    
        }
    }
    
    private App createApp(String appId, String name, Consumer<App> consumer) {
        App app;
        try {
            app = appService.getApp(appId);
        } catch (EntityNotFoundException e) {
            app = createApp();
            app.setName(appId);
            app.setShortName(appId);
            app.setIdentifier(appId);
            if (consumer != null) {
                consumer.accept(app);
            }
            app = appService.createApp(app);
        }
        if (!orgService.getOrganizationOpt(appId, SAGE_ID).isPresent()) {
            Organization org = Organization.create();
            org.setAppId(appId);
            org.setIdentifier(SAGE_ID);
            org.setName("Sage Bionetworks");
            orgService.createOrganization(org);
        }
        return app;
    }
    
    private void createAccount(App app, StudyParticipant admin) {
        AccountId accountId = AccountId.forSynapseUserId(app.getIdentifier(), admin.getSynapseUserId());
        if (!accountService.getAccount(accountId).isPresent()) {
            SubpopulationGuid subpopGuid = SubpopulationGuid.create(app.getIdentifier());
            userAdminService.createUser(app, admin, subpopGuid, false, false);    
        }
    }
    
    private App createApp() {
        App app = App.create();
        app.setReauthenticationEnabled(false);
        app.setSponsorName("Sage Bionetworks");
        app.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        app.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        app.setSupportEmail("bridge-testing+support@sagebase.org");
        app.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
        app.setEmailVerificationEnabled(true);
        app.setVerifyChannelOnSignInEnabled(true);
        return app;
    }
}
