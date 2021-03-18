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
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.INSTANCE;

import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules2.HasGuid;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Bridge v2 schedules. These replace the older SchedulePlans and their APIs.
 */
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
    
    /**
     * Get all schedules in the system, paged, irrespective of their owning organizations. Schedules are primary
     * entities and can be manipulated independently of other entities in the system.
     */
    public PagedResourceList<Schedule2> getSchedules(String appId, int offsetBy, int pageSize, boolean includeDeleted) {
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
    
    /**
     * Get all the schedules for an organization, paged. Schedules are primary entities and can be 
     * manipulated independently of other entities in the system.
     */
    public PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, String ownerId, int offsetBy,
            int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        if (StringUtils.isBlank(ownerId)) {
            throw new BadRequestException("Caller is not a member of an organization");
        }
        CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, ownerId);

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

    /**
     * Get an individual schedule.
     */
    public Schedule2 getSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        Schedule2 schedule = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_READ_SCHEDULES.checkAndThrow(ORG_ID, schedule.getOwnerId());

        return schedule;
    }
    
    /**
     * Create a schedule. The schedule will be owned by the caller’s organization (unless
     * an admin or superadmin is making the call and they have specified an organization).
     */
    public Schedule2 createSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        // Do not need to check permissions beyond the sanity check in controllers;
        // this schedule will be owned by the caller’s organization, and the caller
        // must be in an organization or be a developer.
        
        String callerAppId = RequestContext.get().getCallerAppId();
        String callerOrgMembership = RequestContext.get().getCallerOrgMembership();
        boolean isAdmin = RequestContext.get().isInRole(ADMIN, SUPERADMIN);
        App app = appService.getApp(callerAppId);
        
        DateTime createdOn = getCreatedOn();
        schedule.setAppId(callerAppId);
        schedule.setGuid(generateGuid());
        schedule.setCreatedOn(createdOn);
        schedule.setModifiedOn(createdOn);
        schedule.setGuid(generateGuid());
        schedule.setVersion(0L);
        schedule.setPublished(false);
        schedule.setDeleted(false);
        
        // If no id was provided, or the caller is not an administrator, than use the 
        // caller’s organization as the owning organization (schedule must have an 
        // owner, though admins can specify a different organization than their own).
        if (schedule.getOwnerId() == null || !isAdmin) {
            schedule.setOwnerId(callerOrgMembership);    
        }

        // Verify the owner ID (this is also caught by the database, but reports the 
        // error differently than we'd like).
        organizationService.getOrganization(schedule.getAppId(), schedule.getOwnerId());

        preValidationCleanup(app, schedule, (hasGuid) -> hasGuid.setGuid(generateGuid()));
        
        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.createSchedule(schedule);
    }
    
    /**
     * Update a schedule. Will throw an exception once the schedule is published. Ownership
     * cannot be changed once a schedule is created.
     */
    public Schedule2 updateSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        Schedule2 existing = dao.getSchedule(schedule.getAppId(), schedule.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        if (existing.isDeleted() && schedule.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        } 
        if (existing.isPublished()) {
            throw new PublishedEntityException(existing);
        }
        
        App app = appService.getApp(existing.getAppId());
        
        schedule.setCreatedOn(existing.getCreatedOn());
        schedule.setModifiedOn(getModifiedOn());
        schedule.setOwnerId(existing.getOwnerId());
        schedule.setPublished(false);
        preValidationCleanup(app, schedule, (hasGuid) -> {
          if (hasGuid.getGuid() == null) {
              hasGuid.setGuid(generateGuid());
          }
        });

        Validate.entityThrowingException(INSTANCE, schedule);
        
        return dao.updateSchedule(schedule);
    }
    
    /**
     * Publish this schedule so it can no longer be modified. 
     */
    public Schedule2 publishSchedule(String appId, String guid) {
        
        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());    
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        if (existing.isPublished()) {
            throw new PublishedEntityException(existing);
        }
        existing.setPublished(true);
        existing.setModifiedOn(getModifiedOn());
        return dao.updateSchedule(existing);
    }
    
    /**
     * Logically delete this schedule. It is still available to callers who have a 
     * reference to the schedule.
     */
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
    
    /**
     * Physically delete a schedule. Currently there is no protection against 
     * breaking the integrity of the system by permanently deleting a schedule; 
     * this method is for integration tests and admin cleanup.
     */
    public void deleteSchedulePermanently(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);

        Schedule2 existing = dao.getSchedule(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, existing.getOwnerId());
        dao.deleteSchedulePermanently(existing);
    }
    
    /**
     * Set GUIDs on objects that don't have them; clean up event keys or set
     * them to null if they're not valid, so they will fail validation.
     */
    public void preValidationCleanup(App app, Schedule2 schedule, Consumer<HasGuid> consumer) {
        checkNotNull(app);
        checkNotNull(schedule);
        
        Set<String> keys = app.getActivityEventKeys();

        for (Session session : schedule.getSessions()) {
            consumer.accept(session);
            session.setSchedule(schedule);
            for (TimeWindow window : session.getTimeWindows()) {
                consumer.accept(window);
            }
            session.setStartEventId(formatActivityEventId(keys, session.getStartEventId()));
        }
    }
}
