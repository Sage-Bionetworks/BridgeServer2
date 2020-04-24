package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.FPHSController.FPHS_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.FPHSService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

public class FPHSControllerTest extends Mockito {

    @Mock
    AuthenticationService mockAuthService;
    
    @Mock
    FPHSService mockFphsService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    NotificationTopicService mockNotificationTopicService;
    
    @Mock
    BridgeConfig mockBridgeConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Spy
    @InjectMocks
    FPHSController controller;
    
    @InjectMocks
    SessionUpdateService sessionUpdateService;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        controller.setSessionUpdateService(sessionUpdateService);
        
        App app = App.create();
        app.setIdentifier(FPHS_ID);

        when(mockStudyService.getStudy(FPHS_ID)).thenReturn(app);
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    private UserSession setUserSession() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withHealthCode("BBB").build();
        
        UserSession session = new UserSession(participant);
        session.setAppId(FPHS_ID);
        session.setAuthenticated(true);
        
        doReturn(session).when(controller).getSessionIfItExists();
        return session;
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(FPHSController.class);
        assertGet(FPHSController.class, "verifyExternalIdentifier");
        assertPost(FPHSController.class, "registerExternalIdentifier");
        assertCreate(FPHSController.class, "addExternalIdentifiers");
    }
    
    @Test
    public void verifyOK() throws Exception {
        FPHSExternalIdentifier result = controller.verifyExternalIdentifier("foo");
        
        // No session is required
        verifyNoMoreInteractions(mockAuthService);
        assertEquals(result.getExternalId(), "foo");
    }
    
    @Test
    public void verifyFails() throws Exception {
        doThrow(new EntityNotFoundException(FPHSExternalIdentifier.class)).when(mockFphsService)
                .verifyExternalIdentifier(any());
        
        try {
            controller.verifyExternalIdentifier("foo");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals(e.getMessage(), "ExternalIdentifier not found.");
        }
    }
    
    @Test
    public void verifyFailsWhenNull() throws Exception {
        doThrow(new InvalidEntityException("ExternalIdentifier cannot be blank, null or missing."))
                .when(mockFphsService).verifyExternalIdentifier(any());
        
        try {
            controller.verifyExternalIdentifier(null);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getMessage(), "ExternalIdentifier cannot be blank, null or missing.");
        }
    }
    
    @Test
    public void registrationRequiresAuthenticatedConsentedUser() throws Exception {
        mockRequestBody(mockRequest, ExternalIdentifier.create(FPHS_ID, "foo"));
        try {
            controller.registerExternalIdentifier();
            fail("Should have thrown exception");
        } catch(NotAuthenticatedException e) {
            assertEquals(e.getMessage(), "Not signed in.");
        }
    }

    @Test
    public void registrationOK() throws Exception {
        UserSession session = setUserSession();
        mockRequestBody(mockRequest, ExternalIdentifier.create(FPHS_ID, "foo"));

        StatusMessage result = controller.registerExternalIdentifier();
        assertEquals(result.getMessage(), "External identifier added to user profile.");

        assertEquals(session.getParticipant().getDataGroups(), ImmutableSet.of("football_player"));
        verify(mockConsentService).getConsentStatuses(any(CriteriaContext.class));
    }
    
    @Test
    public void addIdentifiersRequiresAdmin() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("AAA");
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("BBB");
        mockRequestBody(mockRequest, ImmutableList.of(id1, id2));
        
        // There's a user, but not an admin user
        setUserSession();
        try {
            controller.addExternalIdentifiers();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            assertEquals("Caller does not have permission to access this service.", e.getMessage());
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void addIdentifiersOK() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("AAA");
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("BBB");
        mockRequestBody(mockRequest, ImmutableList.of(id1, id2));
        
        UserSession session = setUserSession();
        // Now when we have an admin user, we get back results
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        StatusMessage result = controller.addExternalIdentifiers();
        assertEquals(result.getMessage(), "External identifiers added.");
        
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mockFphsService).addExternalIdentifiers(captor.capture());
        
        List<FPHSExternalIdentifier> passedList = (List<FPHSExternalIdentifier>)captor.getValue();
        assertEquals(passedList.size(), 2);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void addIdentifiersOKRejectsStudyAdmin() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("AAA");
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("BBB");
        mockRequestBody(mockRequest, ImmutableList.of(id1, id2));
        
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(App.create());
        
        UserSession session = setUserSession();
        session.setAppId(TEST_APP_ID);
        // Now when we have an admin user, we get back results
        session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.addExternalIdentifiers();
    }
}
