package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
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
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnum;
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
    private AppConfigElementService mockConfigElementService;
    
    @Captor
    private ArgumentCaptor<Study> studyCaptor;
    
    @InjectMocks
    private StudyService service;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        AppConfigEnum configEnum = new AppConfigEnum();
        when(mockConfigElementService.getAppConfigEnum(eq(TEST_APP_ID), any())).thenReturn(configEnum);
    }
    
    @AfterMethod
    public void afterEmthod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @AfterMethod
    public void afterMethod( ) {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getStudy() {
        Study study = Study.create();
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        Study returnedValue = service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
        assertEquals(returnedValue, study);
        
        verify(mockStudyDao).getStudy(TEST_APP_ID, TEST_STUDY_ID);
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
        assertNull(persisted.getVersion());
        assertFalse(persisted.isDeleted());
        assertNotEquals(persisted.getCreatedOn(), timestamp);
        assertNotEquals(persisted.getModifiedOn(), timestamp);
        
        verify(mockSponsorService).createStudyWithSponsorship(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
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
        assertNotNull(returnedValue.getModifiedOn());
        
        verify(mockCacheProvider).removeObject(CACHE_KEY);
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
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(existing);

        // It doesn’t even matter what you’re submitting, it’ll fail
        Study study = Study.create();
        study.setName("new name");
        study.setIdentifier(TEST_STUDY_ID);
        study.setScheduleGuid("some-other-guid");
        study.setCustomEvents(ImmutableList.of(new StudyCustomEvent("event2", IMMUTABLE)));
        
        service.updateStudy(TEST_APP_ID, study);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        assertEquals(studyCaptor.getValue().getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(studyCaptor.getValue().getCustomEvents(), events);
    }
    
    @Test
    public void deleteStudyAllowedByPhase() {
        Study study = Study.create();
        study.setPhase(DESIGN);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(studyCaptor.capture());
        Study persisted = studyCaptor.getValue();
        assertTrue(persisted.isDeleted());
        assertNotNull(persisted.getModifiedOn());
        
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*Study cannot be deleted during phase.*")
    public void deleteStudyNotAllowedByPhase() {
        Study study = Study.create();
        study.setPhase(IN_FLIGHT);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudyNotFound() {
        service.deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void deleteStudyPermanently() {
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(Study.create());
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockScheduleService, never()).deleteSchedulePermanently(any(), any());
        verify(mockStudyDao).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }    

    @Test
    public void deleteStudyPermanently_deletesScheduleFirst() {
        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyDao.getStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(study);
        
        service.deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockScheduleService).deleteSchedulePermanently(TEST_APP_ID, SCHEDULE_GUID);
        verify(mockStudyDao).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudyPermanentlyNotFound() {
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
        
        Schedule2 schedule = new Schedule2();
        when(mockScheduleService.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(schedule);
        
        service.transitionToRecruitment(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        
        verify(mockScheduleService).publishSchedule(TEST_APP_ID, SCHEDULE_GUID);
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
        
        Schedule2 schedule = new Schedule2();
        schedule.setPublished(true);
        when(mockScheduleService.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(schedule);
        
        service.transitionToRecruitment(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        
        verify(mockScheduleService, never()).publishSchedule(any(), any());
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
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), RECRUITMENT);
        assertNull(study.getScheduleGuid());

        verifyZeroInteractions(mockScheduleService);
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
        
        Schedule2 schedule = new Schedule2();
        when(mockScheduleService.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(schedule);

        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), WITHDRAWN);
        
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
        
        Schedule2 schedule = new Schedule2();
        schedule.setPublished(true);
        when(mockScheduleService.getSchedule(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(schedule);
        
        service.transitionToWithdrawn(TEST_APP_ID, TEST_STUDY_ID);
        
        verify(mockStudyDao).updateStudy(study);
        assertEquals(study.getPhase(), WITHDRAWN);
        
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
    }
    
    @Test
    public void removeScheduleFromStudies() {
        service.removeScheduleFromStudies(TEST_APP_ID, SCHEDULE_GUID);
        verify(mockStudyDao).removeScheduleFromStudies(TEST_APP_ID, SCHEDULE_GUID);
    }
}
