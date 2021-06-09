package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

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
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AdherenceService;

public class AdherenceControllerTest extends Mockito {

    @Mock
    AdherenceService mockService;

    @Mock
    AccountService mockAccountService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;

    @Spy
    @InjectMocks
    AdherenceController controller;
    
    @Captor
    ArgumentCaptor<AdherenceRecordList> listCaptor;
    
    @Captor
    ArgumentCaptor<AdherenceRecordsSearch> searchCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .build());
        session.setAppId(TEST_APP_ID);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AdherenceController.class);
        assertPost(AdherenceController.class, "updateAdherenceRecords");
        assertPost(AdherenceController.class, "searchForAdherenceRecordsForSelf");
        assertPost(AdherenceController.class, "searchForAdherenceRecords");
    }
    
    @Test
    public void updateAdherenceRecords() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(TEST_APP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(TEST_USER_ID));
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        mockRequestBody(mockRequest, list);
        
        StatusMessage retValue = controller.updateAdherenceRecords(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue, AdherenceController.SAVED_MSG);
        
        verify(mockService).updateAdherenceRecords(eq(TEST_APP_ID), listCaptor.capture());
        AdherenceRecordList recordsList = listCaptor.getValue();
        for (AdherenceRecord record : recordsList.getRecords()) {
            assertEquals(record.getStudyId(), TEST_STUDY_ID);
            assertEquals(record.getUserId(), TEST_USER_ID);
        }
    }    
    
    @Test
    public void updateAdherenceRecordsForSelf() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        mockRequestBody(mockRequest, list);
        
        StatusMessage retValue = controller.updateAdherenceRecordsForSelf(TEST_STUDY_ID);
        assertEquals(retValue, AdherenceController.SAVED_MSG);
        
        verify(mockService).updateAdherenceRecords(eq(TEST_APP_ID), listCaptor.capture());
        AdherenceRecordList recordsList = listCaptor.getValue();
        for (AdherenceRecord record : recordsList.getRecords()) {
            assertEquals(record.getStudyId(), TEST_STUDY_ID);
            assertEquals(record.getUserId(), TEST_USER_ID);
        }
    }

    @Test
    public void searchForAdherenceRecordsForSelf() throws Exception { 
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        List<AdherenceRecord> list = ImmutableList.of(rec1, rec2);
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<>(list, 100);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withOffsetBy(10).withPageSize(50).build();
        mockRequestBody(mockRequest, search);
        
        when(mockService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = controller
                .searchForAdherenceRecordsForSelf(TEST_STUDY_ID);
        assertSame(retValue, page);
        
        verify(mockService).getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture());
        AdherenceRecordsSearch captured = searchCaptor.getValue();
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getOffsetBy(), Integer.valueOf(10));
        assertEquals(captured.getPageSize(), Integer.valueOf(50));
    }

    @Test
    public void searchForAdherenceRecords() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(any(), any())).thenReturn(Optional.of("some-other-id"));

        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        List<AdherenceRecord> list = ImmutableList.of(rec1, rec2);
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<>(list, 100);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withOffsetBy(10).withPageSize(50).build();
        mockRequestBody(mockRequest, search);
        
        when(mockService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = controller
                .searchForAdherenceRecords(TEST_STUDY_ID, "some-other-id");
        assertSame(retValue, page);
        
        verify(mockService).getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture());
        AdherenceRecordsSearch captured = searchCaptor.getValue();
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getUserId(), "some-other-id");
        assertEquals(captured.getOffsetBy(), Integer.valueOf(10));
        assertEquals(captured.getPageSize(), Integer.valueOf(50));        
    }
}