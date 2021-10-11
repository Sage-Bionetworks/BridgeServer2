package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLMENT_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;
import org.sagebionetworks.bridge.models.studies.Study;

public class EnrollmentServiceTest extends Mockito {

    @Mock
    AccountService mockAccountService;
    
    @Mock
    SponsorService mockSponsorService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    EnrollmentDao mockEnrollmentDao;
    
    @InjectMocks
    @Spy
    EnrollmentService service;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        doReturn(CREATED_ON).when(service).getEnrollmentDateTime();
        doReturn(MODIFIED_ON).when(service).getWithdrawalDateTime();
    }
    
    @Test
    public void getEnrollmentsForStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        PagedResourceList<EnrollmentDetail> page = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockEnrollmentDao.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 10, 50)).thenReturn(page);
        
        PagedResourceList<EnrollmentDetail> retValue = service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 10, 50);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), Integer.valueOf(50));
        assertEquals(retValue.getRequestParams().get(ENROLLMENT_FILTER), EnrollmentFilter.ENROLLED);
        
        verify(mockEnrollmentDao).getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 10, 50);
    }
    
    @Test
    public void getEnrollmentsForStudyWithDefaults() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        PagedResourceList<EnrollmentDetail> page = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockEnrollmentDao.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, null, null)).thenReturn(page);
        
        PagedResourceList<EnrollmentDetail> retValue = service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, null, null);
        assertSame(retValue, page);
        
        verify(mockEnrollmentDao).getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, null, null);
    }
        
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getEnrollmentsForStudyNotAuthorized() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerOrgMembership(TEST_ORG_ID).build());
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(false);
        
        service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, true, 10, 50);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getEnrollmentsForStudyOffsetNegative() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, true, -1, 50);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getEnrollmentsForStudyUnderMin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, true, 0, 0);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getEnrollmentsForStudyOverMax() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, true, 0, 1000);
    }
    
    @Test
    public void getEnrollmentsForUser() {
        // We had a bug where the unparsed userId being passed into this method was
        // being passed along, rather than the ID from the retrieved account. The 
        // userId passed in to this method can be one of a number of identifiers...
        // the parameter in this case should not be userId, should be something like
        // userIdToken as a cue that it needs to be parsed.
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, "extId");
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        List<EnrollmentDetail> details = ImmutableList.of();
        when(mockEnrollmentDao.getEnrollmentsForUser(TEST_APP_ID, null, TEST_USER_ID)).thenReturn(details);
        
        List<EnrollmentDetail> retValue = service.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID,
                "externalId:extId");
        assertEquals(retValue, details);
        
        verify(mockEnrollmentDao).getEnrollmentsForUser(TEST_APP_ID, ImmutableSet.of(), TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getEnrollmentsForUserNotFound() {
        AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.empty());
        
        service.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void enrollBySelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        // This should not effect the behavior of the enrollment.
        Enrollment otherStudy = Enrollment.create(TEST_APP_ID, "otherStudy", TEST_USER_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(otherStudy));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        DateTime timestamp = DateTime.now();
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId("extId");
        enrollment.setEnrolledOn(timestamp);

        Enrollment retValue = service.enroll(enrollment);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getStudyId(), TEST_STUDY_ID);
        assertEquals(retValue.getAccountId(), TEST_USER_ID);
        assertEquals(retValue.getExternalId(), "extId");
        assertEquals(retValue.getEnrolledOn(), timestamp);
        assertNull(retValue.getEnrolledBy());
        assertNull(retValue.getWithdrawnOn());
        assertNull(retValue.getWithdrawnBy());
        assertNull(retValue.getWithdrawalNote());
        assertFalse(retValue.isConsentRequired());
        
        assertTrue(account.getEnrollments().contains(retValue));
    }
    
    @Test
    public void enrollByAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        Enrollment retValue = service.enroll(enrollment);
        assertEquals(retValue.getAccountId(), TEST_USER_ID);
        assertEquals(retValue.getEnrolledBy(), "adminUser");
    }
    
    @Test
    public void enrollByResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.TRUE);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        Enrollment retValue = service.enroll(enrollment);
        assertEquals(retValue.getAccountId(), TEST_USER_ID);
        assertEquals(retValue.getEnrolledBy(), "adminUser");
    }
    
    @Test
    public void enrollByStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        Enrollment retValue = service.enroll(enrollment);
        assertEquals(retValue.getAccountId(), TEST_USER_ID);
    }
    
    // TODO: Should this throw an exception? I think during migration, it cannot
    @Test
    public void enrollAlreadyExists() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        Enrollment otherStudy = Enrollment.create(TEST_APP_ID, "otherStudy", TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(existing, otherStudy));
        
        when(mockAccountService.getAccount(ACCOUNT_ID))
            .thenReturn(Optional.of(account));
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.enroll(enrollment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void enrollAccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        doThrow(new EntityNotFoundException(Account.class))
            .when(mockAccountService).editAccount(any(), any());
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId("extId");

        service.enroll(enrollment);
    }
    
    @Test
    public void enrollAlreadyExistsButIsWithdrawn() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment unrelatedEnrollment = Enrollment.create(TEST_APP_ID, "someOtherStudy", TEST_USER_ID);
        
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        // This should cause the enrollment to be recreated.
        existing.setWithdrawnOn(MODIFIED_ON);
        account.setEnrollments(Sets.newHashSet(unrelatedEnrollment, existing));
        
        when(mockAccountService.getAccount(ACCOUNT_ID))
            .thenReturn(Optional.of(account));
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId("extId");
        
        Enrollment retValue = service.enroll(enrollment);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getStudyId(), TEST_STUDY_ID);
        assertEquals(retValue.getAccountId(), TEST_USER_ID);
        assertEquals(retValue.getExternalId(), "extId");
        assertEquals(retValue.getEnrolledOn(), CREATED_ON);
        assertNull(retValue.getEnrolledBy());
        assertNull(retValue.getWithdrawnOn());
        assertNull(retValue.getWithdrawnBy());
        assertNull(retValue.getWithdrawalNote());
        assertFalse(retValue.isConsentRequired());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void enrollNotAuthorizedAsAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        doThrow(new UnauthorizedException()).when(mockAccountService).editAccount(any(), any());
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        service.enroll(enrollment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void enrollNotAuthorizedAsStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyB"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        // the call to sponsorService returns null
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);

        service.enroll(enrollment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void enrollInvalidEnrollment() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setAccountId(null);

        service.enroll(enrollment);
    }
    
    @Test
    public void unenrollBySelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());
                
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        Enrollment otherStudy = Enrollment.create(TEST_APP_ID, "otherStudy", TEST_USER_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(otherStudy, existing));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setWithdrawnOn(MODIFIED_ON.minusHours(1));
        enrollment.setWithdrawalNote("Withdrawal reason");
        
        Enrollment retValue = service.unenroll(enrollment);
        assertEquals(retValue.getWithdrawnOn(), MODIFIED_ON.minusHours(1));
        assertNull(retValue.getWithdrawnBy());
        assertEquals(retValue.getWithdrawalNote(), "Withdrawal reason");

        verify(mockAccountService).editAccount(any(), any());
        Enrollment captured = Iterables.getLast(account.getEnrollments(), null);
        assertEquals(captured.getWithdrawnOn(), MODIFIED_ON.minusHours(1));
        assertNull(captured.getWithdrawnBy());
        assertEquals(captured.getWithdrawalNote(), "Withdrawal reason");        
    }
    
    @Test
    public void unenrollBySelfDefaultsWithdrawnOn() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());
                
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(existing));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setWithdrawalNote("Withdrawal reason");
        
        Enrollment retValue = service.unenroll(enrollment);
        assertEquals(retValue.getWithdrawnOn(), MODIFIED_ON);

        verify(mockAccountService).editAccount(any(), any());
        Enrollment captured = Iterables.getLast(account.getEnrollments(), null);
        assertEquals(captured.getWithdrawnOn(), MODIFIED_ON);
    }
    
    @Test
    public void unenrollByThirdPartyAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
                
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(existing));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setWithdrawalNote("Withdrawal reason");
        
        Enrollment retValue = service.unenroll(enrollment);
        assertEquals(retValue.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(retValue.getWithdrawnBy(), "adminUser");
        assertEquals(retValue.getWithdrawalNote(), "Withdrawal reason");

        verify(mockAccountService).editAccount(any(), any());
        Enrollment captured = Iterables.getFirst(account.getEnrollments(), null);
        assertEquals(captured.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(captured.getWithdrawnBy(), "adminUser");
        assertEquals(captured.getWithdrawalNote(), "Withdrawal reason");
    }
    
    @Test
    public void unenrollByThirdPartyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.TRUE);
                
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(existing));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setWithdrawalNote("Withdrawal reason");
        
        Enrollment retValue = service.unenroll(enrollment);
        assertEquals(retValue.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(retValue.getWithdrawnBy(), "adminUser");
        assertEquals(retValue.getWithdrawalNote(), "Withdrawal reason");

        verify(mockAccountService).editAccount(any(), any());
        Enrollment captured = Iterables.getFirst(account.getEnrollments(), null);
        assertEquals(captured.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(captured.getWithdrawnBy(), "adminUser");
        assertEquals(captured.getWithdrawalNote(), "Withdrawal reason");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void unenrollDoesNotExists() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.unenroll(enrollment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void unenrollAccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        doThrow(new EntityNotFoundException(Account.class))
            .when(mockAccountService).editAccount(any(), any());
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.unenroll(enrollment);
    }
    
    @Test
    public void unenrollAlreadyExistsButIsWithdrawn() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Enrollment existing = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        existing.setWithdrawnOn(MODIFIED_ON);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEnrollments(Sets.newHashSet(existing));
        TestUtils.mockEditAccount(mockAccountService, account);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.unenroll(enrollment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void unenrollNotAuthorizedAsAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        doThrow(new UnauthorizedException()).when(mockAccountService).editAccount(any(), any());
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.unenroll(enrollment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void unenrollNotAuthorizedAsStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.FALSE);

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        doThrow(new UnauthorizedException()).when(mockAccountService).editAccount(any(), any());

        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        service.unenroll(enrollment);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void unenrollInvalidEnrollment() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setAccountId(null);

        service.unenroll(enrollment);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void getEnrollmentsForStudyStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        
        service.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, null, null);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void getEnrollmentsForUserStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
    
        service.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void getEnrollmentsForUserStudyNullable() {
        when(mockStudyService.getStudy(TEST_APP_ID, null, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        
        Account account = Account.create();
        when(mockAccountService.getAccount(ACCOUNT_ID))
            .thenReturn(Optional.of(account));
    
        service.getEnrollmentsForUser(TEST_APP_ID, null, TEST_USER_ID);
    }
    
    @Test
    public void getEnrollmentsForUserFilteringForStudies() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB");
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(callerStudies)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, "extId");
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        List<EnrollmentDetail> details = ImmutableList.of();
        when(mockEnrollmentDao.getEnrollmentsForUser(TEST_APP_ID, callerStudies, TEST_USER_ID)).thenReturn(details);
        
        List<EnrollmentDetail> retValue = service.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, "externalId:extId");
        assertSame(retValue, details);
        
        verify(mockEnrollmentDao).getEnrollmentsForUser(TEST_APP_ID, callerStudies, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void enrollStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));

        service.enroll(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID));
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void addEnrollmentStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        
        service.addEnrollment(Account.create(), Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID));
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Study not found.")
    public void unenrollAccountStudyNotFound() {
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true))
            .thenThrow(new EntityNotFoundException(Study.class));
        
        service.unenroll(Account.create(), Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID));
    }
}