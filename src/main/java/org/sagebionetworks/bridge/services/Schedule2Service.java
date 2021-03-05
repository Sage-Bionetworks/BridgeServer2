package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_SCHEDULES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_SCHEDULES;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.INSTANCE;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class Schedule2Service {
    
    private AppService appService;
    
    private OrganizationService organizationService;
    
    private Schedule2Dao dao;
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    @Autowired
    final void setScheduleDao(Schedule2Dao dao) {
        this.dao = dao;
    }
    
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    public PagedResourceList<Schedule2> getSchedules(
            String appId, int offsetBy, int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        // Cannot match on organization; this call has to be made by a developer or admin
        CAN_READ_SCHEDULES.checkAndThrow();
        
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getSchedules(appId, offsetBy, pageSize, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    public PagedResourceList<Schedule2> getSchedulesForOrganization(
            String appId, String ownerId, int offsetBy, int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, ownerId);

        if (StringUtils.isBlank(ownerId)) {
            throw new BadRequestException("Caller is not a member of an organization");
        }
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getSchedulesForOrganization(appId, ownerId, offsetBy, pageSize, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }

    public Schedule2 getSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);

        Schedule2 schedule = dao.getSchedule(appId, guid).orElse(null);
        if (schedule != null) {
            CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, schedule.getOwnerId());
        } else {
            throw new EntityNotFoundException(Schedule2.class);
        }
        return schedule;
    }
    
    public Schedule2 createSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        // Do not need to check permissions beyond the sanity check in controllers;
        // this schedule will be owned by the caller’s organization, and the caller
        // must be in an organization or be a developer.
        
        String callerAppId = RequestContext.get().getCallerAppId();
        String callerOrgMembership = RequestContext.get().getCallerOrgMembership();
        App app = appService.getApp(callerAppId);
        
        DateTime createdOn = getCreatedOn();
        schedule.setAppId(callerAppId);
        schedule.setGuid(generateGuid());
        schedule.setCreatedOn(createdOn);
        schedule.setModifiedOn(createdOn);
        schedule.setGuid(generateGuid());
        schedule.setVersion(0L);
        schedule.setDeleted(false);
        if (callerOrgMembership == null) {
            Optional<Organization> opt = organizationService.getOrganizationOpt(callerAppId, schedule.getOwnerId());
            if (opt.isPresent()) {
                callerOrgMembership = schedule.getOwnerId();
            }
        }
        schedule.setOwnerId(callerOrgMembership);
        preValidationCleanup(app, schedule);
        
        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.createSchedule(schedule);
    }
    
    public Schedule2 updateSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        Schedule2 existing = dao.getSchedule(schedule.getAppId(), schedule.getGuid()).orElse(null);
        
        if (existing != null) {
            CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());    
        } else {
            throw new EntityNotFoundException(Schedule2.class);
        }
        if (existing.isDeleted() && schedule.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        
        App app = appService.getApp(existing.getAppId());
        
        schedule.setCreatedOn(existing.getCreatedOn());
        schedule.setModifiedOn(getModifiedOn());
        schedule.setOwnerId(existing.getOwnerId());
        preValidationCleanup(app, schedule);

        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.updateSchedule(schedule);
    }
    
    public void deleteSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        dao.deleteSchedule(existing);
    }
    
    public void deleteSchedulePermanently(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);

        // If the schedule is being used, we don't want to delete it. However, we 
        // don't have an object that refers to it yet...we need a protocol.
        
        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        dao.deleteSchedulePermanently(existing);
    }
    
    /**
     * Set guids on objects that don't have guids; clean up event keys or set
     * them to null if they're not valid, so they fail validation.
     */
    public void preValidationCleanup(App app, Schedule2 schedule) {
        Set<String> keys = app.getActivityEventKeys();
        schedule.setDurationStartEventId(
                formatActivityEventId(keys,schedule.getDurationStartEventId()));

        for (Session session : schedule.getSessions()) {
            if (session.getGuid() == null) {
                session.setGuid(generateGuid());
            }
            session.setSchedule(schedule);
            for (TimeWindow window : session.getTimeWindows()) {
                if (window.getGuid() == null) {
                    window.setGuid(generateGuid());
                }
            }
            for (AssessmentReference ref : session.getAssessments()) {
                if (ref.getGuid() == null) {
                    ref.setGuid(generateGuid());
                }
            }
            session.setStartEventId(
                    formatActivityEventId(keys, session.getStartEventId()));
        }
    }
}
