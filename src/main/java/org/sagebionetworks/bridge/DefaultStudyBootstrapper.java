package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
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
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component("defaultStudyBootstrapper")
public class DefaultStudyBootstrapper  implements ApplicationListener<ContextRefreshedEvent> {

    static final SubpopulationGuid SHARED_SUBPOP = SubpopulationGuid.create(SHARED_STUDY_ID_STRING);
    static final SubpopulationGuid API_SUBPOP = SubpopulationGuid.create(API_STUDY_ID_STRING);
    
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
    private final StudyService studyService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;

    @Autowired
    public DefaultStudyBootstrapper(UserAdminService userAdminService, StudyService studyService,
            AnnotationBasedTableCreator annotationBasedTableCreator, DynamoInitializer dynamoInitializer) {
        this.userAdminService = userAdminService;
        this.studyService = studyService;
        this.dynamoInitializer = dynamoInitializer;
        this.annotationBasedTableCreator = annotationBasedTableCreator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<TableDescription> tables = annotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb");
        dynamoInitializer.init(tables);

        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerStudyId(API_STUDY_ID_STRING)
                .withCallerRoles(ImmutableSet.of(ADMIN, SUPERADMIN, DEVELOPER, RESEARCHER))
                .withCallerUserId("DefaultStudyBootstrapper").build());

        // Create the "api" study if it doesn't exist. This is used for local testing and integ tests.
        try {
            studyService.getStudy(API_STUDY_ID_STRING);
        } catch (EntityNotFoundException e) {
            Study study = Study.create();
            study.setName("Test Study");
            study.setShortName("TestStudy");
            study.setIdentifier(API_STUDY_ID_STRING);
            study.setReauthenticationEnabled(false);
            study.setSponsorName("Sage Bionetworks");
            study.setMinAgeOfConsent(18);
            study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
            study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
            study.setSupportEmail("support@sagebridge.org");
            study.setDataGroups(Sets.newHashSet(TEST_DATA_GROUPS));
            study.setTaskIdentifiers(Sets.newHashSet(TEST_TASK_IDENTIFIERS));
            study.setUserProfileAttributes(Sets.newHashSet("can_be_recontacted"));
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            study.setEmailVerificationEnabled(true);
            study.setVerifyChannelOnSignInEnabled(true);
            study = studyService.createStudy(study);
            
            StudyParticipant admin = new StudyParticipant.Builder()
                    .withEmail(config.get("admin.email"))
                    .withPassword(config.get("admin.password"))
                    .withRoles(ImmutableSet.of(ADMIN, RESEARCHER)).build();
            userAdminService.createUser(study, admin, API_SUBPOP, false, false);

            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(config.get("api.developer.email"))
                    .withPassword(config.get("api.developer.password"))
                    .withRoles(ImmutableSet.of(DEVELOPER)).build();
            userAdminService.createUser(study, dev, API_SUBPOP, false, false);
        }

        // Create the "shared" study if it doesn't exist. This is used for the Shared Module Library.
        try {
            studyService.getStudy(SHARED_STUDY_ID_STRING);
        } catch (EntityNotFoundException e) {
            Study study = Study.create();
            study.setName("Shared Module Library");
            study.setReauthenticationEnabled(false);
            study.setShortName("SharedLib");
            study.setSponsorName("Sage Bionetworks");
            study.setIdentifier(SHARED_STUDY_ID_STRING);
            study.setSupportEmail(config.get("admin.email"));
            study.setTechnicalEmail(config.get("admin.email"));
            study.setConsentNotificationEmail(config.get("admin.email"));
            study.setEmailVerificationEnabled(true);
            study.setVerifyChannelOnSignInEnabled(true);
            study = studyService.createStudy(study);
            
            StudyParticipant dev = new StudyParticipant.Builder()
                    .withEmail(config.get("shared.developer.email"))
                    .withPassword(config.get("shared.developer.password"))
                    .withRoles(ImmutableSet.of(DEVELOPER)).build();
            userAdminService.createUser(study, dev, SHARED_SUBPOP, false, false);
        }
    }
}
