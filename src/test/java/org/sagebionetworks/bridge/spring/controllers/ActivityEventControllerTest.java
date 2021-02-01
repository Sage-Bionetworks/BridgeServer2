package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertEquals;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.AppService;

public class ActivityEventControllerTest extends Mockito {

    @Mock
    private AppService appService;
    
    @Mock
    private ActivityEventService mockActivityEventService;
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    private ActivityEventController controller = new ActivityEventController();

    private App app;
    
    @BeforeMethod
    private void before() {
        MockitoAnnotations.initMocks(this);
        
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build());
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        app = App.create();
        when(appService.getApp(TEST_APP_ID)).thenReturn(app);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ActivityEventController.class);
        assertCreate(ActivityEventController.class, "createCustomActivityEvent");
        assertGet(ActivityEventController.class, "getSelfActivityEvents");        
        assertGet(ActivityEventController.class, "getSelfActivityEvents");
    }
    
    @Test
    public void createGlobalCustomActivityEvent() throws Exception {
        String json = TestUtils.createJson("{'eventKey':'foo','timestamp':'%s'}", TIMESTAMP.toString());
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        StatusMessage message = controller.createCustomActivityEvent();
        assertEquals("Event recorded", message.getMessage());
        
        verify(mockActivityEventService).publishCustomEvent(app, null, HEALTH_CODE, "foo", TIMESTAMP);
    }
    
    @Test
    public void createScopedStudyCustomActivityEvent() throws Exception {
        String json = TestUtils.createJson("{'eventKey':'foo','timestamp':'%s'}", TIMESTAMP.toString());
        doReturn(TestUtils.toInputStream(json)).when(mockRequest).getInputStream();
        
        StatusMessage message = controller.createCustomActivityEvent();
        assertEquals("Event recorded", message.getMessage());
        
        verify(mockActivityEventService).publishCustomEvent(app, null, HEALTH_CODE, "foo", TIMESTAMP);
    }
    
    @Test
    public void getSelfActivityEvents() throws Exception {
        DynamoActivityEvent event = new DynamoActivityEvent();
        event.setEventId("foo");
        event.setHealthCode(HEALTH_CODE);
        event.setTimestamp(TIMESTAMP.getMillis());
        
        List<ActivityEvent> activityEvents = ImmutableList.of(event);
        when(mockActivityEventService.getActivityEventList(TEST_APP_ID, null, HEALTH_CODE)).thenReturn(activityEvents);
        
        ResourceList<ActivityEvent> list = controller.getSelfActivityEvents();
        ActivityEvent returnedEvent = list.getItems().get(0);
        assertEquals("foo", returnedEvent.getEventId());
        assertEquals(new Long(TIMESTAMP.getMillis()), returnedEvent.getTimestamp());
        
        verify(mockActivityEventService).getActivityEventList(TEST_APP_ID, null, HEALTH_CODE);
    }
}
