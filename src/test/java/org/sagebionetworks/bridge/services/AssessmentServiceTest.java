package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.NONPOSITIVE_REVISION_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.INFO1;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.UNIVERSAL;
import static org.sagebionetworks.bridge.services.AssessmentService.IDENTIFIER_REQUIRED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.dao.AssessmentResourceDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.models.organizations.Organization;

public class AssessmentServiceTest extends Mockito {
    private static final String NEW_IDENTIFIER = "oneNewId";
    private static final int REVISION_VALUE = 3;
    private static final PagedResourceList<Assessment> EMPTY_LIST = new PagedResourceList<>(ImmutableList.of(), 0);
    private static final Assessment ASSESSMENT = new Assessment();
    
    @Mock
    AssessmentDao mockDao;
    
    @Mock
    AssessmentResourceDao mockResourceDao;
    
    @Mock
    AssessmentConfigService mockConfigService;
    
    @Mock
    OrganizationService mockOrganizationService;
    
    @Mock
    Organization mockOrganization;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;
    
    @Captor
    ArgumentCaptor<AssessmentConfig> configCaptor;
    
    @InjectMocks
    @Spy
    AssessmentService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        when(service.generateGuid()).thenReturn(GUID);
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        when(service.getPageSize()).thenReturn(5);
        
