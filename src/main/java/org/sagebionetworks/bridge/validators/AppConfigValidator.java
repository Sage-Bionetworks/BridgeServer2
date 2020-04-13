package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.files.FileReference;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.AppConfigElementService;
import org.sagebionetworks.bridge.services.FileService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigValidator implements Validator {

    private SurveyService surveyService;
    private UploadSchemaService schemaService;
    private AppConfigElementService appConfigElementService;
    private FileService fileService;
    private boolean isNew;
    private Set<String> dataGroups;
    private Set<String> substudyIds;
    
    public AppConfigValidator(SurveyService surveyService, UploadSchemaService schemaService,
            AppConfigElementService appConfigElementService, FileService fileService, Set<String> dataGroups,
            Set<String> substudyIds, boolean isNew) {
        this.surveyService = surveyService;
        this.schemaService = schemaService;
        this.fileService = fileService;
        this.appConfigElementService = appConfigElementService;
        this.dataGroups = dataGroups;
        this.substudyIds = substudyIds;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfig appConfig = (AppConfig)object;
        
        if (!isNew && isBlank(appConfig.getGuid())) {
            errors.rejectValue("guid", "is required");
        }
        if (isBlank(appConfig.getLabel())) {
            errors.rejectValue("label", "is required");
        }
        if (appConfig.getCriteria() == null) {
            errors.rejectValue("criteria", "are required");
        } else {
            CriteriaUtils.validate(appConfig.getCriteria(), dataGroups, substudyIds, errors);    
        }
        if (isBlank(appConfig.getStudyId())) {
            errors.rejectValue("studyId", "is required");
        } else {
            // We can't validate schema references if there is no studyId
            if (appConfig.getSchemaReferences() != null) {
                for (int i=0; i < appConfig.getSchemaReferences().size(); i++) {
                    SchemaReference ref = appConfig.getSchemaReferences().get(i);
                    errors.pushNestedPath("schemaReferences["+i+"]");
                    if (StringUtils.isBlank(ref.getId())) {
                        errors.rejectValue("id", "is required");
                    }
                    if (ref.getRevision() == null) {
                        errors.rejectValue("revision", "is required");
                    }
                    if (StringUtils.isNotBlank(ref.getId()) && ref.getRevision() != null) {
                        try {
                            UploadSchema schema = schemaService.getUploadSchemaByIdAndRev(appConfig.getStudyId(),
                                    ref.getId(), ref.getRevision());
                            // We do throw a validation error if the object is logically deleted because while the
                            // object will still be accessible through the API, it was deleted, suggesting the intention
                            // to stop using it, which is not reflected in this appConfig
                            if (schema.isDeleted()) {
                                errors.rejectValue("", "does not refer to an upload schema"); 
                            }
                            
                        } catch(EntityNotFoundException e) {
                            errors.rejectValue("", "does not refer to an upload schema");
                        }
                    }
                    errors.popNestedPath();
                }
            }
            if (appConfig.getConfigReferences() != null) {
                for (int i=0; i < appConfig.getConfigReferences().size(); i++) {
                    ConfigReference ref = appConfig.getConfigReferences().get(i);
                    errors.pushNestedPath("configReferences["+i+"]");
                    if (StringUtils.isBlank(ref.getId())) {
                        errors.rejectValue("id", "is required");
                    }
                    if (ref.getRevision() == null) {
                        errors.rejectValue("revision", "is required");
                    }
                    if (StringUtils.isNotBlank(ref.getId()) && ref.getRevision() != null) {
                        try {
                            AppConfigElement element = appConfigElementService
                                    .getElementRevision(appConfig.getStudyId(), ref.getId(), ref.getRevision());
                            // We do throw a validation error if the object is logically deleted because while the
                            // object will still be accessible through the API, it was deleted, suggesting the intention
                            // to stop using it, which is not reflected in this appConfig
                            if (element.isDeleted()) {
                                errors.rejectValue("", "does not refer to a configuration element"); 
                            }
                        } catch(EntityNotFoundException e) {
                            errors.rejectValue("", "does not refer to a configuration element");
                        }
                    }
                    errors.popNestedPath();
                }
            }
        }
        if (appConfig.getSurveyReferences() != null) {
            for (int i=0; i < appConfig.getSurveyReferences().size(); i++) {
                SurveyReference ref = appConfig.getSurveyReferences().get(i);
                errors.pushNestedPath("surveyReferences["+i+"]");
                if (ref.getCreatedOn() == null) {
                    errors.rejectValue("createdOn", "is required");
                } else {
                    GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref);
                    Survey survey = surveyService.getSurvey(appConfig.getStudyId(), keys, false, false);
                    // We do throw a validation error if the object is logically deleted because while the
                    // object will still be accessible through the API, it was deleted, suggesting the intention
                    // to stop using it, which is not reflected in this appConfig
                    if (survey == null || survey.isDeleted()) {
                        errors.rejectValue("", "does not refer to a survey");    
                    } else if (!survey.isPublished()) {
                        errors.rejectValue("", "has not been published");
                    }
                }
                errors.popNestedPath();
            }
        }
        if (appConfig.getFileReferences() != null) {
            for (int i=0; i < appConfig.getFileReferences().size(); i++) {
                FileReference ref = appConfig.getFileReferences().get(i);
                errors.pushNestedPath("fileReferences["+i+"]");
                if (ref.getGuid() == null || ref.getCreatedOn() == null) {
                    if (ref.getGuid() == null) {
                        errors.rejectValue("fileGuid", "is required");
                    }
                    if (ref.getCreatedOn() == null) {
                        errors.rejectValue("createdOn", "is required");
                    }
                } else {
                    Optional<FileRevision> revision = fileService.getFileRevision(ref.getGuid(), ref.getCreatedOn());
                    if (!revision.isPresent()) {
                        errors.rejectValue("", "does not refer to a file revision");
                    }
                }
                errors.popNestedPath();
            }
        }
    }

}
