package org.sagebionetworks.bridge.spring.controllers;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.HealthDataDocumentationService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.testng.Assert.assertSame;
import static org.testng.AssertJUnit.assertEquals;

public class HealthDataDocumentationControllerTest {
    private static final String TEST_DOCUMENTATION = "test documentation";

    @Mock
    private HealthDataDocumentationService mockService;

    @InjectMocks
    @Spy
    private HealthDataDocumentationController controller;

    @Mock
    private HttpServletRequest mockRequest;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock request
        doReturn(mockRequest).when(controller).request();

        // Mock session
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(HealthDataController.class);
        assertPost(HealthDataDocumentationController.class, "createOrUpdateHealthDataDocumentation");
        assertGet(HealthDataDocumentationController.class, "getHealthDataDocumentationForId");
        assertGet(HealthDataDocumentationController.class, "getAllHealthDataDocumentationForParentId");
        assertDelete(HealthDataDocumentationController.class, "deleteHealthDataDocumentationForIdentifier");
        assertDelete(HealthDataDocumentationController.class, "deleteAllHealthDataDocumentationForParentId");
    }

    @Test
    public void createOrUpdateHealthDataDocumentation() throws Exception {
        HealthDataDocumentation doc = HealthDataDocumentation.create();
        doc.setIdentifier(IDENTIFIER);
        doc.setDocumentation(TEST_DOCUMENTATION);
        mockRequestBody(mockRequest, doc);

        when(mockService.createOrUpdateHealthDataDocumentation(any(HealthDataDocumentation.class))).thenReturn(doc);
        HealthDataDocumentation result = controller.createOrUpdateHealthDataDocumentation();

        ArgumentCaptor<HealthDataDocumentation> docCaptor = ArgumentCaptor.forClass(HealthDataDocumentation.class);
        verify(controller).setDocParentIdHelper(docCaptor.capture(), eq(TEST_APP_ID));
        assertEquals(docCaptor.getValue().getParentId(), TEST_APP_ID);
        assertEquals(result.getIdentifier(), IDENTIFIER);
        assertEquals(result.getDocumentation(), TEST_DOCUMENTATION);
    }

    @Test
    public void getHealthDataDocumentationForId() {
        HealthDataDocumentation doc = HealthDataDocumentation.create();
        doc.setIdentifier(IDENTIFIER);
        when(mockService.getHealthDataDocumentationForId(TEST_APP_ID, IDENTIFIER)).thenReturn(doc);

        HealthDataDocumentation result = controller.getHealthDataDocumentationForId(IDENTIFIER);
        assertSame(result, doc);

        verify(mockService).getHealthDataDocumentationForId(TEST_APP_ID, IDENTIFIER);
    }

    @Test
    public void getAllHealthDocumentationForParentId() {
        ForwardCursorPagedResourceList<HealthDataDocumentation> docList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(HealthDataDocumentation.create()), null);
        when(mockService.getAllHealthDataDocumentation(TEST_APP_ID, 10, OFFSET_KEY)).thenReturn(docList);

        ForwardCursorPagedResourceList<HealthDataDocumentation> resultList =
                controller.getAllHealthDataDocumentationForParentId("10", OFFSET_KEY);
        assertSame(resultList, docList);
        assertEquals(resultList.getRequestParams().size(), 1);
        assertEquals(resultList.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        verify(mockService).getAllHealthDataDocumentation(TEST_APP_ID, 10, OFFSET_KEY);
    }

    @Test
    public void deleteHealthDataDocumentationForIdentifier() {
        StatusMessage statusMessage = controller.deleteHealthDataDocumentationForIdentifier(IDENTIFIER);
        assertEquals(statusMessage.getMessage(), "Health data documentation has been deleted for the given identifier.");

        verify(mockService).deleteHealthDataDocumentation(TEST_APP_ID, IDENTIFIER);
    }

    @Test
    public void deleteAllHealthDataDocumentationForParentId() {
        StatusMessage statusMessage = controller.deleteAllHealthDataDocumentationForParentId();
        assertEquals(statusMessage.getMessage(), "Health data documentation has been deleted.");

        verify(mockService).deleteAllHealthDataDocumentation(TEST_APP_ID);
    }
}