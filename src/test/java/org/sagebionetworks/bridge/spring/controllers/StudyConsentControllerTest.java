package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

public class StudyConsentControllerTest extends Mockito {
    
    private static final String GUID = "guid";
    private static final String DATETIME_STRING = DateTime.now().toString();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(GUID);
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
    }

    @Mock
    StudyService mockStudyService;
    @Mock
    StudyConsentService mockStudyConsentService;
    @Mock
    SubpopulationService mockSubpopService;
    @Mock
    Subpopulation mockSubpopulation;
    @Mock
    HttpServletRequest mockRequest;
    
    @InjectMocks
    @Spy
    private StudyConsentController controller;
    
    private UserSession session;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setStudyIdentifier(STUDY_ID);
        session.setAuthenticated(true);
        
        when(mockSubpopService.getSubpopulation(STUDY_ID, SubpopulationGuid.create(GUID)))
                .thenReturn(mockSubpopulation);
        
        doReturn(mockRequest).when(controller).request();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(StudyConsentController.class);
        assertGet(StudyConsentController.class, "getAllConsents");
        assertGet(StudyConsentController.class, "getActiveConsent");
        assertGet(StudyConsentController.class, "getMostRecentConsent");
        assertGet(StudyConsentController.class, "getConsent");
        assertCreate(StudyConsentController.class, "addConsent");
        assertPost(StudyConsentController.class, "publishConsent");
        assertGet(StudyConsentController.class, "getAllConsentsV2");
        assertGet(StudyConsentController.class, "getActiveConsentV2");
        assertGet(StudyConsentController.class, "getMostRecentConsentV2");
        assertGet(StudyConsentController.class, "getConsentV2");
        assertCreate(StudyConsentController.class, "addConsentV2");
        assertPost(StudyConsentController.class, "publishConsentV2");
    }
    
    @Test
    public void getAllConsentsV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        List<StudyConsent> consents = Lists.newArrayList(new DynamoStudyConsent1(), new DynamoStudyConsent1());
        when(mockStudyConsentService.getAllConsents(SUBPOP_GUID)).thenReturn(consents);
        
        ResourceList<StudyConsent> result = controller.getAllConsentsV2(GUID);
        
        // Do not need to extensively verify, just verify contents are returned in ResourceList
        assertEquals(result.getItems(), consents);
    }

    @Test
    public void getActiveConsentV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "<document/>");
        when(mockStudyConsentService.getActiveConsent(any())).thenReturn(view);
        
        StudyConsentView result = controller.getActiveConsentV2(GUID);
        
        assertEquals("<document/>", result.getDocumentContent());
    }
    
    @Test
    public void getMostRecentConsentV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "<document/>");
        when(mockStudyConsentService.getMostRecentConsent(SUBPOP_GUID)).thenReturn(view);
        
        StudyConsentView result = controller.getMostRecentConsentV2(GUID);
        
        assertEquals("<document/>", result.getDocumentContent());
    }

    @Test
    public void getConsentV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "<document/>");
        when(mockStudyConsentService.getConsent(SUBPOP_GUID, DateTime.parse(DATETIME_STRING).getMillis())).thenReturn(view);
        
        StudyConsentView result = controller.getConsentV2(GUID, DATETIME_STRING);
        
        assertEquals("<document/>", result.getDocumentContent());
    }
    
    @Test
    public void addConsentV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyConsentForm form = new StudyConsentForm("<document/>");
        mockRequestBody(mockRequest, form);
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "<document/>");
        when(mockStudyConsentService.addConsent(eq(SUBPOP_GUID), any())).thenReturn(view);
        
        StudyConsentView result = controller.addConsentV2(GUID);
        
        assertEquals("<document/>", result.getDocumentContent());
    }

    @Test
    public void publishConsentV2() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockStudyService.getStudy(STUDY_ID)).thenReturn(STUDY);
        
        StatusMessage result = controller.publishConsentV2(GUID, DATETIME_STRING);
        assertEquals(result.getMessage(), "Consent document set as active.");

        verify(mockStudyConsentService).publishConsent(STUDY, mockSubpopulation,
                DateTime.parse(DATETIME_STRING).getMillis());
    }
}
