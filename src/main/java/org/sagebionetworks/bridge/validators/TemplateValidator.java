package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.Template;

public class TemplateValidator implements Validator {

    private final Set<String> dataGroups;
    private final Set<String> substudyIds;

    public TemplateValidator(Set<String> dataGroups, Set<String> substudyIds) {
        this.dataGroups = dataGroups;
        this.substudyIds = substudyIds;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Template.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Template template = (Template)object;

        if (StringUtils.isBlank(template.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (template.getTemplateType() == null) {
            errors.rejectValue("templateType", "is required");
        }
        if (template.getCriteria() != null) {
            CriteriaUtils.validate(template.getCriteria(), dataGroups, substudyIds, errors);    
        }
    }

}
