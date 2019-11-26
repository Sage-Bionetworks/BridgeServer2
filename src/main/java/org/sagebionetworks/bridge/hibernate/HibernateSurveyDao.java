package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyId;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.sagebionetworks.bridge.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class HibernateSurveyDao implements SurveyDao {
    private static final String IDENTIFIER_PREFIX = "identifier:";
    Integer OFFSET = 0;
    Integer PAGESIZE = 1;
    
    private static final String SELECT_TEMPLATE = "SELECT survey ";
    private static final String SELECT_ELEMENT_TEMPLATE = "SELECT surveyElement ";
    
    private static final String GET_ALL = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND guid = :guid";
    
    private static final String GET_ALL_SURVEYS = "FROM HibernateSurvey as survey WHERE studyKey = :studyKey";
    
    private static final String GET_ALL_ACTIVE_PUBLISH = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND deleted = 0 AND published = 1 ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND guid = :guid AND deleted = 0 ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE_PUBLISH = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND guid = :guid AND deleted = 0 AND published = :published ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE_PUBLISH_WITH_IDENTIFIER = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND identifier = :identifier AND deleted = 0 AND published = :published ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE_WITH_IDENTIFIER = "FROM HibernateSurvey as survey " + 
            "WHERE studyKey = :studyKey AND identifier = :identifier AND createdOn = :createdOn AND deleted = 0 ORDER BY createdOn DESC";
    
    private static final String GET_ELEMENTS = "FROM HibernateSurveyElement as surveyElement " + 
            "WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn ORDER BY createdOn DESC";
    
    private static final String DELETE_SURVEY_ELEMENTS = "DELETE FROM HibernateSurveyElement WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn";
    
    private HibernateHelper hibernateHelper;
    
    private UploadSchemaService uploadSchemaService;
    
    /** This makes interfacing with Hibernate easier. */
    @Resource(name = "basicHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }
    
    @Override
    public Survey createSurvey(Survey survey) {
        System.out.println("createSurvey");
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
        hibernateHelper.create(survey, null);
        System.out.println("12");
        return survey;
    }

    @Override
    public Survey updateSurvey(StudyIdentifier studyIdentifier, Survey survey) {
        Survey existingSurvey = getSurvey(studyIdentifier, survey, true);
        
        if (existingSurvey != null) {
            hibernateHelper.update(survey, null);
            return survey;
        } else {
            return createSurvey(survey);
        }
    }

    @Override
    public Survey versionSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existingSurvey = getSurvey(studyIdentifier, keys, false);
        System.out.println(existingSurvey);
        HibernateSurvey copy = new HibernateSurvey(existingSurvey);
        copy.setSchemaRevision(null);
        for (SurveyElement element : copy.getElements()) {
            element.setGuid(generateGuid());
        }
        System.out.println("12");
        return createSurvey(copy);
    }

    @Override
    public Survey publishSurvey(StudyIdentifier studyIdentifier, Survey survey, boolean newSchemaRev) {        
        if (!survey.isPublished()) {
            // update survey
            survey.setPublished(true);
            survey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
            // make schema from survey
            if (!survey.getUnmodifiableQuestionList().isEmpty()) {
                UploadSchema schema = uploadSchemaService.createUploadSchemaFromSurvey(studyIdentifier, survey, newSchemaRev);
                survey.setSchemaRevision(schema.getRevision());
            }
            updateSurvey(studyIdentifier, survey);
        }
        return survey;
    }

    @Override
    public void deleteSurvey(Survey survey) {
        survey.setDeleted(true);
        hibernateHelper.update(survey, null);
    }
    
    @Override
    public void deleteSurveyPermanently(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        Survey existing = getSurvey(studyIdentifier, keys, false);
        
        if (existing != null) {
            deleteAllElements(existing.getGuid(), existing.getCreatedOn());
            
            SurveyId surveyId = new SurveyId(keys);
            hibernateHelper.deleteById(HibernateSurvey.class, surveyId);
            
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
    public Survey getSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys, boolean includeElements) {
        if (keys.getGuid().toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
            ImmutableMap<String,Object> params = ImmutableMap.of(
                    "studyKey", studyIdentifier.getIdentifier(),
                    "identifier", keys.getGuid().substring(IDENTIFIER_PREFIX.length()),
                    "createdOn", keys.getCreatedOn());
            
            String getQuery = SELECT_TEMPLATE + GET_ACTIVE_WITH_IDENTIFIER;
            
            List<HibernateSurvey> results = hibernateHelper.queryGet(
                    getQuery, params, OFFSET, PAGESIZE, HibernateSurvey.class);
            
            if (!results.isEmpty()) {
                return results.get(0);
            }
            return null;
        }
        SurveyId surveyId = new SurveyId(keys);
        Survey ret = hibernateHelper.getById(HibernateSurvey.class, surveyId);
        if (ret != null && includeElements) {
            attachSurveyElements(ret);
        }
        return ret;
    }

    @Override
    public String getSurveyGuidForIdentifier(StudyIdentifier studyIdentifier, String surveyId) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier(),
                "identifier", surveyId);
        
        String getQuery = SELECT_TEMPLATE + GET_ACTIVE_WITH_IDENTIFIER;
        
        List<? extends HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, OFFSET, PAGESIZE, HibernateSurvey.class);
        if (!results.isEmpty()) {
            return results.get(0).getGuid();
        }
        return null;
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid, boolean includeDeleted) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier(),
                "guid", guid);
        
        String getQuery = SELECT_TEMPLATE + ((!includeDeleted) ? GET_ACTIVE : GET_ALL);
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, OFFSET, 50, HibernateSurvey.class);
        
        return ImmutableList.copyOf(results);
    }

    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier(),
                "guid", guid);
        
        String getQuery = SELECT_TEMPLATE + GET_ACTIVE;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, OFFSET, 50, HibernateSurvey.class);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid,
            boolean includeElements) {
        if (guid.toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
            ImmutableMap<String, Object> params = ImmutableMap.of(
                    "studyKey", studyIdentifier.getIdentifier(),
                    "identifier", guid.substring(IDENTIFIER_PREFIX.length()),
                    "published", 1);
            
            String getQuery = SELECT_TEMPLATE + GET_ACTIVE_PUBLISH_WITH_IDENTIFIER;
            List<HibernateSurvey> results = hibernateHelper.queryGet(
                    getQuery, params, OFFSET, 50, HibernateSurvey.class);
            if (!results.isEmpty()) {
                ImmutableMap<String, Object> elementParams = ImmutableMap.of(
                        "surveyGuid", results.get(0).getGuid(),
                        "createdOn", results.get(0).getCreatedOn());
                
                String getElementQuery = SELECT_ELEMENT_TEMPLATE + GET_ELEMENTS;
                
                List<HibernateSurveyElement> elementResults = hibernateHelper.queryGet(
                        getElementQuery, elementParams, OFFSET, 50, HibernateSurveyElement.class);
//                results.get(0).setElements(elementResults);
                return results.get(0);
            }
        } else {
            ImmutableMap<String, Object> params = ImmutableMap.of(
                    "studyKey", studyIdentifier.getIdentifier(),
                    "guid", guid,
                    "published", 1);
            
            String getQuery = SELECT_TEMPLATE + GET_ACTIVE_PUBLISH;
            List<HibernateSurvey> results = hibernateHelper.queryGet(
                    getQuery, params, OFFSET, 50, HibernateSurvey.class);
            if (!results.isEmpty()) {
                ImmutableMap<String, Object> elementParams = ImmutableMap.of(
                        "surveyGuid", guid,
                        "createdOn", results.get(0).getCreatedOn());
                
                String getElementQuery = SELECT_ELEMENT_TEMPLATE + GET_ELEMENTS;
                
                List<HibernateSurveyElement> elementResults = hibernateHelper.queryGet(
                        getElementQuery, elementParams, OFFSET, 50, HibernateSurveyElement.class);
//                results.get(0).setElements(elementResults);
                return results.get(0);
            }
        }
        
        return null;
    }

    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier,
            boolean includeDeleted) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier());
        
