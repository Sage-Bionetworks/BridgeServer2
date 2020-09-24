package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

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
import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
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
        EnrollmentDetail en1 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user1"), null, null, null);
        EnrollmentDetail en2 = new EnrollmentDetail(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "user2"), null, null, null);
        PagedResourceList<EnrollmentDetail> page = new PagedResourceList<>(ImmutableList.of(en1, en2), 10);
        when(mockService.getEnrollmentsForStudy(TEST_APP_ID, TEST_STUDY_ID, ENROLLED, 5, 40)).thenReturn(page);
        
        PagedResourceList<EnrollmentDetail> retValue = controller.getEnrollmentsForStudy(
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
}
