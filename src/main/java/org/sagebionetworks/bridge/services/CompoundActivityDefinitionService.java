package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.CompoundActivityDefinitionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.validators.CompoundActivityDefinitionValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Compound Activity Definition Service. */
@Component
public class CompoundActivityDefinitionService {
    private SchedulePlanService schedulePlanService;
    
    private CompoundActivityDefinitionDao compoundActivityDefDao;

    @Autowired
    public final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    /** DAO, autowired by Spring. */
    @Autowired
    public final void setCompoundActivityDefDao(CompoundActivityDefinitionDao compoundActivityDefDao) {
        this.compoundActivityDefDao = compoundActivityDefDao;
    }

    /** Creates a compound activity definition. */
    public CompoundActivityDefinition createCompoundActivityDefinition(String appId,
            CompoundActivityDefinition compoundActivityDefinition) {
        // Set app to prevent people from creating defs in other studies.
        compoundActivityDefinition.setAppId(appId);

        // validate def
        Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.createCompoundActivityDefinition(compoundActivityDefinition);
    }

    /** Deletes a compound activity definition. */
    public void deleteCompoundActivityDefinition(String appId, String taskId) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }
        checkConstraintViolations(appId, taskId);
        
        // call through to dao
        compoundActivityDefDao.deleteCompoundActivityDefinition(appId, taskId);
    }

    /** Deletes all compound activity definitions in the specified app. Used when we physically delete an app. */
    public void deleteAllCompoundActivityDefinitionsInApp(String appId) {
        // no user input - app comes from controller

        // call through to dao
        compoundActivityDefDao.deleteAllCompoundActivityDefinitionsInApp(appId);
    }

    /** List all compound activity definitions in an app. */
    public List<CompoundActivityDefinition> getAllCompoundActivityDefinitionsInApp(String appId) {
        // no user input - app comes from controller

        // call through to dao
        return compoundActivityDefDao.getAllCompoundActivityDefinitionsInApp(appId);
    }

    /** Get a compound activity definition by ID. */
    public CompoundActivityDefinition getCompoundActivityDefinition(String appId, String taskId) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

        // call through to dao
        return compoundActivityDefDao.getCompoundActivityDefinition(appId, taskId);
    }

    /** Update a compound activity definition. */
    public CompoundActivityDefinition updateCompoundActivityDefinition(String appId, String taskId,
            CompoundActivityDefinition compoundActivityDefinition) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

        // Set the appId and taskId. This prevents people from updating the wrong def or updating a def in another
        // app.
        compoundActivityDefinition.setAppId(appId);
        compoundActivityDefinition.setTaskId(taskId);

        // validate def
        Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.updateCompoundActivityDefinition(compoundActivityDefinition);
    }
    
    private void checkConstraintViolations(String appId, String taskId) {
        // You cannot physically delete a compound activity if it is referenced by a logically deleted schedule plan. 
        // It's possible the schedule plan could be restored. All you can do is logically delete the compound activity.
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, appId, true);
        SchedulePlan match = findFirstMatchingPlan(plans, taskId);
        if (match != null) {
            throw new ConstraintViolationException.Builder().withMessage(
                    "Cannot delete compound activity definition: it is referenced by a schedule plan that is still accessible through the API")
                    .withEntityKey("taskId", taskId).withEntityKey("type", "CompoundActivityDefinition")
                    .withReferrerKey("guid", match.getGuid()).withReferrerKey("type", "SchedulePlan").build();
        }
    }

    private SchedulePlan findFirstMatchingPlan(List<SchedulePlan> plans, String taskId) {
        for (SchedulePlan plan : plans) {
            List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
            for (Schedule schedule : schedules) {
                for (Activity activity : schedule.getActivities()) {
                    CompoundActivity compoundActivity = activity.getCompoundActivity();
                    if (compoundActivity != null) {
                        if (compoundActivity.getTaskIdentifier().equals(taskId)) {
                            return plan;
                        }
                    }
                }
            }
        }
        return null;
    }    
}
