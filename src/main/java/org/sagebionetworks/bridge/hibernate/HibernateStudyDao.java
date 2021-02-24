package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyId;

import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Component
public class HibernateStudyDao implements StudyDao {
    
    static final String COUNT_PHRASE = "select count(*) ";
    static final String SELECT_PHRASE = "select new org.sagebionetworks.bridge.hibernate."
            + "HibernateStudy(study.name, study.identifier, study.appId, study.deleted, study.version) ";
    static final String FROM_PHRASE = "from HibernateStudy as study where appId = :appId"; 
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "studyHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Study> getStudies(String appId, Set<String> studyIds, 
            Integer offsetBy, Integer pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(FROM_PHRASE, "appId", appId);
        if (studyIds != null) {
            builder.append("and identifier in (:studies)", "studies", studyIds);
        }
        if (!includeDeleted) {
            builder.append("and deleted != 1");
        }
        int total = hibernateHelper.queryCount(COUNT_PHRASE + builder.getQuery(), builder.getParameters());
        
        List<HibernateStudy> hibStudies = hibernateHelper.queryGet(SELECT_PHRASE + builder.getQuery(), 
                builder.getParameters(), offsetBy, pageSize, HibernateStudy.class);
        List<Study> studies = ImmutableList.copyOf(hibStudies);
        
        return new PagedResourceList<>(studies, total, true);
    }

    @Override
    public Study getStudy(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);

        StudyId studyId = new StudyId(appId, id);
        return hibernateHelper.getById(HibernateStudy.class, studyId);
    }
    
    @Override
    public VersionHolder createStudy(Study study) {
        checkNotNull(study);
        
        hibernateHelper.create(study);
        return new VersionHolder(study.getVersion());
    }

    @Override
    public VersionHolder updateStudy(Study study) {
        checkNotNull(study);
        
        hibernateHelper.update(study);
        return new VersionHolder(study.getVersion());
    }

    @Override
    public void deleteStudyPermanently(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        StudyId studyId = new StudyId(appId, id);
        hibernateHelper.deleteById(HibernateStudy.class, studyId);
    }
    
    @Override
    public void deleteAllStudies(String appId) {
        checkNotNull(appId);

        Map<String,Object> parameters = ImmutableMap.of("appId", appId);
        String query = "delete from HibernateStudy where appId = :appId";

        hibernateHelper.queryUpdate(query, parameters);
    }
}
