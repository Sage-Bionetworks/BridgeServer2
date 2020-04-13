package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.SharedModuleMetadataController.DELETED_MSG;
import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.services.SharedModuleMetadataService;

public class SharedModuleMetadataControllerTest extends Mockito {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private static final String METADATA_JSON_TEXT = "{\n" +
            "   \"id\":\"" + MODULE_ID + "\",\n" +
            "   \"name\":\"" + MODULE_NAME + "\",\n" +
            "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
            "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
            "   \"version\":" + MODULE_VERSION + "\n" +
            "}";

    private SharedModuleMetadataController controller;
    private SharedModuleMetadataService mockSvc;
    private UserSession mockSession;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;

    @BeforeMethod
    public void before() {
        // mock service
        mockSvc = mock(SharedModuleMetadataService.class);

        // spy controller and set dependencies
        controller = spy(new SharedModuleMetadataController());
        controller.setMetadataService(mockSvc);

        // mock controller with session with shared study
        mockSession = new UserSession();
        mockSession.setStudyIdentifier(SHARED_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.DEVELOPER);
        
        mockRequest = mock(HttpServletRequest.class);
        doReturn(mockRequest).when(controller).request();
        
        mockResponse = mock(HttpServletResponse.class);
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SharedModuleMetadataController.class);
        assertCreate(SharedModuleMetadataController.class, "createMetadata");
        assertDelete(SharedModuleMetadataController.class, "deleteMetadataByIdAllVersions");
        assertDelete(SharedModuleMetadataController.class, "deleteMetadataByIdAndVersion");
        assertGet(SharedModuleMetadataController.class, "getMetadataByIdAndVersion");
        assertGet(SharedModuleMetadataController.class, "getMetadataByIdLatestVersion");
        assertGet(SharedModuleMetadataController.class, "queryAllMetadata");
        assertGet(SharedModuleMetadataController.class, "queryMetadataById");
        assertPost(SharedModuleMetadataController.class, "updateMetadata");
    }
    
    @Test
    public void create() throws Exception {
        // mock service
        ArgumentCaptor<SharedModuleMetadata> svcInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        when(mockSvc.createMetadata(svcInputMetadataCaptor.capture())).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        TestUtils.mockRequestBody(mockRequest, METADATA_JSON_TEXT);
        SharedModuleMetadata result = controller.createMetadata();
        assertMetadata(result);
        assertMetadataInArgCaptor(svcInputMetadataCaptor);

        // validate permissions
        verify(controller).getAuthenticatedSession(DEVELOPER);
    }
    
    @Test
    public void deleteMetadataByIdAllVersionsOK() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteMetadataByIdAllVersions(MODULE_ID, false);
        assertEquals(result, DELETED_MSG);

        verify(mockSvc).deleteMetadataByIdAllVersions(MODULE_ID);
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void deleteMetadataByIdAllVersionsDevDefaultsToLogical() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteMetadataByIdAllVersions(MODULE_ID, true);
        assertEquals(result, DELETED_MSG);

        verify(mockSvc).deleteMetadataByIdAllVersions(MODULE_ID);
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void deleteMetadataByIdAllVersionsAdminPhysical() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAllVersions(MODULE_ID, true);
        assertEquals(result, DELETED_MSG);

        // verify backend
        verify(mockSvc).deleteMetadataByIdAllVersionsPermanently(MODULE_ID);

        // validate permissions
        verify(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
    }
    
    @Test
    public void deleteMetadataByIdAndVersionOK() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAndVersion(MODULE_ID, 3, false);
        assertEquals(result, DELETED_MSG);

        verify(mockSvc).deleteMetadataByIdAndVersion(MODULE_ID, 3);
        verify(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
    }
    
    @Test
    public void deleteMetadataByIdAndVersionDevDefaultsToLogical() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAndVersion(MODULE_ID, 3, true);
        assertEquals(result, DELETED_MSG);

        verify(mockSvc).deleteMetadataByIdAndVersion(MODULE_ID, 3);
        verify(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
    }
    
    @Test
    public void deleteMetadataByIdAndVersionAdminPhysical() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAndVersion(MODULE_ID, 3, true);
        assertEquals(result, DELETED_MSG);

        // verify backend
        verify(mockSvc).deleteMetadataByIdAndVersionPermanently(MODULE_ID, 3);
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void deleteByIdAllVersionsPermanently() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAllVersions(MODULE_ID, true);
        assertEquals(result, DELETED_MSG);

        // verify backend
        verify(mockSvc).deleteMetadataByIdAllVersionsPermanently(MODULE_ID);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
    }
    
    @Test
    public void deleteByIdAndVersionPermanently() throws Exception {
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        // setup, execute, and validate
        StatusMessage result = controller.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION, true);
        assertEquals(result, DELETED_MSG);

        // verify backend
        verify(mockSvc).deleteMetadataByIdAndVersionPermanently(MODULE_ID, MODULE_VERSION);

        // validate permissions
        verify(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
    }

    @Test
    public void byIdAndVersion() throws Exception {
        // mock service
        when(mockSvc.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        SharedModuleMetadata result = controller.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertMetadata(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }

    @Test
    public void byIdLatestVersion() throws Exception {
        // mock service
        when(mockSvc.getMetadataByIdLatestVersion(MODULE_ID)).thenReturn(makeValidMetadata());

        // setup, execute, and validate
        SharedModuleMetadata result = controller.getMetadataByIdLatestVersion(MODULE_ID);
        assertMetadata(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }

    @Test
    public void queryAll() throws Exception {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("name", "%name%");
        parameters.put("notes", "%notes%");
        
        // mock service
        when(mockSvc.queryAllMetadata(true, true, "name like :name or notes like :notes", parameters,
                ImmutableSet.of("foo", "bar", "baz"), true)).thenReturn(ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        ResourceList<SharedModuleMetadata> result = controller.queryAllMetadata(true, true, "name", "notes", "foo,bar,baz", true);
        assertMetadataList(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }

    @Test
    public void queryAllWithDefaultIncludeDeleted() throws Exception {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("name", "%name%");
        parameters.put("notes", "%notes%");
        
        // mock service
        when(mockSvc.queryAllMetadata(true, true, "name like :name or notes like :notes", parameters,
                ImmutableSet.of("foo", "bar", "baz"), false)).thenReturn(ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        ResourceList<SharedModuleMetadata> result = controller.queryAllMetadata(true, true, "name", "notes",
                "foo,bar,baz", false);
        assertMetadataList(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }
    
    @Test
    public void queryById() throws Exception {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("name", "%name%");
        parameters.put("notes", "%notes%");
        
        // mock service
        when(mockSvc.queryMetadataById(MODULE_ID, true, true, "name like :name or notes like :notes", parameters,
                ImmutableSet.of("foo", "bar", "baz"), false)).thenReturn(ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        ResourceList<SharedModuleMetadata> result = controller.queryMetadataById(MODULE_ID, true, true, "name", "notes",
                "foo,bar,baz", false);
        assertMetadataList(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }

    @Test
    public void queryByIdWithDefaultIncludeDeleted() throws Exception {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("name", "%name%");
        parameters.put("notes", "%notes%");
        
        // mock service
        when(mockSvc.queryMetadataById(MODULE_ID, true, true, "name like :name or notes like :notes", parameters,
                ImmutableSet.of("foo", "bar", "baz"), false)).thenReturn(ImmutableList.of(makeValidMetadata()));

        // setup, execute, and validate
        ResourceList<SharedModuleMetadata> result = controller.queryMetadataById(MODULE_ID, true, true, "name", "notes",
                "foo,bar,baz", false);
        assertMetadataList(result);

        verify(controller, times(0)).getAuthenticatedSession(any());
    }
    
    @Test
    public void parseTags() {
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags(null));
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags(""));
        assertEquals(ImmutableSet.of(), SharedModuleMetadataController.parseTags("   "));
        assertEquals(ImmutableSet.of("foo"), SharedModuleMetadataController.parseTags("foo"));
        assertEquals(ImmutableSet.of("foo", "bar", "baz"), SharedModuleMetadataController.parseTags("foo,bar,baz"));
    }

    @Test
    public void update() throws Exception {
        // mock service
        ArgumentCaptor<SharedModuleMetadata> svcInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        when(mockSvc.updateMetadata(eq(MODULE_ID), eq(MODULE_VERSION), svcInputMetadataCaptor.capture())).thenReturn(
                makeValidMetadata());

        // setup, execute, and validate
        mockRequestBody(mockRequest, METADATA_JSON_TEXT);
        SharedModuleMetadata result = controller.updateMetadata(MODULE_ID, MODULE_VERSION);
        assertMetadata(result);
        assertMetadataInArgCaptor(svcInputMetadataCaptor);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    private static void assertMetadataInArgCaptor(ArgumentCaptor<SharedModuleMetadata> argCaptor) {
        // JSON validation is already tested, so just check obvious things like module ID
        SharedModuleMetadata arg = argCaptor.getValue();
        assertMetadata(arg);
    }

    private static void assertMetadataList(ResourceList<SharedModuleMetadata> metadataResourceList) throws Exception {
        assertEquals(1, metadataResourceList.getItems().size());

        List<SharedModuleMetadata> metadataList = metadataResourceList.getItems();
        assertEquals(1, metadataList.size());
        assertMetadata(metadataList.get(0));
    }

    private static void assertMetadata(SharedModuleMetadata metadata) {
        assertEquals(MODULE_ID, metadata.getId());
        assertEquals(MODULE_NAME, metadata.getName());
        assertEquals(MODULE_VERSION, metadata.getVersion());
        assertEquals(SCHEMA_ID, metadata.getSchemaId());
        assertEquals(SCHEMA_REV, metadata.getSchemaRevision().intValue());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void nonSharedStudyCantCreate() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TEST_APP_ID);
        controller.createMetadata();
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void nonSharedStudyCantDeleteByIdAllVersions() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
        
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TEST_APP_ID);
        controller.deleteMetadataByIdAllVersions(MODULE_ID, false);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void nonSharedStudyCantDeleteByIdAndVersion() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TEST_APP_ID);
        controller.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION, false);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void nonSharedStudyUpdate() throws Exception {
        // Set session to return API study instead. This will cause the server to throw an 403 Unauthorized.
        mockSession.setStudyIdentifier(TEST_APP_ID);
        controller.updateMetadata(MODULE_ID, MODULE_VERSION);
    }

    private static SharedModuleMetadata makeValidMetadata() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }
}
