package org.sagebionetworks.bridge.hibernate;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.EQ;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_QUESTION_TYPE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.BloodPressureConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementConstants;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyId;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class HibernateSurveyDaoTest extends Mockito {
    
    private static final String GUID = "oneGuid";
    private static final long CREATED_ON = DateTime.now(DateTimeZone.UTC).getMillis();
    private static final long TIMESTAMP = DateTime.now().getMillis();
    private static final String IDENTIFIER = "surveyId";
    private static final String IDENTIFIER_PREFIX = "identifier:";
    private static final GuidCreatedOnVersionHolder KEYS = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON);
    static final GuidCreatedOnVersionHolder SURVEY_IDENTIFIER_KEYS = new GuidCreatedOnVersionHolderImpl(IDENTIFIER_PREFIX+IDENTIFIER, CREATED_ON);
    private static final SurveyId surveyId = new SurveyId(KEYS);
    private static final List<SurveyElement> ELEMENTS = ImmutableList.of(getSurveyQuestion(), getSurveyInfoScreen());
    private static final List<HibernateSurveyElement> HIBERNATE_ELEMENTS = ImmutableList.of((HibernateSurveyQuestion)getSurveyQuestion(), (HibernateSurveyInfoScreen)getSurveyInfoScreen());
    
    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    UploadSchemaService mockUploadSchemaService;
    
    @InjectMocks
    @Spy
    HibernateSurveyDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String, Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<Survey> surveyCaptor;
    
    @Captor
    ArgumentCaptor<SurveyId> surveyIdCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        GuidCreatedOnVersionHolder holder = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON);
        SurveyId surveyId = new SurveyId(holder);
        
        doReturn(GUID).when(dao).generateGuid();
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP);
    }
    
    @Test
    public void createSurvey() {
        HibernateSurvey survey = getHibernateSurvey();
        survey.setGuid(null);
        
        Survey result = dao.createSurvey(survey);
        assertSame(survey, result);
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getCreatedOn(), TIMESTAMP);
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertNull(result.getSchemaRevision());
        assertFalse(result.isPublished());
        assertFalse(result.isDeleted());
        assertNull(result.getVersion());
        assertEquals(result.getElements().get(0).getGuid(), GUID);
        
        verify(mockHelper).query(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getValue(), "DELETE FROM HibernateSurveyElement WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn");
        assertEquals(paramsCaptor.getValue().get("surveyGuid"), GUID);
        assertEquals(paramsCaptor.getValue().get("createdOn"), TIMESTAMP);
        
        verify(mockHelper).update(surveyCaptor.capture(), any());
        assertSame(surveyCaptor.getValue(), survey);
    }
    
    @Test
    public void updateSurvey() {
        HibernateSurvey survey = getHibernateSurvey();
        // Some fields are copied over to the existing survey because they are mutable.
        // verify this.
        survey.setName("Updated Name");
        survey.setCopyrightNotice("Updated Copyright Notice");
        survey.setDeleted(true);
        survey.setVersion(3L);
        
        survey.setGuid(GUID);
        survey.setSchemaRevision(3);
        
        HibernateSurvey saved = new HibernateSurvey();
        saved.setGuid(GUID);
        saved.setCreatedOn(TIMESTAMP);
        
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(saved);
        
        Survey result = dao.updateSurvey(TEST_STUDY, survey);
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertNull(result.getSchemaRevision());
        
        verify(mockHelper).query(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getValue(), "DELETE FROM HibernateSurveyElement WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn");
        assertEquals(paramsCaptor.getValue().get("surveyGuid"), GUID);
        assertEquals(paramsCaptor.getValue().get("createdOn"), TIMESTAMP);
        
        verify(mockHelper).update(surveyCaptor.capture(), any());
        Survey updated = surveyCaptor.getValue();
        
        assertEquals(updated.getName(), "Updated Name");
        assertEquals(updated.getCopyrightNotice(), "Updated Copyright Notice");
        assertTrue(updated.isDeleted());
        assertEquals(updated.getVersion(), new Long(3L));
    }
    
    @Test
    public void versionSurvey() {
        HibernateSurvey survey = new HibernateSurvey();
        // Mess up some things to verify they are changed by versioning
        survey.setGuid(GUID);
        survey.setPublished(true);
        survey.setDeleted(true);
        survey.setVersion(100L);
        survey.setCreatedOn(0L);
        survey.setModifiedOn(0L);
        survey.setSchemaRevision(2);
        survey.setElements(ImmutableList.of());
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(survey);
        
        Survey result = dao.versionSurvey(TEST_STUDY, KEYS);
        
        assertFalse(result.isPublished());
        assertFalse(result.isDeleted());
        assertNull(result.getVersion());
        assertEquals(result.getCreatedOn(), TIMESTAMP);
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertNull(result.getSchemaRevision());
        
        verify(mockHelper).query(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getValue(), "DELETE FROM HibernateSurveyElement WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn");
        assertEquals(paramsCaptor.getValue().get("surveyGuid"), GUID);
        assertEquals(paramsCaptor.getValue().get("createdOn"), TIMESTAMP);
        
        verify(mockHelper).update(surveyCaptor.capture(), any());
        Survey updated = surveyCaptor.getValue();
        
        assertFalse(updated.isPublished());
        assertFalse(updated.isDeleted());
        assertNull(updated.getVersion());
        assertEquals(updated.getCreatedOn(), TIMESTAMP);
        assertEquals(updated.getModifiedOn(), TIMESTAMP);
        assertNull(updated.getSchemaRevision());
    }
    
    @Test
    public void publishSurveySchemaRevision() {
        HibernateSurvey survey = getHibernateSurvey();
        
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(survey);
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(SurveyElement.class))).thenReturn(ELEMENTS);
        
        DynamoUploadSchema savedSchema = new DynamoUploadSchema();
        savedSchema.setRevision(3);
        when(mockUploadSchemaService.createUploadSchemaFromSurvey(TEST_STUDY, survey, true)).thenReturn(savedSchema);
        
        Survey result = dao.publishSurvey(TEST_STUDY, survey, true);
        assertTrue(result.isPublished());
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertEquals(result.getSchemaRevision(), new Integer(3));
        assertEquals(result.getElements().size(), 2);
        
        verify(mockUploadSchemaService).createUploadSchemaFromSurvey(TEST_STUDY, survey, true);
        verify(mockHelper).update(surveyCaptor.capture(), any());
        assertSame(surveyCaptor.getValue(), result);
    }
    
    @Test
    public void publishSurveyNoSchemaRevision() {
        HibernateSurvey survey = getHibernateSurvey();
        
        DynamoUploadSchema savedSchema = new DynamoUploadSchema();
        savedSchema.setRevision(3);
        when(mockUploadSchemaService.createUploadSchemaFromSurvey(TEST_STUDY, survey, false)).thenReturn(savedSchema);
        
        Survey result = dao.publishSurvey(TEST_STUDY, survey, false);
        assertTrue(survey.isPublished());
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertEquals(result.getSchemaRevision(), new Integer(3));
        assertEquals(result.getElements().size(), 2);
        
        verify(mockUploadSchemaService).createUploadSchemaFromSurvey(TEST_STUDY, survey, false);
        verify(mockHelper).update(surveyCaptor.capture(), any());
        assertSame(surveyCaptor.getValue(), result);
    }
    
    @Test
    public void publishSurveyNoQuestions() {
        HibernateSurvey survey = getHibernateSurvey();
        survey.setElements(ImmutableList.of());
        
        Survey result = dao.publishSurvey(TEST_STUDY, survey, true);
        assertTrue(result.isPublished());
        assertEquals(result.getModifiedOn(), TIMESTAMP);
        assertEquals(result.getSchemaRevision(), new Integer(1));
        
        verify(mockUploadSchemaService, never()).createUploadSchemaFromSurvey(any(), any(), anyBoolean());
        verify(mockHelper).update(surveyCaptor.capture(), any());
        assertSame(surveyCaptor.getValue(), result);
    }
    
    @Test
    public void publishSurveyAlreadyPublished() {
        HibernateSurvey survey = getHibernateSurvey();
        survey.setPublished(true);
        dao.publishSurvey(TEST_STUDY, survey, true);
        
        verify(mockUploadSchemaService, never()).createUploadSchemaFromSurvey(any(), any(), anyBoolean());
        verify(mockHelper, never()).update(any(), any());
    }
    
    @Test
    public void deleteSurvey() {
        HibernateSurvey survey = getHibernateSurvey();
        
        dao.deleteSurvey(survey);
        
        verify(mockHelper).update(surveyCaptor.capture(), any());
        assertTrue(surveyCaptor.getValue().isDeleted());
    }
    
    @Test
    public void deleteSurveyPermanently() {
        HibernateSurvey survey = getHibernateSurvey();
        
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(survey);
        
        dao.deleteSurveyPermanently(TEST_STUDY, KEYS);
        
        verify(mockHelper).deleteById(eq(HibernateSurvey.class), surveyIdCaptor.capture());
        assertEquals(surveyIdCaptor.getValue().getSurveyGuid(), GUID);
        assertEquals(surveyIdCaptor.getValue().getCreatedOn(), CREATED_ON);
        
        verify(mockUploadSchemaService).deleteUploadSchemaByIdPermanently(TEST_STUDY, IDENTIFIER);
    }
    
    @Test
    public void deleteSurveyPermanentlyNotFound() {
        dao.deleteSurveyPermanently(TEST_STUDY, KEYS);
        
        verify(mockHelper, never()).deleteById(any(), any());
        verify(mockUploadSchemaService, never()).deleteUploadSchemaByIdPermanently(any(), any());
    }
    
    @Test
    public void deleteSurveyPermanentlySchemaNotFound() {
        doThrow(new EntityNotFoundException(UploadSchema.class)).when(mockUploadSchemaService)
                .deleteUploadSchemaByIdPermanently(any(), any());
        
        HibernateSurvey hibernateSurvey = getHibernateSurvey();
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(hibernateSurvey);
        
        dao.deleteSurveyPermanently(TEST_STUDY, KEYS);
        
        verify(mockHelper).deleteById(eq(HibernateSurvey.class), surveyIdCaptor.capture());
        assertEquals(surveyIdCaptor.getValue().getSurveyGuid(), GUID);
        assertEquals(surveyIdCaptor.getValue().getCreatedOn(), CREATED_ON);
        
        verify(mockUploadSchemaService).deleteUploadSchemaByIdPermanently(TEST_STUDY, IDENTIFIER);
    }
    
    @Test
    public void getSurveyIncludeElements() throws Exception {
        HibernateSurvey existing = getHibernateSurvey();
        
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(existing);
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class)))
            .thenReturn(HIBERNATE_ELEMENTS);
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON);
        Survey survey = dao.getSurvey(TEST_STUDY, keys, true);
        assertEquals(survey.getIdentifier(), IDENTIFIER);
        assertEquals(survey.getElements().size(), 2);
        assertSame(existing, survey);
        
        verify(mockHelper).getById(eq(HibernateSurvey.class), surveyIdCaptor.capture());
        assertEquals(surveyIdCaptor.getValue().getSurveyGuid(), GUID);
        assertEquals(surveyIdCaptor.getValue().getCreatedOn(), CREATED_ON);
    }
    
    @Test
    public void getSurveyNotFound() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON);
        
        assertNull(dao.getSurvey(TEST_STUDY, keys, true));
    }
    
    @Test
    public void getSurveyWithIdentifier() throws Exception {
        HibernateSurvey existing = getHibernateSurvey();
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class))).thenReturn(ImmutableList.of(existing));
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class))).thenReturn(HIBERNATE_ELEMENTS);
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(IDENTIFIER_PREFIX+IDENTIFIER, CREATED_ON);
        Survey survey = dao.getSurvey(TEST_STUDY, keys, true);
        assertEquals(survey.getIdentifier(), IDENTIFIER);
        assertEquals(survey.getElements().size(), 2);
        assertSame(existing, survey);
        
        verify(mockHelper).queryGet(queryCaptor.capture(),paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        
        assertEquals(queryCaptor.getValue(), "SELECT survey FROM HibernateSurvey as survey WHERE " +
                "studyKey = :studyKey AND identifier = :identifier AND deleted = 0 AND createdOn = :createdOn " + 
                "ORDER BY createdOn DESC");
        assertEquals(paramsCaptor.getValue().get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(paramsCaptor.getValue().get("identifier"), IDENTIFIER);
        assertEquals(paramsCaptor.getValue().get("createdOn"), CREATED_ON);
    }
    
    @Test
    public void getSurveyNotFoundWithIdentifier() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(IDENTIFIER_PREFIX+IDENTIFIER, CREATED_ON);
        
        assertNull(dao.getSurvey(TEST_STUDY, keys, true));
    }
    
    @Test
    public void getSurveyAllVersionsIncludeDeleted() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        survey1A.setDeleted(true);
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        survey1B.setDeleted(true);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
                .thenReturn(ImmutableList.of(survey1A, survey1B));
        
        List<Survey> results = dao.getSurveyAllVersions(TEST_STUDY, GUID, true);
        assertEquals(results, ImmutableList.of(survey1A, survey1B));
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("guid"), GUID);
        
        String getQuery = queryCaptor.getAllValues().get(0);
        assertEquals(getQuery, "SELECT survey FROM HibernateSurvey as survey WHERE " + 
                "studyKey = :studyKey AND guid = :guid ORDER BY createdOn DESC");
    }
    
    @Test
    public void getSurveyMostRecentVersion() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
                .thenReturn(ImmutableList.of(survey1A, survey1B));
        
        Survey survey = dao.getSurveyMostRecentVersion(TEST_STUDY, GUID);
        assertSame(survey, survey1A);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("guid"), GUID);
        assertEquals(queryCaptor.getValue(), "SELECT survey FROM HibernateSurvey as survey " + 
                "WHERE studyKey = :studyKey AND guid = :guid AND deleted = 0 ORDER BY createdOn DESC");
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionIncludeElements() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
                .thenReturn(ImmutableList.of(survey1A, survey1B));
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class)))
            .thenReturn(HIBERNATE_ELEMENTS);
        
        Survey result = dao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, GUID, true);
        assertSame(result, survey1A);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("guid"), GUID);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurveyElement.class));
        queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("surveyGuid"), GUID);
        assertEquals(queryParams.get("createdOn"), CREATED_ON);
        
        String surveyQuery = queryCaptor.getAllValues().get(0);
        String surveyElementQuery = queryCaptor.getAllValues().get(1);
        
        assertEquals(surveyQuery, "SELECT survey FROM HibernateSurvey as survey WHERE studyKey = :studyKey " + 
                "AND deleted = 0 AND published = 1 AND guid = :guid ORDER BY createdOn DESC");
        assertEquals(surveyElementQuery, "SELECT surveyElement FROM HibernateSurveyElement as surveyElement " + 
                "WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn ORDER BY order ASC");
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersionIncludeDeleted() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        // Survey 2A
        HibernateSurvey survey2A = getHibernateSurvey();
        survey2A.setGuid("guidTwo");
        // Survey 2B
        HibernateSurvey survey2B = getHibernateSurvey();
        survey2B.setGuid("guidTwo");
        survey2B.setCreatedOn(CREATED_ON-2000L);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
        .thenReturn(ImmutableList.of(survey1A, survey1B, survey2A, survey2B));
        
        List<Survey> results = dao.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY, true);
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).getCreatedOn(), CREATED_ON);
        assertEquals(results.get(1).getCreatedOn(), CREATED_ON);
        assertEquals(ImmutableSet.of(results.get(0).getGuid(), results.get(1).getGuid()),
                ImmutableSet.of(GUID, "guidTwo"));
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        assertEquals(queryCaptor.getValue(), "SELECT survey FROM HibernateSurvey as survey " + 
                "WHERE studyKey = :studyKey AND published = 1 ORDER BY guid, createdOn DESC");
    }
    
    @Test
    public void constraintRulesShiftedToQuestionAfterRules() {
        HibernateSurvey survey = getHibernateSurvey();
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(survey);
        
        // This needs to be shifted to the after rules of the question itself
        BloodPressureConstraints constraints = new BloodPressureConstraints();
        constraints.getRules().add(new SurveyRule.Builder().withEndSurvey(true).build());
        HibernateSurveyQuestion question = new HibernateSurveyQuestion();
        question.setUiHint(UIHint.BLOODPRESSURE);
        question.setType(SURVEY_QUESTION_TYPE);
        question.setConstraints(constraints);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class))).thenReturn(ImmutableList.of(question));
        
        Survey result = dao.getSurvey(TEST_STUDY, KEYS, true);
        HibernateSurveyQuestion resultQuestion = (HibernateSurveyQuestion) result.getUnmodifiableQuestionList().get(0);
        assertTrue(resultQuestion.getAfterRules().get(0).getEndSurvey());
    }
    
    @Test
    public void constraintRulesOnQuestionOverwriteConstraintRules() {
        HibernateSurvey survey = getHibernateSurvey();
        when(mockHelper.getById(eq(HibernateSurvey.class), any())).thenReturn(survey);
        
        // This needs to be shifted to the after rules of the question itself
        BloodPressureConstraints constraints = new BloodPressureConstraints();
        // This does not have endSurvey as a rule, but it will be replaced
        constraints.getRules().add(new SurveyRule.Builder().build());
        HibernateSurveyQuestion question = new HibernateSurveyQuestion();
        question.setAfterRules(ImmutableList.of(new SurveyRule.Builder().withEndSurvey(true).build()));
        question.setUiHint(UIHint.BLOODPRESSURE);
        question.setType(SURVEY_QUESTION_TYPE);
        question.setConstraints(constraints);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class))).thenReturn(ImmutableList.of(question));
        
        Survey result = dao.getSurvey(TEST_STUDY, KEYS, true);
        SurveyQuestion resultQuestion = result.getUnmodifiableQuestionList().get(0);
        SurveyRule rule = resultQuestion.getConstraints().getRules().get(0);
        assertTrue(rule.getEndSurvey());
    }
    
    @Test
    public void getSurveyIncludeElementsByIdentifer() {
        HibernateSurvey survey = getHibernateSurvey();
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class))).thenReturn(ImmutableList.of(survey));
        
        // There is a question that should be retrieved.
        HibernateSurveyQuestion question = new HibernateSurveyQuestion();
        question.setType(SurveyElementConstants.SURVEY_QUESTION_TYPE);
        question.setUiHint(UIHint.BLOODPRESSURE);
        question.setConstraints(new BloodPressureConstraints());
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class))).thenReturn(ImmutableList.of(question));
        
        Survey result = dao.getSurvey(TEST_STUDY, SURVEY_IDENTIFIER_KEYS, true);
        assertEquals(result.getElements().size(), 1);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("identifier"), IDENTIFIER);
        assertEquals(queryParams.get("createdOn"), CREATED_ON);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurveyElement.class));
        queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("surveyGuid"), GUID);
        assertEquals(queryParams.get("createdOn"), CREATED_ON);
        
        String surveyQuery = queryCaptor.getAllValues().get(0);
        String surveyElementQuery = queryCaptor.getAllValues().get(1);
        
        assertEquals(surveyQuery, "SELECT survey FROM HibernateSurvey as survey WHERE studyKey = :studyKey " + 
                "AND identifier = :identifier AND deleted = 0 AND createdOn = :createdOn ORDER BY createdOn DESC");
        assertEquals(surveyElementQuery, "SELECT surveyElement FROM HibernateSurveyElement as surveyElement " + 
                "WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn ORDER BY order ASC");
    }
    
    @Test
    public void getSurveyAllVersionsIncludeDeletedByIdentifier() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        survey1A.setDeleted(true);
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        survey1B.setDeleted(true);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
            .thenReturn(ImmutableList.of(survey1A, survey1B));
        
        List<Survey> results = dao.getSurveyAllVersions(TEST_STUDY, IDENTIFIER_PREFIX+IDENTIFIER, true);
        assertEquals(results, ImmutableList.of(survey1A, survey1B));
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("identifier"), IDENTIFIER);
        
        String getQuery = queryCaptor.getAllValues().get(0);
        assertEquals(getQuery, "SELECT survey FROM HibernateSurvey as survey WHERE studyKey = :studyKey AND identifier = :identifier ORDER BY createdOn DESC");
    }
    
    @Test
    public void getSurveyMostRecentVersionByIdentifier() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
                .thenReturn(ImmutableList.of(survey1A, survey1B));

        Survey survey = dao.getSurveyMostRecentVersion(TEST_STUDY, IDENTIFIER_PREFIX+IDENTIFIER);
        assertSame(survey, survey1A);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("identifier"), IDENTIFIER);
        
        String getQuery = queryCaptor.getAllValues().get(0);
        assertEquals(getQuery, "SELECT survey FROM HibernateSurvey as survey WHERE studyKey = :studyKey " + 
                "AND identifier = :identifier AND deleted = 0 ORDER BY createdOn DESC");
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionIncludeElementsByIdentifier() {
        // Survey 1A
        HibernateSurvey survey1A = getHibernateSurvey();
        // Survey 1B
        HibernateSurvey survey1B = getHibernateSurvey();
        survey1B.setCreatedOn(CREATED_ON-2000L);
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurvey.class)))
                .thenReturn(ImmutableList.of(survey1A, survey1B));
        
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(HibernateSurveyElement.class)))
            .thenReturn(HIBERNATE_ELEMENTS);
        
        Survey result = dao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, IDENTIFIER_PREFIX+IDENTIFIER, true);
        assertSame(result, survey1A);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurvey.class));
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("studyKey"), TEST_STUDY_IDENTIFIER);
        assertEquals(queryParams.get("identifier"), IDENTIFIER);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), any(), any(), eq(HibernateSurveyElement.class));
        queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("surveyGuid"), GUID);
        assertEquals(queryParams.get("createdOn"), CREATED_ON);
        
        String surveyQuery = queryCaptor.getAllValues().get(0);
        String surveyElementQuery = queryCaptor.getAllValues().get(1);
        
        assertEquals(surveyQuery, "SELECT survey FROM HibernateSurvey as survey WHERE studyKey = :studyKey " + 
                "AND deleted = 0 AND published = 1 AND identifier = :identifier ORDER BY createdOn DESC");
        assertEquals(surveyElementQuery, "SELECT surveyElement FROM HibernateSurveyElement as surveyElement " + 
                "WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn ORDER BY order ASC");
    }
    
    private HibernateSurvey getHibernateSurvey() {
        HibernateSurvey hibernateSurvey = new HibernateSurvey();
        hibernateSurvey.setGuid(GUID);
        hibernateSurvey.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        hibernateSurvey.setIdentifier(IDENTIFIER);
        hibernateSurvey.setCreatedOn(CREATED_ON);
        hibernateSurvey.setElements(ELEMENTS);
        hibernateSurvey.setPublished(false);
        hibernateSurvey.setSchemaRevision(1);
        hibernateSurvey.setVersion(1L);
        
        return hibernateSurvey;
    }
    
    private static SurveyElement getSurveyQuestion() {
        HibernateSurveyQuestion question = new HibernateSurveyQuestion();
        question.setPrompt("prompt");
        question.setPromptDetail("promptDetail");
        question.setFireEvent(true);
        question.setUiHint(UIHint.COMBOBOX);
        question.setConstraints(new IntegerConstraints());
        question.setSurveyCompoundKey("surveyCompoundKey");
        question.setIdentifier("identifier");
        question.setType("SurveyQuestion");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        question.setBeforeRules(Lists.newArrayList(beforeRule));
        question.setAfterRules(Lists.newArrayList(afterRule));
        
        return question;
    }
    
    public static SurveyElement getSurveyInfoScreen() {
        HibernateSurveyInfoScreen screen = new HibernateSurveyInfoScreen();
        screen.setPrompt("prompt");
        screen.setPromptDetail("promptDetail");
        screen.setTitle("title");
        screen.setImage(new Image("sourceUrl", 100, 100));
        screen.setSurveyCompoundKey("surveyCompoundKey");
        screen.setIdentifier("identifier");
        screen.setType("SurveyInfoScreen");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        screen.setBeforeRules(Lists.newArrayList(beforeRule));
        screen.setAfterRules(Lists.newArrayList(afterRule));
        
        return screen;
    }
}
