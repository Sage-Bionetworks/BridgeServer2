package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
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

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.CacheAdminService;

public class CacheAdminControllerTest extends Mockito {
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private CacheAdminService mockCacheAdminService;
    
    @Mock
    private AccountDao mockAccountDao;

    @InjectMocks
    @Spy
    private CacheAdminController controller = new CacheAdminController();
    
    private UserSession session;

    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setParticipant(new StudyParticipant.Builder().withId(USER_ID).build());
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(CacheAdminController.class);
        assertGet(CacheAdminController.class, "listItems");
        assertDelete(CacheAdminController.class, "removeItem");
    }    
    
    @Test
    public void listItems() throws Exception {
        session.setStudyIdentifier(TEST_STUDY);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        
        Set<String> items = ImmutableSet.of("A", "B", "C");
        when(mockCacheAdminService.listItems()).thenReturn(items);
        
        // This should be a ResourceList, but it's not currently, so we're maintaining that.
        Set<String> cacheItems = controller.listItems();
        assertEquals(items, cacheItems);
        
        verify(mockCacheAdminService).listItems();
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void listItemsRejectsStudyAdmin() throws Exception {
        Set<String> items = ImmutableSet.of("A", "B", "C");
        when(mockCacheAdminService.listItems()).thenReturn(items);
        
        // This should be a ResourceList, but it's not currently, so we're maintaining that.
        controller.listItems();
    }
    
    @Test
    public void removeItem() throws Exception {
        session.setStudyIdentifier(TEST_STUDY);
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        
        StatusMessage result = controller.removeItem("cacheKey");
        assertEquals("Item removed from cache.", result.getMessage());
        
        verify(mockCacheAdminService).removeItem("cacheKey");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void removeItemRejectsStudyAdmin() throws Exception {
        controller.removeItem("cacheKey");
    }    
}
