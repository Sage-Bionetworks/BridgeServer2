package org.sagebionetworks.bridge.services;

import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.TagDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;

public class TagServiceTest extends Mockito {
    @Mock
    TagDao mockDao;
    
    @InjectMocks
    TagService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getTags() {
        Map<String, List<String>> results = ImmutableMap.of();
        when(mockDao.getTags()).thenReturn(results);
        
        Map<String, List<String>> retValue = service.getTags();
        assertSame(results, retValue);
        
        verify(mockDao).getTags();
    }
    
    @Test
    public void addTag() {
        service.addTag("testValue");
        verify(mockDao).addTag("testValue");
    }
    
    @Test
    public void addDefaultTag() {
        service.addTag("default:testValue");
        verify(mockDao).addTag("testValue");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void addTagNoEmptyValue() {
        service.addTag("  ");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void addTagNull() {
        service.addTag(null);
    }
    
    @Test
    public void deleteTag() {
        service.deleteTag("testValue");
        verify(mockDao).deleteTag("testValue");
    }
    
    @Test
    public void deleteDefaultTag() { 
        service.deleteTag("default:testValue");
        verify(mockDao).deleteTag("testValue");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deleteTagNoEmptyValue() {
        service.deleteTag("");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deleteTagNull() {
        service.deleteTag(null);
    }
}
