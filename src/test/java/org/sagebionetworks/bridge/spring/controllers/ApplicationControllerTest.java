package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertContentType;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.PASSWORD_DESCRIPTION;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.ROBOTS_TXT_CONTENT;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.STUDY_ID;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.STUDY_NAME;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.SUPPORT_EMAIL;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.util.HtmlUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UrlShortenerService;

public class ApplicationControllerTest extends Mockito {

    @Mock
    AppService appService;
    
    @Mock
    AuthenticationService authenticationService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Mock
    UrlShortenerService urlShortenerService;
    
    @Mock
    Model model;
    
    @InjectMocks
    @Spy
    ApplicationController controller;
    
    App app;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        ViewCache viewCache = new ViewCache();
        viewCache.setCachePeriod(BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS);
        viewCache.setObjectMapper(new ObjectMapper());
        viewCache.setCacheProvider(cacheProvider);
        controller.setViewCache(viewCache);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setName("<Test Study>");
        app.setSupportEmail("support@email.com");
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        doReturn(app).when(appService).getApp(TEST_APP_ID);
    }
    
    @Test
    public void robotsFile() {
        ResponseEntity<String> response = controller.getRobots(model);
        assertEquals(response.getStatusCodeValue(), 200);
        assertEquals(response.getBody(), ROBOTS_TXT_CONTENT);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertGet(ApplicationController.class, "getRobots", "/robots.txt");
        assertContentType(ApplicationController.class, "getRobots", "text/plain");
        assertGet(ApplicationController.class, "loadApp", "/", "/index.html");
        assertGet(ApplicationController.class, "verifyStudyEmail", "/vse", "/mobile/verifyStudyEmail.html");
        assertGet(ApplicationController.class, "verifyEmail", "/ve", "/mobile/verifyEmail.html");
        assertGet(ApplicationController.class, "resetPassword", "/rp", "/mobile/resetPassword.html");
        assertGet(ApplicationController.class, "startSessionWithPath", "/s/{studyId}",
                "/mobile/{studyId}/startSession.html");
        assertGet(ApplicationController.class, "startSessionWithQueryParam", "/mobile/startSession.html");
        assertGet(ApplicationController.class, "androidAppLinks", "/.well-known/assetlinks.json");
        assertGet(ApplicationController.class, "appleAppLinks", "/.well-known/apple-app-site-association");
        assertGet(ApplicationController.class, "redirectToURL", "/r/{token}");
    }
    
    @Test
    public void verifyEmailWorks() throws Exception {
        String templateName = controller.verifyEmail(model, TEST_APP_ID);
        
        assertEquals(templateName, "verifyEmail");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        verify(model).addAttribute(SUPPORT_EMAIL, app.getSupportEmail());
        verify(model).addAttribute(STUDY_ID, app.getIdentifier());
        verify(appService).getApp(TEST_APP_ID);
    }

    @Test
    public void verifyStudyEmailWorks() throws Exception {
        String templateName = controller.verifyStudyEmail(model, TEST_APP_ID);

        assertEquals(templateName, "verifyStudyEmail");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        verify(appService).getApp(TEST_APP_ID);
    }

    @Test
    public void resetPasswordWorks() throws Exception {
        String templateName = controller.resetPassword(model, TEST_APP_ID);
        
        assertEquals(templateName, "resetPassword");
        String passwordDescription = BridgeUtils.passwordPolicyDescription(app.getPasswordPolicy());
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        verify(model).addAttribute(SUPPORT_EMAIL, app.getSupportEmail());
        verify(model).addAttribute(STUDY_ID, app.getIdentifier());
        verify(model).addAttribute(PASSWORD_DESCRIPTION, passwordDescription);
        verify(appService).getApp(TEST_APP_ID);
    }

    @Test
    public void startSessionWorksWithRequestParam() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        
        String templateName = controller.startSessionWithQueryParam(model, TEST_APP_ID);
        assertEquals(templateName, "startSession");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        verify(model).addAttribute(STUDY_ID, app.getIdentifier());
        verify(appService).getApp(TEST_APP_ID);
    }

    @Test
    public void startSessionWorksWithPathParam() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        
        String templateName = controller.startSessionWithPath(model, TEST_APP_ID);
        assertEquals(templateName, "startSession");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(app.getName(), "UTF-8"));
        verify(model).addAttribute(STUDY_ID, app.getIdentifier());
        verify(appService).getApp(TEST_APP_ID);
    }
    
    @Test
    public void androidAppLinks() throws Exception {
        DynamoApp study2 = new DynamoApp();
        study2.setIdentifier(TEST_APP_ID);
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(app, study2)).when(appService).getApps();
        
        app.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK);
        app.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_2);
        study2.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_3);
        study2.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_4);
        
        String json = controller.androidAppLinks();
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(TestConstants.ANDROID_APP_LINK, getLinkAtIndex(node, 0));
        assertEquals(TestConstants.ANDROID_APP_LINK_2, getLinkAtIndex(node, 1));
        assertEquals(TestConstants.ANDROID_APP_LINK_3, getLinkAtIndex(node, 2));
        assertEquals(TestConstants.ANDROID_APP_LINK_4, getLinkAtIndex(node, 3));
    }
    
    private AndroidAppLink getLinkAtIndex(JsonNode node, int index) throws Exception {
        return BridgeObjectMapper.get().treeToValue(node.get(index).get("target"), AndroidAppLink.class);
    }

    @Test
    public void appleAppLinks() throws Exception {
        DynamoApp study2 = new DynamoApp();
        study2.setIdentifier(TEST_APP_ID);
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(app, study2)).when(appService).getApps();
        
        app.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK);
        app.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_2);
        study2.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_3);
        study2.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_4);

        String json = controller.appleAppLinks();
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        JsonNode applinks = node.get("applinks");
        JsonNode details = applinks.get("details");
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        AppleAppLink link0 = mapper.readValue(details.get(0).toString(), AppleAppLink.class);
        AppleAppLink link1 = mapper.readValue(details.get(1).toString(), AppleAppLink.class);
        AppleAppLink link2 = mapper.readValue(details.get(2).toString(), AppleAppLink.class);
        AppleAppLink link3 = mapper.readValue(details.get(3).toString(), AppleAppLink.class);
        assertEquals(TestConstants.APPLE_APP_LINK, link0);
        assertEquals(TestConstants.APPLE_APP_LINK_2, link1);
        assertEquals(TestConstants.APPLE_APP_LINK_3, link2);
        assertEquals(TestConstants.APPLE_APP_LINK_4, link3);        
    }

    @Test
    public void redirectOk() throws Exception {
        when(urlShortenerService.retrieveUrl("ABC")).thenReturn("https://long.url.com/");
        
        ResponseEntity<String> response = controller.redirectToURL("ABC");
        assertEquals(302, response.getStatusCodeValue());
        assertEquals("https://long.url.com/", response.getHeaders().get("Location").get(0));
        verify(urlShortenerService).retrieveUrl("ABC");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void redirectBad() throws Exception {
        controller.redirectToURL(" ");
    }
    
    @Test
    public void redirectFails() throws Exception {
        when(urlShortenerService.retrieveUrl("ABC")).thenReturn(null);
        
        ResponseEntity<String> response = controller.redirectToURL("ABC");
        assertEquals(404, response.getStatusCodeValue()); // temporary redirect
    }
}
