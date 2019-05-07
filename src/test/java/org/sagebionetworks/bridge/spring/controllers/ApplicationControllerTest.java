package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.PASSWORD_DESCRIPTION;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.STUDY_ID;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.STUDY_NAME;
import static org.sagebionetworks.bridge.spring.controllers.ApplicationController.SUPPORT_EMAIL;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UrlShortenerService;

public class ApplicationControllerTest extends Mockito {

    @Mock
    StudyService studyService;
    
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
    
    Study study;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        ViewCache viewCache = new ViewCache();
        viewCache.setCachePeriod(BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS);
        viewCache.setObjectMapper(new ObjectMapper());
        viewCache.setCacheProvider(cacheProvider);
        controller.setViewCache(viewCache);
        
        study = Study.create();
        study.setIdentifier("test-study");
        study.setName("<Test Study>");
        study.setSupportEmail("support@email.com");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        doReturn(study).when(studyService).getStudy("test-study");
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
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
        String templateName = controller.verifyEmail(model, "test-study");
        
        assertEquals(templateName, "verifyEmail");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        verify(model).addAttribute(SUPPORT_EMAIL, study.getSupportEmail());
        verify(model).addAttribute(STUDY_ID, study.getIdentifier());
        verify(studyService).getStudy("test-study");
    }

    @Test
    public void verifyStudyEmailWorks() throws Exception {
        String templateName = controller.verifyStudyEmail(model, "test-study");

        assertEquals(templateName, "verifyStudyEmail");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        verify(studyService).getStudy("test-study");
    }

    @Test
    public void resetPasswordWorks() throws Exception {
        String templateName = controller.resetPassword(model, "test-study");
        
        assertEquals(templateName, "resetPassword");
        String passwordDescription = BridgeUtils.passwordPolicyDescription(study.getPasswordPolicy());
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        verify(model).addAttribute(SUPPORT_EMAIL, study.getSupportEmail());
        verify(model).addAttribute(STUDY_ID, study.getIdentifier());
        verify(model).addAttribute(PASSWORD_DESCRIPTION, passwordDescription);
        verify(studyService).getStudy("test-study");
    }

    @Test
    public void startSessionWorksWithRequestParam() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        
        String templateName = controller.startSessionWithQueryParam(model, "test-study", "email", "token");
        assertEquals(templateName, "startSession");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        verify(model).addAttribute(STUDY_ID, study.getIdentifier());
        verify(studyService).getStudy("test-study");
    }

    @Test
    public void startSessionWorksWithPathParam() throws Exception {
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        
        String templateName = controller.startSessionWithPath(model, "test-study", "email", "token");
        assertEquals(templateName, "startSession");
        verify(model).addAttribute(STUDY_NAME, HtmlUtils.htmlEscape(study.getName(), "UTF-8"));
        verify(model).addAttribute(STUDY_ID, study.getIdentifier());
        verify(studyService).getStudy("test-study");
    }
    
    @Test
    public void androidAppLinks() throws Exception {
        DynamoStudy study2 = new DynamoStudy();
        study2.setIdentifier("test-study2");
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(study, study2)).when(studyService).getStudies();
        
        study.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK);
        study.getAndroidAppLinks().add(TestConstants.ANDROID_APP_LINK_2);
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
        DynamoStudy study2 = new DynamoStudy();
        study2.setIdentifier("test-study2");
        study2.setSupportEmail("support@email.com");
        study2.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        doReturn(ImmutableList.of(study, study2)).when(studyService).getStudies();
        
        study.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK);
        study.getAppleAppLinks().add(TestConstants.APPLE_APP_LINK_2);
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
