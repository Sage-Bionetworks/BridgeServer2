package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.TemplateType;

public interface TemplateDao {
    
    PagedResourceList<? extends Template> getTemplates(TemplateType type, int offset, int limit);

    Optional<Template> getTemplate(String guid);
    
    void createTemplate(Template template);
    
    void updateTemplate(Template template);
    
    void deleteTemplatePermanently(String guid);
    
}
