package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoSurveyDao implements SurveyDao {

    static final String IDENTIFIER_PREFIX = "identifier:";
    
    class QueryBuilder {
        
        private static final String PUBLISHED_PROPERTY = "published";
        private static final String DELETED_PROPERTY = "deleted";
        private static final String CREATED_ON_PROPERTY = "versionedOn";
        private static final String STUDY_KEY_PROPERTY = "studyKey";
        
        boolean skipElements;
        String surveyGuid;
        String studyIdentifier;
        long createdOn;
        boolean published;
        boolean notDeleted;
        
        QueryBuilder setSkipElements(boolean skipElements) {
            this.skipElements = skipElements;
            return this;
        }
        QueryBuilder setSurvey(String surveyGuid) {
            this.surveyGuid = surveyGuid;
            return this;
        }
        QueryBuilder setStudy(StudyIdentifier studyIdentifier) {
            if (studyIdentifier == null) {
                this.studyIdentifier = null;
            } else {
                this.studyIdentifier = studyIdentifier.getIdentifier();
            }
            return this;
        }
        QueryBuilder setCreatedOn(long createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        QueryBuilder isPublished() {
            this.published = true;
            return this;
        }
        QueryBuilder setDeleted(boolean includeDeleted) {
            this.notDeleted = !includeDeleted;
            return this;
        }
        
        List<Survey> getAll() {
            List<DynamoSurvey> dynamoSurveys = null;
            if (surveyGuid == null) {
                dynamoSurveys = queryBySecondaryIndex();
            } else if (surveyGuid.toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
                dynamoSurveys = queryBySurveyIdentifier();
            } else {
                dynamoSurveys = query();
            }
            return ImmutableList.copyOf(dynamoSurveys);
        }
        
        Survey getOne() {
            List<Survey> surveys = getAll();
            if (!surveys.isEmpty()) {
                if (!skipElements) {
                    attachSurveyElements(surveys.get(0));    
                }
                return surveys.get(0);
            }
            return null;
        }

        private List<DynamoSurvey> query() {
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withScanIndexForward(false);
            query.withHashKeyValues(new DynamoSurvey(surveyGuid, createdOn));    
            if (studyIdentifier != null) {
                query.withQueryFilterEntry(STUDY_KEY_PROPERTY, equalsString(studyIdentifier));
            }
            if (createdOn != 0L) {
                query.withRangeKeyCondition(CREATED_ON_PROPERTY, equalsNumber(Long.toString(createdOn)));
            }
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
        }
        
        private List<DynamoSurvey> queryBySecondaryIndex() {
            if (studyIdentifier == null) {
                throw new IllegalStateException("Calculated the need to query by secondary index, but study identifier is not set");
            }
            DynamoSurvey hashKey = new DynamoSurvey();
            hashKey.setStudyIdentifier(studyIdentifier);
            
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withHashKeyValues(hashKey);
            // DDB will throw an error if you don't set this to eventual consistency.
            query.withConsistentRead(false);
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            return surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
        }
        
        private List<DynamoSurvey> queryBySurveyIdentifier() {
            if (studyIdentifier == null) {
                throw new IllegalStateException("Querying by survey identifier, but study identifier is not set");
            }
            DynamoSurvey hashKey = new DynamoSurvey();
            hashKey.setStudyIdentifier(studyIdentifier);
            DynamoDBQueryExpression<DynamoSurvey> query = new DynamoDBQueryExpression<DynamoSurvey>();
            query.withHashKeyValues(hashKey);
            query.withConsistentRead(false);
            query.withRangeKeyCondition("identifier", equalsString(surveyGuid.substring(11)));
            
            if (published) {
                query.withQueryFilterEntry(PUBLISHED_PROPERTY, equalsNumber("1"));
            }
            if (notDeleted) {
                query.withQueryFilterEntry(DELETED_PROPERTY, equalsNumber("0"));
            }
            if (createdOn != 0L) {
                query.withQueryFilterEntry(CREATED_ON_PROPERTY, equalsNumber(Long.toString(createdOn)));
            }
            
            List<DynamoSurvey> results = surveyMapper.queryPage(DynamoSurvey.class, query).getResults();
            
            List<DynamoSurvey> surveys = new ArrayList<>(results);
            Collections.sort(surveys, Collections.reverseOrder(Comparator.comparing(DynamoSurvey::getCreatedOn)));
            return surveys;
        }

        private Condition equalsNumber(String equalTo) {
            Condition condition = new Condition();
            condition.withComparisonOperator(ComparisonOperator.EQ);
            condition.withAttributeValueList(new AttributeValue().withN(equalTo));
            return condition;
        }
        
        private Condition equalsString(String equalTo) {
            Condition condition = new Condition();
            condition.withComparisonOperator(ComparisonOperator.EQ);
            condition.withAttributeValueList(new AttributeValue().withS(equalTo));
            return condition;
        }
        
        private void attachSurveyElements(Survey survey) {
            DynamoSurveyElement template = new DynamoSurveyElement();
            template.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            
            DynamoDBQueryExpression<DynamoSurveyElement> query = new DynamoDBQueryExpression<DynamoSurveyElement>();
            query.withHashKeyValues(template);
            
            QueryResultPage<DynamoSurveyElement> page = surveyElementMapper.queryPage(DynamoSurveyElement.class, query);

            List<SurveyElement> elements = Lists.newArrayList();
            for (DynamoSurveyElement element : page.getResults()) {
                SurveyElement surveyElement = SurveyElementFactory.fromDynamoEntity(element);
                reconcileRules(surveyElement);
                elements.add(surveyElement);
            }
            survey.setElements(elements);
        }
    }
    
    private DynamoDBMapper surveyMapper;
    private DynamoDBMapper surveyElementMapper;
    private UploadSchemaService uploadSchemaService;
    
    @Resource(name = "surveyMapper")
    public void setSurveyMapper(DynamoDBMapper surveyMapper) {
        this.surveyMapper = surveyMapper;
    }
    
    @Resource(name = "surveyElementMapper")
    public void setSurveyElementMapper(DynamoDBMapper surveyElementMapper) {
        this.surveyElementMapper = surveyElementMapper;
    }

    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }

    @Override
    public Survey createSurvey(Survey survey) {
        checkNotNull(survey.getStudyIdentifier(), "Survey study identifier is null");
        if (survey.getGuid() == null) {
            survey.setGuid(generateGuid());
        }
        long time = DateUtils.getCurrentMillisFromEpoch();
        survey.setCreatedOn(time);
        survey.setModifiedOn(time);
        survey.setSchemaRevision(null);
        survey.setPublished(false);
        survey.setDeleted(false);
        survey.setVersion(null);
        return saveSurvey(survey);
    }

    @Override
    public Survey publishSurvey(StudyIdentifier study, Survey survey, boolean newSchemaRev) {
        if (!survey.isPublished()) {
            // update survey
            survey.setPublished(true);
            survey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());

            // make schema from survey
            if (!survey.getUnmodifiableQuestionList().isEmpty()) {
                UploadSchema schema = uploadSchemaService.createUploadSchemaFromSurvey(study, survey, newSchemaRev);
                survey.setSchemaRevision(schema.getRevision());
            }

            try {
                surveyMapper.save(survey);
            } catch(ConditionalCheckFailedException e) {
                throw new ConcurrentModificationException(survey);
            }
        }
        return survey;
    }
    
    @Override
    public Survey updateSurvey(StudyIdentifier studyIdentifier, Survey survey) {
        Survey existing = getSurvey(studyIdentifier, survey, false);
        
        // copy over mutable fields
        existing.setName(survey.getName());
        existing.setElements(survey.getElements());
        existing.setCopyrightNotice(survey.getCopyrightNotice());
        existing.setDeleted(survey.isDeleted());

        // copy over DDB version so we can handle concurrent modification exceptions
        existing.setVersion(survey.getVersion());

        // internal bookkeeping - update modified timestamp, clear schema revision from unpublished survey
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        existing.setSchemaRevision(null);

        return saveSurvey(existing);
    }
    
    @Override
    public Survey versionSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        DynamoSurvey existing = (DynamoSurvey)getSurvey(studyIdentifier, keys, true);
        DynamoSurvey copy = new DynamoSurvey(existing);
        copy.setPublished(false);
        copy.setDeleted(false);
        copy.setVersion(null);
        long time = DateUtils.getCurrentMillisFromEpoch();
        copy.setCreatedOn(time);
        copy.setModifiedOn(time);
        copy.setSchemaRevision(null);
        for (SurveyElement element : copy.getElements()) {
            element.setGuid(generateGuid());
        }
        return saveSurvey(copy);
    }

    @Override
    public void deleteSurvey(Survey survey) {
        checkNotNull(survey);
        
        survey.setDeleted(true);
        saveSurvey(survey);
    }

    @Override
    public void deleteSurveyPermanently(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(studyIdentifier, keys, false);

        if (existing != null) {
            deleteAllElements(existing.getGuid(), existing.getCreatedOn());
            surveyMapper.delete(existing);
            // Delete the schemas as well, or they accumulate.
            try {
                StudyIdentifier studyId = new StudyIdentifierImpl(existing.getStudyIdentifier());
                uploadSchemaService.deleteUploadSchemaByIdPermanently(studyId, existing.getIdentifier());
            } catch(EntityNotFoundException e) {
                // This is OK. Just means this survey wasn't published.
            }
        }
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid, boolean includeDeleted) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).setDeleted(includeDeleted).getAll();
    }
    
    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(guid).setDeleted(false).getOne();
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid, boolean includeElements) {
        return new QueryBuilder().setStudy(studyIdentifier).isPublished().setSurvey(guid).setDeleted(false)
                .setSkipElements(!includeElements).getOne();
    }
    
    // secondary index query (not survey GUID) 
    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyIdentifier).isPublished().setDeleted(includeDeleted).getAll();
        return findMostRecentVersions(surveys);
    }
    
    // secondary index query (not survey GUID)
    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        List<Survey> surveys = new QueryBuilder().setStudy(studyIdentifier).setDeleted(includeDeleted).getAll();
        return findMostRecentVersions(surveys);
    }
    
    /**
     * Get a specific survey version regardless of whether or not is has been deleted. This is the only call 
     * that will return a deleted survey. With most scheduling now pointing to the most recently published 
     * version (not a specific timestamped version), this method should be rarely called.
     */
    @Override
    public Survey getSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys, boolean includeElements) {
        return new QueryBuilder().setStudy(studyIdentifier).setSurvey(keys.getGuid()).setCreatedOn(keys.getCreatedOn())
                .setSkipElements(!includeElements).getOne();
    }

    /** {@inheritDoc} */
    @Override
    public String getSurveyGuidForIdentifier(StudyIdentifier studyId, String surveyId) {
        // Hash key.
        DynamoSurvey hashKey = new DynamoSurvey();
        hashKey.setStudyIdentifier(studyId.getIdentifier());

        // Range key.
        Condition rangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(surveyId));

        // Construct query.
        DynamoDBQueryExpression<DynamoSurvey> expression = new DynamoDBQueryExpression<DynamoSurvey>()
                .withConsistentRead(false)
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("identifier", rangeKeyCondition)
                .withLimit(1);

        // Execute query.
        QueryResultPage<DynamoSurvey> resultPage = surveyMapper.queryPage(DynamoSurvey.class, expression);
        List<DynamoSurvey> surveyList = resultPage.getResults();
        if (surveyList.isEmpty()) {
            return null;
        }

        return surveyList.get(0).getGuid();
    }

    /**
     * This scan gets expensive when there are many revisions. We don't know the set of unique GUIDs, so 
     * we also have to iterate over everything. 
     * @param surveys
     * @return
     */
    private List<Survey> findMostRecentVersions(List<Survey> surveys) {
        if (surveys.isEmpty()) {
            return surveys;
        }
        Map<String, Survey> map = Maps.newLinkedHashMap();
        for (Survey survey : surveys) {
            Survey stored = map.get(survey.getGuid());
            if (stored == null || survey.getCreatedOn() > stored.getCreatedOn()) {
                map.put(survey.getGuid(), survey);
            }
        }
        return ImmutableList.copyOf(map.values());
    }
    
    private Survey saveSurvey(Survey survey) {
        deleteAllElements(survey.getGuid(), survey.getCreatedOn());
        
        List<DynamoSurveyElement> dynamoElements = Lists.newArrayList();
        for (int i=0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            element.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            element.setOrder(i);
            if (element.getGuid() == null) {
                element.setGuid(generateGuid());
            }
            reconcileRules(element);
            dynamoElements.add((DynamoSurveyElement)element);
        }
        
        List<FailedBatch> failures = surveyElementMapper.batchSave(dynamoElements);
        BridgeUtils.ifFailuresThrowException(failures);

        try {
            surveyMapper.save(survey);
        } catch(ConditionalCheckFailedException throwable) {
            // At this point, delete the elements you just created... compensating transaction
            deleteAllElements(survey.getGuid(), survey.getCreatedOn());
            throw new ConcurrentModificationException(survey);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
        return survey;
    }

    // Package-scoped for unit tests.
    void deleteAllElements(String surveyGuid, long createdOn) {
        DynamoSurveyElement template = new DynamoSurveyElement();
        template.setSurveyKeyComponents(surveyGuid, createdOn);
        
        DynamoDBQueryExpression<DynamoSurveyElement> query = new DynamoDBQueryExpression<DynamoSurveyElement>();
        query.withHashKeyValues(template);
        
        List<DynamoSurveyElement> page = surveyElementMapper.query(DynamoSurveyElement.class, query);
        List<FailedBatch> failures = surveyElementMapper.batchDelete(page);
        BridgeUtils.ifFailuresThrowException(failures);
    }
}
