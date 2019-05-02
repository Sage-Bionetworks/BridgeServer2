package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertEquals;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableSet;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.CacheAdminService;

public class CacheAdminControllerTest extends Mockito {
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private CacheAdminService mockCacheAdminService;

    @InjectMocks
    @Spy
    private CacheAdminController controller = new CacheAdminController();

    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
        
        UserSession session = new UserSession();
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void annotatedCorrectly() throws Exception {
        assertCrossOrigin(CacheAdminController.class);
        assertGet(CacheAdminController.class, "listItems");
        assertDelete(CacheAdminController.class, "removeItem");
    }    
    
    @Test
    public void listItems() throws Exception {
        Set<String> items = ImmutableSet.of("A", "B", "C");
        when(mockCacheAdminService.listItems()).thenReturn(items);
        
        // This should be a ResourceList, but it's not currently, so we're maintaining that.
        Set<String> cacheItems = controller.listItems();
        assertEquals(items, cacheItems);
        
        verify(mockCacheAdminService).listItems();
    }
    
    @Test
    public void removeItem() throws Exception {
        StatusMessage result = controller.removeItem("cacheKey");
        assertEquals("Item removed from cache.", result.getMessage());
        
        verify(mockCacheAdminService).removeItem("cacheKey");
    }
}
