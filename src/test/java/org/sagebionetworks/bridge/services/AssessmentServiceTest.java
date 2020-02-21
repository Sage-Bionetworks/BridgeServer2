package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.services.AssessmentService.IDENTIFIER_REQUIRED;
import static org.sagebionetworks.bridge.services.AssessmentService.OFFSET_BY_CANNOT_BE_NEGATIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

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

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.Substudy;

public class AssessmentServiceTest extends Mockito {
    private static final String APP_ID_VALUE = "appId";
    private static final StudyIdentifierImpl APP_AS_STUDY_ID = new StudyIdentifierImpl(APP_ID_VALUE);
    private static final int REVISION_VALUE = 3;
    private static final PagedResourceList<Assessment> EMPTY_LIST = new PagedResourceList<>(ImmutableList.of(), 0);
    private static final Assessment ASSESSMENT = new Assessment();
    
    @Mock
    AssessmentDao mockDao;
    
    @Mock
    SubstudyService mockSubstudyService;
    
    @Mock
    Substudy mockSubstudy;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;
    
    @InjectMocks
    @Spy
    AssessmentService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        when(service.generateGuid()).thenReturn(GUID);
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void getAssessments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(ASSESSMENT), 100);
        when(mockDao.getAssessments(APP_ID_VALUE, 10, 26, STRING_TAGS, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = service.getAssessments(
                APP_ID_VALUE, 10, 26, STRING_TAGS, true);
        
        assertEquals(retValue.getItems().get(0), ASSESSMENT);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 26);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
        assertEquals(retValue.getRequestParams().get("tags"), STRING_TAGS);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "offsetBy cannot be negative")
    public void getAssessmentsNegativeOffsetBy() {
        service.getAssessments(APP_ID_VALUE, -100, 25, null, false);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentsPageSizeUnderMin() {
        service.getAssessments(APP_ID_VALUE, 0, 1, null, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentsPageSizeOverMax() {
        service.getAssessments(APP_ID_VALUE, 0, 100000, null, false);
    }
    
    @Test
    public void createAssessment() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
                .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(true); // can't do this, it's reset
        
        service.createAssessment(APP_ID_VALUE, assessment);
        
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
        
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getOwnerId(), OWNER_ID);
        // Same timestamp on create
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), CREATED_ON);
        assertFalse(assessment.isDeleted());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentUnauthorized() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("not the owner id");
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createAssessmentAlreadyExists() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(false);
        
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
    
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*identifier cannot be missing.*")
    public void createAssessmentInvalid() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(null);
        
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void createAssessmentScrubsMarkup() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        addMarkupToSensitiveFields(assessment);

        service.createAssessment(APP_ID_VALUE, assessment);
        
        assertMarkupRemoved(assessment);
    }

    @Test
    public void createAssessmentRevision() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
                .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(new Assessment()), 1));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(true); // can't do this, it's reset
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
        
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
        
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getOwnerId(), OWNER_ID);
        // same timestamp on creation
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), CREATED_ON);
        assertFalse(assessment.isDeleted());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentRevisionUnauthorized() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("not the owner id");
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void createAssessmentRevisionEntityNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(false);
        
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
    
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }

    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*identifier cannot be missing.*")
    public void createAssessmentRevisionInvalid() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(new Assessment()), 1));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(null);
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }

    @Test
    public void createAssessmentRevisionScrubsMarkup() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(new Assessment()), 1));
        
        Assessment assessment = AssessmentTest.createAssessment();
        addMarkupToSensitiveFields(assessment);

        service.createAssessmentRevision(APP_ID_VALUE, assessment);
        
        assertMarkupRemoved(assessment);
    }

    @Test
    public void updateAssessment() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);
        
        // Fill out only the fields needed to pass validation, leaving the rest to be
        // filled in by the existing assessment
        Assessment assessment = new Assessment();
        assessment.setGuid(GUID); // this always gets set in the controller
        assessment.setTitle("title");
        assessment.setOsName(ANDROID);
        when(mockDao.saveAssessment(APP_ID_VALUE, assessment)).thenReturn(assessment);
        
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        Assessment retValue = service.updateAssessment(APP_ID_VALUE, assessment);
        assertSame(retValue, assessment);
        
        assertEquals(assessment.getIdentifier(), IDENTIFIER);
        assertEquals(assessment.getOwnerId(), OWNER_ID);
        assertEquals(assessment.getOriginGuid(), "originGuid");
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
        
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAssessmentEntityNotFound() {
        Assessment assessment = new Assessment();
        
        when(mockDao.getAssessment(APP_ID_VALUE, assessment.getGuid()))
            .thenReturn(Optional.empty());
        
        service.updateAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void updateAssessmentCanDelete() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        
        when(mockDao.getAssessment(APP_ID_VALUE, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
        assertTrue(assessment.isDeleted());
    }

    @Test
    public void updateAssessmentCanUndelete() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(APP_ID_VALUE, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
        assertFalse(assessment.isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAssessmentCannotUpdatedDeleted() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(APP_ID_VALUE, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void updateAssessmentScrubsMarkup() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        addMarkupToSensitiveFields(assessment);

        when(mockDao.getAssessment(eq(APP_ID_VALUE), any()))
            .thenReturn(Optional.of(assessment));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
        
        assertMarkupRemoved(assessment);
    }

    @Test
    public void globalUserCanUpdateAssessmentWithOwnerId() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void scopedUserCanUpdateAssessmentWithOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        assessment.setOwnerId("app2");
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void scopedUserCannotUpdateAssessmentInvalidOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        assessment.setOwnerId("app3");
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.updateAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void updateSharedAssessment() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(APP_AS_STUDY_ID)
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId(APP_ID_VALUE + ":" + OWNER_ID);
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
        
        verify(mockDao).saveAssessment(SHARED_STUDY_ID_STRING, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentUnauthorizedApp() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(APP_AS_STUDY_ID)
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId("wrong-app:" + OWNER_ID);
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentUnauthorizedOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(APP_AS_STUDY_ID)
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId(APP_ID_VALUE + ":wrong-org");
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSharedAssessmentDeletedEntityNotFound() {
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID))
            .thenReturn(Optional.empty());
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSharedAssessmentLogicallyDeletedEntityNotFound() {
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
    }

    @Test
    public void updateSharedAssessmentCanDelete() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(APP_ID_VALUE + ":" + OWNER_ID);
        existing.setDeleted(false);
        
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        when(mockDao.saveAssessment(APP_ID_VALUE, assessment)).thenReturn(assessment);
        
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
        assertTrue(assessment.isDeleted());
    }
    
    @Test
    public void updateSharedAssessmentCanUndelete() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false)).thenReturn(mockSubstudy);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(APP_ID_VALUE + ":" + OWNER_ID);
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        when(mockDao.saveAssessment(APP_ID_VALUE, assessment)).thenReturn(assessment);
        
        service.updateSharedAssessment(APP_ID_VALUE, assessment);
        assertFalse(assessment.isDeleted());
    }
    
    @Test
    public void getAssessmentByGuid() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID))
            .thenReturn(Optional.of(ASSESSMENT));        
        Assessment retValue = service.getAssessmentByGuid(APP_ID_VALUE, GUID);
        assertSame(retValue, ASSESSMENT);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentByGuidEntityNotFound() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID))
            .thenReturn(Optional.empty());        
        service.getAssessmentByGuid(APP_ID_VALUE, GUID);
    }
        
    @Test
    public void getAssessmentByIdentifier() {
        when(mockDao.getAssessment(APP_ID_VALUE, IDENTIFIER, REVISION_VALUE))
            .thenReturn(Optional.of(ASSESSMENT));        
        Assessment retValue = service.getAssessmentById(APP_ID_VALUE, IDENTIFIER, REVISION_VALUE);
        assertSame(retValue, ASSESSMENT);
    }
        
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentByIdentifierEntityNotFound() {
        when(mockDao.getAssessment(APP_ID_VALUE, IDENTIFIER, REVISION_VALUE))
            .thenReturn(Optional.empty());        
        service.getAssessmentById(APP_ID_VALUE, IDENTIFIER, REVISION_VALUE);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "offsetBy must be positive integer")
    public void getAssessmentByIdentifierBadRevision() {
        service.getAssessmentById(APP_ID_VALUE, IDENTIFIER, -2);
    }
    
    @Test
    public void getLatestAssessment() {
        Assessment rev = AssessmentTest.createAssessment();
        rev.setRevision(2);
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev), 2);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(page);
        
        Assessment retValue = service.getLatestAssessment(APP_ID_VALUE, IDENTIFIER);
        assertSame(retValue, rev);
    }
        
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getLatestAssessmentEntityNotFound() {
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);
        
        service.getLatestAssessment(APP_ID_VALUE, IDENTIFIER);
    }
    
    @Test
    public void getAssessmentRevisions() {
        Assessment rev1 = AssessmentTest.createAssessment();
        Assessment rev2 = AssessmentTest.createAssessment();
        Assessment rev3 = AssessmentTest.createAssessment();
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev3, rev2, rev1), 10);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 10, 25, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = service.getAssessmentRevisionsById(
                APP_ID_VALUE, IDENTIFIER, 10, 25, true);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getItems().size(), 3);
        assertEquals(retValue.getRequestParams().get("identifier"), IDENTIFIER);
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 25);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsEntityNotFound() {
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 10, 25, true)).thenReturn(EMPTY_LIST);
        
        service.getAssessmentRevisionsById(APP_ID_VALUE, IDENTIFIER, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = IDENTIFIER_REQUIRED)
    public void getAssessmentRevisionsNoIdentifier() {
        service.getAssessmentRevisionsById(APP_ID_VALUE, null, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = OFFSET_BY_CANNOT_BE_NEGATIVE)
    public void getAssessmentRevisionsNegativeOffsetBy() {
        service.getAssessmentRevisionsById(APP_ID_VALUE, IDENTIFIER, -10, 25, true);
    }
        
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsPageSizeUnderMin() {
        service.getAssessmentRevisionsById(APP_ID_VALUE, IDENTIFIER, 10, 1, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsPageSizeOverMax() {
        service.getAssessmentRevisionsById(APP_ID_VALUE, IDENTIFIER, 10, 10000, true);
    }

    @Test
    public void getAssessmentRevisionsByGuid() {
        Assessment rev1 = AssessmentTest.createAssessment();
        Assessment rev2 = AssessmentTest.createAssessment();
        Assessment rev3 = AssessmentTest.createAssessment();
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev3, rev2, rev1), 10);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 10, 25, true)).thenReturn(page);
        
        Assessment assessment = AssessmentTest.createAssessment();
        // identifier is the correct identifier already
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        PagedResourceList<Assessment> retValue = service.getAssessmentRevisionsByGuid(
                APP_ID_VALUE, GUID, 10, 25, true);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getItems().size(), 3);
        assertEquals(retValue.getRequestParams().get("guid"), GUID);
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 25);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsByGuidEntityNotFound() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.empty());
        
        service.getAssessmentRevisionsByGuid(APP_ID_VALUE, GUID, 10, 25, true);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsByGuidRevisionsNotFound() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 10, 25, true)).thenReturn(page);

        // This exists, but there are no revisions... this is pathological.
        Assessment assessment = AssessmentTest.createAssessment();
        // identifier is the correct identifier already
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(APP_ID_VALUE, GUID, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = OFFSET_BY_CANNOT_BE_NEGATIVE)
    public void getAssessmentRevisionsByGuidNegativeOffsetBy() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(APP_ID_VALUE, GUID, -10, 25, true);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsByGuidPageSizeUnderMin() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(APP_ID_VALUE, GUID, 10, 1, true);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsByGuidPageSizeOverMax() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(APP_ID_VALUE, GUID, 10, 10000, true);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void publishAssessment() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        
        Optional<Assessment> first = Optional.of(AssessmentTest.createAssessment());
        Optional<Assessment> second = Optional.of(AssessmentTest.createAssessment());
        // We load this object twice, persisting in two different app contexts.
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(first, second);
        
        when(mockDao.publishAssessment(any(), any(), any())).thenReturn(ASSESSMENT);
        
        // Assume no published versions
        when(mockDao.getAssessmentRevisions(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);
        
        Assessment retValue = service.publishAssessment(APP_ID_VALUE, GUID);
        assertSame(retValue, ASSESSMENT);
        
        verify(mockDao).publishAssessment(eq(APP_ID_VALUE), assessmentCaptor.capture(), assessmentCaptor.capture());
        
        Assessment original = assessmentCaptor.getAllValues().get(0);
        Assessment assessmentToPublish = assessmentCaptor.getAllValues().get(1);
        
        assertEquals(original.getOriginGuid(), assessmentToPublish.getGuid());
        assertNull(assessmentToPublish.getOriginGuid());
        assertEquals(assessmentToPublish.getGuid(), GUID);
        assertEquals(assessmentToPublish.getRevision(), 1);
        assertNull(assessmentToPublish.getOriginGuid());
        assertEquals(assessmentToPublish.getOwnerId(), APP_ID_VALUE + ":" + OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void publishAssessmentCallerUnauthorized() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("notTheOwnerId")).build());
        
        service.publishAssessment(APP_ID_VALUE, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void publishAssessmentEntityNotFound() {
        service.publishAssessment(APP_ID_VALUE, GUID);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void publishAssessmentPriorPublishedVersion() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(existing));

        // Same as the happy path version, but this time there is a revision in the
        // shared library
        Assessment revision = AssessmentTest.createAssessment();
        revision.setRevision(10);
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(revision), 1);
        when(mockDao.getAssessmentRevisions(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, 1, true)).thenReturn(page, page);        
        
        service.publishAssessment(APP_ID_VALUE, GUID);
        
        verify(mockDao).publishAssessment(eq(APP_ID_VALUE), assessmentCaptor.capture(), assessmentCaptor.capture());
        
        Assessment assessmentToPublish = assessmentCaptor.getAllValues().get(1);
        assertEquals(assessmentToPublish.getRevision(), 11);
    }
        
    @Test
    public void importAssessment() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID)).thenReturn(Optional.of(sharedAssessment));
        
        Assessment localAssessment = AssessmentTest.createAssessment();
        localAssessment.setRevision(2);
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(localAssessment), 1));
        
        when(mockDao.saveAssessment(APP_ID_VALUE, sharedAssessment)).thenReturn(sharedAssessment);
        
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);
        
        Assessment retValue = service.importAssessment(APP_ID_VALUE, OWNER_ID, GUID);
        assertSame(retValue, sharedAssessment);
        assertEquals(retValue.getRevision(), 3); // next higher than 2
        assertEquals(retValue.getOriginGuid(), GUID);
        assertEquals(retValue.getOwnerId(), OWNER_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "ownerId parameter is required")
    public void importAssessmentOwnerGuidMissing() {
        service.importAssessment(APP_ID_VALUE, "  ", GUID);   
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void importAssessmentCallerUnauthorized() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("notTheOwnerId")).build());

        service.importAssessment(APP_ID_VALUE, OWNER_ID, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void importAssessmentNotFoundException() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        service.importAssessment(APP_ID_VALUE, OWNER_ID, GUID);
    }
    
    @Test
    public void importAssessmentPriorImportedVersion() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_STUDY_ID_STRING, GUID)).thenReturn(Optional.of(sharedAssessment));
        when(mockDao.saveAssessment(APP_ID_VALUE, sharedAssessment)).thenReturn(sharedAssessment);
        
        Assessment localAssessment = AssessmentTest.createAssessment();
        localAssessment.setRevision(3);
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(localAssessment), 1));
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(localAssessment), 1);
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(page);
        
        Assessment retValue = service.importAssessment(APP_ID_VALUE, OWNER_ID, GUID);
        assertSame(retValue, sharedAssessment);
        assertEquals(retValue.getRevision(), 4);
        assertEquals(retValue.getOriginGuid(), GUID);
        assertEquals(retValue.getOwnerId(), OWNER_ID);        
    }
        
    @Test
    public void deleteAssessment() {
        Assessment assessment = new Assessment();
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        service.deleteAssessment(APP_ID_VALUE, GUID);
        
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
        assertTrue(assessment.isDeleted());
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test
    public void deleteAssessmentEntityNotFound() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.empty());
        
        try {
            service.deleteAssessment(APP_ID_VALUE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockDao, never()).saveAssessment(any(), any());
    }
    
    @Test
    public void deleteAssessmentEntityNotFoundOnLogicalAssessment() {
        Assessment assessment = new Assessment();
        assessment.setDeleted(true);
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        
        try {
            service.deleteAssessment(APP_ID_VALUE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockDao, never()).saveAssessment(any(), any());
    }
    
    @Test
    public void deleteAssessmentPermanently() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(ASSESSMENT));
        
        service.deleteAssessmentPermanently(APP_ID_VALUE, GUID);
        
        verify(mockDao).deleteAssessment(APP_ID_VALUE, ASSESSMENT);
    }
    
    @Test
    public void deleteAssessmentPermanentlyEntityNotFoundIsQuite() {
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.empty());
        service.deleteAssessmentPermanently(APP_ID_VALUE, GUID);
        verify(mockDao, never()).deleteAssessment(any(), any());
    }
    
    // Ownership tests
    
    // ---- CREATE
    
    @Test
    public void globalUserCanCreateWithOwnerId() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        BridgeUtils.setRequestContext(NULL_INSTANCE); // no substudies
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        
        service.createAssessment(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void globalUserCreateWithInvalidOwnerId() {
        // There is no successful lookup of the stubstudy
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void scopedUserCanCreateWithOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app2");
        
        service.createAssessment(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*ownerId cannot be missing, null, or blank.*")
    public void scopedUserCannotCreateWithoutOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(null);
        
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true))
            .thenReturn(EMPTY_LIST);
        
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void scopedUserCannotCreateWithInvalidOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app3");
        
        service.createAssessment(APP_ID_VALUE, assessment);
    }
    
    // ---- CREATE REVISION
    
    @Test
    public void globalUserCanCreateRevisionWithOwnerId() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);

        Assessment assessment = AssessmentTest.createAssessment();
        
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void globalUserCreateRevisionWithInvalidOwnerId() {
        // There is no successful lookup of the stubstudy
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }
    
    @Test
    public void scopedUserCanCreateRevisionWithOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app2");
        
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
        verify(mockDao).saveAssessment(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*ownerId cannot be missing, null, or blank.*")
    public void scopedUserCannotCreateRevisionWithoutOwnerId() {
        // This really has no impact on anything because ownerId is null.
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(null);
        
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void scopedUserCannotCreateRevisionWithInvalidOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app3");
        
        service.createAssessmentRevision(APP_ID_VALUE, assessment);
    }
    
    // ---- PUBLISH
    // Publication uses a saved object so it should always have an owner id. We're only
    // checking that a global user can publish anything, and a scoped user can only 
    // publish what they control (no tests that the value itself is null).
    
    @Test
    public void globalUserCanPublishWithOwnerId() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);

        Assessment assessment = AssessmentTest.createAssessment();
        
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        when(mockDao.getAssessmentRevisions(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.publishAssessment(APP_ID_VALUE, GUID);
        
        verify(mockDao).publishAssessment(eq(APP_ID_VALUE), any(), assessmentCaptor.capture());
        assertEquals(assessmentCaptor.getValue().getOwnerId(), APP_ID_VALUE + ":" + OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void globalUserPublishWithInvalidOwnerId() {
        // There is no successful lookup of the stubstudy
        
        Assessment assessment = AssessmentTest.createAssessment();
        
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.publishAssessment(APP_ID_VALUE, GUID);
    }

    @Test
    public void scopedUserCanPublishWithOwnerId() {
        when(mockSubstudyService.getSubstudy(APP_AS_STUDY_ID, OWNER_ID, false))
            .thenReturn(mockSubstudy);
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app1");
        
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        when(mockDao.getAssessmentRevisions(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.publishAssessment(APP_ID_VALUE, GUID);
        
        verify(mockDao).publishAssessment(eq(APP_ID_VALUE), any(), assessmentCaptor.capture());
        assertEquals(assessmentCaptor.getValue().getOwnerId(), "appId:app1");
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = "Assessment must be associated to one of the caller’s organizations.")
    public void scopedUserCannotPublishWithInvalidOwnerId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("app1", "app2")).build());
        when(mockDao.getAssessmentRevisions(APP_ID_VALUE, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("app3");
        when(mockDao.getAssessment(APP_ID_VALUE, GUID)).thenReturn(Optional.of(assessment));
        when(mockDao.getAssessmentRevisions(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.publishAssessment(APP_ID_VALUE, GUID);
    }

    private void addMarkupToSensitiveFields(Assessment assessment) {
        assessment.setTitle("<object>ᚷᛁᚠ᛫ᚻᛖ᛫ᚹᛁᛚᛖ᛫ᚠᚩᚱ᛫ᛞᚱᛁᚻᛏᚾᛖ᛫ᛞᚩᛗᛖᛋ᛫ᚻᛚᛇᛏᚪᚾ᛬");
        assessment.setSummary("<script></script>");
        assessment.setValidationStatus("some text</script>");
        assessment.setNormingStatus("However this is <b>acceptable</b>.");        
        assessment.setTags(ImmutableSet.of("<scriopt>Мон ярсан суликадо</script>"));
        Map<String, Set<String>> fields = new HashMap<>();
        fields.put("<b>This</b>", ImmutableSet.of("<tag>Not right at all</tag>"));
        assessment.setCustomizationFields(fields);
    }

    private void assertMarkupRemoved(Assessment assessment) {
        assertEquals(assessment.getTitle(), "ᚷᛁᚠ᛫ᚻᛖ᛫ᚹᛁᛚᛖ᛫ᚠᚩᚱ᛫ᛞᚱᛁᚻᛏᚾᛖ᛫ᛞᚩᛗᛖᛋ᛫ᚻᛚᛇᛏᚪᚾ᛬");
        assertEquals(assessment.getSummary(), "");
        assertEquals(assessment.getValidationStatus(), "some text");
        assertEquals(assessment.getNormingStatus(), "However this is <b>acceptable</b>.");
        assertEquals(Iterables.getFirst(assessment.getTags(), null), "Мон ярсан суликадо");
        Map.Entry<String, Set<String>> entry = Iterables.getFirst(assessment.getCustomizationFields().entrySet(), null);
        String key = entry.getKey();
        String value = Iterables.getFirst(entry.getValue(), null);
        assertEquals(key, "This");
        assertEquals(value, "Not right at all");
    }    
}