//        String countQuery = SELECT_COUNT + GET_ACTIVE;
        String getQuery = SELECT_TEMPLATE + GET_ALL_ACTIVE_PUBLISH;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, OFFSET, 50, HibernateSurvey.class);
        
        return findMostRecentVersions(results);
    }

    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier());
        
        String getQuery = SELECT_TEMPLATE + GET_ALL_SURVEYS;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, OFFSET, 50, HibernateSurvey.class);
        
        return findMostRecentVersions(results);
    }
    
    /**
     * This scan gets expensive when there are many revisions. We don't know the set of unique GUIDs, so 
     * we also have to iterate over everything. 
     * @param surveys
     * @return
     */
    private List<Survey> findMostRecentVersions(List<HibernateSurvey> surveys) {
        if (surveys.isEmpty()) {
            return ImmutableList.copyOf(surveys);
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
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    // Package-scoped for unit tests.
    void deleteAllElements(String surveyGuid, long createdOn) {
        ImmutableMap<String,Object> params = ImmutableMap.of(
                "surveyGuid", surveyGuid, 
                "createdOn", createdOn);
        
        hibernateHelper.query(DELETE_SURVEY_ELEMENTS, params);
    }
    
    private void attachSurveyElements(Survey survey) {
        ImmutableMap<String, Object> elementParams = ImmutableMap.of(
                "surveyGuid", survey.getGuid(),
                "createdOn", survey.getCreatedOn());
        
        String getElementQuery = SELECT_ELEMENT_TEMPLATE + GET_ELEMENTS;
        
        List<HibernateSurveyElement> elementResults = hibernateHelper.queryGet(
                getElementQuery, elementParams, OFFSET, 50, HibernateSurveyElement.class);
        
        List<SurveyElement> elements = Lists.newArrayList();
        for (HibernateSurveyElement element : elementResults) {
            SurveyElement surveyElement = SurveyElementFactory.fromHibernateEntity(element);
//            reconcileRules(surveyElement);
            elements.add((SurveyElement) surveyElement);
        }
        survey.setElements(elements);
    }
}
