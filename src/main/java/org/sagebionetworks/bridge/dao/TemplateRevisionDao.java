package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public interface TemplateRevisionDao {
    PagedResourceList<? extends TemplateRevision> getTemplateRevisions(String templateGuid, Integer offset, Integer pageSize);

    Optional<TemplateRevision> getTemplateRevision(String templateGuid, DateTime createdOn);
    
    void createTemplateRevision(TemplateRevision revision);
}
