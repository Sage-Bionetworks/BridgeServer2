package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

import org.apache.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.AppService;

public class ExternalIdControllerV4Test extends Mockito {

    @Mock
    ExternalIdService mockService;

    @Mock
    AppService mockAppService;

    @Mock
    AuthenticationService mockAuthService;

    @Mock
    BridgeConfig mockBridgeConfig;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @Captor
    ArgumentCaptor<ExternalIdentifier> externalIdCaptor;

    ForwardCursorPagedResourceList<ExternalIdentifierInfo> list;

    App app;

    UserSession session;

    @Spy
    @InjectMocks
    ExternalIdControllerV4 controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        List<ExternalIdentifierInfo> items = ImmutableList.of(new ExternalIdentifierInfo("id1", null, true),
                new ExternalIdentifierInfo("id2", null, false));
        list = new ForwardCursorPagedResourceList<>(items, "nextPageOffsetKey");

        app = App.create();
        app.setIdentifier(TEST_APP_ID);

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ExternalIdControllerV4.class);
        assertGet(ExternalIdControllerV4.class, "getExternalIdentifiers");
        assertCreate(ExternalIdControllerV4.class, "createExternalIdentifier");
        assertDelete(ExternalIdControllerV4.class, "deleteExternalIdentifier");
        assertPost(ExternalIdControllerV4.class, "generatePassword");
        assertGet(ExternalIdControllerV4.class, "getExternalIdentifiersForStudy");
    }
    
    @Test
    public void getExternalIdentifiers() throws Exception {
        try {
            controller.getExternalIdentifiers(null, null, null, null);    
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_GONE);
        }
    }

    @Test
    public void createExternalIdentifier() throws Exception {
        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        extId.setStudyId("studyId");
        mockRequestBody(mockRequest, extId);

        try {
            controller.createExternalIdentifier();
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_GONE);
        }
    }

    @Test
    public void deleteExternalIdentifier() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        StatusMessage result = controller.deleteExternalIdentifier("externalId");
        assertEquals(result.getMessage(), "External identifier deleted from account.");

        verify(mockService).deleteExternalIdPermanently(eq(app), externalIdCaptor.capture());
        assertEquals(externalIdCaptor.getValue().getIdentifier(), "externalId");
        assertEquals(externalIdCaptor.getValue().getAppId(), TEST_APP_ID);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void generatePasswordRequiresResearcher() throws Exception {
        controller.generatePassword("extid");
    }

    @Test
    public void generatePassword() throws Exception {
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);
        GeneratedPassword password = new GeneratedPassword("extid", "user-id", "some-password");
        when(mockAuthService.generatePassword(app, "extid")).thenReturn(password);

        GeneratedPassword result = controller.generatePassword("extid");
        assertEquals(result.getExternalId(), "extid");
        assertEquals(result.getPassword(), "some-password");
        assertEquals(result.getUserId(), "user-id");

        verify(mockAuthService).generatePassword(app, "extid");
    }
    
    @Test
    public void getExternalIdentifiersForStudy() {
        PagedResourceList<ExternalIdentifierInfo> page = new PagedResourceList<>(ImmutableList.of(), 1000, true);
        when(mockService.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, "idFilter", 1000, 50))
            .thenReturn(page);
        
        PagedResourceList<ExternalIdentifierInfo> retValue = controller.getExternalIdentifiersForStudy(
                TEST_STUDY_ID, "1000", "50", "idFilter");
        assertEquals(retValue, page);
        
        verify(mockService).getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, "idFilter", 1000, 50);
    }

    @Test
    public void getExternalIdentifiersForNoParameters() {
        PagedResourceList<ExternalIdentifierInfo> page = new PagedResourceList<>(ImmutableList.of(), 1000, true);
        when(mockService.getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, null, 0, API_DEFAULT_PAGE_SIZE))
            .thenReturn(page);
        
        PagedResourceList<ExternalIdentifierInfo> retValue = controller.getExternalIdentifiersForStudy(
                TEST_STUDY_ID, null, null, null);
        assertEquals(retValue, page);
        
        verify(mockService).getPagedExternalIds(TEST_APP_ID, TEST_STUDY_ID, null, 0, API_DEFAULT_PAGE_SIZE);        
    }
}
