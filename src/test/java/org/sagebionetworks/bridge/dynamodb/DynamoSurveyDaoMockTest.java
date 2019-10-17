package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DynamoSurveyDaoMockTest {
    private static final DateTime MOCK_NOW = DateTime.parse("2016-08-24T15:23:57.123-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();

    private static final int SCHEMA_REV = 42;

    private static final String SURVEY_GUID = "test-guid";
    private static final long SURVEY_CREATED_ON = 1337;
    private static final String SURVEY_ID = "test-survey";
    private static final GuidCreatedOnVersionHolder SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID,
            SURVEY_CREATED_ON);

    private Survey survey;
    
    @Spy
    private DynamoSurveyDao surveyDao;

    @Mock
    private DynamoDBMapper mockSurveyMapper;

    @Mock
    private DynamoDBMapper mockSurveyElementMapper;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Mock
    private QueryResultPage<DynamoSurvey> mockQueryResultPage;
    
    @Mock
    private QueryResultPage<SurveyElement> mockElementQueryResultPage;
    
    @Captor
    private ArgumentCaptor<Survey> surveyCaptor;
    
    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }
    
    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);
        // set up survey
        survey = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        survey.setIdentifier(SURVEY_ID);
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);

        // mock schema dao
        UploadSchema schema = UploadSchema.create();
        schema.setRevision(SCHEMA_REV);

        when(mockSchemaService.createUploadSchemaFromSurvey(TestConstants.TEST_STUDY, survey, true)).thenReturn(
                schema);

        // set up survey dao for test
        surveyDao.setSurveyMapper(mockSurveyMapper);
        surveyDao.setUploadSchemaService(mockSchemaService);
        surveyDao.setSurveyElementMapper(mockSurveyElementMapper);

        // spy getSurvey() - There's a lot of complex logic in that query builder that's irrelevant to what we're
        // trying to test. Rather than over-specify our test and make our tests overly complicated, we'll just spy out
        // getSurvey().
        doReturn(survey).when(surveyDao).getSurvey(any(), eq(SURVEY_KEY), anyBoolean());
    }

    @Test
    public void getSurveyGuidForIdentifier() {
        // Mock query.
        DynamoSurvey survey = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        when(mockQueryResultPage.getResults()).thenReturn(ImmutableList.of(survey));
        when(mockSurveyMapper.queryPage(eq(DynamoSurvey.class), any())).thenReturn(mockQueryResultPage);

        // Execute.
        String surveyGuid = surveyDao.getSurveyGuidForIdentifier(TestConstants.TEST_STUDY, SURVEY_ID);
        assertEquals(surveyGuid, SURVEY_GUID);

        // Verify query.
        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoSurvey> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getStudyIdentifier(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertFalse(query.isConsistentRead());
        assertEquals(query.getLimit().intValue(), 1);

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("identifier");
        assertEquals(rangeKeyCondition.getComparisonOperator(), ComparisonOperator.EQ.toString());
        assertEquals(rangeKeyCondition.getAttributeValueList().get(0).getS(), SURVEY_ID);
    }

    @Test
    public void getSurveyGuidForIdentifier_NoSurvey() {
        // Mock query.
        when(mockQueryResultPage.getResults()).thenReturn(ImmutableList.of());
        when(mockSurveyMapper.queryPage(eq(DynamoSurvey.class), any())).thenReturn(mockQueryResultPage);

        // Execute.
        String surveyGuid = surveyDao.getSurveyGuidForIdentifier(TestConstants.TEST_STUDY, SURVEY_ID);
        assertNull(surveyGuid);

        // Query is verified in previous test.
    }

    @Test
    public void updateSurveySucceedsOnUndeletedSurvey() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setDeleted(true);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        survey.setDeleted(false);
        survey.setName("New title");
        Survey updatedSurvey = surveyDao.updateSurvey(TestConstants.TEST_STUDY, survey);
        
        assertEquals(updatedSurvey.getName(), "New title");
    }
    
    @Test
    public void publishSurvey() {
        // populate the survey with at least one question
        SurveyQuestion surveyQuestion = new DynamoSurveyQuestion();
        surveyQuestion.setIdentifier("int");
        surveyQuestion.setConstraints(new IntegerConstraints());

        survey.setElements(ImmutableList.of(surveyQuestion));

        // execute and validate
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, survey, true);
        assertTrue(retval.isPublished());
        assertEquals(retval.getModifiedOn(), MOCK_NOW_MILLIS);
        assertEquals(retval.getSchemaRevision().intValue(), SCHEMA_REV);

        verify(mockSurveyMapper).save(same(retval));
    }

    @Test
    public void publishSurveyWithInfoScreensOnly() {
        // populate the survey with an info screen and no questions
        SurveyInfoScreen infoScreen = new DynamoSurveyInfoScreen();
        infoScreen.setIdentifier("test-info-screen");
        infoScreen.setTitle("Test Info Screen");
        infoScreen.setPrompt("This info screen doesn't do anything, other than not being a question.");

        survey.setElements(ImmutableList.of(infoScreen));

        // same test as above, except we *don't* call through to the upload schema DAO
        Survey retval = surveyDao.publishSurvey(TestConstants.TEST_STUDY, survey, true);
        assertTrue(retval.isPublished());
        assertEquals(retval.getModifiedOn(), MOCK_NOW_MILLIS);
        assertNull(retval.getSchemaRevision());

        verify(mockSurveyMapper).save(same(retval));
        verify(mockSchemaService, never()).createUploadSchemaFromSurvey(any(), any(), anyBoolean());
    }

    @Test
    public void deleteSurveyAlsoDeletesSchema() {
        // We also need to spy deleteAllElements(), because there's also a lot of complex logic there.
        doNothing().when(surveyDao).deleteAllElements(SURVEY_GUID, SURVEY_CREATED_ON);
        
        // Execute
        surveyDao.deleteSurveyPermanently(TestConstants.TEST_STUDY, SURVEY_KEY);

        // Validate backends
        verify(surveyDao).deleteAllElements(SURVEY_GUID, SURVEY_CREATED_ON);
        verify(mockSurveyMapper).delete(survey);
        verify(mockSchemaService).deleteUploadSchemaByIdPermanently(TestConstants.TEST_STUDY, SURVEY_ID);
    }
    
    @Test
    public void deleteSurveyPermanentlyNoSurvey() {
        List<Survey> results = Lists.newArrayList();
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("keys", DateTime.now().getMillis());
        surveyDao.deleteSurveyPermanently(TestConstants.TEST_STUDY, keys);
        
        verify(mockSurveyMapper, never()).delete(any());
    }
    
    @Test
    public void updateSurveyUndeleteExistingDeletedOK() {
        DynamoSurvey existing = new DynamoSurvey(SURVEY_GUID, SURVEY_CREATED_ON);
        existing.setIdentifier(SURVEY_ID);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(true);
        existing.setPublished(false);
        
        List<Survey> results = Lists.newArrayList(existing);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockSurveyMapper).queryPage(eq(DynamoSurvey.class), any());
        
        survey.setDeleted(false);
        survey.getElements().add(SurveyInfoScreen.create());
        surveyDao.updateSurvey(TestConstants.TEST_STUDY, survey);

        verify(mockSurveyMapper).save(surveyCaptor.capture());
        assertFalse(surveyCaptor.getValue().isDeleted());
        assertEquals(surveyCaptor.getValue().getElements().size(), 1);
    }
}
