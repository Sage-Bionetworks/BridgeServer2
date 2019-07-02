package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;

import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateRevisionId;

@Component
public class HibernateTemplateRevisionDao implements TemplateRevisionDao {
    static final String TEMPLATE_GUID_PARAM_NAME = "templateGuid";
    static final String SELECT_TEMPLATE = "SELECT revision ";
    static final String SELECT_COUNT = "SELECT count(*) ";
    static final String GET_ALL = "FROM HibernateTemplateRevision AS revision " + 
            "WHERE templateGuid = :templateGuid ORDER BY createdOn DESC";

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "templateRevisionHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public PagedResourceList<? extends TemplateRevision> getTemplateRevisions(String templateGuid, Integer offset, Integer pageSize) {
        checkNotNull(templateGuid);
        
        ImmutableMap<String,Object> params = ImmutableMap.of(TEMPLATE_GUID_PARAM_NAME, templateGuid);
        
        int total = hibernateHelper.queryCount(SELECT_COUNT + GET_ALL, params);
        
        List<? extends HibernateTemplateRevision> results = hibernateHelper.queryGet(
                SELECT_TEMPLATE + GET_ALL, params, offset, pageSize, HibernateTemplateRevision.class);
        
        return new PagedResourceList<>(results, total)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_BY, offset)
                .withRequestParam(TOTAL, total);
    }

    @Override
    public Optional<TemplateRevision> getTemplateRevision(String templateGuid, DateTime createdOn) {
        checkNotNull(templateGuid);
        checkNotNull(createdOn);
        
        TemplateRevisionId revisionId = new TemplateRevisionId(templateGuid, createdOn);
        HibernateTemplateRevision revision = hibernateHelper.getById(HibernateTemplateRevision.class, revisionId);
        if (revision == null) {
            return Optional.empty();
        }
        return Optional.of(revision);
    }

    @Override
    public void createTemplateRevision(TemplateRevision revision) {
        checkNotNull(revision);
        
        hibernateHelper.create(revision, null);
    }

}
