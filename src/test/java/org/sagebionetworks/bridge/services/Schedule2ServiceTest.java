package org.sagebionetworks.bridge.services;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.getClientData;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.Period;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.PublishedEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;

public class Schedule2ServiceTest extends Mockito {
    
    @Mock
    AppService mockAppService;
    
    @Mock
    OrganizationService mockOrganizationService;
    
    @Mock
    Schedule2Dao mockDao;

    @InjectMocks
    @Spy
    Schedule2Service service;
    
    @Captor
    ArgumentCaptor<Schedule2> scheduleCaptor;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(CREATED_ON).when(service).getCreatedOn();
        doReturn(MODIFIED_ON).when(service).getModifiedOn();
        doReturn(GUID).when(service).generateGuid();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    private void permitToAccess() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
    }
    
    @Test
    public void getSchedules() {
        permitToAccess();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<Schedule2>(list, 10);
        
        when(mockDao.getSchedules(TEST_APP_ID, 100, 50, true)).thenReturn(page);
        
        PagedResourceList<Schedule2> retValue = service.getSchedules(TEST_APP_ID, 100, 50, true);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("offsetBy"), 100);
        assertEquals(retValue.getRequestParams().get("pageSize"), 50);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
        
        verify(mockDao).getSchedules(TEST_APP_ID, 100, 50, true);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSchedulesNoPermission() {
        service.getSchedules(TEST_APP_ID, 100, 50, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "offsetBy cannot be negative")
    public void getSchedulesNegativeOffset() {
        permitToAccess();
        
        service.getSchedules(TEST_APP_ID, -20, 50, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "pageSize must be from 5-100 records")
    public void getSchedulesPageTooSmall() {
        permitToAccess();
        
        service.getSchedules(TEST_APP_ID, 100, 2, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "pageSize must be from 5-100 records")
    public void getSchedulesPageTooLarge() {
        permitToAccess();
        
        service.getSchedules(TEST_APP_ID, 100, 101, true);
    }
    
    @Test
    public void getSchedulesForOrganization() {
        permitToAccess();
        
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        PagedResourceList<Schedule2> page = new PagedResourceList<Schedule2>(list, 10);
        
        when(mockDao.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 50, true)).thenReturn(page);
        
        PagedResourceList<Schedule2> retValue = service
                .getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 50, true);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("offsetBy"), 100);
        assertEquals(retValue.getRequestParams().get("pageSize"), 50);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
        
        verify(mockDao).getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 50, true);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSchedulesForOrganizationNoPermission() {
        service.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 50, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "offsetBy cannot be negative")
    public void getSchedulesForOrganizationNegativeOffset() {
        permitToAccess();
        
        service.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID,-20, 50, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "pageSize must be from 5-100 records")
    public void getSchedulesForOrganizationPageTooSmall() {
        permitToAccess();
        
        service.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 2, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "pageSize must be from 5-100 records")
    public void getSchedulesForOrganizationPageTooLarge() {
        permitToAccess();
        
        service.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 100, 101, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Caller is not a member of an organization")
    public void getSchedulesForOrganizationNoOrgIdParam() { 
        permitToAccess();
        
        service.getSchedulesForOrganization(TEST_APP_ID, null, 100, 101, true);
    }
    
    @Test
    public void getSchedule() {
        permitToAccess();
        
        Schedule2 schedule = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        Schedule2 retValue = service.getSchedule(TEST_APP_ID, TEST_STUDY_ID, GUID);
        assertEquals(retValue, schedule);
    }
    
    @Test
    public void getScheduleWorksForEnrollee() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        Schedule2 schedule = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        Schedule2 retValue = service.getSchedule(TEST_APP_ID, TEST_STUDY_ID, GUID);
        assertEquals(retValue, schedule);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getScheduleNoPermission() {
        Schedule2 schedule = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        service.getSchedule(TEST_APP_ID, null, GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Schedule not found.")
    public void getScheduleNotFound() {
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.getSchedule(TEST_APP_ID, null, GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getScheduleWrongOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        Schedule2 schedule = new Schedule2();
        schedule.setOwnerId("some-other-organization");
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        service.getSchedule(TEST_APP_ID, null, GUID);
    }
    
    @Test
    public void createSchedule() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockDao.createSchedule(any())).then(returnsFirstArg());
        
        Schedule2 schedule = new Schedule2();
        schedule.setAppId("wrong-app-id");
        schedule.setOwnerId("wrong-owner-id");
        schedule.setName("Name");
        schedule.setGuid("wrong-guid");
        schedule.setDuration(Period.parse("P3W"));
        schedule.setClientData(getClientData());
        schedule.setCreatedOn(CREATED_ON.minusDays(1));
        schedule.setModifiedOn(MODIFIED_ON.minusDays(1));
        schedule.setDeleted(true);
        schedule.setPublished(true);
        schedule.setVersion(1L);
        
        Schedule2 retValue = service.createSchedule(schedule);
        assertEquals(retValue, schedule);

        verify(mockDao).createSchedule(scheduleCaptor.capture());
        Schedule2 captured = scheduleCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getOwnerId(), TEST_ORG_ID);
        assertEquals(captured.getName(), "Name");
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getDuration(), Period.parse("P3W"));
        assertEquals(captured.getClientData().toString(), getClientData().toString());
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), CREATED_ON);
        assertFalse(captured.isDeleted());
        assertFalse(captured.isPublished());
        assertEquals(captured.getVersion(), 0L);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createScheduleIsInvalid() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Schedule2 schedule = new Schedule2();
        
        service.createSchedule(schedule);
    }
    
    @Test
    public void createScheduleVerifiesOwnerIdForNonOrgCaller() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockOrganizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Name");
        schedule.setDuration(Period.parse("P2W"));
        schedule.setClientData(getClientData());
        schedule.setOwnerId("this-will-be-ignored");
        
        service.createSchedule(schedule);        
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*ownerId cannot be null or blank.*")
    public void createScheduleCallerHasNoOrgWorks() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, null))
            .thenThrow(new IllegalArgumentException());
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Name");
        schedule.setDuration(Period.parse("P2W"));
        schedule.setClientData(getClientData());
        
        service.createSchedule(schedule);        
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createScheduleDoesNotVerifyOwnerIdForNonOrgCaller() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockOrganizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.empty());
        
        Schedule2 schedule = new Schedule2();
        schedule.setName("Name");
        schedule.setDuration(Period.parse("P2Y"));
        schedule.setClientData(getClientData());
        schedule.setOwnerId(TEST_ORG_ID);
        
        service.createSchedule(schedule);        
    }
    
    @Test
    public void updateSchedule() {
        permitToAccess();
        
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setOwnerId("wrong-owner-id");
        schedule.setName("Name");
        schedule.setGuid(GUID);
        schedule.setDuration(Period.parse("P3W"));
        schedule.setClientData(getClientData());
        schedule.setCreatedOn(CREATED_ON.minusDays(1));
        schedule.setModifiedOn(MODIFIED_ON);
        schedule.setDeleted(false);
        schedule.setPublished(true);
        schedule.setVersion(2L);
        
        Schedule2 existing = new Schedule2();
        existing.setAppId(TEST_APP_ID);
        existing.setOwnerId(TEST_ORG_ID);
        existing.setName("Old Name");
        existing.setGuid(GUID);
        existing.setDuration(Period.parse("P4W"));
        existing.setClientData(getClientData());
        existing.setCreatedOn(CREATED_ON);
        existing.setModifiedOn(MODIFIED_ON.minusDays(1));
        existing.setDeleted(false);
        existing.setPublished(false);
        existing.setVersion(1L);
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        when(mockDao.updateSchedule(any())).thenReturn(existing);
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Schedule2 retValue = service.updateSchedule(schedule);
        assertEquals(retValue, existing);
        
        verify(mockDao).updateSchedule(scheduleCaptor.capture());
        Schedule2 captured = scheduleCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getOwnerId(), TEST_ORG_ID);
        assertEquals(captured.getName(), "Name");
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getDuration(), Period.parse("P3W"));
        assertEquals(captured.getClientData().toString(), getClientData().toString());
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertFalse(captured.isDeleted());
        assertFalse(captured.isPublished());
        assertEquals(captured.getVersion(), 2L);
    }
    
    @Test(expectedExceptions = PublishedEntityException.class)
    public void updateScheduledLockedForUpdates() {
        permitToAccess();
        
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(GUID);
        
        Schedule2 existing = new Schedule2();
        existing.setPublished(true);
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        when(mockDao.updateSchedule(any())).thenReturn(existing);
        
        service.updateSchedule(schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateScheduleIsInvalid() {
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        existing.setAppId(TEST_APP_ID);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(GUID);
        service.updateSchedule(schedule);
    }
   
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateScheduleNotFound() {
        permitToAccess();
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(GUID);
        service.updateSchedule(schedule);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Schedule not found.")
    public void updateScheduleBothDeletedActAsNotFound() {
        permitToAccess();
        
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setOwnerId("wrong-owner-id");
        schedule.setName("Name");
        schedule.setGuid(GUID);
        schedule.setDuration(Period.parse("P3Y"));
        schedule.setClientData(getClientData());
        schedule.setCreatedOn(CREATED_ON.minusDays(1));
        schedule.setModifiedOn(MODIFIED_ON);
        schedule.setDeleted(true);
        schedule.setVersion(2L);
        
        Schedule2 existing = new Schedule2();
        existing.setAppId(TEST_APP_ID);
        existing.setOwnerId(TEST_ORG_ID);
        existing.setName("Old Name");
        existing.setGuid(GUID);
        existing.setDuration(Period.parse("P4Y"));
        existing.setClientData(getClientData());
        existing.setCreatedOn(CREATED_ON);
        existing.setModifiedOn(MODIFIED_ON.minusDays(1));
        existing.setDeleted(true);
        existing.setVersion(1L);
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        when(mockDao.updateSchedule(any())).thenReturn(existing);
        
        service.updateSchedule(schedule);
    }
    
    @Test
    public void publishSchedule() { 
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.publishSchedule(TEST_APP_ID, GUID);
        
        verify(mockDao).updateSchedule(scheduleCaptor.capture());
        assertTrue(scheduleCaptor.getValue().isPublished());
        assertEquals(scheduleCaptor.getValue().getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void publishScheduleNotFound() { 
        permitToAccess();
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.publishSchedule(TEST_APP_ID, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void publishScheduleFailsForLogicallyDeletedSchedule() { 
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        existing.setDeleted(true);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.publishSchedule(TEST_APP_ID, GUID);
    }
    
    @Test(expectedExceptions = PublishedEntityException.class)
    public void publishScheduleFailsForPublishedSchedule() { 
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        existing.setPublished(true);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.publishSchedule(TEST_APP_ID, GUID);
    }
    
    @Test
    public void deleteSchedule() {
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteSchedule(TEST_APP_ID, GUID);
        
        verify(mockDao).deleteSchedule(existing);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteScheduleNotFound() {
        permitToAccess();
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.deleteSchedule(TEST_APP_ID, GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteScheduleLogicallyDeleted() {
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        existing.setDeleted(true);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteSchedule(TEST_APP_ID, GUID);
        
        verify(mockDao).deleteSchedule(existing);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteScheduleNoPermission() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        
        Schedule2 existing = new Schedule2();
        existing.setOwnerId("some-other-org");
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteSchedule(TEST_APP_ID, GUID);
    }
    
    @Test
    public void deleteSchedulePermanently() {
        permitToAccess();
        
        Schedule2 existing = new Schedule2();
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteSchedulePermanently(TEST_APP_ID, GUID);
        
        verify(mockDao).deleteSchedulePermanently(existing);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteSchedulePermanentlyNotFound() {
        permitToAccess();
        
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.deleteSchedulePermanently(TEST_APP_ID, GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteSchedulePermanentlyNoPermission() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());
        
        Schedule2 existing = new Schedule2();
        existing.setOwnerId("some-other-org");
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteSchedulePermanently(TEST_APP_ID, GUID);
    }
    
    @Test
    public void setsAllGuidsOnCreate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerAppId(TEST_APP_ID)
                .build());

        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockOrganizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        // test valid schedule has GUID set on all the fields. for this test,
        // let's set something else and verify that all fields are set, despite not
        // being blank (allowing for a copy to be made).
        
        doReturn("otherGuid").when(service).generateGuid();
        
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        
        service.createSchedule(schedule);
        
        assertEquals(schedule.getGuid(), "otherGuid");
        schedule.getSessions().forEach(session -> {
            assertEquals(session.getGuid(), "otherGuid");
            assertEquals(session.getSchedule(), schedule);
            session.getTimeWindows().forEach(window -> {
                assertEquals(window.getGuid(), "otherGuid");
            });
        });
    }
    
    @Test
    public void setsBlankGuidsOnUpdate() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());

        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        when(mockOrganizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        Schedule2 existing = new Schedule2();
        existing.setAppId(TEST_APP_ID);
        existing.setOwnerId(TEST_ORG_ID);
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(Optional.of(existing));

        Schedule2 schedule = Schedule2Test.createValidSchedule();
        
        Session session1 = SessionTest.createValidSession();
        
        Session session2 = SessionTest.createValidSession();
        session2.setGuid(null);
        
        TimeWindow window1 = SessionTest.createValidSession().getTimeWindows().get(0);
        TimeWindow window2 = SessionTest.createValidSession().getTimeWindows().get(0);
        window2.setGuid(null);
        session1.setTimeWindows(ImmutableList.of(window1, window2));
        
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        // test valid schedule has GUID set on all the fields. for this test,
        // let's set something else and verify that only blank GUID fields are set, 
        // allowing for copies and new items to be added to the schedule.

        doReturn("otherGuid").when(service).generateGuid();
        
        service.updateSchedule(schedule);
        
        assertEquals(schedule.getSessions().get(0).getGuid(), SESSION_GUID_1);
        assertEquals(schedule.getSessions().get(0).getTimeWindows().get(0).getGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(schedule.getSessions().get(0).getTimeWindows().get(1).getGuid(), "otherGuid");
        assertEquals(schedule.getSessions().get(1).getGuid(), "otherGuid");
        assertEquals(schedule.getSessions().get(1).getTimeWindows().get(0).getGuid(), SESSION_WINDOW_GUID_1);
    }
    
    @Test
    public void getTimelineForScheduleNoPersistence() {
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        Timeline timeline = service.getTimelineForSchedule(TEST_APP_ID, GUID);
        assertNotNull(timeline);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTimelineForScheduleScheduleNotFound() {
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.getTimelineForSchedule(TEST_APP_ID, GUID);
    }
    
    @Test
    public void getTimelineForSchedulePersistenceWhenNoRecords() {
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        Timeline timeline = service.getTimelineForSchedule(TEST_APP_ID, GUID);
        assertNotNull(timeline);
    }

    @Test
    public void getTimelineForSchedulePersistenceWhenRecordsOutOfDate() {
        Schedule2 schedule = new Schedule2();
        schedule.setModifiedOn(MODIFIED_ON);
        when(mockDao.getSchedule(TEST_APP_ID, GUID)).thenReturn(Optional.of(schedule));
        
        Timeline timeline = service.getTimelineForSchedule(TEST_APP_ID, GUID);
        assertNotNull(timeline);
    }
}
