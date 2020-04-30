package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.UploadSchemaController.DELETED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.UploadSchemaController.DELETED_REVISION_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadSchemaControllerTest extends Mockito {
    private static final String TEST_SCHEMA_ID = "controller-test-schema";
    private static final String TEST_SCHEMA_JSON = "{\n" +
                    "   \"name\":\"Controller Test Schema\",\n" +
                    "   \"revision\":3,\n" +
                    "   \"schemaId\":\"controller-test-schema\",\n" +
                    "   \"schemaType\":\"ios_data\",\n" +
                    "   \"fieldDefinitions\":[\n" +
                    "       {\n" +
                    "           \"name\":\"field-name\",\n" +
                    "           \"required\":true,\n" +
                    "           \"type\":\"STRING\"\n" +
                    "       }\n" +
                    "   ]\n" +
                    "}";

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UploadSchemaController.class);
        assertCreate(UploadSchemaController.class, "createSchemaRevisionV4");
        assertPost(UploadSchemaController.class, "createOrUpdateUploadSchema");
        assertDelete(UploadSchemaController.class, "deleteAllRevisionsOfUploadSchema");
        assertDelete(UploadSchemaController.class, "deleteSchemaRevision");
        assertGet(UploadSchemaController.class, "getUploadSchema");
        assertGet(UploadSchemaController.class, "getUploadSchemaAllRevisions");
        assertGet(UploadSchemaController.class, "getUploadSchemaByIdAndRev");
        assertGet(UploadSchemaController.class, "getUploadSchemaByAppAndSchemaAndRev");
        assertGet(UploadSchemaController.class, "getUploadSchemasForApp");
        assertPost(UploadSchemaController.class, "updateSchemaRevisionV4");
    }
    
    @Test
    public void createV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createSchemaRevisionV4(eq(TEST_APP_ID), createdSchemaCaptor.capture())).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        String result = controller.createSchemaRevisionV4();
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaCaptor);
    }

    @Test
    public void createSchema() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> createdSchemaArgCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.createOrUpdateUploadSchema(eq(TEST_APP_ID), createdSchemaArgCaptor.capture()))
                .thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        String result = controller.createOrUpdateUploadSchema();
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(createdSchemaArgCaptor);
    }

    @Test
    public void deleteAllRevisionsOfUploadSchema() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", false);
        assertEquals(result, DELETED_MSG);
        verify(mockSvc).deleteUploadSchemaById(TEST_APP_ID, "delete-schema");
    }
    
    @Test
    public void deleteAllRevisionsOfUploadSchemaPermanently() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", true);
        assertEquals(result, DELETED_MSG);
        verify(mockSvc).deleteUploadSchemaByIdPermanently(TEST_APP_ID, "delete-schema");
    }
    
    @Test
    public void deleteAllRevisionsOfUploadSchemaPermanentlyForDeveloperIsLogical() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithServiceWithoutSecondRole(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteAllRevisionsOfUploadSchema("delete-schema", true);
        assertEquals(result, DELETED_MSG);
        verify(mockSvc).deleteUploadSchemaById(TEST_APP_ID, "delete-schema");
    }
    
    @Test
    public void deleteSchemaRevision() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteSchemaRevision("delete-schema", 4, false);
        assertEquals(result, DELETED_REVISION_MSG);
        verify(mockSvc).deleteUploadSchemaByIdAndRevision(TEST_APP_ID, "delete-schema", 4);
    }
    
    @Test
    public void deleteSchemaRevisionPermanently() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteSchemaRevision("delete-schema", 4, true);
        assertEquals(result, DELETED_REVISION_MSG);
        verify(mockSvc).deleteUploadSchemaByIdAndRevisionPermanently(TEST_APP_ID, "delete-schema", 4);
    }
    
    @Test
    public void deleteSchemaRevisionPermanentlyForDeveloperIsLogical() throws Exception {
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithServiceWithoutSecondRole(mockSvc, DEVELOPER, ADMIN);
        
        StatusMessage result = controller.deleteSchemaRevision("delete-schema", 4, true);
        assertEquals(result, DELETED_REVISION_MSG);
        // We do not call the permanent delete, we call the logical delete, as the user is a developer.
        verify(mockSvc).deleteUploadSchemaByIdAndRevision(TEST_APP_ID, "delete-schema", 4);
    }
    
    @Test
    public void getSchemaById() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchema(TEST_APP_ID, TEST_SCHEMA_ID)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        String result = controller.getUploadSchema(TEST_SCHEMA_ID);
        assertSchemaInResult(result);
    }

    @Test
    public void getSchemaByIdAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TEST_APP_ID, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, WORKER);
        String result = controller.getUploadSchemaByIdAndRev(TEST_SCHEMA_ID, 1);
        assertSchemaInResult(result);
    }

    @Test
    public void getByStudyAndSchemaAndRev() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaByIdAndRev(TEST_APP_ID, TEST_SCHEMA_ID, 1)).thenReturn(
                makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, WORKER);
        UploadSchema result = controller.getUploadSchemaByAppAndSchemaAndRev(TEST_APP_ID, TEST_SCHEMA_ID, 1);

        // Unlike the other methods, this also returns appId
        assertEquals(result.getSchemaId(), TEST_SCHEMA_ID);
        assertEquals(result.getAppId(), TEST_APP_ID);
    }

    @Test
    public void getSchemasForStudyNoDeleted() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForApp(TEST_APP_ID, false)).thenReturn(ImmutableList.of(
                makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, RESEARCHER);
        String result = controller.getUploadSchemasForApp(false);

        JsonNode resultNode = BridgeObjectMapper.get().readTree(result);
        assertEquals(resultNode.get("type").textValue(), "ResourceList");
        assertEquals(resultNode.get("items").size(), 1);

        JsonNode itemListNode = resultNode.get("items");
        assertEquals(itemListNode.size(), 1);

        UploadSchema resultSchema = BridgeObjectMapper.get().treeToValue(itemListNode.get(0), UploadSchema.class);
        assertEquals(resultSchema.getSchemaId(), TEST_SCHEMA_ID);
        assertNull(resultSchema.getAppId());
    }

    @Test
    public void getSchemasForStudyIncludeDeleted() throws Exception {
        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemasForApp(TEST_APP_ID, true))
                .thenReturn(ImmutableList.of(makeUploadSchemaForOutput()));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER, RESEARCHER);
        controller.getUploadSchemasForApp(true);
        
        verify(mockSvc).getUploadSchemasForApp(TEST_APP_ID, true);
    }
    
    @Test
    public void getAllRevisionsOfASchemaExcludeDeleted() throws Exception {
        String schemaId = "controller-test-schema";

        // Create a couple of revisions
        UploadSchema schema1 = makeUploadSchemaForOutput(1);
        UploadSchema schema2 = makeUploadSchemaForOutput(2);
        UploadSchema schema3 = makeUploadSchemaForOutput(3);

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TEST_APP_ID, schemaId, false)).thenReturn(ImmutableList.of(
                schema3, schema2, schema1));

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        String result = controller.getUploadSchemaAllRevisions(schemaId, false);

        JsonNode resultNode = BridgeObjectMapper.get().readTree(result);
        assertEquals(resultNode.get("type").textValue(), "ResourceList");
        assertEquals(resultNode.get("items").size(), 3);

        JsonNode itemsNode = resultNode.get("items");
        assertEquals(itemsNode.size(), 3);

        // Schemas are returned in reverse order.
        UploadSchema returnedSchema3 = BridgeObjectMapper.get().treeToValue(itemsNode.get(0), UploadSchema.class);
        assertEquals(returnedSchema3.getRevision(), 3);
        assertEquals(returnedSchema3.getSchemaId(), TEST_SCHEMA_ID);
        assertNull(returnedSchema3.getAppId());

        UploadSchema returnedSchema2 = BridgeObjectMapper.get().treeToValue(itemsNode.get(1), UploadSchema.class);
        assertEquals(returnedSchema2.getRevision(), 2);
        assertEquals(returnedSchema2.getSchemaId(), TEST_SCHEMA_ID);
        assertNull(returnedSchema2.getAppId());

        UploadSchema returnedSchema1 = BridgeObjectMapper.get().treeToValue(itemsNode.get(2), UploadSchema.class);
        assertEquals(returnedSchema1.getRevision(), 1);
        assertEquals(returnedSchema1.getSchemaId(), TEST_SCHEMA_ID);
        assertNull(returnedSchema1.getAppId());
    }

    @Test
    public void getAllRevisionsOfASchemaIncludeDeleted() throws Exception {
        String schemaId = "controller-test-schema";

        // mock UploadSchemaService
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        when(mockSvc.getUploadSchemaAllRevisions(TEST_APP_ID, schemaId, false)).thenReturn(ImmutableList.of());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        controller.getUploadSchemaAllRevisions(schemaId, true);

        verify(mockSvc).getUploadSchemaAllRevisions(TEST_APP_ID, schemaId, true);
    }
    
    @Test
    public void updateV4() throws Exception {
        // mock service
        UploadSchemaService mockSvc = mock(UploadSchemaService.class);
        ArgumentCaptor<UploadSchema> updatedSchemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSvc.updateSchemaRevisionV4(eq(TEST_APP_ID), eq(TEST_SCHEMA_ID), eq(1),
                updatedSchemaCaptor.capture())).thenReturn(makeUploadSchemaForOutput());

        // setup, execute, and validate
        UploadSchemaController controller = setupControllerWithService(mockSvc, DEVELOPER);
        String result = controller.updateSchemaRevisionV4(TEST_SCHEMA_ID, 1);
        assertSchemaInResult(result);
        assertSchemaInArgCaptor(updatedSchemaCaptor);
    }

    private static UploadSchemaController setupControllerWithServiceWithoutSecondRole(UploadSchemaService svc, Roles role1, Roles role2) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1)).build());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1, role2);

        // mock request JSON
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        mockRequestBody(mockRequest, TEST_SCHEMA_JSON);
        doReturn(mockRequest).when(controller).request();
        
        return controller;
    }
    
    private static UploadSchemaController setupControllerWithService(UploadSchemaService svc, Roles role1, Roles role2) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1, role2)).build());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1, role2);

        // mock request JSON
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        mockRequestBody(mockRequest, TEST_SCHEMA_JSON);
        doReturn(mockRequest).when(controller).request();
        
        return controller;
    }
    
    private static UploadSchemaController setupControllerWithService(UploadSchemaService svc, Roles role1) throws Exception {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);
        mockSession.setParticipant(new StudyParticipant.Builder().withRoles(Sets.newHashSet(role1)).build());

        // spy controller
        UploadSchemaController controller = spy(new UploadSchemaController());
        controller.setUploadSchemaService(svc);
        controller.setBridgeConfig(mock(BridgeConfig.class));
        doReturn(mockSession).when(controller).getAuthenticatedSession(role1);

        // mock request JSON
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        mockRequestBody(mockRequest, TEST_SCHEMA_JSON);
        doReturn(mockRequest).when(controller).request();
        
        return controller;
    }

    private static UploadSchema makeUploadSchemaForOutput() throws Exception {
        return makeUploadSchemaForOutput(3);
    }
    
    private static UploadSchema makeUploadSchemaForOutput(int revision) throws Exception {
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().readTree(TEST_SCHEMA_JSON);
        node.put("revision", revision);

        // Server returns schemas with app IDs (which are filtered out selectively in some methods).
        node.put("appId", TEST_APP_ID);
        node.put("studyId", TEST_APP_ID);

        return BridgeObjectMapper.get().convertValue(node, UploadSchema.class);
    }

    private static void assertSchemaInResult(String result) throws Exception {
        // JSON validation is already tested, so just check obvious things like schema ID
        // Also, (most) method results don't include appId
        UploadSchema schema = BridgeObjectMapper.get().readValue(result, UploadSchema.class);
        assertEquals(schema.getSchemaId(), TEST_SCHEMA_ID);
        assertNull(schema.getAppId());
    }

    private static void assertSchemaInArgCaptor(ArgumentCaptor<UploadSchema> argCaptor) {
        // Similarly, just check schema ID
        UploadSchema arg = argCaptor.getValue();
        assertEquals(arg.getSchemaId(), TEST_SCHEMA_ID);
    }
}
