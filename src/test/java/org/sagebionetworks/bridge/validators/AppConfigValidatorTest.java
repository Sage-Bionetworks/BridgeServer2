package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.List;
import java.util.Optional;

import org.joda.time.DateTime;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.assessments.Assessment;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AppConfigValidatorTest extends Mockito {
    
    private static final SurveyReference INVALID_SURVEY_REF = new SurveyReference(null, "guid", null);
    private static final SurveyReference VALID_UNRESOLVED_SURVEY_REF = new SurveyReference(null, "guid",
            DateTime.now());
    private static final GuidCreatedOnVersionHolder VALID_RESOLVED_SURVEY_KEYS = new GuidCreatedOnVersionHolderImpl(
            VALID_UNRESOLVED_SURVEY_REF);
    private static final SchemaReference INVALID_SCHEMA_REF = new SchemaReference("guid", null);
    private static final SchemaReference VALID_SCHEMA_REF = new SchemaReference("guid", 3);
    private static final ConfigReference VALID_CONFIG_REF = new ConfigReference("id", 3L);
    private static final AssessmentReference INVALID_ASSESSMENT_REF = new AssessmentReference(null, null, null);
    private static final AssessmentReference VALID_ASSESSMENT_REF = new AssessmentReference(GUID, null, null);
    
    @Mock
    private SurveyService mockSurveyService;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Mock
    private AppConfigElementService mockAppConfigElementService;
    
    @Mock
    private FileService mockFileService;
    
    @Mock
    private AssessmentService mockAssessmentService;
    
    private AppConfigValidator newValidator;
    
    private AppConfigValidator updateValidator;
    
    private AppConfig appConfig;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        appConfig = AppConfig.create();
        appConfig.setAppId(TEST_APP_ID);
        
        this.newValidator = new AppConfigValidator(mockSurveyService, mockSchemaService, mockAppConfigElementService,
                mockFileService, mockAssessmentService, USER_DATA_GROUPS, USER_STUDY_IDS, true);
        this.updateValidator = new AppConfigValidator(mockSurveyService, mockSchemaService, mockAppConfigElementService,
                mockFileService, mockAssessmentService, USER_DATA_GROUPS, USER_STUDY_IDS, false);
    }
    
    @Test
    public void assessmentReferenceNoGuidValidated() {
        appConfig.setAssessmentReferences(Lists.newArrayList(VALID_ASSESSMENT_REF, INVALID_ASSESSMENT_REF));
        
        Assessment assessment = new Assessment();
        assessment.setIdentifier("anIdentifier");
        assessment.setOriginGuid("originGuid");
        
        Assessment sharedAssessment = new Assessment();
        sharedAssessment.setIdentifier("aSharedIdentifier");
        
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID)).thenReturn(assessment);
        when(mockAssessmentService.getAssessmentByGuid(SHARED_APP_ID, null, "originGuid")).thenReturn(sharedAssessment);
        
        assertValidatorMessage(newValidator, appConfig, "assessmentReferences[1].guid", "is required");
    }
    
    @Test
    public void assessmentReferenceInvalidGuidValidated() {
        appConfig.setAssessmentReferences(ImmutableList.of(VALID_ASSESSMENT_REF));
        
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID))
            .thenThrow(new EntityNotFoundException(Assessment.class));
        
        assertValidatorMessage(newValidator, appConfig, "assessmentReferences[0].guid", "does not refer to an assessment");
    }
    
    @Test
    public void assessmentReferenceIncludedTwice() {
        appConfig.setAssessmentReferences(ImmutableList.of(VALID_ASSESSMENT_REF, VALID_ASSESSMENT_REF));
        
        assertValidatorMessage(newValidator, appConfig, "assessmentReferences[1].guid", "refers to the same assessment as another reference");
    }
    
    @Test
    public void configReferenceValidated() {
        ConfigReference ref1 = new ConfigReference("id:1", 1L);
        ConfigReference ref2 = new ConfigReference("id:2", 2L);
        
        when(mockAppConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(AppConfigElement.create());
        
        List<ConfigReference> references = ImmutableList.of(ref1, ref2);
        appConfig.setConfigReferences(references);
        appConfig.setLabel("label");
        appConfig.setCriteria(Criteria.create());
        
        // This succeeds because the mock does not throw an exception
        Validate.entityThrowingException(newValidator, appConfig);
    }
    
    @Test
    public void configReferenceInvalid() {
        ConfigReference ref = new ConfigReference(null, null);
        
        appConfig.setConfigReferences(ImmutableList.of(ref));
        
        assertValidatorMessage(newValidator, appConfig, "configReferences[0].id", "is required");
        assertValidatorMessage(newValidator, appConfig, "configReferences[0].revision", "is required");
    }
    
    @Test
    public void configReferenceNotFound() { 
        ConfigReference ref1 = new ConfigReference("id:1", 1L);
        appConfig.setConfigReferences(ImmutableList.of(ref1));
        appConfig.setAppId(TEST_APP_ID);
        
        when(mockAppConfigElementService.getElementRevision(TEST_APP_ID, "id:1", 1L))
                .thenThrow(new EntityNotFoundException(AppConfigElement.class));
        
        // This succeeds because the mock does not throw an exception
        assertValidatorMessage(newValidator, appConfig, "configReferences[0]", "does not refer to a configuration element");
    }
    
    @Test
    public void configReferenceIncludedTwice() {
        ConfigReference ref1 = new ConfigReference("id:1", 1L);
        
        when(mockAppConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(AppConfigElement.create());
        
        List<ConfigReference> references = ImmutableList.of(ref1, ref1);
        appConfig.setConfigReferences(references);
        appConfig.setLabel("label");
        appConfig.setCriteria(Criteria.create());
        
        assertValidatorMessage(newValidator, appConfig, "configReferences[1]", "refers to the same config as another reference");
    }
    
    @Test
    public void guidRequired() {
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(updateValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void labelRequired() {
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
        
        appConfig.setLabel("");
        assertValidatorMessage(newValidator, appConfig, "label", "is required");
    }
    
    @Test
    public void appIdRequired() {
        appConfig.setAppId(null);
        assertValidatorMessage(newValidator, appConfig, "appId", "is required");
        
        appConfig.setAppId("");
        assertValidatorMessage(newValidator, appConfig, "appId", "is required");
    }
    
    @Test
    public void criteriaAreRequired() {
        assertValidatorMessage(newValidator, appConfig, "criteria", "are required");
    }
    
    @Test
    public void surveyReferencesHaveCreatedOnTimestamps() { 
        appConfig.getSurveyReferences().add(INVALID_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0].createdOn", "is required");
    }
    
    @Test
    public void surveyReferencesIncludedTwice() {
        appConfig.setSurveyReferences(ImmutableList.of(VALID_UNRESOLVED_SURVEY_REF, VALID_UNRESOLVED_SURVEY_REF));
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[1]",
                "refers to the same survey as another reference");
    }

    @Test
    public void schemaReferencesHaveId() {
        SchemaReference invalidSchemaRef = new SchemaReference(null, 3);
        appConfig.getSchemaReferences().add(invalidSchemaRef);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].id", "is required");
    }
    
    @Test
    public void schemaReferencesHaveRevision() { 
        appConfig.getSchemaReferences().add(INVALID_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0].revision", "is required");
    }
    
    @Test
    public void schemaDoesNotExistOnCreate() {
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, "guid", 3))
                .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(newValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    @Test
    public void schemaDoesNotExistOnUpdate() {
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, "guid", 3))
            .thenThrow(new EntityNotFoundException(AppConfig.class));
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }
    
    @Test
    public void schemaReferenceIncludedTwice() {
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, "guid", 3)).thenReturn(UploadSchema.create());
        
        appConfig.setSchemaReferences(ImmutableList.of(VALID_SCHEMA_REF, VALID_SCHEMA_REF));
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[1]", "refers to the same schema as another reference");
        
    }
    
    @Test
    public void surveyDoesNotExistOnCreate() {
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void surveyDoesNotExistOnUpdate() {
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }    
    
    @Test
    public void surveyIsNotPublishedOnCreate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(newValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void surveyIsNotPublishedOnUpdate() {
        Survey survey = Survey.create();
        survey.setPublished(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "has not been published");
    }
    
    @Test
    public void criteriaAreValidated() { 
        Criteria criteria = Criteria.create();
        criteria.setNoneOfGroups(Sets.newHashSet("bad-group"));
        criteria.setAllOfStudyIds(Sets.newHashSet("wrong-group"));
        
        appConfig.setCriteria(criteria);
        
        assertValidatorMessage(newValidator, appConfig, "noneOfGroups", "'bad-group' is not in enumeration: group1, group2");
        assertValidatorMessage(newValidator, appConfig, "allOfStudyIds", "'wrong-group' is not in enumeration: studyA, studyB");
    }
    
    @Test
    public void rejectsReferenceToLogicallyDeletedSurvey() {
        Survey survey = Survey.create();
        survey.setDeleted(true);
        when(mockSurveyService.getSurvey(TEST_APP_ID, VALID_RESOLVED_SURVEY_KEYS, false, false)).thenReturn(survey);
        
        appConfig.getSurveyReferences().add(VALID_UNRESOLVED_SURVEY_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "surveyReferences[0]", "does not refer to a survey");
    }
    
    @Test
    public void rejectsReferenceToLogicallyDeletedSchema() {
        UploadSchema schema = UploadSchema.create();
        schema.setDeleted(true);
        
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, "guid", 3)).thenReturn(schema);
        
        appConfig.getSchemaReferences().add(VALID_SCHEMA_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "schemaReferences[0]", "does not refer to an upload schema");
    }

    @Test
    public void rejectsReferenceToLogicallyDeletedConfigElement() {
        AppConfigElement element = AppConfigElement.create();
        element.setDeleted(true);
        
        when(mockAppConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(element);
        
        appConfig.getConfigReferences().add(VALID_CONFIG_REF);
        
        assertValidatorMessage(updateValidator, appConfig, "configReferences[0]", "does not refer to a configuration element");
    }
    
    @Test
    public void fileReferenceIsValid() {
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        appConfig.setLabel("label");
        appConfig.setCriteria(Criteria.create());
        appConfig.getFileReferences().add(ref);
        
        when(mockFileService.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.of(new FileRevision()));

        Validate.entityThrowingException(newValidator, appConfig);
    }
    
    @Test
    public void fileReferenceHasFieldsGuid() {
        FileReference invalidFileRef = new FileReference(null, null);
        appConfig.getFileReferences().add(invalidFileRef);
        
        assertValidatorMessage(newValidator, appConfig, "fileReferences[0].fileGuid", "is required");
        assertValidatorMessage(newValidator, appConfig, "fileReferences[0].createdOn", "is required");
    }
    
    @Test
    public void fileReferenceDoesNotExist() {
        FileReference invalidFileRef = new FileReference(GUID, TIMESTAMP);
        appConfig.getFileReferences().add(invalidFileRef);
        
        when(mockFileService.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.empty());
        
        assertValidatorMessage(newValidator, appConfig, "fileReferences[0]", "does not refer to a file revision");
    }
    
    @Test
    public void fileReferenceIncludedTwice() {
        FileReference fileRef = new FileReference(GUID, TIMESTAMP);
        appConfig.setFileReferences(ImmutableList.of(fileRef, fileRef));
        
        FileRevision rev = new FileRevision();
        when(mockFileService.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.of(rev));
        
        assertValidatorMessage(newValidator, appConfig, "fileReferences[1]", "refers to the same file as another reference");
    }
}
