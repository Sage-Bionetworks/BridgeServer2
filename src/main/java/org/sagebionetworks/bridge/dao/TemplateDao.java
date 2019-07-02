package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;

public interface TemplateDao {
    
    PagedResourceList<? extends Template> getTemplates(StudyIdentifier studyId, TemplateType type, Integer offset,
            Integer limit, boolean includeDeleted);

    Optional<Template> getTemplate(StudyIdentifier studyId, String guid);
    
    void createTemplate(Template template);
    
    void updateTemplate(Template template);
    
    void deleteTemplatePermanently(StudyIdentifier studyId, String guid);
    
}
