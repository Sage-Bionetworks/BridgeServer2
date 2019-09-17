package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID;
import static org.sagebionetworks.bridge.DefaultStudyBootstrapper.API_SUBPOP;
import static org.sagebionetworks.bridge.DefaultStudyBootstrapper.SHARED_SUBPOP;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

public class DefaultStudyBootstrapperTest extends Mockito {

    @Mock
    StudyService mockStudyService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    AnnotationBasedTableCreator mockTableCreator; 
    
    @Mock
    DynamoInitializer mockDynamoInitializer; 

    @InjectMocks
    DefaultStudyBootstrapper defaultStudyBootstrapper;
    
    @Captor
    ArgumentCaptor<Study> studyCaptor;

    @Captor
    ArgumentCaptor<SubpopulationGuid> subpopCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(mockStudyService.getStudy(any(StudyIdentifier.class))).thenThrow(EntityNotFoundException.class);
        when(mockStudyService.createStudy(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void createsDefaultStudyWhenMissing() {
        defaultStudyBootstrapper.onApplicationEvent(null);

        verify(mockStudyService, times(2)).createStudy(studyCaptor.capture());

        List<Study> createdStudyList = studyCaptor.getAllValues();

        // Validate api study.
        Study study = createdStudyList.get(0);
        assertEquals(study.getName(), "Test Study");
        assertEquals(study.getIdentifier(), BridgeConstants.API_STUDY_ID_STRING);
        assertEquals(study.getSponsorName(), "Sage Bionetworks");
        assertEquals(study.getShortName(), "TestStudy");
        assertEquals(study.getMinAgeOfConsent(), 18);
        assertEquals(study.getConsentNotificationEmail(), "bridge-testing+consent@sagebase.org");
        assertEquals(study.getTechnicalEmail(), "bridge-testing+technical@sagebase.org");
        assertEquals(study.getSupportEmail(), "support@sagebridge.org");
        assertEquals(study.getUserProfileAttributes(), Sets.newHashSet("can_be_recontacted"));
        assertEquals(study.getPasswordPolicy(), new PasswordPolicy(2, false, false, false, false));
        assertTrue(study.isEmailVerificationEnabled());

        // Validate shared study. No need to test every attribute. Just validate the important attributes.
        Study sharedStudy = createdStudyList.get(1);
        assertEquals(sharedStudy.getName(), "Shared Module Library");
        assertEquals(sharedStudy.getIdentifier(), BridgeConstants.SHARED_STUDY_ID_STRING);

        // So it doesn't get out of sync, validate the study. However, default templates are set 
        // by the service. so those two errors are expected.
        try {
            Validate.entityThrowingException(new StudyValidator(), study);    
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().keySet().size(), 2);
            assertEquals(e.getErrors().get("verifyEmailTemplate").size(), 1);
            assertEquals(e.getErrors().get("resetPasswordTemplate").size(), 1);
        }
        
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        verify(mockUserAdminService, times(3)).createUser(any(), participantCaptor.capture(),
                subpopCaptor.capture(), eq(false), eq(false));
        
        assertEquals(BridgeUtils.getRequestContext().getCallerStudyId(), BridgeConstants.API_STUDY_ID_STRING);
        assertEquals(BridgeUtils.getRequestContext().getCallerRoles(), ImmutableSet.of(ADMIN, DEVELOPER, RESEARCHER));
        assertEquals(BridgeUtils.getRequestContext().getCallerUserId(), "DefaultStudyBootstrapper");
        
        assertEquals(API_SUBPOP, subpopCaptor.getAllValues().get(0));
        assertEquals(API_SUBPOP, subpopCaptor.getAllValues().get(1));
        assertEquals(SHARED_SUBPOP, subpopCaptor.getAllValues().get(2));
        
        StudyParticipant admin = participantCaptor.getAllValues().get(0);
        assertEquals(admin.getRoles(), ImmutableSet.of(ADMIN, RESEARCHER));
        assertEquals(admin.getEmail(), config.get("admin.email"));
        assertEquals(admin.getPassword(), config.get("admin.password"));
        
        StudyParticipant apiDev = participantCaptor.getAllValues().get(1);
        assertEquals(apiDev.getRoles(), ImmutableSet.of(DEVELOPER));
        assertEquals(apiDev.getEmail(), config.get("api.developer.email"));
        assertEquals(apiDev.getPassword(), config.get("api.developer.password"));
        
        StudyParticipant sharedDev = participantCaptor.getAllValues().get(2);
        assertEquals(sharedDev.getRoles(), ImmutableSet.of(DEVELOPER));
        assertEquals(sharedDev.getEmail(), config.get("shared.developer.email"));
        assertEquals(sharedDev.getPassword(), config.get("shared.developer.password"));
    }
}
