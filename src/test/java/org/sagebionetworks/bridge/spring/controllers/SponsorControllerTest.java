package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.spring.controllers.SponsorController.ADD_SPONSOR_MSG;
import static org.sagebionetworks.bridge.spring.controllers.SponsorController.REMOVE_SPONSOR_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SponsorService;

public class SponsorControllerTest extends Mockito {
    
    private static final String ADD_MSG = String.format(ADD_SPONSOR_MSG, TEST_ORG_ID, TEST_STUDY_ID);
    private static final String REMOVE_MSG = String.format(REMOVE_SPONSOR_MSG, TEST_ORG_ID, TEST_STUDY_ID);

    @Mock
    SponsorService mockService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    SponsorController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SponsorController.class);
        assertGet(SponsorController.class, "getStudySponsors");
        assertGet(SponsorController.class, "getSponsoredStudies");
        assertPost(SponsorController.class, "addStudySponsor");
        assertDelete(SponsorController.class, "removeStudySponsor");
    }
    
    @Test
    public void getStudySponsors() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        PagedResourceList<Organization> page = new PagedResourceList<>(
                ImmutableList.of(Organization.create(), Organization.create()), 100);
        when(mockService.getStudySponsors(TEST_APP_ID, "testStudy", 10, 40)).thenReturn(page);
        
        PagedResourceList<Organization> retValue = controller.getStudySponsors("testStudy", "10", "40");
        assertSame(retValue, page);
        
        verify(mockService).getStudySponsors(TEST_APP_ID, "testStudy", 10, 40);
    }
    
    @Test
    public void getStudySponsorsUsesDefaults() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        PagedResourceList<Organization> page = new PagedResourceList<>(
                ImmutableList.of(Organization.create(), Organization.create()), 100);
        when(mockService.getStudySponsors(TEST_APP_ID, "testStudy", 0, API_DEFAULT_PAGE_SIZE)).thenReturn(page);
        
        PagedResourceList<Organization> retValue = controller.getStudySponsors("testStudy", null, null);
        assertSame(retValue, page);
        
        verify(mockService).getStudySponsors(TEST_APP_ID, "testStudy", 0, API_DEFAULT_PAGE_SIZE);
    }
    
    @Test
    public void getSponsoredStudies() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        PagedResourceList<Study> page = new PagedResourceList<>(
                ImmutableList.of(Study.create(), Study.create()), 100);
        when(mockService.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 15, 75)).thenReturn(page);
        
        PagedResourceList<Study> retValue = controller.getSponsoredStudies(TEST_ORG_ID, "15", "75");
        assertSame(retValue, page);
        
        verify(mockService).getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 15, 75);
    }
    
    @Test
    public void getSponsoredStudiesUsesDefaults() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        PagedResourceList<Study> page = new PagedResourceList<>(
                ImmutableList.of(Study.create(), Study.create()), 100);
        when(mockService.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 0, API_DEFAULT_PAGE_SIZE)).thenReturn(page);
        
        PagedResourceList<Study> retValue = controller.getSponsoredStudies(TEST_ORG_ID, null, null);
        assertSame(retValue, page);
        
        verify(mockService).getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 0, API_DEFAULT_PAGE_SIZE);
    }

    
    @Test
    public void addStudySponsorOkForSuperuser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage retValue = controller.addStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        assertEquals(retValue.getMessage(), ADD_MSG);
    }
        
    @Test
    public void addStudySponsorOkForAdminInOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage retValue = controller.addStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        assertEquals(retValue.getMessage(), ADD_MSG);
    }
    
    @Test
    public void addStudySponsorOkForAdminNotInOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerOrgMembership("some-other-org").build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        controller.addStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test
    public void removeStudySponsorOkForSuperuser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage retValue = controller.removeStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        assertEquals(retValue.getMessage(), REMOVE_MSG);
    }
    
    @Test
    public void removeStudySponsorOkForAdminInOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        StatusMessage retValue = controller.removeStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        assertEquals(retValue.getMessage(), REMOVE_MSG);
    }
    
    @Test
    public void removeStudySponsorOkForAdminNotInOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerOrgMembership("another-org").build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        controller.removeStudySponsor(TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockService).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
}
