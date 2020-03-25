package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

import org.hibernate.Session;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;

public class HibernateTagDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    Session mockSession;
    
    @InjectMocks
    @Spy
    HibernateTagDao dao;
    
    @Captor
    ArgumentCaptor<Tag> tagCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockHelper.executeWithExceptionHandling(any(), any())).then(answer -> {
            Function<Session,HibernateAssessment> func = answer.getArgument(1);
            return func.apply(mockSession);
        });
    }
    
    @Test
    public void getTags() {
        List<Tag> list = ImmutableList.of(new Tag("a:1"), new Tag("a:2"), new Tag("a:3"), new Tag("a:4"),
                new Tag("a:5"), new Tag("b:6"), new Tag("b:7"), new Tag("b:8"), new Tag("b:9"), new Tag("b:0"),
                new Tag("a:10"), new Tag("defaultTag"));
        when(mockHelper.queryGet("from Tag", null, null, null, Tag.class)).thenReturn(list);
        
        Map<String, List<String>> retValue = dao.getTags();
        assertEquals(3, retValue.keySet().size());
        assertEquals(ImmutableList.of("defaultTag"), retValue.get("default"));
        assertEquals(ImmutableList.of("1", "2", "3", "4", "5", "10"), retValue.get("a"));
        assertEquals(ImmutableList.of("6", "7", "8", "9", "0"), retValue.get("b"));
    }
    
    @Test
    public void addTag() {
        dao.addTag("tagValue");
        
        verify(mockSession).saveOrUpdate(tagCaptor.capture());
        assertEquals("tagValue", tagCaptor.getValue().getValue());
    }

    @Test
    public void deleteTag() {
        dao.deleteTag("tagValue");
        
        verify(mockSession).remove(tagCaptor.capture());
        assertEquals("tagValue", tagCaptor.getValue().getValue());
    }
}
