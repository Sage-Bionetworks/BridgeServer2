package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.TagService;

public class TagControllerTest extends Mockito {
    
    @Mock
    TagService mockService;
    
    @Mock
    ViewCache mockViewCache;
    
    @InjectMocks
    @Spy
    TagController controller;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Tag> tagCaptor;
    
    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(TagController.class);
        assertGet(TagController.class, "getTags");
        assertCreate(TagController.class, "addTag");
        assertDelete(TagController.class, "deleteTag");
    }
    
    @Test
    public void getTags() throws Exception {
        String json = "{\"a\":[\"1\",\"2\",\"3\"]}";
        when(mockViewCache.getView(eq(CacheKey.tagList()), any())).thenReturn(json);
        
        String retValue = controller.getTags();
        assertEquals(json, retValue);
    }
    
    @Test
    public void addTag() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        mockRequestBody(mockRequest, new Tag("tagValue"));
        
        controller.addTag();
        verify(mockService).addTag("tagValue");
    }
    
    @Test
    public void deleteTag() {
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        controller.deleteTag("tagValue");
        verify(mockService).deleteTag("tagValue");
    }
}
