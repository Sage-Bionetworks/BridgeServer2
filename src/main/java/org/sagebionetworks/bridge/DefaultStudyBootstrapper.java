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
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UserAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component("defaultStudyBootstrapper")
public class DefaultStudyBootstrapper  implements ApplicationListener<ContextRefreshedEvent> {

    static final SubpopulationGuid SHARED_SUBPOP = SubpopulationGuid.create(SHARED_APP_ID);
    static final SubpopulationGuid API_SUBPOP = SubpopulationGuid.create(API_APP_ID);
    
    /**
     * The data group set in the test (api) study. This includes groups that are required for the SDK integration tests.
     */
    public static final Set<String> TEST_DATA_GROUPS = ImmutableSet.of("sdk-int-1", "sdk-int-2", "group1", TEST_USER_GROUP);

    /**
     * The task identifiers set in the test (api) study. This includes task identifiers that are required for the SDK
     * integration tests.
     */
    public static final Set<String> TEST_TASK_IDENTIFIERS = ImmutableSet.of("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

    private final UserAdminService userAdminService;
    private final AppService appService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;

    @Autowired
    public DefaultStudyBootstrapper(UserAdminService userAdminService, AppService appService,
            AnnotationBasedTableCreator annotationBasedTableCreator, DynamoInitializer dynamoInitializer) {
        this.userAdminService = userAdminService;
        this.appService = appService;
        this.dynamoInitializer = dynamoInitializer;
        this.annotationBasedTableCreator = annotationBasedTableCreator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<TableDescription> tables = annotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb");
        dynamoInitializer.init(tables);

        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerAppId(API_APP_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN, SUPERADMIN, DEVELOPER, RESEARCHER))
                .withCallerUserId("DefaultStudyBootstrapper").build());

        // Create the "api" app if it doesn't exist. This is used for local testing and integ tests.
        try {
            appService.getApp(API_APP_ID);
        } catch (EntityNotFoundException e) {
            App app = App.create();
            app.setName("Test Study");
            app.setShortName("TestStudy");
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
                    .withEmail(config.get("admin.email"))
                    .withPassword(config.get("admin.password"))
                    .withRoles(ImmutableSet.of(ADMIN, RESEARCHER)).build();
            userAdminService.createUser(app, admin, API_SUBPOP, false, false);

            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(config.get("api.developer.email"))
                    .withPassword(config.get("api.developer.password"))
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
            app.setSupportEmail(config.get("admin.email"));
            app.setTechnicalEmail(config.get("admin.email"));
            app.setConsentNotificationEmail(config.get("admin.email"));
            app.setEmailVerificationEnabled(true);
            app.setVerifyChannelOnSignInEnabled(true);
            app = appService.createApp(app);
            
            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(config.get("shared.developer.email"))
                    .withPassword(config.get("shared.developer.password"))
                    .withRoles(ImmutableSet.of(DEVELOPER)).build();
            userAdminService.createUser(app, dev, SHARED_SUBPOP, false, false);
        }
    }
}
