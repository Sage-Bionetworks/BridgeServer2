package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.ANALYSIS;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.COMPLETED;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.IN_FLIGHT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.LEGACY;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.RECRUITMENT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.WITHDRAWN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentPhase;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class StudyServiceTest extends Mockito {
    private static final PagedResourceList<Study> STUDIES = new PagedResourceList<>(
            ImmutableList.of(Study.create(), Study.create()), 5);
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);
    private static final CacheKey CACHE_KEY = CacheKey.publicStudy(TEST_APP_ID, TEST_STUDY_ID);
    
    @Mock
    private StudyDao mockStudyDao;
    
    @Mock
    private OrganizationDao mockOrganizationDao;
    
    @Mock
    private SponsorService mockSponsorService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private Schedule2Service mockScheduleService;
    
    @Mock
    private AccountService mockAccountService;

    @Mock
    private DemographicService mockDemographicService;

    @Mock
    private AlertService alertService;

    @Mock
    private AssessmentService assessmentService;

    @Captor
    private ArgumentCaptor<Study> studyCaptor;
    
    @InjectMocks
    @Spy
    private StudyService service;

    private Schedule2 schedule;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        RequestContext.set(new RequestContext.Builder().withCallerOrgMembership(TEST_ORG_ID).build());
        
        schedule = new Schedule2();
        schedule.setOwnerId(TEST_ORG_ID);
        AssessmentReference assessmentReference = new AssessmentReference();
        assessmentReference.setAppId(TEST_APP_ID);
        assessmentReference.setGuid(ASSESSMENT_1_GUID);
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        session.setAssessments(ImmutableList.of(assessmentReference));
        schedule.setSessions(ImmutableList.of(session));
        when(mockScheduleService.getScheduleForStudyValidator(any(), any())).thenReturn(Optional.of(schedule));
        when(mockScheduleService.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(schedule);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(Optional.of(schedule));
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        doReturn(MODIFIED_ON).when(service).getDateTime();
    }
    
    @AfterMethod
    public void afterMethod( ) {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getStudy() {
        Study study = Study.create();
        study.setModifiedOn(MODIFIED_ON);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        Study returnedValue = service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        assertEquals(returnedValue, study);
        
        verify(mockStudyDao).getStudy(TEST_APP_ID, TEST_STUDY_ID);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).setObject(cacheKey, MODIFIED_ON);
    }
    
    @Test
    public void getZoneId_clientTimeZoneReturned() {
        String retValue = service.getZoneId(TEST_APP_ID, TEST_STUDY_ID, TEST_CLIENT_TIME_ZONE);
        assertEquals(retValue, TEST_CLIENT_TIME_ZONE);
    }
    
    @Test
    public void getZoneId_studyTimeZoneReturned() {
        Study study = Study.create();
        study.setStudyTimeZone(TEST_CLIENT_TIME_ZONE);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, false)).thenReturn(study);
        
        String retValue = service.getZoneId(TEST_APP_ID, TEST_STUDY_ID, null);
        assertEquals(retValue, TEST_CLIENT_TIME_ZONE);
    }
    
    @Test
    public void getZoneId_defaultZoneReturned() {
        Study study = Study.create();
        study.setModifiedOn(MODIFIED_ON);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, false)).thenReturn(study);
        
        doReturn("America/Chicago").when(service).getDefaultTimeZoneId();
        
        String retValue = service.getZoneId(TEST_APP_ID, TEST_STUDY_ID, null);
        assertEquals(retValue, "America/Chicago");
    }
    
    @Test
    public void getStudyIds() {
        Study studyA = Study.create();
        studyA.setIdentifier("studyA");
        
        Study studyB = Study.create();
        studyB.setIdentifier("studyB");
        
        PagedResourceList<Study> studies = new PagedResourceList<>(ImmutableList.of(studyA, studyB), 2); 
        when(mockStudyDao.getStudies(TEST_APP_ID, null, null, null, false)).thenReturn(studies);
        
        Set<String> studyIds = service.getStudyIds(TEST_APP_ID);
        assertEquals(studyIds, ImmutableSet.of("studyA","studyB"));
        
        verify(mockStudyDao).getStudies(TEST_APP_ID, null, null, null, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getStudyNotFoundThrowingException() {
        service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
    }
    
    @Test
    public void getStudyNotFoundNotThrowingException() {
        Study study = service.getStudy(TEST_APP_ID, TEST_STUDY_ID, false);
        assertNull(study);
        verify(mockCacheProvider, never()).setObject(any(), any());
    }

    @Test
    public void getStudyIdsUsingSchedule() {
        List<String> studyIdList = ImmutableList.of(TEST_STUDY_ID);
        when(mockStudyDao.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(studyIdList);

        List<String> retVal = service.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID);
        assertSame(retVal, studyIdList);

        verify(mockStudyDao).getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID);
    }

    @Test
    public void getStudiesIncludeDeleted() {
        when(mockStudyDao.getStudies(TEST_APP_ID, null, 0, 50, true)).thenReturn(STUDIES);
        
        PagedResourceList<Study> returnedValue = service.getStudies(TEST_APP_ID, 0, 50, true);
        assertEquals(returnedValue, STUDIES);
        
        verify(mockStudyDao).getStudies(TEST_APP_ID, null, 0, 50, true);
    }
    
    @Test
    public void getStudiesExcludeDeleted() {
        when(mockStudyDao.getStudies(TEST_APP_ID, null, 10, 20, false)).thenReturn(STUDIES);
        
        PagedResourceList<Study> returnedValue = service.getStudies(TEST_APP_ID, 10, 20, false);
        assertEquals(returnedValue, STUDIES);
        
        verify(mockStudyDao).getStudies(TEST_APP_ID, null, 10, 20, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudiesOffsetNegative() { 
        service.getStudies(TEST_APP_ID, -1, 20, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudiesPageSizeTooSmall() { 
        service.getStudies(TEST_APP_ID, 0, API_MINIMUM_PAGE_SIZE-1, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudiesPageSizeTooLarge() { 
        service.getStudies(TEST_APP_ID, 0, API_MAXIMUM_PAGE_SIZE+1, false);
    }
    
    @Test
    public void getStudiesWithNullParams() {
        when(mockStudyDao.getStudies(TEST_APP_ID, null, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
    
        service.getStudies(TEST_APP_ID, null, null, false);
        
        verify(mockStudyDao).getStudies(TEST_APP_ID, null, null, null, false);
    }
    
    @Test
    public void getStudiesScopesSearchForScopedRoles() {
        Set<String> studies = ImmutableSet.of("studyA", "studyB");
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withOrgSponsoredStudies(studies)
                .build());
        
        when(mockStudyDao.getStudies(TEST_APP_ID, studies, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));

        service.getStudies(TEST_APP_ID, null, null, false);
    
        verify(mockStudyDao).getStudies(TEST_APP_ID, studies, null, null, false);
    }
    
    @Test
    public void getStudiesScopesSearchForScopedRolesWhenTheyAreNull() {
        Set<String> studies = ImmutableSet.of();
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withOrgSponsoredStudies(studies)
                .build());
        
        when(mockStudyDao.getStudies(TEST_APP_ID, studies, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));

        service.getStudies(TEST_APP_ID, null, null, false);
    
        verify(mockStudyDao).getStudies(TEST_APP_ID, studies, null, null, false);
    }
    
    @Test
    public void getStudiesDoesNotScopeSearchForUnscopedRoles() {
        Set<String> studies = ImmutableSet.of("studyA", "studyB");
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(studies).build());
        
        when(mockStudyDao.getStudies(TEST_APP_ID, null, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));

        service.getStudies(TEST_APP_ID, null, null, false);
    
        verify(mockStudyDao).getStudies(TEST_APP_ID, null, null, null, false);
    }
    
    @Test
    public void createStudyWithSponsorship() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        study.setAppId("junk");
        study.setVersion(10L);
        study.setDeleted(true);
        study.setPhase(IN_FLIGHT);
        DateTime timestamp = DateTime.now().minusHours(2);
        study.setCreatedOn(timestamp);
        study.setModifiedOn(timestamp);

        when(mockStudyDao.createStudy(any())).thenReturn(VERSION_HOLDER);
        
        VersionHolder returnedValue = service.createStudy(TEST_APP_ID, study, true);
        assertEquals(returnedValue, VERSION_HOLDER);
        
        verify(mockStudyDao).createStudy(studyCaptor.capture());
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getIdentifier(), TEST_STUDY_ID);
        assertEquals(persisted.getName(), "oneName");
        assertEquals(persisted.getAppId(), TEST_APP_ID);
        assertEquals(persisted.getPhase(), DESIGN);
        assertEquals(persisted.getVersion(), 0L);
        assertFalse(persisted.isDeleted());
        assertNotEquals(persisted.getCreatedOn(), timestamp);
        assertNotEquals(persisted.getModifiedOn(), timestamp);
        
        verify(mockSponsorService).createStudyWithSponsorship(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).setObject(cacheKey, MODIFIED_ON);
    }
    
    @Test
    public void createStudyWithoutSponsorship() {
        Study study = Study.create();
        study.setIdentifier("oneId");
        study.setName("oneName");

        when(mockStudyDao.createStudy(any())).thenReturn(VERSION_HOLDER);
        
        service.createStudy(TEST_APP_ID, study, true);
        
        verify(mockStudyDao).createStudy(studyCaptor.capture());
        verify(mockSponsorService, never()).addStudySponsor(any(), any(), any());
    }
    
    @Test
    public void createStudyWithoutSponsorshipWhenCallerInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        study.setAppId("junk");
        study.setVersion(10L);
        study.setDeleted(true);
        DateTime timestamp = DateTime.now().minusHours(2);
        study.setCreatedOn(timestamp);
        study.setModifiedOn(timestamp);
        
        service.createStudy(TEST_APP_ID, study, false);
        
        verify(mockStudyDao).createStudy(any());
        verify(mockSponsorService, never()).addStudySponsor(any(), any(), any());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createStudyInvalidStudy() {
        service.createStudy(TEST_APP_ID, Study.create(), true);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createStudyAlreadyExists() {
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.createStudy(TEST_APP_ID, study, true);
    }
    
    @Test
    public void updateStudy() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);
        when(mockStudyDao.updateStudy(any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setAppId("wrongAppId");
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("newName");
        study.setPhase(DESIGN);
        
        VersionHolder versionHolder = service.updateStudy(TEST_APP_ID, study);
        assertEquals(versionHolder, VERSION_HOLDER);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        
        Study returnedValue = studyCaptor.getValue();
        assertEquals(returnedValue.getAppId(), TEST_APP_ID);
        assertEquals(returnedValue.getIdentifier(), TEST_STUDY_ID);
        assertEquals(returnedValue.getName(), "newName");
        assertEquals(returnedValue.getPhase(), DESIGN);
        assertEquals(returnedValue.getScheduleGuid(), SCHEDULE_GUID);
        assertNotNull(returnedValue.getCreatedOn());
        assertEquals(returnedValue.getModifiedOn(), MODIFIED_ON);
        
        verify(mockCacheProvider).removeObject(CACHE_KEY);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).setObject(cacheKey, MODIFIED_ON);
    }
    
    @Test
    public void updateStudy_setScheduleGuidWorks() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);
        when(mockStudyDao.updateStudy(any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setAppId("wrongAppId");
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("newName");
        study.setPhase(DESIGN);
        study.setScheduleGuid(SCHEDULE_GUID);
        
        service.updateStudy(TEST_APP_ID, study);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        
        Study returnedValue = studyCaptor.getValue();
        assertEquals(returnedValue.getScheduleGuid(), SCHEDULE_GUID);
    }
    
    @Test
    public void updateStudy_unsetScheduleGuidDoesNotWork() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);
        when(mockStudyDao.updateStudy(any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setAppId("wrongAppId");
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("newName");
        study.setPhase(DESIGN);
        
        service.updateStudy(TEST_APP_ID, study);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        
        Study returnedValue = studyCaptor.getValue();
        assertEquals(returnedValue.getScheduleGuid(), SCHEDULE_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateStudyInvalidStudy() {
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        service.updateStudy(TEST_APP_ID, study);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateStudyEntityNotFound() {
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        study.setDeleted(true);

        service.updateStudy(TEST_APP_ID, study);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateStudyEntityDeleted() {
        Study existing = Study.create();
        existing.setDeleted(true);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        study.setDeleted(true);
        
        service.updateStudy(TEST_APP_ID, study);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*Study cannot be changed during phase.*")
    public void updateStudyCannotUpdateMetadata() { 
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(COMPLETED);
        existing.setCreatedOn(DateTime.now());
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        // It doesn’t even matter what you’re submitting, it’ll fail due to the phase
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        
        service.updateStudy(TEST_APP_ID, study);
    }

    @Test
    public void updateStudyCanUpdateCore() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        StudyCustomEvent event1 = new StudyCustomEvent("event1", IMMUTABLE);
        StudyCustomEvent event2 = new StudyCustomEvent("event2", IMMUTABLE);
        List<StudyCustomEvent> events = ImmutableList.of(event1, event2);
        
        Study study = Study.create();
        study.setName("new name");
        study.setIdentifier(TEST_STUDY_ID);
        study.setCustomEvents(events);
        
        service.updateStudy(TEST_APP_ID, study);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        assertEquals(studyCaptor.getValue().getCustomEvents(), events);
    }
    
    @Test
    public void updateStudyCannotUpdateCoreFields() { 
        StudyCustomEvent event1 = new StudyCustomEvent("event1", IMMUTABLE);
        StudyCustomEvent event2 = new StudyCustomEvent("event2", IMMUTABLE);
        List<StudyCustomEvent> events = ImmutableList.of(event1, event2);
        
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(IN_FLIGHT);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(SCHEDULE_GUID);
        existing.setCustomEvents(events);
        existing.setStudyStartEventId("event1");
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        // It doesn’t even matter what you’re submitting, it’ll fail
        Study study = Study.create();
        study.setName("new name");
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid("some-other-guid");
        study.setStudyStartEventId("event2");
        study.setCustomEvents(ImmutableList.of(new StudyCustomEvent("event2", IMMUTABLE)));
        
        service.updateStudy(TEST_APP_ID, study);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        assertEquals(studyCaptor.getValue().getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(studyCaptor.getValue().getCustomEvents(), events);
        assertEquals(studyCaptor.getValue().getStudyStartEventId(), "event1");
    }
    
    @Test
    public void updateStudy_canUpdateWithNullScheduleGuid() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(null);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        Study study = Study.create();
        study.setAppId("wrongAppId");
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("newName");
        study.setPhase(DESIGN);
        study.setScheduleGuid(null);

        service.updateStudy(TEST_APP_ID, study);

        verify(mockStudyDao).updateStudy(studyCaptor.capture());

        Study returnedValue = studyCaptor.getValue();
        assertEquals(returnedValue.getAppId(), TEST_APP_ID);
        assertEquals(returnedValue.getIdentifier(), TEST_STUDY_ID);
        assertEquals(returnedValue.getName(), "newName");
        assertNull(returnedValue.getScheduleGuid());
    }
    
    @Test
    public void deleteStudy() {
        Study study = Study.create();
        study.setPhase(DESIGN);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        Study persisted = studyCaptor.getValue();
        assertTrue(persisted.isDeleted());
        assertNotNull(persisted.getModifiedOn());
        
        verify(mockCacheProvider).removeObject(CACHE_KEY);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(cacheKey);

        // verify alerts for this study are deleted
        verify(alertService).deleteAlertsForStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*Study cannot be deleted during phase.*")
    public void deleteStudy_studyWrongPhase() {
        Study study = Study.create();
        study.setPhase(IN_FLIGHT);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void deleteStudy_adminCanForceStudyInWrongPhase() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        verify(mockCacheProvider).removeObject(CACHE_KEY);

        // verify alerts for this study are deleted
        verify(alertService).deleteAlertsForStudy(TEST_APP_ID, TEST_STUDY_ID);
    }

    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudy_studyNotFound() {
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void deleteStudyPermanently() {
        Study study = Study.create();
        study.setPhase(DESIGN);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockScheduleService, never()).deleteSchedulePermanently(any(), any());
        verify(mockDemographicService).deleteAllValidationConfigs(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockStudyDao).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(cacheKey);
    }

    @Test
    public void deleteStudyPermanently_deletesScheduleFirst() {
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockScheduleService).deleteSchedulePermanently(TEST_APP_ID, SCHEDULE_GUID);
        verify(mockStudyDao).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deleteStudyPermanently_studyWrongPhase() {
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void deleteStudyPermanently_adminCanForceStudyInWrongPhase() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudyPermanently_studyNotFound() {
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void deleteAllStudies() {
        service.deleteAllStudies(TEST_APP_ID);
        verify(mockStudyDao).deleteAllStudies(TEST_APP_ID);
    }
    
    @Test
    public void transitionToDesign() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(LEGACY);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToDesign(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), DESIGN);     
        assertEquals(study.getModifiedOn(), MODIFIED_ON);

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToRecruitment() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);

        Assessment assessment = new Assessment();
        assessment.setGuid(ASSESSMENT_1_GUID);
        assessment.setPhase(AssessmentPhase.REVIEW);
        when(assessmentService.getAssessmentByGuid(TEST_APP_ID, null, ASSESSMENT_1_GUID)).thenReturn(assessment);

        service.transitionToRecruitment(TEST_APP_ID, TEST_STUDY_ID);

        verify(assessmentService).updateAssessment(TEST_APP_ID, null, assessment);
        
        verify(mockAccountService).deleteAllPreviewAccounts(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        
        verify(mockScheduleService).publishSchedule(TEST_APP_ID, SCHEDULE_GUID);

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToRecruitmentScheduleAlreadyPublished() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);

        schedule.setPublished(true);

        Assessment assessment = new Assessment();
        assessment.setGuid(ASSESSMENT_1_GUID);
        assessment.setPhase(AssessmentPhase.REVIEW);
        when(assessmentService.getAssessmentByGuid(TEST_APP_ID, null, ASSESSMENT_1_GUID)).thenReturn(assessment);

        service.transitionToRecruitment(TEST_APP_ID, TEST_STUDY_ID);

        verify(assessmentService).updateAssessment(TEST_APP_ID, null, assessment);
        
        verify(mockAccountService).deleteAllPreviewAccounts(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        
        verify(mockScheduleService, never()).publishSchedule(any(), any());

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToRecruitmentWithNoSchedule() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToRecruitment(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockAccountService).deleteAllPreviewAccounts(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        assertNull(study.getScheduleGuid());

        verifyZeroInteractions(mockScheduleService);

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToInFlight() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToInFlight(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), IN_FLIGHT);    
        assertEquals(study.getModifiedOn(), MODIFIED_ON);   

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToAnalysis() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(IN_FLIGHT);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToAnalysis(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), ANALYSIS);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);

        // verify no alerts deleted
        verifyZeroInteractions(alertService);
    }
    
    @Test
    public void transitionToCompleted() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(ANALYSIS);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToCompleted(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), COMPLETED);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);

        // verify alerts deleted for this study
        verify(alertService).deleteAlertsForStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void transitionToWithdrawn() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setScheduleGuid(SCHEDULE_GUID);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);

        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), WITHDRAWN);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        
        verify(mockScheduleService).publishSchedule(TEST_APP_ID, SCHEDULE_GUID);
    }
    
    @Test
    public void transitionToWithdrawnScheduleAlreadyPublished() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);

        schedule.setPublished(true);

        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), WITHDRAWN);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        
        verify(mockScheduleService, never()).publishSchedule(any(), any());
    }
    
    @Test
    public void transitionToWithdrawnWithNoSchedule() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), WITHDRAWN);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        assertNull(study.getScheduleGuid());

        verifyZeroInteractions(mockScheduleService);
    }
    
    // As all the phase transition methods use the same code, I'm only goind to write the error
    // path tests one time.
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void cannotTransitionWhenStudyNotFound() { 
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(null);
        
        service.transitionToAnalysis(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotTransitionWhenNotAllowedByRole() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Study cannot transition from “design” to “completed”.")
    public void cannotTransitionBetweenCertainPhases() { 
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(DESIGN);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToCompleted(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void transitionsInvalidateCache() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.transitionToInFlight(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockCacheProvider).removeObject(CACHE_KEY);
        
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).setObject(cacheKey, MODIFIED_ON);
    }
    
    @Test
    public void removeStudyEtags() {
        when(mockStudyDao.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
            .thenReturn(ImmutableList.of("studyA", "studyB"));
        
        service.removeStudyEtags(TEST_APP_ID, SCHEDULE_GUID);
        verify(mockStudyDao).removeScheduleFromStudies(TEST_APP_ID, SCHEDULE_GUID);
        
        verify(mockCacheProvider).removeObject(CacheKey.etag(Schedule2.class, TEST_APP_ID, "studyA"));
        verify(mockCacheProvider).removeObject(CacheKey.etag(Schedule2.class, TEST_APP_ID, "studyB"));
    }
    
    @Test
    public void updateStudyEtags() {
        when(mockStudyDao.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
            .thenReturn(ImmutableList.of("studyA", "studyB"));
        
        service.updateStudyEtags(TEST_APP_ID, SCHEDULE_GUID, CREATED_ON);
        
        verify(mockCacheProvider).setObject(CacheKey.etag(Schedule2.class, TEST_APP_ID, "studyA"), CREATED_ON);
        verify(mockCacheProvider).setObject(CacheKey.etag(Schedule2.class, TEST_APP_ID, "studyB"), CREATED_ON);
    }
    
    @Test
    public void getCustomEventIdsFromSchedule_onlyGetsCustomEventIds() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);
    
        Session session1 = new Session();
        session1.setStartEventIds(ImmutableList.of("aaa","bbb","custom:ccc"));
        Session session2 = new Session();
        session2.setStartEventIds(ImmutableList.of("ddd","custom:eee"));
        schedule.setSessions(ImmutableList.of(session1, session2));

        Set<String> eventIds = service.getCustomEventIdsFromSchedule(TEST_APP_ID, SCHEDULE_GUID);

        Set<String> expectedSet = Sets.newHashSet("ccc", "eee");
        assertEquals(eventIds, expectedSet);
    }
    
    @Test 
    public void getCustomEventIdsFromSchedule_emptyScheduleReturnsEmpty() {
        Study existing = Study.create();
        existing.setIdentifier(TEST_STUDY_ID);
        existing.setName("oldName");
        existing.setPhase(DESIGN);
        existing.setCreatedOn(DateTime.now());
        existing.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        Set<String> eventIds = service.getCustomEventIdsFromSchedule(TEST_APP_ID, SCHEDULE_GUID);
    
        Set<String> expectedSet = Sets.newHashSet();
        assertEquals(eventIds, expectedSet);
    }
    
    @Test
    public void revertToDesign() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        Study study = Study.create();
        study.setPhase(RECRUITMENT);
        study.setIdentifier(TEST_STUDY_ID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
    
        service.revertToDesign(TEST_APP_ID, TEST_STUDY_ID);
    
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), DESIGN);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
    
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    
        CacheKey cacheKey = CacheKey.etag(Study.class, TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).setObject(cacheKey, MODIFIED_ON);
    }
    
    @Test 
    public void revertToDesign_notSuperadmin() {
        // This study mock is unnecessary if the exceptions are thrown as expected,
        // but useful for reaching the message in the fail condition if they are not thrown.
        Study study = Study.create();
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        for (Roles role : Roles.values()) {
            if (role.equals(SUPERADMIN)) {
                continue;
            }
            
            RequestContext.set(new RequestContext.Builder()
                    .withCallerRoles(ImmutableSet.of(role)).build());
            
            try {
                service.revertToDesign(TEST_APP_ID, TEST_STUDY_ID);
                fail("Should have thrown UnauthorizedException for role: " + role.toString());
            } catch (UnauthorizedException e) {
                assertEquals(e.getMessage(), "Only superadmins can revert studies to design");
            }
        }
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void revertToDesign_studyDoesNotExist() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(null);
    
        service.revertToDesign(TEST_APP_ID, TEST_STUDY_ID);
    }
}
