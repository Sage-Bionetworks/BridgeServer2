package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.TagService;

public class TagControllerTest extends Mockito {
    private static final String JSON = "{\"a\":[\"1\",\"2\",\"3\"]}";
    
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
        session.setStudyIdentifier(API_APP_ID);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(TagController.class);
        assertGet(TagController.class, "getTags");
        assertCreate(TagController.class, "addTag");
        assertDelete(TagController.class, "deleteTag");
    }
    
    @Test
    public void getTagsCached() throws Exception {
        when(mockViewCache.getView(eq(CacheKey.tagList()), any())).thenReturn(JSON);
        
        String retValue = controller.getTags();
        assertEquals(retValue, JSON);
    }
    
    @Test
    public void getTagsNotCached() throws Exception {
        when(mockViewCache.getView(eq(CacheKey.tagList()), any())).thenReturn(null);
        
        CacheProvider mockProvider = mock(CacheProvider.class);
        ViewCache viewCache = new ViewCache();
        viewCache.setCacheProvider(mockProvider);
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        controller.setViewCache(viewCache);
        
        Map<String, List<String>> map = ImmutableMap.of("a", ImmutableList.of("1", "2", "3"));
        when(mockService.getTags()).thenReturn(map);
        
        String retValue = controller.getTags();
        assertEquals(retValue, JSON);
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
