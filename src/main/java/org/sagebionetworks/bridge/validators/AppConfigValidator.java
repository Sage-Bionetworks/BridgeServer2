package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.assessments.AssessmentReference;
import org.sagebionetworks.bridge.models.files.FileReference;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.AppConfigElementService;
import org.sagebionetworks.bridge.services.AssessmentService;
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
    private AssessmentService assessmentService;
    private boolean isNew;
    private Set<String> dataGroups;
    private Set<String> studyIds;
    
    public AppConfigValidator(SurveyService surveyService, UploadSchemaService schemaService,
            AppConfigElementService appConfigElementService, FileService fileService,
            AssessmentService assessmentService, Set<String> dataGroups, Set<String> studyIds, boolean isNew) {
        this.surveyService = surveyService;
        this.schemaService = schemaService;
        this.fileService = fileService;
        this.appConfigElementService = appConfigElementService;
        this.assessmentService = assessmentService;
        this.dataGroups = dataGroups;
        this.studyIds = studyIds;
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
            CriteriaUtils.validate(appConfig.getCriteria(), dataGroups, studyIds, errors);    
        }
        if (isBlank(appConfig.getAppId())) {
            errors.rejectValue("appId", "is required");
        } else {
            // We can't validate schema references if there is no appId
            if (appConfig.getSchemaReferences() != null) {
                Set<SchemaReference> uniqueRefs = new HashSet<>();
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
                        if (uniqueRefs.contains(ref)) {
                            errors.rejectValue("", "refers to the same schema as another reference");
                        } else {
                            try {
                                UploadSchema schema = schemaService.getUploadSchemaByIdAndRev(appConfig.getAppId(),
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
                            uniqueRefs.add(ref);
                        }
                    }
                    errors.popNestedPath();
                }
            }
            if (appConfig.getConfigReferences() != null) {
                Set<ConfigReference> uniqueRefs = new HashSet<>();
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
                        if (uniqueRefs.contains(ref)) {
                            errors.rejectValue("", "refers to the same config as another reference");
                        } else {
                            try {
                                AppConfigElement element = appConfigElementService
                                        .getElementRevision(appConfig.getAppId(), ref.getId(), ref.getRevision());
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
                        uniqueRefs.add(ref);
                    }
                    errors.popNestedPath();
                }
            }
        }
        if (appConfig.getSurveyReferences() != null) {
            Set<SurveyReference> uniqueRefs = new HashSet<>();
            for (int i=0; i < appConfig.getSurveyReferences().size(); i++) {
                SurveyReference ref = appConfig.getSurveyReferences().get(i);
                errors.pushNestedPath("surveyReferences["+i+"]");
                if (ref.getCreatedOn() == null) {
                    errors.rejectValue("createdOn", "is required");
                } else {
                    if (uniqueRefs.contains(ref)) {
                        errors.rejectValue("", "refers to the same survey as another reference");
                    } else {
                        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref);
                        Survey survey = surveyService.getSurvey(appConfig.getAppId(), keys, false, false);
                        // We do throw a validation error if the object is logically deleted because while the
                        // object will still be accessible through the API, it was deleted, suggesting the intention
                        // to stop using it, which is not reflected in this appConfig
                        if (survey == null || survey.isDeleted()) {
                            errors.rejectValue("", "does not refer to a survey");    
                        } else if (!survey.isPublished()) {
                            errors.rejectValue("", "has not been published");
                        }
                    }
                    uniqueRefs.add(ref);
                }
                errors.popNestedPath();
            }
        }
        if (appConfig.getFileReferences() != null) {
            Set<FileReference> uniqueRefs = new HashSet<>();
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
                    if (uniqueRefs.contains(ref)) {
                        errors.rejectValue("", "refers to the same file as another reference");
                    } else {
                        Optional<FileRevision> revision = fileService.getFileRevision(ref.getGuid(), ref.getCreatedOn());
                        if (!revision.isPresent()) {
                            errors.rejectValue("", "does not refer to a file revision");
                        }
                    }
                    uniqueRefs.add(ref);
                }
                errors.popNestedPath();
            }
        }
        // The only field that needs to be filled out is the assessment guid. We will find the relevant identifiers.
        if (appConfig.getAssessmentReferences() != null) {
            Set<AssessmentReference> uniqueRefs = new HashSet<>();
            for (int i=0; i < appConfig.getAssessmentReferences().size(); i++) {
                AssessmentReference ref = appConfig.getAssessmentReferences().get(i);
                errors.pushNestedPath("assessmentReferences["+i+"]");
                boolean validRef = true;
                if (ref.getAppId() == null) {
                    validRef = false;
                    errors.rejectValue("appId", "is required");
                } else if (!ref.getAppId().equals(appConfig.getAppId()) && !ref.getAppId().equals(SHARED_APP_ID)) {
                    validRef = false;
                    errors.rejectValue("appId", "is not a valid app");
                }
                if (ref.getGuid() == null) {
                    validRef = false;
                    errors.rejectValue("guid", "is required");
                } else if (!uniqueRefs.add(ref)) {
                    validRef = false;
                    errors.rejectValue("guid", "refers to the same assessment as another reference");
                }
                if (validRef) {
                    try {
                        assessmentService.getAssessmentByGuid(ref.getAppId(), null, ref.getGuid());
                    } catch(EntityNotFoundException e) {
                        errors.rejectValue("guid", "does not refer to an assessment in given app");
                    }
                }
                errors.popNestedPath();
            }
        }
    }
}
