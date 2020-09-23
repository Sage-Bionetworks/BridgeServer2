package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Enrollment;
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
        
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER, ADMIN);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(EnrollmentController.class);
        assertGet(EnrollmentController.class, "getEnrollmentsForStudy");
        assertCreate(EnrollmentController.class, "enroll");
        assertDelete(EnrollmentController.class, "unenroll");
    }
    
    @Test
    public void getEnrollmentsForStudy() {
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user1");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user2");
        PagedResourceList<Enrollment> page = new PagedResourceList<>(ImmutableList.of(en1, en2), 10);
        when(mockService.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, 5, 40)).thenReturn(page);
        
        PagedResourceList<Enrollment> retValue = controller.getEnrollmentsForStudy(
                TEST_STUDY_ID, "5", "40", "enrolled");
        assertSame(retValue, page);
        
        verify(mockService).getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, 5, 40);
    }
    
    @Test
    public void enroll() throws Exception {
        Enrollment enrollment = new HibernateEnrollment();
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setExternalId("anExternalId");
        enrollment.setAccountId(USER_ID);
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
        assertEquals(value.getAccountId(), USER_ID);
        assertTrue(value.isConsentRequired());
    }

    @Test
    public void unenroll() throws Exception {
        Enrollment completed = new HibernateEnrollment();
        when(mockService.unenroll(any())).thenReturn(completed);

        Enrollment retValue = controller.unenroll(TEST_STUDY_ID, USER_ID, "This is a note");
        assertSame(retValue, completed);
        
        verify(mockService).unenroll(enrollmentCaptor.capture());
        
        Enrollment value = enrollmentCaptor.getValue();
        assertEquals(value.getWithdrawalNote(), "This is a note");
        assertEquals(value.getAppId(), TEST_APP_ID);
        assertEquals(value.getStudyId(), TEST_STUDY_ID);
        assertEquals(value.getAccountId(), USER_ID);
    }
    
    @Test
    public void getUserEnrollments() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID, "externalId");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "anotherStudy", USER_ID);
        en2.setConsentRequired(true);
        en2.setEnrolledOn(CREATED_ON);
        en2.setWithdrawnOn(MODIFIED_ON);
        en2.setEnrolledBy("enrolledBy");
        en2.setWithdrawnBy("withdrawnBy");
        en2.setWithdrawalNote("withdrawal note");
        account.setEnrollments(ImmutableSet.of(en1, en2));
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        List<EnrollmentMigration> returnValue = controller.getUserEnrollments(USER_ID);
        assertEquals(returnValue.size(), 2);
        assertEquals(returnValue.stream().map(m -> m.getStudyId()).collect(toSet()), 
                ImmutableSet.of(TEST_STUDY_ID, "anotherStudy"));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUserEnrollmentsAccountNotFound() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        when(mockAccountService.getAccount(any())).thenReturn(null);
        
        controller.getUserEnrollments(USER_ID);
    }
    
    @Test
    public void updateUserEnrollments() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        Enrollment newEnrollment = Enrollment.create(TEST_APP_ID, "anotherStudy", USER_ID);
        mockRequestBody(mockRequest, ImmutableSet.of(EnrollmentMigration.create(newEnrollment)));
        
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, USER_ID);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        Account mockAccount = mock(Account.class);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID, "externalId");
        Set<Enrollment> enrollments = Sets.newHashSet(en1);
        when(mockAccount.getEnrollments()).thenReturn(enrollments);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        mockEditAccount(mockAccountService, mockAccount);
        
        StatusMessage message = controller.updateUserEnrollments("healthcode:"+USER_ID);
        assertEquals(message.getMessage(), "Enrollments updated.");
        
        verify(mockAccount, times(2)).getEnrollments();
        assertEquals(enrollments.size(), 1);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateUserEnrollmentsAccountNotFound() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        Enrollment newEnrollment = Enrollment.create(TEST_APP_ID, "anotherStudy", USER_ID);
        mockRequestBody(mockRequest, ImmutableSet.of(EnrollmentMigration.create(newEnrollment)));
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        when(mockAccountService.getAccount(any())).thenReturn(null);
        
        controller.updateUserEnrollments(USER_ID);
    }
    
    @Test
    public void updateUserEnrollmentsRemovingAll() throws Exception {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        mockRequestBody(mockRequest, ImmutableSet.of());
        
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, USER_ID);
        
        AccountService mockAccountService = mock(AccountService.class);
        controller.setAccountService(mockAccountService);
        
        Account mockAccount = mock(Account.class);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID, "externalId");
        Set<Enrollment> enrollments = Sets.newHashSet(en1);
        when(mockAccount.getEnrollments()).thenReturn(enrollments);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        mockEditAccount(mockAccountService, mockAccount);
        
        controller.updateUserEnrollments("healthcode:"+USER_ID);
        
        verify(mockAccount, times(2)).getEnrollments();
        assertTrue(enrollments.isEmpty());
    }
}
