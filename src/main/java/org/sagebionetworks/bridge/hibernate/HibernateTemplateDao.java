package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.TemplateType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class HibernateTemplateDao implements TemplateDao {
    private static final String GET_QUERY = "SELECT template FROM HibernateTemplate as template " + 
            "WHERE templateType = :templateType AND studyId = :studyId ORDER BY createdOn DESC";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "templateHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public PagedResourceList<? extends Template> getTemplates(StudyIdentifier studyId, TemplateType type, int offset, int pageSize) {
        checkNotNull(type);
        
        ImmutableMap<String,Object> params = ImmutableMap.of("templateType", type.name());

        int count = hibernateHelper.queryCount(GET_QUERY, params);
        List<? extends HibernateTemplate> results = hibernateHelper.queryGet(GET_QUERY, params, offset, pageSize, HibernateTemplate.class);
        
        return new PagedResourceList<>(results, count)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_BY, offset);
    }
    
    @Override
    public Optional<Template> getTemplate(String guid) { 
        checkNotNull(guid);
        
        Template template = hibernateHelper.getById(HibernateTemplate.class, guid);
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
    public void deleteTemplatePermanently(String guid) {
        checkNotNull(guid);

        hibernateHelper.deleteById(HibernateTemplate.class, guid);
    }

}
