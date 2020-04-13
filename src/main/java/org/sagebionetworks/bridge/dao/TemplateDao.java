package org.sagebionetworks.bridge.dao;

import java.util.Optional;
import java.util.function.Consumer;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;

public interface TemplateDao {
    
    PagedResourceList<? extends Template> getTemplates(String studyId, TemplateType type, Integer offset,
            Integer limit, boolean includeDeleted);

    Optional<Template> getTemplate(String studyId, String guid);
    
    void createTemplate(Template template, Consumer<Template> consumer);
    
    void updateTemplate(Template template);
    
    void deleteTemplatePermanently(String studyId, String guid);
    
    void deleteTemplatesForStudy(String studyId);
}
