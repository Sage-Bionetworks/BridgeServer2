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
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementFactory;
import org.sagebionetworks.bridge.models.surveys.SurveyId;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;
import org.sagebionetworks.bridge.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class HibernateSurveyDao implements SurveyDao {
    private static final String IDENTIFIER_PREFIX = "identifier:";
    
    private static final String SELECT_TEMPLATE = "SELECT survey FROM HibernateSurvey as survey ";
    
    private static final String ORDER_BY_GUID_CREATED_ON = "ORDER BY guid, createdOn DESC";
    
    private static final String ORDER_BY_CREATED_ON = "ORDER BY createdOn DESC";
    
    private static final String GET_ACTIVE = "WHERE studyKey = :studyKey AND guid = :guid AND deleted = 0 ";
    
    private static final String GET_ACTIVE_WITH_IDENTIFIER = "WHERE studyKey = :studyKey AND identifier = :identifier AND deleted = 0 ";
    
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
    public Survey updateSurvey(StudyIdentifier studyIdentifier, Survey survey) {
        Survey existingSurvey = getSurvey(studyIdentifier, survey, true);
        
        // copy over mutable fields
        existingSurvey.setName(survey.getName());
        existingSurvey.setElements(survey.getElements());
        existingSurvey.setCopyrightNotice(survey.getCopyrightNotice());
        existingSurvey.setDeleted(survey.isDeleted());

        // copy over DDB version so we can handle concurrent modification exceptions
        existingSurvey.setVersion(survey.getVersion());

        // internal bookkeeping - update modified timestamp, clear schema revision from unpublished survey
        existingSurvey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        existingSurvey.setSchemaRevision(null);

        return saveSurvey(existingSurvey);
    }
    
    @Override
    public Survey versionSurvey(StudyIdentifier studyIdentifier, GuidCreatedOnVersionHolder keys) {
        HibernateSurvey existingSurvey = (HibernateSurvey) getSurvey(studyIdentifier, keys, true);
        HibernateSurvey copy = new HibernateSurvey(existingSurvey);
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
            hibernateHelper.update(survey, null);
        }
        return survey;
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
            
            String getQuery = SELECT_TEMPLATE + GET_ACTIVE_WITH_IDENTIFIER + "AND createdOn = :createdOn " + ORDER_BY_CREATED_ON;
            
            List<HibernateSurvey> results = hibernateHelper.queryGet(getQuery, params, null, 1, HibernateSurvey.class);
            
            if (!results.isEmpty()) {
                return attachSurveyElements(results.get(0));
            }
            return null;
        }
        SurveyId surveyId = new SurveyId(keys);
        Survey ret = hibernateHelper.getById(HibernateSurvey.class, surveyId);
        if (ret != null) {
            return attachSurveyElements(ret);
        }
        return null;
    }

    @Override
    public String getSurveyGuidForIdentifier(StudyIdentifier studyIdentifier, String surveyId) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier(),
                "identifier", surveyId);
        
        String getQuery = SELECT_TEMPLATE + GET_ACTIVE_WITH_IDENTIFIER + ORDER_BY_CREATED_ON;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(getQuery, params, null, 1, HibernateSurvey.class);
        if (!results.isEmpty()) {
            return results.get(0).getGuid();
        }
        return null;
    }

    @Override
    public List<Survey> getSurveyAllVersions(StudyIdentifier studyIdentifier, String guid, boolean includeDeleted) {
        String getQuery = SELECT_TEMPLATE;
        
        Builder<String,Object> paramBuilder = ImmutableMap.<String, Object> builder();
        paramBuilder.put("studyKey", studyIdentifier.getIdentifier());
        
        if (guid.toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
            paramBuilder.put("identifier", guid.substring(IDENTIFIER_PREFIX.length()));
            
            getQuery += ((!includeDeleted) ? GET_ACTIVE_WITH_IDENTIFIER : "WHERE studyKey = :studyKey AND identifier = :identifier ");
        } else {
            paramBuilder.put("guid", guid);
            
            getQuery += ((!includeDeleted) ? GET_ACTIVE : "WHERE studyKey = :studyKey AND guid = :guid ");
        }
        
        getQuery += ORDER_BY_CREATED_ON;
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, paramBuilder.build(), null, null, HibernateSurvey.class);
        
        return ImmutableList.copyOf(results);
    }

    @Override
    public Survey getSurveyMostRecentVersion(StudyIdentifier studyIdentifier, String guid) {
        String getQuery = SELECT_TEMPLATE;
        
        Builder<String,Object> paramBuilder = ImmutableMap.<String, Object> builder();
        paramBuilder.put("studyKey", studyIdentifier.getIdentifier());
        
        if (guid.toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
            paramBuilder.put("identifier", guid.substring(IDENTIFIER_PREFIX.length()));
            
            getQuery += GET_ACTIVE_WITH_IDENTIFIER;
        } else {
            paramBuilder.put("guid", guid);
            
            getQuery += GET_ACTIVE;
        }
        
        getQuery += ORDER_BY_CREATED_ON;
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, paramBuilder.build(), null, 1, HibernateSurvey.class);
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    @Override
    public Survey getSurveyMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier, String guid,
            boolean includeElements) {
        String getQuery = SELECT_TEMPLATE + "WHERE studyKey = :studyKey AND deleted = 0 AND published = 1 ";
        
        Builder<String,Object> paramBuilder = ImmutableMap.<String, Object> builder();
        paramBuilder.put("studyKey", studyIdentifier.getIdentifier());
        
        if (guid.toLowerCase().startsWith(IDENTIFIER_PREFIX)) {
            paramBuilder.put("identifier", guid.substring(IDENTIFIER_PREFIX.length()));
            
            getQuery += "AND identifier = :identifier ";
        } else {
            paramBuilder.put("guid", guid);
            
            getQuery += "AND guid = :guid ";
        }
        getQuery += ORDER_BY_CREATED_ON;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, paramBuilder.build(), null, 1, HibernateSurvey.class);
        if (!results.isEmpty()) {
            return attachSurveyElements(results.get(0));
        }
        
        return null;
    }

    @Override
    public List<Survey> getAllSurveysMostRecentlyPublishedVersion(StudyIdentifier studyIdentifier,
            boolean includeDeleted) {
        
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier());
        
        String getQuery = SELECT_TEMPLATE + "WHERE studyKey = :studyKey AND published = 1 " + 
                ((!includeDeleted) ? "AND deleted = 0 " : "") + ORDER_BY_GUID_CREATED_ON;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, null, null, HibernateSurvey.class);
        
        return findMostRecentVersions(results);
    }

    @Override
    public List<Survey> getAllSurveysMostRecentVersion(StudyIdentifier studyIdentifier, boolean includeDeleted) {
        ImmutableMap<String, Object> params = ImmutableMap.of(
                "studyKey", studyIdentifier.getIdentifier());
        
        String getQuery = SELECT_TEMPLATE + "WHERE studyKey = :studyKey " + 
                ((!includeDeleted) ? "AND deleted = 0 " : "") + ORDER_BY_GUID_CREATED_ON;
        
        List<HibernateSurvey> results = hibernateHelper.queryGet(
                getQuery, params, null, null, HibernateSurvey.class);
        
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
        
        String getQuery = "DELETE FROM HibernateSurveyElement WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn";
        
        hibernateHelper.query(getQuery, params);
    }
    
    private Survey attachSurveyElements(Survey survey) {
        ImmutableMap<String, Object> elementParams = ImmutableMap.of(
                "surveyGuid", survey.getGuid(),
                "createdOn", survey.getCreatedOn());
        
        String getElementQuery = "SELECT surveyElement FROM HibernateSurveyElement as surveyElement " + 
                "WHERE surveyGuid = :surveyGuid AND createdOn = :createdOn ORDER BY order ASC";
        
        List<HibernateSurveyElement> elementResults = hibernateHelper.queryGet(
                getElementQuery, elementParams, null, null, HibernateSurveyElement.class);
        
        List<SurveyElement> elements = Lists.newArrayList();
        for (HibernateSurveyElement element : elementResults) {
            SurveyElement surveyElement = SurveyElementFactory.fromHibernateEntity(element);
            reconcileRules(surveyElement);
            elements.add((SurveyElement) surveyElement);
        }
        survey.setElements(elements);
        return survey;
    }
    
    private Survey saveSurvey(Survey survey) {
        deleteAllElements(survey.getGuid(), survey.getCreatedOn());
        List<HibernateSurveyElement> hibernateElements = Lists.newArrayList();
        for (int i = 0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            element.setSurveyKeyComponents(survey.getGuid(), survey.getCreatedOn());
            element.setOrder(i);
            if (element.getGuid() == null) {
                element.setGuid(generateGuid());
            }
            reconcileRules(element);
            hibernateElements.add((HibernateSurveyElement) element);
        }
        
        hibernateHelper.update(survey, null);
        return survey;
    }
}
