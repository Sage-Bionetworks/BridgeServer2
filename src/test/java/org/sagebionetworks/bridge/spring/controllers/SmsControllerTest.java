package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertEquals;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.SmsService;
import org.sagebionetworks.bridge.services.StudyService;

public class SmsControllerTest extends Mockito {
    private static final String MESSAGE_ID = "my-message-id";

    private static final StudyParticipant PARTICIPANT_WITH_NO_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .build();
    private static final StudyParticipant PARTICIPANT_WITH_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .withPhone(PHONE).build();

    private static final Study DUMMY_STUDY;
    static {
        DUMMY_STUDY = Study.create();
        DUMMY_STUDY.setIdentifier(API_APP_ID);
    }

    private SmsController controller;
    private ParticipantService mockParticipantService;
    private SmsService mockSmsService;

    @BeforeMethod
    public void before() {
        // Mock study service.
        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(DUMMY_STUDY);

        // Mock SMS service.
        mockParticipantService = mock(ParticipantService.class);
        mockSmsService = mock(SmsService.class);

        // Set up controller.
        controller = spy(new SmsController());
        controller.setParticipantService(mockParticipantService);
        controller.setSmsService(mockSmsService);
        controller.setStudyService(mockStudyService);

        // Mock get session.
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SmsController.class);
        assertGet(SmsController.class, "getMostRecentMessage");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getMostRecentMessage_ParticipantWithNoPhone() {
        when(mockParticipantService.getParticipant(DUMMY_STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        controller.getMostRecentMessage(USER_ID);
    }

    @Test
    public void getMostRecentMessage_ParticipantWithPhone() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(DUMMY_STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        // Setup test. This method is a passthrough for SmsMessage, so just verify one attribute.
        SmsMessage svcOutput = SmsMessage.create();
        svcOutput.setMessageId(MESSAGE_ID);
        when(mockSmsService.getMostRecentMessage(PHONE.getNumber())).thenReturn(svcOutput);

        // Execute and verify.
        SmsMessage result = controller.getMostRecentMessage(USER_ID);

        assertEquals(result.getMessageId(), MESSAGE_ID);
    }
}
