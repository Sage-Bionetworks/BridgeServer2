package org.sagebionetworks.bridge.hibernate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.math.BigInteger;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.google.common.base.Stopwatch;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.NativeQuery;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.TimelineDao;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

@Component
public class HibernateTimelineDao implements TimelineDao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateTimelineDao.class);

    private static final String BATCH_DELETE_RECORDS = "DELETE FROM TimelineMetadata where scheduleGuid = :scheduleGuid";
    private SessionFactory sessionFactory;
    private int batchSize;

    @Autowired
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
    
    @Autowired
    public void setBridgeConfig(BridgeConfig config) {
        this.batchSize = config.getInt("timeline.batch.size");
    }
    
    @Override
    public DateTime getModifedOn(String scheduleGuid) {
        EntityManager em = sessionFactory.createEntityManager();
        
        Query query = em.createNativeQuery("select scheduleModifiedOn from TimelineMetadata where scheduleGuid = :scheduleGuid");
        query.setParameter("scheduleGuid", scheduleGuid);
        
        List<?> list = query.getResultList();
        
        if (list.isEmpty()) {
            return null;
        }
        long timestamp = ((BigInteger)list.get(0)).longValue();
        return new DateTime(timestamp);
    }
    
    @Override
    public void persistMetadata(List<TimelineMetadata> metadata) {
        // batch these operations.
        Stopwatch stopwatch = Stopwatch.createStarted();
        try (Session session = sessionFactory.openSession()) {
            session.setJdbcBatchSize(batchSize);
            
            Transaction transaction = session.beginTransaction();
            
            NativeQuery<?> query = session.createNativeQuery(BATCH_DELETE_RECORDS);
            query.setParameter("scheduleGuid", metadata.get(0).getScheduleGuid());
            query.executeUpdate();

            for (int i=0, len = metadata.size(); i < len; i++) {
                TimelineMetadata meta = metadata.get(i);
                session.save(meta);
                if ((i % batchSize) == 0) {
                    session.flush();
                    session.clear();
                }
            }
            transaction.commit();
        }
        LOG.info("Persisting " + metadata.size() + " timeline records took " + 
                stopwatch.elapsed(MILLISECONDS) + " ms");
    }

    @Override
    public TimelineMetadata getMetadata(String instanceGuid) {
        EntityManager em = sessionFactory.createEntityManager();
        return em.find(TimelineMetadata.class, instanceGuid);
    }

}
