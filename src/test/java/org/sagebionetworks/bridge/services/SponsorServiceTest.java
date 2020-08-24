package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.SponsorDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

public class SponsorServiceTest extends Mockito {
    
    @Mock
    OrganizationService mockOrgService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    SponsorDao mockSponsorDao;
    
    @InjectMocks
    SponsorService service;
    
    @BeforeMethod
    public void beforeMethods() {
        MockitoAnnotations.initMocks(this);
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getStudySponsors() {
        PagedResourceList<Organization> page = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockSponsorDao.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 10, 100)).thenReturn(page);
        
        PagedResourceList<Organization> retValue = service.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 10, 100);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), 10);
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), 100);
        
        verify(mockSponsorDao).getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 10, 100);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getStudySponsorsOffsetNegative() {
        service.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, -2, 100);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getStudySponsorsPageSizeTooSmall() {
        service.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 0, 2);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getStudySponsorsPageSizeTooLarge() {
        service.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 0, 2000);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void getStudySponsorsStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        
        service.getStudySponsors(TEST_APP_ID, TEST_STUDY_ID, 0, 100);
    }

    @Test
    public void getSponsoredStudies() {
        PagedResourceList<Study> page = new PagedResourceList<>(
                ImmutableList.of(Study.create(), Study.create()), 100);
        when(mockSponsorDao.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 10, 60)).thenReturn(page);
        
        PagedResourceList<Study> retValue = service.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 10, 60);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), 10);
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), 60);

        verify(mockSponsorDao).getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 10, 60);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getSponsoredStudiesOffsetNegative() {
        service.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, -1, 60);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getSponsoredStudiesPageSizeTooSmall() {
        service.getSponsoredStudies(TEST_APP_ID, TEST_STUDY_ID, 0, 2);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getSponsoredStudiesPageSizeTooLarge() {
        service.getSponsoredStudies(TEST_APP_ID, TEST_STUDY_ID, 0, 2000);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void getSponsoredStudiesStudyNotFound() {
        when(mockOrgService.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenThrow(new EntityNotFoundException(Organization.class));

        service.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, 0, 100);
    }

    @Test
    public void addStudySponsorAsAdmin() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }

    @Test
    public void addStudySponsorAsOrgMember() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void addStudySponsorFails() {
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test
    public void removeStudySponsorAsAdmin() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        when(mockSponsorDao.doesOrganizationSponsorStudy(
                TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test
    public void removeStudySponsorAsOrgMember() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        when(mockSponsorDao.doesOrganizationSponsorStudy(
                TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void removeStudySponsorFails() {
        when(mockSponsorDao.doesOrganizationSponsorStudy(
                TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Study not found.")
    public void removeStudyNoStudy() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        when(mockOrgService.getOrganization(TEST_APP_ID, TEST_ORG_ID)).thenReturn(Organization.create());
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void removeStudyNoOrganization() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(Study.create());
        when(mockOrgService.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenThrow(new EntityNotFoundException(Organization.class));
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Organization '"
            + TEST_ORG_ID + "' is not a sponsor of study '" + TEST_STUDY_ID + "'")
    public void removeStudyNoAssociation() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(Study.create());
        when(mockOrgService.getOrganization(TEST_APP_ID, TEST_ORG_ID)).thenReturn(Organization.create());
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
}
