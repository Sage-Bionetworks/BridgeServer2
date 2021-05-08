package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventId;

@Component
public class HibernateStudyActivityEventDao implements StudyActivityEventDao {
    
    private HibernateHelper helper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean deleteCustomEvent(StudyActivityEvent event) {
        checkNotNull(event);
        
        StudyActivityEventId id = new StudyActivityEventId(
                event.getUserId(), event.getStudyId(), event.getEventId(), event.getTimestamp());
        
        StudyActivityEvent existing = helper.getById(StudyActivityEvent.class, id);
        if (event.getUpdateType().canDelete(existing, event)) {
            helper.deleteById(StudyActivityEvent.class, id);
        }
        return false;
    }

    @Override
    public void publishEvent(StudyActivityEvent event) {
        checkNotNull(event);
        
        helper.saveOrUpdate(event);
    }

    @Override
    public List<StudyActivityEvent> getRecentStudyActivityEvents(String userId, String studyId) {
        // Is there a better way to get this than a subselect?
        QueryBuilder inner = new QueryBuilder();
        inner.append("SELECT createdOn FROM StudyActivityEvents");
        inner.append("WHERE userId = :userId AND studyId = :studyId AND eventId = sae.eventId");
        inner.append("ORDER BY createdOn DESC LIMIT 1");
        
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT * FROM StudyActivityEvents AS sae");
        builder.append("WHERE userId = :userId AND studyId = :studyId", "userId", userId, "studyId", studyId);
        builder.append("AND createdOn = (" + inner.getQuery() + ")");
        
        return helper.nativeQueryGet(
                builder.getQuery(), builder.getParameters(), null, null, StudyActivityEvent.class);
    }
    
    @Override
    public Map<String, StudyActivityEvent> getRecentStudyActivityEventMap(String userId, String studyId) {
        return getRecentStudyActivityEvents(userId, studyId).stream()
                .collect(toMap(StudyActivityEvent::getEventId, e -> e));
    }

    @Override
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(String userId, String studyId, 
            String eventId, int offsetBy, int pageSize) {

        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT * FROM StudyActivityEvents");
        builder.append("WHERE userId = :userId AND studyId = :studyId", "userId", userId, "studyId", studyId);
        builder.append("AND eventId = :eventId", "eventId", eventId);

        List<StudyActivityEvent> records = helper.nativeQueryGet(
                builder.getQuery(), builder.getParameters(), offsetBy, pageSize, StudyActivityEvent.class);
        
        int count = helper.nativeQueryCount(builder.getQuery(), builder.getParameters());
        
        return new PagedResourceList<>(records, count);
    }

}
