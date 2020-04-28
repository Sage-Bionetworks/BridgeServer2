package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class SchedulePlanService {
    
    private SchedulePlanDao schedulePlanDao;
    private SurveyService surveyService;
    private SubstudyService substudyService;

    @Autowired
    public final void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }
    @Autowired
    public final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }

    public List<SchedulePlan> getSchedulePlans(ClientInfo clientInfo, String studyIdentifier,
            boolean includeDeleted) {
        return schedulePlanDao.getSchedulePlans(clientInfo, studyIdentifier, includeDeleted);
    }

    public SchedulePlan getSchedulePlan(String studyIdentifier, String guid) {
        return schedulePlanDao.getSchedulePlan(studyIdentifier, guid);
    }

    public SchedulePlan createSchedulePlan(App app, SchedulePlan plan) {
        checkNotNull(app);
        checkNotNull(plan);

        // Plan must always be in user's app, remove version and recreate guid for copies
        plan.setAppId(app.getIdentifier());
        plan.setVersion(null);
        plan.setGuid(BridgeUtils.generateGuid());
        
        // This can happen if the submission is invalid, we want to proceed to validation
        if (plan.getStrategy() != null) {
            List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
            for (Schedule schedule : schedules) {
                for (int i=0; i < schedule.getActivities().size(); i++) {
                    Activity activity = schedule.getActivities().get(i);
                    
                    Activity activityWithGuid = new Activity.Builder().withActivity(activity)
                            .withGuid(BridgeUtils.generateGuid()).build();
                    schedule.getActivities().set(i, activityWithGuid);
                }
            }
        }
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        Validate.entityThrowingException(
                new SchedulePlanValidator(app.getDataGroups(), substudyIds, app.getTaskIdentifiers()), plan);

        lookupSurveyReferenceIdentifiers(app.getIdentifier(), plan);
        return schedulePlanDao.createSchedulePlan(app.getIdentifier(), plan);
    }
    
    public SchedulePlan updateSchedulePlan(App app, SchedulePlan plan) {
        checkNotNull(app);
        checkNotNull(plan);
        
        // Plan must always be in user's app
        plan.setAppId(app.getIdentifier());
        
        // This can happen if the submission is invalid, we want to proceed to validation
        if (plan.getStrategy() != null) {
            // Verify that all GUIDs that are supplied already exist in the plan. If they don't (or GUID is null),
            // then add a new GUID.
            SchedulePlan existing = schedulePlanDao.getSchedulePlan(app.getIdentifier(), plan.getGuid());
            Set<String> existingGuids = getAllActivityGuids(existing);
            List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
            for (Schedule schedule : schedules) {
                for (int i=0; i < schedule.getActivities().size(); i++) {
                    Activity activity = schedule.getActivities().get(i);
                    if (activity.getGuid() == null || !existingGuids.contains(activity.getGuid())) {
                        Activity activityWithGuid = new Activity.Builder().withActivity(activity)
                                .withGuid(BridgeUtils.generateGuid()).build();
                        schedule.getActivities().set(i, activityWithGuid);
                    }
                }
            }
        }
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        Validate.entityThrowingException(
                new SchedulePlanValidator(app.getDataGroups(), substudyIds, app.getTaskIdentifiers()), plan);
        
        lookupSurveyReferenceIdentifiers(plan.getAppId(), plan);
        return schedulePlanDao.updateSchedulePlan(plan.getAppId(), plan);
    }

    public void deleteSchedulePlan(String studyIdentifier, String guid) {
        checkNotNull(studyIdentifier);
        checkNotNull(isNotBlank(guid));
        
        schedulePlanDao.deleteSchedulePlan(studyIdentifier, guid);
    }
    
    public void deleteSchedulePlanPermanently(String studyIdentifier, String guid) {
        checkNotNull(studyIdentifier);
        checkNotNull(isNotBlank(guid));
        
        schedulePlanDao.deleteSchedulePlanPermanently(studyIdentifier, guid);
    }
    
    /**
     * Get all the GUIDs for all the activities that already exist. These are the only GUIDs that should be returned
     * from the client on an update.
     */
    private Set<String> getAllActivityGuids(SchedulePlan plan) {
        Set<String> activityGuids = Sets.newHashSet();
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                activityGuids.add(activity.getGuid());
            }
        }
        return activityGuids;
    }

    /**
     * If the activity has a survey reference, look up the survey's identifier. Don't trust the client to 
     * supply the correct one for the survey's primary keys. We're adding this when writing schedules because 
     * the clients have come to depend on the presence of the identifier key, and this is more efficient than 
     * looking it up on every read.
     * 
     * @param studyId
     * @param activity
     * @return
     */
    private void lookupSurveyReferenceIdentifiers(String studyId, SchedulePlan plan) {
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                activity = updateActivityWithSurveyIdentifier(studyId, activity);
                schedule.getActivities().set(i, activity);
            }
        }
    }

    private Activity updateActivityWithSurveyIdentifier(String studyId, Activity activity) {
        if (activity.getSurvey() != null) {
            SurveyReference ref = activity.getSurvey();
            
            if (ref.getCreatedOn() == null) { // pointer to most recently published survey
                Survey survey = surveyService.getSurveyMostRecentlyPublishedVersion(studyId, ref.getGuid(), false);
                return new Activity.Builder().withActivity(activity)
                        .withPublishedSurvey(survey.getIdentifier(), survey.getGuid()).build();
            } else {
                GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(ref.getGuid(), ref.getCreatedOn().getMillis());
                Survey survey = surveyService.getSurvey(studyId, keys, false, true);
                return new Activity.Builder().withActivity(activity)
                        .withSurvey(survey.getIdentifier(), ref.getGuid(), ref.getCreatedOn()).build();
            }
        }
        return activity;
    }
}
