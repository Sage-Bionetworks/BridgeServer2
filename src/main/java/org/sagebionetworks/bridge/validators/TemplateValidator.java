package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.templates.Template;

import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

public class TemplateValidator implements Validator {

    private final Set<String> dataGroups;
    private final Set<String> studyIds;

    public TemplateValidator(Set<String> dataGroups, Set<String> studyIds) {
        this.dataGroups = dataGroups;
        this.studyIds = studyIds;
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
        validateStringLength(errors, 255, template.getName(), "name");
        if (template.getTemplateType() == null) {
            errors.rejectValue("templateType", "is required");
        }
        if (template.getCriteria() != null) {
            CriteriaUtils.validate(template.getCriteria(), dataGroups, studyIds, errors);    
        }
        validateStringLength(errors, TEXT_SIZE, template.getDescription(), "description");
    }

}
