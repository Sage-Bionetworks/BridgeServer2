package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class CacheAdminServiceTest {

    private static final String REQUEST_INFO_KEY = CacheKey.requestInfo("10E9SFUz9BYrqCrTzfiaNW").toString();
    
    private CacheAdminService adminService;
    
    @BeforeMethod
    public void before() {
        adminService = new CacheAdminService();
        
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(createStubJedis());

        adminService.setJedisPool(pool);
    }
    
    @Test
    public void listsItemsWithoutSessions() {
        Set<String> set = adminService.listItems();
        assertEquals(set.size(), 2);
        assertTrue(set.contains("foo:App"));
        assertTrue(set.contains("baz:Survey:view"));
    }
    
    
    @Test
    public void canRemoveItem() {
        adminService.removeItem("foo:App");
        Set<String> set = adminService.listItems();
        assertEquals(set.size(), 1);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void doesNotRemoveSessions() {
        adminService.removeItem("bar:session");
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void doesNotRemoveUserSessions() {
        adminService.removeItem("xh7YDmjGQuTKnfdv9iJb0:session:user");
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void throwsExceptionWhenThereIsNoKey() {
        adminService.removeItem("not:a:key");
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwsExceptionWhenKeyIsEmpty() {
        adminService.removeItem(" ");
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwsExceptionWhenKeyIsNull() {
        adminService.removeItem(null);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void cannotRemoveRequestInfo() {
        adminService.removeItem(REQUEST_INFO_KEY);
    }
    
    private Jedis createStubJedis() {
        Set<String> KEYS = Sets.newHashSet(CacheKey.app("foo").toString(),
                CacheKey.viewKey(Survey.class, "baz").toString());
        return new Jedis("") {
            @Override
            public Set<String> keys(String pattern) {
                return ImmutableSet.copyOf(KEYS);
            }
            @Override
            public Long del(String key) {
                return (KEYS.remove(key)) ? 1L : 0L;
            }
        };
    }
}
