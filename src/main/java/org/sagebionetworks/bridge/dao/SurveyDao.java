package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.Survey;

public interface SurveyDao {

    /**
     * Create a new survey. 
     */
    Survey createSurvey(Survey survey);
    
    /**
     * Update an unpublished survey. A survey version can be edited until it is published.
     */
    Survey updateSurvey(String studyIdentifier, Survey survey);
    
    /**
     * Version this survey (create a copy with a new createdOn timestamp). New versions are 
     * created unpublished and can be modified.
     */
    Survey versionSurvey(String studyIdentifier, GuidCreatedOnVersionHolder keys);

    /**
     * Make this version of this survey available for scheduling. One scheduled for publishing,
     * a survey version can no longer be changed (it can still be the source of a new version).
     * There can be more than one published version of a survey.
     *
     *
     * @param study
     *         study that the survey lives in
     * @param survey
     *         the survey
     * @param newSchemaRev
     *         true if you want to cut a new survey schema, false if you should (attempt to) modify the existing one
     * @return published survey
     */
    Survey publishSurvey(String study, Survey survey, boolean newSchemaRev);

    /**
     * Delete this survey. Survey still exists in system and can be retrieved by direct reference
     * (URLs that directly reference the GUID and createdOn timestamp of the survey), put cannot be 
     * retrieved in any list of surveys, and is no longer considered when finding the most recently 
     * published version of the survey. 
     */
    void deleteSurvey(Survey survey);

    /**
     * Admin API to remove the survey from the backing store. This exists to clean up surveys from tests. This will
     * remove the survey regardless of publish status, whether it has responses. This will delete all survey elements
     * as well.
     *
     * @param keys survey keys (guid, created-on timestamp)
     */
    void deleteSurveyPermanently(String studyIdentifier, GuidCreatedOnVersionHolder keys);

    /**
     * Get a specific version of a survey with or without its elements.
     */
    Survey getSurvey(String studyIdentifier, GuidCreatedOnVersionHolder keys, boolean includeElements);

    /**
     * Helper method to get the survey guid for the given study and survey identifier. Returns null if no such survey
     * exists. Primarily used to check identifier uniqueness.
     */
    String getSurveyGuidForIdentifier(String studyId, String surveyId);

    /**
     * Get all versions of a specific survey, ordered by most recent version 
     * first in the list.
     */
    List<Survey> getSurveyAllVersions(String studyIdentifier, String guid, boolean includeDeleted);
    
    /**
     * Get the most recent version of a survey, regardless of whether it is published
     * or not.
     */
    Survey getSurveyMostRecentVersion(String studyIdentifier, String guid);
    
    /**
     * Get the most recent version of a survey that is published, with or without its elements. 
     * More recent, unpublished versions of the survey will be ignored. 
     */
    Survey getSurveyMostRecentlyPublishedVersion(String studyIdentifier, String guid, boolean includeElements);
    
    /**
     * Get the most recent version of each survey in the study, that has been published. 
     */
    List<Survey> getAllSurveysMostRecentlyPublishedVersion(String studyIdentifier, boolean includeDeleted);
    
    /**
     * Get the most recent version of each survey in the study, whether published or not.
     */
    List<Survey> getAllSurveysMostRecentVersion(String studyIdentifier, boolean includeDeleted);
    
}
