package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentMigration;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.EnrollmentService;

public class EnrollmentControllerTest extends Mockito {
    
    @Mock
    EnrollmentService mockService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    EnrollmentController controller;
    
    @Captor
    ArgumentCaptor<Enrollment> enrollmentCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAdministrativeSession();
    }
    
    @AfterMethod
    public void afterMethod() { 
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(EnrollmentController.class);
        assertGet(EnrollmentController.class, "getEnrollmentsForStudy");
        assertCreate(EnrollmentController.class, "enroll");
        assertDelete(EnrollmentController.class, "unenroll");
        assertPost(EnrollmentController.class, "updateEnrollment");
    }
    
    @Test
    public void getEnrollmentsForStudy() {
        EnrollmentDetail en1 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user1"), null, null, null);
        EnrollmentDetail en2 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user2"), null, null, null);
        PagedResourceList<EnrollmentDetail> page = new PagedResourceList<>(ImmutableList.of(en1, en2), 10);
        when(mockService.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 5, 40)).thenReturn(page);
        
        PagedResourceList<EnrollmentDetail> retValue = controller.getEnrollmentsForStudy(
                TEST_STUDY_ID, "5", "40", "enrolled", "true");
        assertSame(retValue, page);
        
        verify(mockService).getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, true, 5, 40);
    }
    
    @Test
    public void getEnrollmentsForStudyWithDefaults() {
        EnrollmentDetail en1 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user1"), null, null, null);
        EnrollmentDetail en2 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user2"), null, null, null);
        PagedResourceList<EnrollmentDetail> page = new PagedResourceList<>(ImmutableList.of(en1, en2), 10);
        when(mockService.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, 0, 50)).thenReturn(page);
        
        PagedResourceList<EnrollmentDetail> retValue = controller.getEnrollmentsForStudy(
                TEST_STUDY_ID, null, null, null, null);
        assertSame(retValue, page);
        
        verify(mockService).getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, null, false, 0, 50);
    }
    
    @Test
    public void enroll() throws Exception {
        Enrollment enrollment = new HibernateEnrollment();
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setExternalId("anExternalId");
        enrollment.setAccountId(TEST_USER_ID);
        enrollment.setConsentRequired(true);
        
        TestUtils.mockRequestBody(mockRequest, enrollment);
        
        Enrollment completed = new HibernateEnrollment();
        when(mockService.enroll(any())).thenReturn(completed);
        
        Enrollment retValue = controller.enroll(TEST_STUDY_ID);
        assertSame(retValue, completed);
        
        verify(mockService).enroll(enrollmentCaptor.capture());
        
        Enrollment value = enrollmentCaptor.getValue();
        assertEquals(value.getAppId(), TEST_APP_ID);
        assertEquals(value.getStudyId(), TEST_STUDY_ID);
        assertEquals(value.getEnrolledOn(), CREATED_ON);
        assertEquals(value.getExternalId(), "anExternalId");
        assertEquals(value.getAccountId(), TEST_USER_ID);
        assertTrue(value.isConsentRequired());
    }

    @Test
    public void unenroll() throws Exception {
        Enrollment completed = new HibernateEnrollment();
        when(mockService.unenroll(any())).thenReturn(completed);

        Enrollment retValue = controller.unenroll(TEST_STUDY_ID, TEST_USER_ID, "This is a note");
        assertSame(retValue, completed);
        
        verify(mockService).unenroll(enrollmentCaptor.capture());
        
        Enrollment value = enrollmentCaptor.getValue();
        assertEquals(value.getWithdrawalNote(), "This is a note");
        assertEquals(value.getAppId(), TEST_APP_ID);
        assertEquals(value.getStudyId(), TEST_STUDY_ID);
        assertEquals(value.getAccountId(), TEST_USER_ID);
    }
    
    @Test
    public void updateUserEnrollments() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        Enrollment newEnrollment = Enrollment.create(TEST_APP_ID, "anotherStudy", TEST_USER_ID);
        mockRequestBody(mockRequest, ImmutableSet.of(EnrollmentMigration.create(newEnrollment)));
        
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, TEST_USER_ID);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        Account mockAccount = mock(Account.class);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, "externalId");
        Set<Enrollment> enrollments = Sets.newHashSet(en1);
        when(mockAccount.getEnrollments()).thenReturn(enrollments);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(mockAccount));
        
        mockEditAccount(mockAccountService, mockAccount);
        
        StatusMessage message = controller.updateUserEnrollments("healthcode:" + TEST_USER_ID);
        assertEquals(message.getMessage(), "Enrollments updated.");
        
        verify(mockAccount, times(2)).getEnrollments();
        assertEquals(enrollments.size(), 1);
    }
    
    // updateUserEnrollmentsAccountNotFound now happens in the accountService.editAccount
    
    @Test
    public void updateUserEnrollmentsRemovingAll() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        mockRequestBody(mockRequest, ImmutableSet.of());
        
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, TEST_USER_ID);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        Account mockAccount = mock(Account.class);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, "externalId");
        Set<Enrollment> enrollments = Sets.newHashSet(en1);
        when(mockAccount.getEnrollments()).thenReturn(enrollments);
        when(mockAccountService.getAccount(accountId)).thenReturn(Optional.of(mockAccount));
        
        mockEditAccount(mockAccountService, mockAccount);
        
        controller.updateUserEnrollments("healthcode:" + TEST_USER_ID);
        
        verify(mockAccount, times(2)).getEnrollments();
        assertTrue(enrollments.isEmpty());
    }

    @Test
    public void updateEnrollmentNote() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAdministrativeSession();

        Enrollment enrollment = new HibernateEnrollment();
        enrollment.setAppId("otherAppId");
        enrollment.setStudyId("otherStudyId");
        enrollment.setAccountId("otherUserId");
        enrollment.setNote(TEST_NOTE);

        mockRequestBody(mockRequest, enrollment);

        controller.updateEnrollment(TEST_STUDY_ID, TEST_USER_ID);

        verify(mockService).editEnrollment(enrollmentCaptor.capture());

        Enrollment captured = enrollmentCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getAccountId(), TEST_USER_ID);
        assertEquals(captured.getNote(), TEST_NOTE);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateEnrollmentValidatesRoles() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());

        controller.updateEnrollment(TEST_STUDY_ID, TEST_USER_ID);
    }
}
