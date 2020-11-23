package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.services.SponsorService.STRING_SET_TYPE_REF;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
import org.sagebionetworks.bridge.dao.SponsorDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

public class SponsorServiceTest extends Mockito {
    
    static final CacheKey CACHE_KEY = CacheKey.orgSponsoredStudies(TEST_APP_ID, TEST_ORG_ID);
    
    @Mock
    OrganizationService mockOrgService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    SponsorDao mockSponsorDao;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @InjectMocks
    SponsorService service;
    
    @BeforeMethod
    public void beforeMethods() {
        MockitoAnnotations.initMocks(this);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(null);
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
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }

    @Test
    public void addStudySponsorAsOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void addStudySponsorFails() {
        service.addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).addStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test
    public void removeStudySponsorAsAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        when(mockSponsorDao.doesOrganizationSponsorStudy(
                TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
    }
    
    @Test
    public void removeStudySponsorAsOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        when(mockSponsorDao.doesOrganizationSponsorStudy(
                TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        verify(mockCacheProvider).removeObject(CACHE_KEY);
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
        RequestContext.set(new RequestContext.Builder()
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
        RequestContext.set(new RequestContext.Builder()
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
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(Study.create());
        when(mockOrgService.getOrganization(TEST_APP_ID, TEST_ORG_ID)).thenReturn(Organization.create());
        
        service.removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
        
        verify(mockSponsorDao).removeStudySponsor(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }
    
    @Test
    public void isStudySponsoredBy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        
        when(mockSponsorDao.doesOrganizationSponsorStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);
        
        boolean retValue = service.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID);
        assertTrue(retValue);
        
        verify(mockSponsorDao).doesOrganizationSponsorStudy(TEST_APP_ID, TEST_STUDY_ID, TEST_ORG_ID);
    }

    @Test
    public void isStudySponsoredByStudyBlank() {
        assertFalse(service.isStudySponsoredBy("", TEST_ORG_ID));
    }

    @Test
    public void isStudySponsoredByOrgNull() {
        assertFalse(service.isStudySponsoredBy(TEST_STUDY_ID, null));
    }
    
    private void mockGetSponsoredStudies() {
        Study study1 = Study.create();
        study1.setIdentifier("study1");
        
        Study study2 = Study.create();
        study2.setIdentifier("study2");
        
        PagedResourceList<Study> page = new PagedResourceList<>(ImmutableList.of(study1, study2), 2); 
        when(mockSponsorDao.getSponsoredStudies(TEST_APP_ID, TEST_ORG_ID, null, null))
            .thenReturn(page);
    }
    
    @Test
    public void getSponsoredStudyIds() {
        when(mockOrgService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        mockGetSponsoredStudies();
        
        Set<String> retValue = service.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID);
        assertEquals(retValue, ImmutableSet.of("study1", "study2"));
        
        verify(mockCacheProvider).setObject(CACHE_KEY, retValue);
    }
    
    @Test
    public void getSponsoredStudyIdsOrgNotFound() {
        when(mockOrgService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.empty());
        
        Set<String> retValue = service.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID);
        assertEquals(retValue, ImmutableSet.of());
    }

    @Test
    public void getSponsoredStudyIdsCached() {
        Set<String> set = ImmutableSet.of("study1", "study2");
        when(mockCacheProvider.getObject(CACHE_KEY, STRING_SET_TYPE_REF)).thenReturn(set);

        Set<String> retValue = service.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID);
        assertEquals(retValue, ImmutableSet.of("study1", "study2"));

        verify(mockSponsorDao, never()).getSponsoredStudies(any(), any(), any(), any());
    }
}
