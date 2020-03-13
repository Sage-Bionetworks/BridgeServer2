package org.sagebionetworks.bridge.hibernate;

import java.util.Map;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.event.spi.DeleteEvent;
import org.hibernate.event.spi.DeleteEventListener;
import org.hibernate.event.spi.MergeEvent;
import org.hibernate.event.spi.MergeEventListener;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.hibernate.event.spi.SaveOrUpdateEvent;
import org.hibernate.event.spi.SaveOrUpdateEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.Tag;

/**
 * Detect any change to the list of tags and clear the tag cache. 
 */
@SuppressWarnings("serial")
@Component
public class TagEventListener implements DeleteEventListener, SaveOrUpdateEventListener, 
    MergeEventListener, PersistEventListener {
    
    CacheProvider cacheProvider;
    
    @Autowired
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Override
    public void onDelete(DeleteEvent event) throws HibernateException {
        clearProvider(event.getObject());
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void onDelete(DeleteEvent event, Set transientEntities) throws HibernateException {
        clearProvider(event.getObject());
    }

    @Override
    public void onSaveOrUpdate(SaveOrUpdateEvent event) throws HibernateException {
        clearProvider(event.getObject());
    }

    @Override
    public void onPersist(PersistEvent event) throws HibernateException {
        clearProvider(event.getObject());
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void onPersist(PersistEvent event, Map createdAlready) throws HibernateException {
        clearProvider(event.getObject());
    }

    @Override
    public void onMerge(MergeEvent event) throws HibernateException {
        clearProvider(event.getEntity());
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void onMerge(MergeEvent event, Map copiedAlready) throws HibernateException {
        clearProvider(event.getEntity());
    }
    
    private void clearProvider(Object object) {
        if (object instanceof Tag) {
            cacheProvider.removeObject(CacheKey.tagList());    
        }
    }
}
