package org.sagebionetworks.bridge.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

public class ViewCacheTest {
    
    private BridgeObjectMapper mapper;
    private App app;
    
    @BeforeMethod
    public void before() {
        mapper = BridgeObjectMapper.get();
        
        app = TestUtils.getValidApp(ViewCacheTest.class);
    }
    
    @Test
    public void nothingWasCached() throws Exception {
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        CacheKey cacheKey = cache.getCacheKey(App.class, app.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey, String.class)).thenReturn(null);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<App>() {
            @Override public App get() {
                App app = TestUtils.getValidApp(ViewCacheTest.class);
                app.setName("Test Study 2");
                return app;
            }
        });
        
        App foundApp = BridgeObjectMapper.get().readValue(json, DynamoApp.class);
        assertEquals(foundApp.getName(), "Test Study 2");
    }
    
    @Test
    public void nothingWasCachedAndThereIsAnException() {
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        CacheKey cacheKey = cache.getCacheKey(App.class, app.getIdentifier());
        
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey, String.class)).thenReturn(null);
        cache.setCacheProvider(provider);
        
        // It doesn't get wrapped or transformed or anything
        try {
            cache.getView(cacheKey, new Supplier<App>() {
                @Override public App get() {
                    throw new BridgeServiceException("There has been a problem retrieving the study");
                }
            });
            fail("This should have thrown an exception");
        } catch(BridgeServiceException e) {
            assertEquals(e.getMessage(), "There has been a problem retrieving the study");
        }
    }
    
    @Test
    public void somethingIsCached() throws Exception {
        String originalStudyJson = mapper.writeValueAsString(app);
        
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        CacheKey cacheKey = cache.getCacheKey(App.class, app.getIdentifier());
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getObject(cacheKey, String.class)).thenReturn(originalStudyJson);
        cache.setCacheProvider(provider);
        
        String json = cache.getView(cacheKey, new Supplier<App>() {
            @Override public App get() {
                fail("This should not be called");
                return null;
            }
        });
        
        App foundApp = BridgeObjectMapper.get().readValue(json, DynamoApp.class);
        assertEquals(foundApp.getName(), "Test App [ViewCacheTest]");
    }
    
    @Test
    public void removeFromCacheWorks() throws Exception {
        final String originalStudyJson = mapper.writeValueAsString(app);
        
        ViewCache cache = new ViewCache();
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        final CacheKey cacheKey = cache.getCacheKey(App.class, app.getIdentifier());
        cache.setCacheProvider(getSimpleCacheProvider(cacheKey, originalStudyJson));
        
        cache.removeView(cacheKey);
        
        String json = cache.getView(cacheKey, new Supplier<App>() {
            @Override public App get() {
                App app = TestUtils.getValidApp(ViewCacheTest.class);
                app.setName("Test Study 2");
                return app;
            }
        });
        App foundApp = BridgeObjectMapper.get().readValue(json, DynamoApp.class);
        assertEquals(foundApp.getName(), "Test Study 2");
    }
    
    @Test
    public void getCacheKeyWorks() {
        ViewCache cache = new ViewCache();
        
        CacheKey cacheKey = cache.getCacheKey(App.class, "mostRandom", "leastRandom");
        assertEquals(cacheKey.toString(), "mostRandom:leastRandom:App:view");
    }
    
    @Test
    public void canReconfigureViewCache() throws Exception {
        CacheProvider provider = mock(CacheProvider.class);
        
        Survey survey = Survey.create();
        survey.setIdentifier("config-test");

        ObjectMapper mapper = new ObjectMapper();
        // need this filter config for mapper to work on Survey
        FilterProvider filter = new SimpleFilterProvider().setFailOnUnknownId(false);
        mapper.setFilterProvider(filter);
        
        ViewCache cache = new ViewCache();
        cache.setCachePeriod(1000);
        cache.setObjectMapper(mapper);
        cache.setCacheProvider(provider);
        
        CacheKey cacheKey = cache.getCacheKey(Survey.class, survey.getIdentifier());
        cache.getView(cacheKey, () -> survey);
        
        // The string from this mapper doesn't have the "type" attribute, so if this passes, we
        // can be confident that the right mapper has been used.
        verify(provider).setObject(cacheKey, mapper.writeValueAsString(survey), 1000);
    }
    
    private CacheProvider getSimpleCacheProvider(final CacheKey cacheKey, final String originalStudyJson) {
        return new CacheProvider() {
            private Map<CacheKey,String> map = Maps.newHashMap();
            {
                map.put(cacheKey, originalStudyJson);
            }
            public <T> T getObject(CacheKey cacheKey, Class<T> clazz) {
                try {
                    return BridgeObjectMapper.get().readValue(map.get(cacheKey), clazz);    
                } catch(Exception e) {
                    return null;
                }
            }
            public void setObject(CacheKey cacheKey, Object object) {
                try {
                    String ser = BridgeObjectMapper.get().writeValueAsString(object);
                    map.put(cacheKey, ser);
                } catch(Exception e) {
                }
            }
            public void setObject(CacheKey cacheKey, Object object, int secondsUntilExpire) {
                try {
                    String ser = BridgeObjectMapper.get().writeValueAsString(object);
                    map.put(cacheKey, ser);
                } catch(Exception e) {
                }
            }
            public void removeObject(CacheKey cacheKey) {
                map.remove(cacheKey);
            }
        };
    }
    
}