        // The default assumption is that you are in the correct organization (or else
        // you are an administrator). 
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getAssessments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(ASSESSMENT), 100);
        when(mockDao.getAssessments(TEST_APP_ID, TEST_OWNER_ID, 10, 26, STRING_TAGS, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = service.getAssessments(
                TEST_APP_ID, TEST_OWNER_ID, 10, 26, STRING_TAGS, true);
        
        assertEquals(retValue.getItems().get(0), ASSESSMENT);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 26);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
        assertEquals(retValue.getRequestParams().get("tags"), STRING_TAGS);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getAssessmentsNegativeOffsetBy() {
        service.getAssessments(TEST_APP_ID, TEST_OWNER_ID, -100, 25, null, false);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentsPageSizeUnderMin() {
        service.getAssessments(TEST_APP_ID, TEST_OWNER_ID, 0, 1, null, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentsPageSizeOverMax() {
        service.getAssessments(TEST_APP_ID, TEST_OWNER_ID, 0, 100000, null, false);
    }
    
    @Test
    public void createAssessment() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
                .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(true); // can't do this, it's reset
        
        service.createAssessment(TEST_APP_ID, assessment);
        
        verify(mockDao).createAssessment(eq(TEST_APP_ID), eq(assessment), configCaptor.capture());
        
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getOwnerId(), TEST_OWNER_ID);
        // Same timestamp on create
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), CREATED_ON);
        assertFalse(assessment.isDeleted());
        
        AssessmentConfig config = configCaptor.getValue();
        assertEquals(config.getCreatedOn(), CREATED_ON);
        assertEquals(config.getModifiedOn(), CREATED_ON);
        assertNotNull(config.getConfig());
    }
    
    @Test
    public void createAssessmentAdjustsOsNameAlias() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOsName("Both");
        
        service.createAssessment(TEST_APP_ID, assessment);
        
        assertEquals(assessment.getOsName(), UNIVERSAL);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentUnauthorized() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.createAssessment(TEST_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createAssessmentAlreadyExists() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(false);
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(assessment), 1));
        
        service.createAssessment(TEST_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*identifier cannot be null or blank.*")
    public void createAssessmentInvalid() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(null);
        
        service.createAssessment(TEST_APP_ID, assessment);
    }
    
    @Test
    public void createAssessmentScrubsMarkup() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(EMPTY_LIST);
        
        Assessment assessment = AssessmentTest.createAssessment();
        addMarkupToSensitiveFields(assessment);

        service.createAssessment(TEST_APP_ID, assessment);
        
        assertMarkupRemoved(assessment);
    }

    @Test
    public void createAssessmentRevision() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
                .thenReturn(mockOrganization);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.of(AssessmentTest.createAssessment()));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(null);
        assessment.setGuid(null);
        assessment.setDeleted(true); // can't do this, it's reset
        
        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
        
        verify(mockDao).createAssessment(eq(TEST_APP_ID), eq(assessment), configCaptor.capture());
        
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getIdentifier(), IDENTIFIER);
        assertEquals(assessment.getOwnerId(), TEST_OWNER_ID);
        // same timestamp on creation
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), CREATED_ON);
        assertFalse(assessment.isDeleted());
        
        AssessmentConfig config = configCaptor.getValue();
        assertEquals(config.getCreatedOn(), CREATED_ON);
        assertEquals(config.getModifiedOn(), CREATED_ON);
        assertNotNull(config.getConfig());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentRevisionUnauthorized() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("studyD")).build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(TEST_OWNER_ID);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void createAssessmentRevisionEntityNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid(null);
        assessment.setDeleted(false);
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessmentRevisions(any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
    
        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
    }

    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*identifier cannot be null or blank.*")
    public void createAssessmentRevisionInvalid() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.of(new Assessment()));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(null);
        
        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
    }

    @Test
    public void createAssessmentRevisionScrubsMarkup() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        addMarkupToSensitiveFields(assessment);

        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
        
        assertMarkupRemoved(assessment);
    }

    @Test
    public void updateAssessment() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        // Fill out only the fields needed to pass validation, leaving the rest to be
        // filled in by the existing assessment
        Assessment assessment = new Assessment();
        assessment.setGuid(GUID); // this always gets set in the controller
        assessment.setTitle("title");
        assessment.setOsName(ANDROID);
        when(mockDao.updateAssessment(TEST_APP_ID, assessment)).thenReturn(assessment);
        
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        Assessment retValue = service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        assertSame(retValue, assessment);
        
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getOwnerId(), TEST_OWNER_ID);
        assertEquals(retValue.getOriginGuid(), "originGuid");
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        
        verify(mockDao).updateAssessment(TEST_APP_ID, retValue);
    }
    
    @Test
    public void updateAssessmentAdjustsOsNameAlias() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOsName("Both");
        when(mockDao.updateAssessment(TEST_APP_ID, assessment)).thenReturn(assessment);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        
        assertEquals(assessment.getOsName(), UNIVERSAL);
    }    

    @Test
    public void updateAssessmentSomeFieldsImmutable() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.of(existing));
        when(mockDao.updateAssessment(eq(TEST_APP_ID), any()))
            .thenReturn(existing);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier("junk");
        assessment.setOwnerId("junk");
        assessment.setOriginGuid("junk");
        assessment.setCreatedOn(CREATED_ON.minusDays(1));
        assessment.setModifiedOn(MODIFIED_ON.minusDays(1));
        assessment.setDeleted(false);
        
        Assessment retValue = service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getOwnerId(), TEST_OWNER_ID);
        assertEquals(retValue.getOriginGuid(), "originGuid");
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        
        verify(mockDao).updateAssessment(eq(TEST_APP_ID), assessmentCaptor.capture());
        Assessment saved = assessmentCaptor.getValue();
        assertEquals(saved.getIdentifier(), IDENTIFIER);
        assertEquals(saved.getOwnerId(), TEST_OWNER_ID);
        assertEquals(saved.getOriginGuid(), "originGuid");
        assertEquals(saved.getCreatedOn(), CREATED_ON);
        assertEquals(saved.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAssessmentEntityNotFound() {
        Assessment assessment = new Assessment();
        
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment.getGuid()))
            .thenReturn(Optional.empty());
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
    }
    
    @Test
    public void updateAssessmentCanDelete() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        assertTrue(assessment.isDeleted());
    }

    @Test
    public void updateAssessmentCanUndelete() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        assertFalse(assessment.isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAssessmentCannotUpdatedDeleted() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
    }
    
    @Test
    public void updateAssessmentScrubsMarkup() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        addMarkupToSensitiveFields(assessment);

        when(mockDao.getAssessment(eq(TEST_APP_ID), eq(TEST_OWNER_ID), any()))
            .thenReturn(Optional.of(assessment));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
        
        assertMarkupRemoved(assessment);
    }
   
    @Test
    public void updateSharedAssessment() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(Organization.create());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOriginGuid("unusualGuid");
        existing.setDeleted(false);
        existing.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
        
        verify(mockDao).updateAssessment(SHARED_APP_ID, assessment);
        
        assertEquals(assessment.getIdentifier(), IDENTIFIER);
        assertEquals(TEST_APP_ID + ":" + TEST_OWNER_ID, assessment.getOwnerId());
        assertEquals(assessment.getOriginGuid(), "unusualGuid");
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test
    public void updateSharedAssessmentAdjustsOsNameAlias() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(Organization.create());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOsName("Both");
        service.updateSharedAssessment(TEST_APP_ID, assessment);
        
        assertEquals(assessment.getOsName(), UNIVERSAL);
    }
    
    @Test
    public void updateSharedAssessmentSomeFieldsImmutable() {
        String ownerIdInShared = TEST_APP_ID + ":" + TEST_OWNER_ID;
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(Organization.create());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(ownerIdInShared);
        existing.setDeleted(false);
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        when(mockDao.updateAssessment(eq(SHARED_APP_ID), any()))
                .thenReturn(existing);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier("junk");
        assessment.setOwnerId("junk");
        assessment.setOriginGuid("junk");
        assessment.setCreatedOn(CREATED_ON.minusDays(1));
        assessment.setModifiedOn(MODIFIED_ON.minusDays(1));
        assessment.setDeleted(false);
        
        Assessment retValue = service.updateSharedAssessment(TEST_APP_ID, assessment);
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getOwnerId(), ownerIdInShared);
        assertEquals(retValue.getOriginGuid(), "originGuid");
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        
        verify(mockDao).updateAssessment(eq(SHARED_APP_ID), assessmentCaptor.capture());
        Assessment saved = assessmentCaptor.getValue();
        assertEquals(saved.getIdentifier(), IDENTIFIER);
        assertEquals(saved.getOwnerId(), ownerIdInShared);
        assertEquals(saved.getOriginGuid(), "originGuid");
        assertEquals(saved.getCreatedOn(), CREATED_ON);
        assertEquals(saved.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentUnauthorizedApp() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId("wrong-app:" + TEST_OWNER_ID);
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentUnauthorizedOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId(TEST_APP_ID + ":wrong-org");
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
    }
    
    @Test
    public void updateSharedAssessmentForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN))
                .withCallerAppId(TEST_APP_ID).build());
        when(mockOrganizationService.getOrganization(
                TEST_APP_ID, TEST_OWNER_ID)).thenReturn(Organization.create());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
        
        verify(mockDao).updateAssessment(SHARED_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSharedAssessmentDeletedEntityNotFound() {
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.empty());
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSharedAssessmentLogicallyDeletedEntityNotFound() {
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID))
            .thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.updateSharedAssessment(TEST_APP_ID, assessment);
    }

    @Test
    public void updateSharedAssessmentCanDelete() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(true);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        existing.setDeleted(false);
        
        when(mockDao.getAssessment(SHARED_APP_ID, null, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        when(mockDao.updateAssessment(TEST_APP_ID, assessment)).thenReturn(assessment);
        
        service.updateSharedAssessment(TEST_APP_ID, assessment);
        assertTrue(assessment.isDeleted());
    }
    
    @Test
    public void updateSharedAssessmentCanUndelete() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setDeleted(false);
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        existing.setDeleted(true);
        
        when(mockDao.getAssessment(SHARED_APP_ID, null, assessment.getGuid()))
            .thenReturn(Optional.of(existing));
        when(mockDao.updateAssessment(TEST_APP_ID, assessment)).thenReturn(assessment);
        
        service.updateSharedAssessment(TEST_APP_ID, assessment);
        assertFalse(assessment.isDeleted());
    }
    
    @Test
    public void getAssessmentByGuid() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.of(ASSESSMENT));        
        Assessment retValue = service.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID);
        assertSame(retValue, ASSESSMENT);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentByGuidEntityNotFound() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(Optional.empty());        
        service.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID);
    }
        
    @Test
    public void getAssessmentByIdentifier() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, REVISION_VALUE))
            .thenReturn(Optional.of(ASSESSMENT));        
        Assessment retValue = service.getAssessmentById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, REVISION_VALUE);
        assertSame(retValue, ASSESSMENT);
    }
        
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentByIdentifierEntityNotFound() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, REVISION_VALUE))
            .thenReturn(Optional.empty());        
        service.getAssessmentById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, REVISION_VALUE);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = NONPOSITIVE_REVISION_ERROR)
    public void getAssessmentByIdentifierBadRevision() {
        service.getAssessmentById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, -2);
    }
    
    @Test
    public void getLatestAssessment() {
        Assessment rev = AssessmentTest.createAssessment();
        rev.setRevision(2);
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev), 2);
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 0, 1, false)).thenReturn(page);
        
        Assessment retValue = service.getLatestAssessment(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER);
        assertSame(retValue, rev);
    }
        
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getLatestAssessmentEntityNotFound() {
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 0, 1, false)).thenReturn(EMPTY_LIST);
        
        service.getLatestAssessment(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER);
    }
    
    @Test
    public void getAssessmentRevisions() {
        Assessment rev1 = AssessmentTest.createAssessment();
        Assessment rev2 = AssessmentTest.createAssessment();
        Assessment rev3 = AssessmentTest.createAssessment();
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev3, rev2, rev1), 10);
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = service.getAssessmentRevisionsById(
                TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getItems().size(), 3);
        assertEquals(retValue.getRequestParams().get("identifier"), IDENTIFIER);
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 25);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsEntityNotFound() {
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true)).thenReturn(EMPTY_LIST);
        
        service.getAssessmentRevisionsById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = IDENTIFIER_REQUIRED)
    public void getAssessmentRevisionsNoIdentifier() {
        service.getAssessmentRevisionsById(TEST_APP_ID, TEST_OWNER_ID, null, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getAssessmentRevisionsNegativeOffsetBy() {
        service.getAssessmentRevisionsById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, -10, 25, true);
    }
        
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsPageSizeUnderMin() {
        service.getAssessmentRevisionsById(TEST_APP_ID,TEST_OWNER_ID,  IDENTIFIER, 10, 1, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsPageSizeOverMax() {
        service.getAssessmentRevisionsById(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 10000, true);
    }

    @Test
    public void getAssessmentRevisionsByGuid() {
        Assessment rev1 = AssessmentTest.createAssessment();
        Assessment rev2 = AssessmentTest.createAssessment();
        Assessment rev3 = AssessmentTest.createAssessment();
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(rev3, rev2, rev1), 10);
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true)).thenReturn(page);
        
        Assessment assessment = AssessmentTest.createAssessment();
        // identifier is the correct identifier already
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        PagedResourceList<Assessment> retValue = service.getAssessmentRevisionsByGuid(
                TEST_APP_ID, TEST_OWNER_ID, GUID, 10, 25, true);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
        assertEquals(retValue.getItems().size(), 3);
        assertEquals(retValue.getRequestParams().get("guid"), GUID);
        assertEquals(retValue.getRequestParams().get("offsetBy"), 10);
        assertEquals(retValue.getRequestParams().get("pageSize"), 25);
        assertTrue((Boolean)retValue.getRequestParams().get("includeDeleted"));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsByGuidEntityNotFound() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.empty());
        
        service.getAssessmentRevisionsByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID, 10, 25, true);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAssessmentRevisionsByGuidRevisionsNotFound() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 10, 25, true)).thenReturn(page);

        // This exists, but there are no revisions... this is pathological.
        Assessment assessment = AssessmentTest.createAssessment();
        // identifier is the correct identifier already
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID, 10, 25, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getAssessmentRevisionsByGuidNegativeOffsetBy() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID, -10, 25, true);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsByGuidPageSizeUnderMin() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID, 10, 1, true);
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getAssessmentRevisionsByGuidPageSizeOverMax() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        service.getAssessmentRevisionsByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID, 10, 10000, true);
    }
    
    @Test
    public void publishAssessment() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment existing =  AssessmentTest.createAssessment();
        // Change some of these values from what they should be set to on the published object.
        existing.setGuid("oldGuid");
        existing.setRevision(-1);
        existing.setOriginGuid(null);
        existing.setOwnerId(TEST_OWNER_ID);
        existing.setVersion(-1L);        
        
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, "oldGuid")).thenReturn(Optional.of(existing));
        when(mockDao.publishAssessment(any(), any(), any(), any())).thenReturn(ASSESSMENT);
        
        // Assume no published versions
        when(mockDao.getAssessmentRevisions(SHARED_APP_ID, null, IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);
        when(mockConfigService.getAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, "oldGuid")).thenReturn(new AssessmentConfig());
        
        Assessment retValue = service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, "oldGuid");
        assertSame(retValue, ASSESSMENT);

        verify(mockDao).publishAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), 
                assessmentCaptor.capture(), any(AssessmentConfig.class));
        
        Assessment original = assessmentCaptor.getAllValues().get(0);
        Assessment assessmentToPublish = assessmentCaptor.getAllValues().get(1);
        
        assertEquals(original.getOriginGuid(), assessmentToPublish.getGuid());
        assertNull(assessmentToPublish.getOriginGuid());
        assertEquals(assessmentToPublish.getGuid(), GUID); // has been reset
        assertEquals(assessmentToPublish.getRevision(), 1);
        assertNull(assessmentToPublish.getOriginGuid());
        assertEquals(assessmentToPublish.getOwnerId(), TEST_APP_ID + ":" + TEST_OWNER_ID);
        assertEquals(assessmentToPublish.getVersion(), 0);
        // verify that a fuller copy also occurred
        assertEquals(assessmentToPublish.getTitle(), existing.getTitle());
        assertEquals(assessmentToPublish.getTags(), existing.getTags());
    }
    
    @Test
    public void publishAssessmentWithNewIdentifier() { 
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(mockOrganization);
    
        Assessment existing =  AssessmentTest.createAssessment();
    
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, "oldGuid")).thenReturn(Optional.of(existing));
        when(mockDao.publishAssessment(any(), any(), any(), any())).thenReturn(ASSESSMENT);
    
        // Assume no published versions
        when(mockDao.getAssessmentRevisions(SHARED_APP_ID, null, NEW_IDENTIFIER, 0, 1, true)).thenReturn(EMPTY_LIST);
    
        Assessment retValue = service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, NEW_IDENTIFIER, "oldGuid");
        assertSame(retValue, ASSESSMENT);

        verify(mockDao).publishAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), 
                assessmentCaptor.capture(), configCaptor.capture());
    
        Assessment assessmentToPublish = assessmentCaptor.getAllValues().get(1);
    
        assertEquals(assessmentToPublish.getIdentifier(), NEW_IDENTIFIER);
        assertEquals(assessmentToPublish.getRevision(), 1);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void publishAssessmentCallerUnauthorized() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notTheOwnerId").build());
        
        service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void publishAssessmentEntityNotFound() {
        service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }

    @Test
    public void publishAssessmentPriorPublishedVersion() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment local = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(local));
        
        AssessmentConfig localConfig = new AssessmentConfig();
        when(mockConfigService.getAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(localConfig);
        
        // Same as the happy path version, but this time there is a revision in the
        // shared library
        Assessment revision = AssessmentTest.createAssessment();
        revision.setRevision(10);
        revision.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(revision), 1);
        when(mockDao.getAssessmentRevisions(SHARED_APP_ID, null, IDENTIFIER, 0, 1, true)).thenReturn(page);        
        
        service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
        
        verify(mockDao).publishAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), 
                assessmentCaptor.capture(), configCaptor.capture());
        
        Assessment assessmentToPublish = assessmentCaptor.getAllValues().get(1);
        assertEquals(assessmentToPublish.getRevision(), 11);
        
        assertSame(localConfig, configCaptor.getValue());
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = ".*Assessment exists in shared library.*")
    public void publishAssessmentPriorPublishedVersionDifferentOwner() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment local = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(local));
        
        // Same as the happy path version, but this time there is a revision in the
        // shared library
        Assessment revision = AssessmentTest.createAssessment();
        revision.setRevision(10);
        revision.setOwnerId("otherApp:" + TEST_OWNER_ID);
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(revision), 1);
        when(mockDao.getAssessmentRevisions(SHARED_APP_ID, null, IDENTIFIER, 0, 1, true)).thenReturn(page);        
        
        service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }

    @Test
    public void importAssessment() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        sharedAssessment.setGuid("sharedGuid");
        when(mockDao.getAssessment(SHARED_APP_ID, null, "sharedGuid")).thenReturn(Optional.of(sharedAssessment));
        
        AssessmentConfig sharedConfig = new AssessmentConfig();
        when(mockConfigService.getSharedAssessmentConfig(SHARED_APP_ID, GUID)).thenReturn(sharedConfig);
        
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));

        when(mockDao.importAssessment(TEST_APP_ID, sharedAssessment, sharedConfig))
            .thenReturn(sharedAssessment);
        
        Assessment retValue = service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
        assertSame(retValue, sharedAssessment);
        assertEquals(retValue.getRevision(), 1);
        assertEquals(retValue.getOriginGuid(), "sharedGuid");
        assertEquals(retValue.getOwnerId(), TEST_OWNER_ID);
        
        verify(mockDao).importAssessment(TEST_APP_ID, sharedAssessment, sharedConfig);
    }

    @Test
    public void importAssessmentWithNewIdentifier() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));

        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, NEW_IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, NEW_IDENTIFIER, GUID);
        
        verify(mockDao).importAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), configCaptor.capture());
        
        // This is at revision 1 because in this test, the new identifier is indeed new.
        assertEquals(assessmentCaptor.getValue().getIdentifier(), NEW_IDENTIFIER);
        assertEquals(assessmentCaptor.getValue().getRevision(), 1);
    }
    
    @Test
    public void importAssessmentWithAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));

        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        service.importAssessment(TEST_APP_ID, "new-owner-id", null, GUID);
    }
    
    @Test
    public void importAssessmentWithSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));

        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        
        service.importAssessment(TEST_APP_ID, "new-owner-id", null, GUID);
        
        verify(mockDao).importAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), any());
        assertEquals(assessmentCaptor.getValue().getOwnerId(), "new-owner-id");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void importAssessmentWithAdminOrgNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));

        when(mockDao.getAssessmentRevisions(TEST_APP_ID, TEST_OWNER_ID, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        when(mockOrganizationService.getOrganization(TEST_APP_ID, "new-owner-id"))
            .thenThrow(new EntityNotFoundException(Organization.class));
        
        service.importAssessment(TEST_APP_ID, "new-owner-id", null, GUID);
        
        verify(mockDao).importAssessment(eq(TEST_APP_ID), assessmentCaptor.capture(), any());
        assertEquals(assessmentCaptor.getValue().getOwnerId(), "new-owner-id");
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "ownerId parameter is required")
    public void importAssessmentFailsWithNoOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(null).build());
        
        service.importAssessment(TEST_APP_ID, null, null, GUID);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "ownerId parameter is required")
    public void importAssessmentOwnerGuidMissing() {
        service.importAssessment(TEST_APP_ID, "  ", null, GUID);   
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void importAssessmentCallerUnauthorized() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notTheOwnerId").build());

        service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void importAssessmentNotFoundException() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }
    
    @Test
    public void importAssessmentPriorImportedVersion() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        sharedAssessment.setGuid("sharedGuid");
        when(mockDao.getAssessment(SHARED_APP_ID, null, "sharedGuid"))
            .thenReturn(Optional.of(sharedAssessment));
        
        AssessmentConfig sharedConfig = new AssessmentConfig();
        when(mockConfigService.getSharedAssessmentConfig(SHARED_APP_ID, GUID))
            .thenReturn(sharedConfig);
        
        Assessment localAssessment = AssessmentTest.createAssessment();
        localAssessment.setRevision(3);
        
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(localAssessment), 1));
        
        when(mockDao.importAssessment(TEST_APP_ID, sharedAssessment, sharedConfig)).thenReturn(sharedAssessment);
        
        Assessment retValue = service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
        assertSame(retValue, sharedAssessment);
        assertEquals(retValue.getRevision(), 4); // 1 higher than 3
        assertEquals(retValue.getOriginGuid(), "sharedGuid");
        assertEquals(retValue.getOwnerId(), TEST_OWNER_ID);
    }
        
    @Test
    public void deleteAssessment() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(Organization.create());
        
        Assessment assessment = new Assessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        service.deleteAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID);
        
        verify(mockDao).updateAssessment(TEST_APP_ID, assessment);
        assertTrue(assessment.isDeleted());
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test
    public void deleteAssessmentEntityNotFound() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.empty());
        
        try {
            service.deleteAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockDao, never()).updateAssessment(any(), any());
    }
    
    @Test
    public void deleteAssessmentEntityNotFoundOnLogicalAssessment() {
        Assessment assessment = new Assessment();
        assessment.setDeleted(true);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(assessment));
        
        try {
            service.deleteAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockDao, never()).updateAssessment(any(), any());
    }
    
    @Test
    public void deleteAssessmentPermanently() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(ASSESSMENT));
        
        service.deleteAssessmentPermanently(TEST_APP_ID, TEST_OWNER_ID, GUID);
        
        verify(mockDao).deleteAssessment(TEST_APP_ID, ASSESSMENT);
    }
    
    @Test
    public void deleteAssessmentPermanentlyEntityNotFoundIsQuite() {
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.empty());
        service.deleteAssessmentPermanently(TEST_APP_ID, TEST_OWNER_ID, GUID);
        verify(mockDao, never()).deleteAssessment(any(), any());
    }
        
    // OWNERSHIP VERIFICATION
    // These are failure cases to verify we are calling AuthUtils.checkOwnership(...)
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentChecksOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        when(mockDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setIdentifier(IDENTIFIER);
        service.createAssessment(TEST_APP_ID, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAssessmentRevisionChecksOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID))
            .thenReturn(mockOrganization);
        
        Assessment existing = AssessmentTest.createAssessment();
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        Assessment assessment = AssessmentTest.createAssessment();
        service.createAssessmentRevision(TEST_APP_ID, TEST_OWNER_ID, GUID, assessment);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateAssessmentChecksOwnership() {
        Assessment assessment = AssessmentTest.createAssessment();
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(assessment.getOwnerId()).build());
        
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        existing.setOwnerId("differentId");
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.updateAssessment(TEST_APP_ID, TEST_OWNER_ID, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void publishAssessmentChecksOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.publishAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void importAssessmentChecksOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        service.importAssessment(TEST_APP_ID, TEST_OWNER_ID, null, GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteAssessmentChecksOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("orgD").build());
        Assessment existing = AssessmentTest.createAssessment();
        existing.setDeleted(false);
        when(mockDao.getAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteAssessment(TEST_APP_ID, TEST_OWNER_ID, GUID);
    }
        
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentChecksOwnership() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        sharedAssessment.setDeleted(false);
        sharedAssessment.setOwnerId("wrongApp:wrongOrg");
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));
        
        service.updateSharedAssessment(TEST_APP_ID, sharedAssessment);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedAssessmentChecksOwnershipWhenFormattedIncorrectly() {
        Assessment sharedAssessment = AssessmentTest.createAssessment();
        sharedAssessment.setDeleted(false);
        sharedAssessment.setOwnerId("A:B:C");
        when(mockDao.getAssessment(SHARED_APP_ID, null, GUID)).thenReturn(Optional.of(sharedAssessment));
        
        service.updateSharedAssessment(TEST_APP_ID, sharedAssessment);
    }
    
    private void addMarkupToSensitiveFields(Assessment assessment) {
        assessment.setTitle("<object>ᚷᛁᚠ᛫ᚻᛖ᛫ᚹᛁᛚᛖ᛫ᚠᚩᚱ᛫ᛞᚱᛁᚻᛏᚾᛖ᛫ᛞᚩᛗᛖᛋ᛫ᚻᛚᛇᛏᚪᚾ᛬");
        assessment.setSummary("<script></script>");
        assessment.setValidationStatus("some text</script>");
        assessment.setNormingStatus("Markup <object></object>can be <b>bold</b>.");        
        assessment.setTags(ImmutableSet.of("<scriopt>Мон ярсан суликадо</script>"));
        
        PropertyInfo info = new PropertyInfo.Builder().withPropName("foo<script></script>")
                .withLabel("foo label<script></script>").withDescription("a description<script></script>")
                .withPropType("string<script></script>").build();
        Map<String, Set<PropertyInfo>> customizationFields = ImmutableMap.of("guid1<script></script>",
                ImmutableSet.of(info));
        assessment.setCustomizationFields(customizationFields);
    }

    private void assertMarkupRemoved(Assessment assessment) {
        assertEquals(assessment.getTitle(), "ᚷᛁᚠ᛫ᚻᛖ᛫ᚹᛁᛚᛖ᛫ᚠᚩᚱ᛫ᛞᚱᛁᚻᛏᚾᛖ᛫ᛞᚩᛗᛖᛋ᛫ᚻᛚᛇᛏᚪᚾ᛬");
        assertEquals(assessment.getSummary(), "");
        assertEquals(assessment.getValidationStatus(), "some text");
        assertEquals(assessment.getNormingStatus(), "Markup can be <b>bold</b>.");
        assertEquals(Iterables.getFirst(assessment.getTags(), null), "Мон ярсан суликадо");
        Map.Entry<String, Set<PropertyInfo>> entry = Iterables.getFirst(assessment.getCustomizationFields().entrySet(), null);
        String key = entry.getKey();
        PropertyInfo value = Iterables.getFirst(entry.getValue(), null);
        assertEquals(key, "guid1");
        assertEquals(value.getPropName(), INFO1.getPropName());
        assertEquals(value.getLabel(), INFO1.getLabel());
        assertEquals(value.getDescription(), INFO1.getDescription());
        assertEquals(value.getPropType(), INFO1.getPropType());
    }    
}