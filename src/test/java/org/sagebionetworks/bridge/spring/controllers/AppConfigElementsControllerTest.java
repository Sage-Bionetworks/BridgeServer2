package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.services.AppConfigElementService;

public class AppConfigElementsControllerTest {
    
    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private static final List<AppConfigElement> APP_CONFIG_ELEMENTS = ImmutableList.of(AppConfigElement.create(),
            AppConfigElement.create());
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);

    @Mock
    AppConfigElementService mockService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Captor
    ArgumentCaptor<AppConfigElement> elementCaptor;
    
    @Captor
    ArgumentCaptor<CacheKey> cacheKeyCaptor;
    
    @InjectMocks
    @Spy
    AppConfigElementsController controller;
    
    private UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        session = new UserSession(new StudyParticipant.Builder().build());
        session.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }
    
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertGet(AppConfigElementsController.class, "getMostRecentElements");
        assertPost(AppConfigElementsController.class, "createElement");
        assertGet(AppConfigElementsController.class, "getElementRevisions");
        assertGet(AppConfigElementsController.class, "getMostRecentElement");
        assertGet(AppConfigElementsController.class, "getElementRevision");
        assertPost(AppConfigElementsController.class, "updateElementRevision");
        assertDelete(AppConfigElementsController.class, "deleteElementAllRevisions");
        assertDelete(AppConfigElementsController.class, "deleteElementRevision");
    }
    
    @Test
    public void getMostRecentElementsIncludeDeleted() throws Exception {
        when(mockService.getMostRecentElements(TEST_STUDY_IDENTIFIER, true)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getMostRecentElements("true");
        
        assertEquals(2, result.getItems().size());
        assertTrue((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(mockService).getMostRecentElements(TEST_STUDY_IDENTIFIER, true);
    }
    
    @Test
    public void getMostRecentElementsExcludeDeleted() throws Exception {
        when(mockService.getMostRecentElements(TEST_STUDY_IDENTIFIER, false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getMostRecentElements("false");

        assertEquals(2, result.getItems().size());
        assertFalse((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(mockService).getMostRecentElements(TEST_STUDY_IDENTIFIER, false);
    }
    
    @Test
    public void getMostRecentElementsDefaultToExcludeDeleted() throws Exception {
        when(mockService.getMostRecentElements(TEST_STUDY_IDENTIFIER, false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getMostRecentElements(null);
        
        assertEquals(2, result.getItems().size());
        assertFalse((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(mockService).getMostRecentElements(TEST_STUDY_IDENTIFIER, false);
    }
    
    @Test
    public void createElement() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        TestUtils.mockRequestBody(mockRequest, element);
        
        when(mockService.createElement(eq(TEST_STUDY_IDENTIFIER), any())).thenReturn(VERSION_HOLDER);
        
        VersionHolder result = controller.createElement();
        assertEquals(new Long(1), result.getVersion());
        
        verify(mockCacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(mockService).createElement(eq(TEST_STUDY_IDENTIFIER), elementCaptor.capture());
        assertEquals("element-id", elementCaptor.getValue().getId());
    }

    @Test
    public void getElementRevisionsIncludeDeleted() throws Exception {
        when(mockService.getElementRevisions(TEST_STUDY_IDENTIFIER, "id", true)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getElementRevisions("id", "true");
        
        assertEquals(2, result.getItems().size());
        assertTrue((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(mockService).getElementRevisions(TEST_STUDY_IDENTIFIER, "id", true);
    }
    
    @Test
    public void getElementRevisionsExcludeDeleted() throws Exception {
        when(mockService.getElementRevisions(TEST_STUDY_IDENTIFIER, "id", false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getElementRevisions("id", "false");
        
        assertEquals(2, result.getItems().size());
        assertFalse((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(mockService).getElementRevisions(TEST_STUDY_IDENTIFIER, "id", false);
    }
    
    @Test
    public void getElementRevisionsDefaultsToExcludeDeleted() throws Exception {
        when(mockService.getElementRevisions(TEST_STUDY_IDENTIFIER, "id", false)).thenReturn(APP_CONFIG_ELEMENTS);
        
        ResourceList<AppConfigElement> result = controller.getElementRevisions("id", null);
        
        assertEquals(2, result.getItems().size());
        assertFalse((Boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(mockService).getElementRevisions(TEST_STUDY_IDENTIFIER, "id", false);
    }
    
    @Test
    public void getMostRecentElement() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        when(mockService.getMostRecentElement(TEST_STUDY_IDENTIFIER, "element-id")).thenReturn(element);
        
        AppConfigElement result = controller.getMostRecentElement("element-id");
        assertEquals("element-id", result.getId());
        
        verify(mockService).getMostRecentElement(TEST_STUDY_IDENTIFIER, "element-id");
    }

    @Test
    public void getElementRevision() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        element.setId("element-id");
        when(mockService.getElementRevision(TEST_STUDY_IDENTIFIER, "id", 3L)).thenReturn(element);
        
        AppConfigElement result = controller.getElementRevision("id", "3");
        assertEquals("element-id", result.getId());
        
        verify(mockService).getElementRevision(TEST_STUDY_IDENTIFIER, "id", 3L);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getElementRevisionBadRevisionNumber() throws Exception {
        controller.getElementRevision("id", "three");
    }
    
    @Test
    public void updateElementRevision() throws Exception {
        AppConfigElement element = AppConfigElement.create();
        // These values should be overwritten by the values in the URL
        element.setId("element-id");
        element.setRevision(3L);
        mockRequestBody(mockRequest, element);
        when(mockService.updateElementRevision(eq(TEST_STUDY_IDENTIFIER), any())).thenReturn(VERSION_HOLDER);
        
        VersionHolder result = controller.updateElementRevision("id", "1");
        assertEquals(new Long(1), result.getVersion());
        
        verify(mockCacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(mockService).updateElementRevision(eq(TEST_STUDY_IDENTIFIER), elementCaptor.capture());
        assertEquals("id", elementCaptor.getValue().getId());
        assertEquals(new Long(1), elementCaptor.getValue().getRevision());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateElementRevisionBadRevisionNumber() {
        controller.updateElementRevision("id", "one");
    }
    
    @Test
    public void deleteElementAllRevisions() throws Exception {
        StatusMessage result = controller.deleteElementAllRevisions("id", "false");
        assertEquals(result.getMessage(), "App config element deleted.");
     
        verify(mockCacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());
        
        verify(mockService).deleteElementAllRevisions(TEST_STUDY_IDENTIFIER, "id");
    }
    
    @Test
    public void deleteElementAllRevisionsPermanently() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        StatusMessage result = controller.deleteElementAllRevisions("id", "true");
        assertEquals(result.getMessage(), "App config element deleted.");
        
        verify(mockCacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());

        verify(mockService).deleteElementAllRevisionsPermanently(TEST_STUDY_IDENTIFIER, "id");
    }
    
    @Test
    public void deleteElementAllRevisionsDefaultsToLogical() throws Exception {
        StatusMessage result = controller.deleteElementAllRevisions("id", "true");
        assertEquals(result.getMessage(), "App config element deleted.");
     
        verify(mockService).deleteElementAllRevisions(TEST_STUDY_IDENTIFIER, "id");
    }
    
    @Test
    public void deleteElementRevision() throws Exception {
        StatusMessage result = controller.deleteElementRevision("id", "3", "false");
        assertEquals(result.getMessage(), "App config element revision deleted.");

        verify(mockCacheProvider).removeSetOfCacheKeys(cacheKeyCaptor.capture());
        assertEquals("api:AppConfigList", cacheKeyCaptor.getValue().toString());

        verify(mockService).deleteElementRevision(TEST_STUDY_IDENTIFIER, "id", 3L);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deleteElementRevisionBadRevisionNumber() throws Exception {
        controller.deleteElementRevision("id", "three", "false");
    }
    
    @Test
    public void deleteElementRevisionPermanently() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        StatusMessage result = controller.deleteElementRevision("id", "3", "true");
        assertEquals(result.getMessage(), "App config element revision deleted.");
     
        verify(mockService).deleteElementRevisionPermanently(TEST_STUDY_IDENTIFIER, "id", 3L);
    }
    
    @Test
    public void deleteElementRevisionDefaultsToLogical() throws Exception {
        StatusMessage result = controller.deleteElementRevision("id", "3", "true");
        assertEquals(result.getMessage(), "App config element revision deleted.");
     
        verify(mockService).deleteElementRevision(TEST_STUDY_IDENTIFIER, "id", 3L);
    }
}
