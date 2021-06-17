package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.RESOURCE_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.ResourceList.CATEGORIES;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.MAX_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.MIN_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.assessments.AssessmentTest.createAssessment;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
import org.sagebionetworks.bridge.dao.AssessmentResourceDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;

public class AssessmentResourceServiceTest extends Mockito {
    private static final String UNSANITIZED_STRING = "bad string<script>removeme</script>";
    private static final String SANITIZED_STRING = "bad string";
    
    @Mock
    AssessmentResourceDao mockDao;
    
    @Mock
    AssessmentService mockAssessmentService;
    
    @Captor
    ArgumentCaptor<AssessmentResource> resourceCaptor;
    
    @Captor
    ArgumentCaptor<List<AssessmentResource>> resourceListCaptor;
    
    @Captor
    ArgumentCaptor<List<String>> guidListCaptor;
    
    @InjectMocks
    @Spy
    AssessmentResourceService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        when(service.generateGuid()).thenReturn(GUID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getResources() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(5); // assessment revision = 5
        when(mockDao.getResources(TEST_APP_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(resource), 10));
        
        PagedResourceList<AssessmentResource> retValue = service.getResources(
                TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true);
        assertEquals(retValue.getItems().size(), 1);
        assertTrue(retValue.getItems().get(0).isUpToDate());
        assertSame(retValue.getItems().get(0), resource);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), 10);
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), 40);
        assertEquals(retValue.getRequestParams().get(CATEGORIES), RESOURCE_CATEGORIES);
        assertEquals(retValue.getRequestParams().get(MIN_REVISION), 1);
        assertEquals(retValue.getRequestParams().get(MAX_REVISION), 100);
        assertTrue((Boolean)retValue.getRequestParams().get(INCLUDE_DELETED));
        
        verify(mockAssessmentService).getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID);
        verify(mockDao).getResources(TEST_APP_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true);
    }
    
    @Test
    public void getResourcesNullArguments() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(4); // assessment revision = 5
        when(mockDao.getResources(TEST_APP_ID, ASSESSMENT_ID, null, null, null, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(resource), 10));
        
        PagedResourceList<AssessmentResource> retValue = service.getResources(
                TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, null, null, null, null, null, false);
        assertEquals(retValue.getItems().size(), 1);
        assertFalse(retValue.getItems().get(0).isUpToDate());
        assertSame(retValue.getItems().get(0), resource);
        
        verify(mockAssessmentService).getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID);
        verify(mockDao).getResources(TEST_APP_ID, ASSESSMENT_ID, null, null, null, null, null, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "maxRevision cannot be greater than minRevision")
    public void getResourcesMaxHigherThanMinRevision() {
        service.getResources(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, null, null, null, 3, 2, false);
    }

    @Test
    public void getResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(5); // assessment revision = 5
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(resource));
        
        AssessmentResource retValue = service.getResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, GUID);
        assertTrue(retValue.isUpToDate());
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void getResourceNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.getResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, GUID);
    }
    
    @Test
    public void createResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setGuid(null);
        resource.setCreatedOn(null);
        resource.setModifiedOn(null);
        resource.setDeleted(true);
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.createResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(resource.getGuid(), GUID);
        assertEquals(resource.getCreatedOn(), CREATED_ON);
        assertEquals(resource.getModifiedOn(), CREATED_ON);
        assertFalse(resource.isDeleted());
        assertEquals(resource.getCreatedAtRevision(), 5);
        assertTrue(resource.isUpToDate());
        
        verify(mockDao).saveResource(TEST_APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createResourceChecksAssessmentOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("orgB");
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        service.createResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, new AssessmentResource());
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createResourceValidates() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        service.createResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, new AssessmentResource());
    }
    
    @Test
    public void createResourceSanitizesStringFields() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = createUnsanitizedResource();
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.createResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
        assertEquals(retValue.getGuid(), GUID);
        assertEquals(retValue.getTitle(), SANITIZED_STRING);
        assertEquals(retValue.getUrl(), SANITIZED_STRING);
        assertEquals(retValue.getFormat(), SANITIZED_STRING);
        assertEquals(retValue.getDate(), SANITIZED_STRING);
        assertEquals(retValue.getDescription(), SANITIZED_STRING);
        assertEquals(retValue.getContributors().get(0), SANITIZED_STRING);
        assertEquals(retValue.getCreators().get(0), SANITIZED_STRING);
        assertEquals(retValue.getPublishers().get(0), SANITIZED_STRING);
        assertEquals(retValue.getLanguage(), SANITIZED_STRING);
    }
    
    @Test
    public void updateResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedOn(null);
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getCreatedAtRevision(), 5);
        assertTrue(retValue.isUpToDate());
    }
    
    @Test
    public void updateResourceSanitizesStringFields() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(false);
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = createUnsanitizedResource();
        resource.setGuid(GUID); // this actually can't be changed, or you get a 404
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
        assertEquals(retValue.getGuid(), GUID);
        assertEquals(retValue.getTitle(), SANITIZED_STRING);
        assertEquals(retValue.getUrl(), SANITIZED_STRING);
        assertEquals(retValue.getFormat(), SANITIZED_STRING);
        assertEquals(retValue.getDate(), SANITIZED_STRING);
        assertEquals(retValue.getDescription(), SANITIZED_STRING);
        assertEquals(retValue.getContributors().get(0), SANITIZED_STRING);
        assertEquals(retValue.getCreators().get(0), SANITIZED_STRING);
        assertEquals(retValue.getPublishers().get(0), SANITIZED_STRING);
        assertEquals(retValue.getLanguage(), SANITIZED_STRING);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateResourceValidates() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(false);
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = new AssessmentResource();
        resource.setGuid(GUID); // this actually can't be changed, or you get a 404
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateResourceChecksAssessmentOwnership() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_ORG_ID);
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        
        service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
          expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void updateResourceNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void updateResourceNotFoundWhenLogicallyDeleted() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(true);
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setDeleted(true);
        
        service.updateResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, resource);
    }
    
    @Test
    public void updateSharedResource() { 
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(SHARED_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedOn(null);
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(SHARED_APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateSharedResource(TEST_APP_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getCreatedAtRevision(), 5);
        assertTrue(retValue.isUpToDate());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedResourceFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID + ":anotherOrg");
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);

        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        
        service.updateSharedResource(TEST_APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test
    public void deleteResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);

        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao).saveResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), resourceCaptor.capture());
        
        AssessmentResource captured = resourceCaptor.getValue();
        assertTrue(captured.isDeleted());
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteResourceChecksAssessmentOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("owner-id");
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);

        service.deleteResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, GUID);
    }
    
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void deleteResourceNotFound() { 
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);

        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.deleteResource(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteResourcePermanently() {
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.of(existing));
        
        service.deleteResourcePermanently(TEST_APP_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao).deleteResource(TEST_APP_ID, existing);
    }
    
    @Test
    public void deleteResourcePermanentlyQuietWhenNotFound() {
        when(mockDao.getResource(TEST_APP_ID, GUID)).thenReturn(Optional.empty());
        
        service.deleteResourcePermanently(TEST_APP_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao, never()).deleteResource(any(), any());
    }
    
    @Test
    public void copyResourcesNoTargets() {
        when(service.getCreatedOn()).thenReturn(TIMESTAMP);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setRevision(100);
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        List<AssessmentResource> resources = ImmutableList.of();
        when(mockDao.saveResources(eq("targetId"), eq(IDENTIFIER), any())).thenReturn(resources);
        
        makeResource("originId", "guid1");
        makeResource("originId", "guid2");
        makeResource("originId", "guid3");
        when(mockDao.getResource("targetId", "guid1")).thenReturn(Optional.empty());
        when(mockDao.getResource("targetId", "guid2")).thenReturn(Optional.empty());
        when(mockDao.getResource("targetId", "guid3")).thenReturn(Optional.empty());
        
        List<AssessmentResource> retValue = service.copyResources("originId", "targetId", assessment, guids);
        assertSame(retValue, resources);
        
        verify(mockDao).saveResources(eq("targetId"), eq(IDENTIFIER), resourceListCaptor.capture());
        
        AssessmentResource ar1 = resourceListCaptor.getValue().get(0);
        assertEquals(ar1.getCreatedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar1.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar1.getGuid(), "guid1");
        assertEquals(ar1.getCreatedAtRevision(), 100);
        assertEquals(ar1.getVersion(), 0);
        assertFalse(ar1.isDeleted());
        
        AssessmentResource ar2 = resourceListCaptor.getValue().get(1);
        assertEquals(ar2.getCreatedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar2.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar2.getGuid(), "guid2");
        assertEquals(ar2.getCreatedAtRevision(), 100);
        assertEquals(ar2.getVersion(), 0);
        assertFalse(ar2.isDeleted());
        
        AssessmentResource ar3 = resourceListCaptor.getValue().get(2);
        assertEquals(ar3.getCreatedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar3.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar3.getGuid(), "guid3");
        assertEquals(ar3.getCreatedAtRevision(), 100);
        assertEquals(ar3.getVersion(), 0);
        assertFalse(ar3.isDeleted());
    }
    
    @Test
    public void copyResourcesTargetsExist() {
        when(service.getCreatedOn()).thenReturn(TIMESTAMP);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setRevision(100);
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        List<AssessmentResource> resources = ImmutableList.of();
        when(mockDao.saveResources(eq("targetId"), eq(IDENTIFIER), any())).thenReturn(resources);
        
        makeResource("originId", "guid1");
        makeResource("originId", "guid2");
        makeResource("originId", "guid3");

        makeResource("targetId", "guid1");
        makeResource("targetId", "guid2");
        makeResource("targetId", "guid3");
        
        List<AssessmentResource> retValue = service.copyResources("originId", "targetId", assessment, guids);
        assertSame(retValue, resources);
        
        verify(mockDao).saveResources(eq("targetId"), eq(IDENTIFIER), resourceListCaptor.capture());
        
        AssessmentResource ar1 = resourceListCaptor.getValue().get(0);
        assertEquals(ar1.getCreatedOn().toString(), CREATED_ON.toString());
        assertEquals(ar1.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar1.getCreatedAtRevision(), 100);
        assertEquals(ar1.getVersion(), 10);        
        
        AssessmentResource ar2 = resourceListCaptor.getValue().get(1);
        assertEquals(ar2.getCreatedOn().toString(), CREATED_ON.toString());
        assertEquals(ar2.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar2.getCreatedAtRevision(), 100);
        assertEquals(ar2.getVersion(), 10);        
        
        AssessmentResource ar3 = resourceListCaptor.getValue().get(2);
        assertEquals(ar3.getCreatedOn().toString(), CREATED_ON.toString());
        assertEquals(ar3.getModifiedOn().toString(), TIMESTAMP.toString());
        assertEquals(ar3.getCreatedAtRevision(), 100);
        assertEquals(ar3.getVersion(), 10);        
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Must specify one or more resource GUIDs")
    public void copyResourcesGuidsEmpty() {
        Assessment assessment = createAssessment();
        service.copyResources("originId", "targetId", assessment, new HashSet<>());
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Must specify one or more resource GUIDs")
    public void copyResourcesGuidsNull() {
        Assessment assessment = createAssessment();
        service.copyResources("originId", "targetId", assessment, null);
    }
    
    @Test
    public void copyResourcesOriginNotFound() {
        when(service.getCreatedOn()).thenReturn(TIMESTAMP);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setRevision(100);
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        when(mockDao.getResource("originId", "guid1")).thenReturn(Optional.empty());
        
        try {
            service.copyResources("originId", "targetId", assessment, guids);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockDao, never()).saveResources(any(), any(), any());
    }
    
    @Test
    public void importAssessmentResources() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        List<AssessmentResource> resources = ImmutableList.of();
        // We test this separately so we can mock it here.
        doReturn(resources).when(service).copyResources(SHARED_APP_ID, TEST_APP_ID, assessment, guids);
        
        List<AssessmentResource> retValue = service.importAssessmentResources(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, guids);
        assertSame(retValue, resources);
        
        verify(service).copyResources(SHARED_APP_ID, TEST_APP_ID, assessment, guids);
    }
    
    @Test
    public void importAssessmentResourcesCallerCorrectOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_OWNER_ID).build());

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        doReturn(ImmutableList.of()).when(service).copyResources(SHARED_APP_ID, TEST_APP_ID, assessment, guids);
        
        service.importAssessmentResources(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, guids);
        
        verify(service).copyResources(SHARED_APP_ID, TEST_APP_ID, assessment, guids);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void importAssessmentResourcesCallerWrongOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notOwnerId").build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_ORG_ID);
        when(mockAssessmentService.getLatestAssessment(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        service.importAssessmentResources(TEST_APP_ID, TEST_ORG_ID, ASSESSMENT_ID, guids);
    }
    
    @Test
    public void publishAssessmentResources() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID+":"+TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        List<AssessmentResource> resources = ImmutableList.of();
        // We test this separately so we can mock it here.
        doReturn(resources).when(service).copyResources(TEST_APP_ID, SHARED_APP_ID, assessment, guids);
        
        List<AssessmentResource> retValue = service.publishAssessmentResources(TEST_APP_ID, ASSESSMENT_ID, guids);
        assertSame(retValue, resources);
        
        verify(service).copyResources(TEST_APP_ID, SHARED_APP_ID, assessment, guids);
    }
    
    @Test
    public void publishAssessmentResourcesOwnerInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID+":"+TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        List<AssessmentResource> resources = ImmutableList.of();
        // We test this separately so we can mock it here.
        doReturn(resources).when(service).copyResources(TEST_APP_ID, SHARED_APP_ID, assessment, guids);
        
        service.publishAssessmentResources(TEST_APP_ID, ASSESSMENT_ID, guids);
        
        verify(service).copyResources(TEST_APP_ID, SHARED_APP_ID, assessment, guids);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void publishAssessmentResourcesCallerWrongOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrongOwnerId").build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID+":"+TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        service.publishAssessmentResources(TEST_APP_ID, ASSESSMENT_ID, guids);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void publishAssessmentResourcesCallerWrongAppContext() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_OWNER_ID)).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID+":"+TEST_OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_APP_ID, null, ASSESSMENT_ID)).thenReturn(assessment);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        
        service.publishAssessmentResources("otherAppContext", ASSESSMENT_ID, guids);
    }
    
    private AssessmentResource makeResource(String appId, String guid) {
        AssessmentResource ar = AssessmentResourceTest.createAssessmentResource();
        ar.setGuid(guid);
        when(mockDao.getResource(appId, guid)).thenReturn(Optional.of(ar));
        return ar;
    }

    private AssessmentResource createUnsanitizedResource() {
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setGuid(UNSANITIZED_STRING);
        resource.setTitle(UNSANITIZED_STRING);
        resource.setUrl(UNSANITIZED_STRING);
        resource.setFormat(UNSANITIZED_STRING);
        resource.setDate(UNSANITIZED_STRING);
        resource.setDescription(UNSANITIZED_STRING);
        resource.setContributors(ImmutableList.of(UNSANITIZED_STRING));
        resource.setCreators(ImmutableList.of(UNSANITIZED_STRING));
        resource.setPublishers(ImmutableList.of(UNSANITIZED_STRING));
        resource.setLanguage(UNSANITIZED_STRING);
        return resource;
    }
}
