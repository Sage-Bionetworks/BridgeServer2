package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

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
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "studyHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Study> getStudies(String appId, Integer offsetBy, Integer pageSize,
            boolean includeDeleted) {
        checkNotNull(appId);
        
        Map<String,Object> parameters = ImmutableMap.of("appId", appId);
        String query = "from HibernateStudy as study where appId=:appId";
        if (!includeDeleted) {
            query += " and deleted != 1";
        }
        int total = hibernateHelper.queryCount("select count(*) " + query, parameters);

        List<HibernateStudy> hibStudies = hibernateHelper.queryGet(query, parameters, 
                offsetBy, pageSize, HibernateStudy.class);
        List<Study> studies = ImmutableList.copyOf(hibStudies);
        
        return new PagedResourceList<>(studies, total);
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
        
        hibernateHelper.create(study, null);
        return new VersionHolder(study.getVersion());
    }

    @Override
    public VersionHolder updateStudy(Study study) {
        checkNotNull(study);
        
        hibernateHelper.update(study, null);
        return new VersionHolder(study.getVersion());
    }

    @Override
    public void deleteStudyPermanently(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM OrganizationsStudies WHERE appId = :appId AND studyId = :studyId");
        builder.getParameters().put("appId", appId);
        builder.getParameters().put("studyId", id);
        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
        
        StudyId studyId = new StudyId(appId, id);
        hibernateHelper.deleteById(HibernateStudy.class, studyId);
    }
}
