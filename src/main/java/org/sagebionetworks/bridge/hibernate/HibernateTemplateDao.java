package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TEMPLATE_TYPE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;

/**
 * DAO implementation for templates. All the business constraints are enforced in TemplateService because they are not 
 * specific to our persistence store.
 */
@Component
public class HibernateTemplateDao implements TemplateDao {
    private static final String SELECT_TEMPLATE = "SELECT template ";
    private static final String SELECT_COUNT = "SELECT count(guid) ";
    
    private static final String GET_ALL = "FROM HibernateTemplate as template " + 
            "WHERE templateType = :templateType AND studyId = :studyId ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE = "FROM HibernateTemplate as template " + 
            "WHERE templateType = :templateType AND studyId = :studyId AND deleted = 0 ORDER BY createdOn DESC";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "templateHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public PagedResourceList<? extends Template> getTemplates(StudyIdentifier studyId, TemplateType type,
            Integer offset, Integer pageSize, boolean includeDeleted) {
        checkNotNull(studyId);
        checkNotNull(type);
        
        ImmutableMap<String,Object> params = ImmutableMap.of(
                TEMPLATE_TYPE, type, 
                "studyId", studyId.getIdentifier());
        String countQuery = SELECT_COUNT + ((!includeDeleted) ? GET_ACTIVE : GET_ALL);
        String getQuery = SELECT_TEMPLATE + ((!includeDeleted) ? GET_ACTIVE : GET_ALL);
        
        int total = hibernateHelper.queryCount(countQuery, params);
        List<? extends HibernateTemplate> results = hibernateHelper.queryGet(
                getQuery, params, offset, pageSize, HibernateTemplate.class);
        
        return new PagedResourceList<>(results, total)
                .withRequestParam(TEMPLATE_TYPE, type)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_BY, offset)
                .withRequestParam(TOTAL, total)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    @Override
    public Optional<Template> getTemplate(StudyIdentifier studyId, String guid) { 
        checkNotNull(guid);
        
        Template template = hibernateHelper.getById(HibernateTemplate.class, guid);
        if (template != null && !template.getStudyId().equals(studyId.getIdentifier())) {
            return Optional.empty();
        }
        return Optional.ofNullable(template);
    }

    @Override
    public void createTemplate(Template template) {
        checkNotNull(template);
        
        hibernateHelper.create(template, null); 
    }

    @Override
    public void updateTemplate(Template template) {
        checkNotNull(template);

        hibernateHelper.update(template, null); 
    }

    @Override
    public void deleteTemplatePermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(guid);
        
        Optional<Template> template = getTemplate(studyId, guid);
        if (template.isPresent()) {
            hibernateHelper.deleteById(HibernateTemplate.class, guid);    
        }
    }

}
