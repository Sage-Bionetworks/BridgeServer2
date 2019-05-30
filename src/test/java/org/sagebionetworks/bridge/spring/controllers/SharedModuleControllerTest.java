package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.testng.Assert.assertEquals;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleImportStatus;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleType;
import org.sagebionetworks.bridge.services.SharedModuleService;

public class SharedModuleControllerTest extends Mockito {
    private static final String MODULE_ID = "test-module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private SharedModuleController controller;
    private SharedModuleService mockSvc;

    @BeforeMethod
    public void before() {
        // mock service and controller
        mockSvc = mock(SharedModuleService.class);
        controller = spy(new SharedModuleController());
        controller.setModuleService(mockSvc);

        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SharedModuleController.class);
        assertPost(SharedModuleController.class, "importModuleByIdAndVersion");
        assertPost(SharedModuleController.class, "importModuleByIdLatestPublishedVersion");
    }

    @Test
    public void byIdAndVersion() throws Exception {
        // mock service
        when(mockSvc.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID, MODULE_VERSION)).thenReturn(
                new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV));

        // setup, execute, and validate
        SharedModuleImportStatus result = controller.importModuleByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertStatus(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void latestPublished() throws Exception {
        // mock service
        when(mockSvc.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, MODULE_ID)).thenReturn(
                new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV));

        // setup, execute, and validate
        SharedModuleImportStatus result = controller.importModuleByIdLatestPublishedVersion(MODULE_ID);
        assertStatus(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    private static void assertStatus(SharedModuleImportStatus status) throws Exception {
        assertEquals(status.getModuleType(), SharedModuleType.SCHEMA);
        assertEquals(status.getSchemaId(), SCHEMA_ID);
        assertEquals(status.getSchemaRevision(), new Integer(SCHEMA_REV));
    }

}
