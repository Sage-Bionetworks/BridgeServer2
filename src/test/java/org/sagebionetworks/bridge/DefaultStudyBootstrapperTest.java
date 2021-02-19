package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.DefaultAppBootstrapper.API_SUBPOP;
import static org.sagebionetworks.bridge.DefaultAppBootstrapper.SHARED_SUBPOP;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
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
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.validators.AppValidator;
import org.sagebionetworks.bridge.validators.Validate;

public class DefaultStudyBootstrapperTest extends Mockito {

    @Mock
    AppService mockAppService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    AnnotationBasedTableCreator mockTableCreator; 
    
    @Mock
    DynamoInitializer mockDynamoInitializer;
    
    @Mock
    S3Initializer mockS3Initializer;
    
    @Mock
    BridgeConfig mockBridgeConfig;

    @InjectMocks
    DefaultAppBootstrapper defaultStudyBootstrapper;
    
    @Captor
    ArgumentCaptor<App> appCaptor;

    @Captor
    ArgumentCaptor<SubpopulationGuid> subpopCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @BeforeMethod
    public void before() {
        // The boostrapper doesn't seem to fully initialize between test methods. It 
        // might be due to the constructor-based dependency injection. Nulling the 
        // field forces full re-initialization
        defaultStudyBootstrapper = null;
        MockitoAnnotations.initMocks(this);
        
        when(mockAppService.getApp(any(String.class))).thenThrow(EntityNotFoundException.class);
        when(mockAppService.createApp(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    
    @Test
    public void callsS3Initialization() {
        defaultStudyBootstrapper.onApplicationEvent(null);
        verify(mockS3Initializer).initBuckets();
    }

    @Test
    public void createsDefaultStudyWhenMissing() {
        when(mockBridgeConfig.get("admin.email")).thenReturn("admin@email");
        when(mockBridgeConfig.get("admin.password")).thenReturn("adminPassword");
        when(mockBridgeConfig.get("api.developer.email")).thenReturn("dev@email");
        when(mockBridgeConfig.get("api.developer.password")).thenReturn("devPassword");
        when(mockBridgeConfig.get("shared.developer.email")).thenReturn("shared@email");
        when(mockBridgeConfig.get("shared.developer.password")).thenReturn("sharedPassword");

        defaultStudyBootstrapper.onApplicationEvent(null);

        verify(mockAppService, times(2)).createApp(appCaptor.capture());

        List<App> createdStudyList = appCaptor.getAllValues();

        // Validate api app.
        App app = createdStudyList.get(0);
        assertEquals(app.getName(), "Test App");
        assertEquals(app.getIdentifier(), API_APP_ID);
        assertEquals(app.getSponsorName(), "Sage Bionetworks");
        assertEquals(app.getShortName(), "TestApp");
        assertEquals(app.getMinAgeOfConsent(), 18);
        assertEquals(app.getConsentNotificationEmail(), "bridge-testing+consent@sagebase.org");
        assertEquals(app.getTechnicalEmail(), "bridge-testing+technical@sagebase.org");
        assertEquals(app.getSupportEmail(), "support@sagebridge.org");
        assertEquals(app.getUserProfileAttributes(), Sets.newHashSet("can_be_recontacted"));
        assertEquals(app.getPasswordPolicy(), new PasswordPolicy(2, false, false, false, false));
        assertTrue(app.isEmailVerificationEnabled());

        // Validate shared app. No need to test every attribute. Just validate the important attributes.
        App sharedApp = createdStudyList.get(1);
        assertEquals(sharedApp.getName(), "Shared Module Library");
        assertEquals(sharedApp.getIdentifier(), SHARED_APP_ID);

        // So it doesn't get out of sync, validate the app. However, default templates are set 
        // by the service. so those two errors are expected.
        try {
            Validate.entityThrowingException(new AppValidator(), app);    
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().keySet().size(), 2);
            assertEquals(e.getErrors().get("verifyEmailTemplate").size(), 1);
            assertEquals(e.getErrors().get("resetPasswordTemplate").size(), 1);
        }
        
        verify(mockUserAdminService, times(3)).createUser(any(), participantCaptor.capture(),
                subpopCaptor.capture(), eq(false), eq(false));
        
        assertEquals(RequestContext.get().getCallerAppId(), API_APP_ID);
        assertTrue(RequestContext.get().isInRole(SUPERADMIN));
        assertEquals(RequestContext.get().getCallerUserId(), "DefaultStudyBootstrapper");
        
        assertEquals(API_SUBPOP, subpopCaptor.getAllValues().get(0));
        assertEquals(API_SUBPOP, subpopCaptor.getAllValues().get(1));
        assertEquals(SHARED_SUBPOP, subpopCaptor.getAllValues().get(2));
        
        StudyParticipant admin = participantCaptor.getAllValues().get(0);
        assertEquals(admin.getRoles(), ImmutableSet.of(SUPERADMIN));
        assertEquals(admin.getEmail(), "admin@email");
        assertEquals(admin.getPassword(), "adminPassword");
        
        StudyParticipant apiDev = participantCaptor.getAllValues().get(1);
        assertEquals(apiDev.getRoles(), ImmutableSet.of(DEVELOPER));
        assertEquals(apiDev.getEmail(), "dev@email");
        assertEquals(apiDev.getPassword(), "devPassword");
        
        StudyParticipant sharedDev = participantCaptor.getAllValues().get(2);
        assertEquals(sharedDev.getRoles(), ImmutableSet.of(DEVELOPER));
        assertEquals(sharedDev.getEmail(), "shared@email");
        assertEquals(sharedDev.getPassword(), "sharedPassword");
    }
}
