package org.sagebionetworks.bridge.hibernate;

import org.hibernate.HibernateException;
import org.hibernate.event.spi.DeleteEvent;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.SaveOrUpdateEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;

public class TagEventListenerTest extends Mockito {
    
    @Mock
    CacheProvider cacheProvider;
    
    @InjectMocks
    TagEventListener listener;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void onDelete() throws HibernateException {
        DeleteEvent event = new DeleteEvent(new Tag("value"), null);
        listener.onDelete(event);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }

    @Test
    public void onDeleteNotTag() throws HibernateException {
        DeleteEvent event = new DeleteEvent(new HibernateAssessment(), null);
        listener.onDelete(event);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onDeleteWithTransientEntities() throws HibernateException {
        DeleteEvent event = new DeleteEvent(new Tag("value"), null);
        listener.onDelete(event, null);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }

    @Test
    public void onDeleteWithTransientEntitiesNotTag() throws HibernateException {
        DeleteEvent event = new DeleteEvent(new HibernateAssessment(), null);
        listener.onDelete(event, null);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onSaveOrUpdate() throws HibernateException {
        SaveOrUpdateEvent event = new SaveOrUpdateEvent(new Tag("value"), null);
        listener.onSaveOrUpdate(event);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }

    @Test
    public void onSaveOrUpdateNotTag() throws HibernateException {
        SaveOrUpdateEvent event = new SaveOrUpdateEvent(new HibernateAssessment(), null);
        listener.onSaveOrUpdate(event);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onPersist() throws HibernateException {
        PersistEvent event = new PersistEvent(new Tag("value"), null);
        listener.onPersist(event);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onPersistNotTag() throws HibernateException {
        PersistEvent event = new PersistEvent(new HibernateAssessment(), null);
        listener.onPersist(event);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onPersistWithCreatedMap() throws HibernateException {
        PersistEvent event = new PersistEvent(new Tag("value"), null);
        listener.onPersist(event, null);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }

    @Test
    public void onPersistWithCreatedMapNotTag() throws HibernateException {
        PersistEvent event = new PersistEvent(new HibernateAssessment(), null);
        listener.onPersist(event, null);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onMerge() throws HibernateException {
        MergeEvent event = new MergeEvent(new Tag("value"), null);
        event.setEntity(new Tag("value"));
        listener.onMerge(event);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }

    @Test
    public void onMergeNotTag() throws HibernateException {
        MergeEvent event = new MergeEvent(new HibernateAssessment(), null);
        event.setEntity(new HibernateAssessment());
        listener.onMerge(event);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onMergeWithCopiedAlready() throws HibernateException {
        MergeEvent event = new MergeEvent(new Tag("value"), null);
        event.setEntity(new Tag("value"));
        listener.onMerge(event, null);
        verify(cacheProvider).removeObject(CacheKey.tagList());
    }
    
    @Test
    public void onMergeWithCopiedAlreadyNotTag() throws HibernateException {
        MergeEvent event = new MergeEvent(new HibernateAssessment(), null);
        event.setEntity(new HibernateAssessment());
        listener.onMerge(event, null);
        verify(cacheProvider, never()).removeObject(CacheKey.tagList());
    }
}
