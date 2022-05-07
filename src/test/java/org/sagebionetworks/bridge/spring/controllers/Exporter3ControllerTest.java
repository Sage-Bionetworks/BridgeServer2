package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.testng.Assert.assertSame;

import com.google.common.collect.ImmutableSet;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.services.Exporter3Service;

public class Exporter3ControllerTest {
    @Mock
    private Exporter3Service mockSvc;

    @InjectMocks
    @Spy
    private Exporter3Controller controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(Exporter3Controller.class);
        assertPost(Exporter3Controller.class, "initExporter3");
        assertPost(Exporter3Controller.class, "initExporter3ForStudy");
    }

    @Test
    public void initExporter3() throws Exception {
        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.initExporter3(TestConstants.TEST_APP_ID)).thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.initExporter3();
        assertSame(retVal, ex3Config);
    }

    @Test
    public void initExporter3ForStudy() throws Exception {
        // Set up request context.
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID))
                .thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.initExporter3ForStudy(TestConstants.TEST_STUDY_ID);
        assertSame(retVal, ex3Config);
    }

    @Test
    public void exportTimelineForStudy() throws Exception{
        // Set up request context.
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.exportTimelineForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID))
                .thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.exportTimelineForStudy(TestConstants.TEST_STUDY_ID);
        assertSame(retVal, ex3Config);
    }
}
