package org.sagebionetworks.bridge.services;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutRequest;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutResult;
import com.amazonaws.services.sns.model.OptInPhoneNumberRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.time.DateUtils;

public class SmsServiceTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final long MOCK_NOW_MILLIS = DateUtils.convertToMillisFromEpoch("2018-10-17T16:21:52.749Z");
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;
    private static final String STUDY_SHORT_NAME = "My Study";
    private static final DateTimeZone TIME_ZONE = DateTimeZone.forOffsetHours(-7);
    private static final String USER_ID = "test-user";
    private static final TemplateRevision REVISION = TemplateRevision.create();
    static {
        REVISION.setDocumentContent(MESSAGE_BODY);
    }

    private static final StudyParticipant PARTICIPANT_WITH_TIME_ZONE = new StudyParticipant.Builder()
            .withId(USER_ID).withHealthCode(HEALTH_CODE).withTimeZone(TIME_ZONE).build();
    private static final StudyParticipant PARTICIPANT_WITHOUT_TIME_ZONE = new StudyParticipant.Builder()
            .withId(USER_ID).withHealthCode(HEALTH_CODE).build();

    private HealthDataService mockHealthDataService;
    private SmsMessageDao mockMessageDao;
    private ParticipantService mockParticipantService;
    private UploadSchemaService mockSchemaService;
    private AmazonSNSClient mockSnsClient;
    private App app;
    private SmsService svc;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @BeforeMethod
    public void before() {
        // Mock schema service to return dummy schema for message log. The schema is empty for the purposes of the
        // test, since we only care that it exists, not what's in it.
        mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, SmsService.MESSAGE_LOG_SCHEMA_ID,
                SmsService.MESSAGE_LOG_SCHEMA_REV)).thenReturn(UploadSchema.create());

        // Mock SMS providers.
        mockSnsClient = mock(AmazonSNSClient.class);
        when(mockSnsClient.publish(any())).thenReturn(new PublishResult().withMessageId(MESSAGE_ID));

        // Mock study service. This is only used to get the study short name.
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setShortName(STUDY_SHORT_NAME);

        // Mock other DAOs and services.
        mockHealthDataService = mock(HealthDataService.class);
        mockMessageDao = mock(SmsMessageDao.class);
        mockParticipantService = mock(ParticipantService.class);

        // Set up service.
        svc = new SmsService();
        svc.setHealthDataService(mockHealthDataService);
        svc.setMessageDao(mockMessageDao);
        svc.setParticipantService(mockParticipantService);
        svc.setSchemaService(mockSchemaService);
        svc.setSnsClient(mockSnsClient);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void sendTransactionalSMSMessageOK() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(HEALTH_CODE, provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(request.getPhoneNumber(), TestConstants.PHONE.getNumber());
        assertEquals(request.getMessage(), MESSAGE_BODY);
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue(),
                "Transactional");
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue(),
                STUDY_SHORT_NAME);

        // We log the SMS message to DDB and to health data.
        verifyLoggedSmsMessage(HEALTH_CODE, MESSAGE_BODY, SmsType.TRANSACTIONAL);
        verifyHealthData(PARTICIPANT_WITH_TIME_ZONE, TIME_ZONE, SmsType.TRANSACTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendPromotionalSMSMessageOK() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(HEALTH_CODE, provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(request.getPhoneNumber(), TestConstants.PHONE.getNumber());
        assertEquals(request.getMessage(), MESSAGE_BODY);
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue(), "Promotional");
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue(),
                STUDY_SHORT_NAME);

        // We log the SMS message to DDB and to health data.
        verifyLoggedSmsMessage(HEALTH_CODE, MESSAGE_BODY, SmsType.PROMOTIONAL);
        verifyHealthData(PARTICIPANT_WITH_TIME_ZONE, TIME_ZONE, SmsType.PROMOTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendSmsMessage_NullUserIdOkay() throws Exception {
        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(null, provider);

        // Everything else is verified. Just verified that the sent message contains no health code.
        verifyLoggedSmsMessage(null, MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We submit no health data.
        verify(mockHealthDataService, never()).submitHealthData(any(), any(), any());
    }

    // branch coverage
    @Test
    public void sendSmsMessage_NoParticipant() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(null);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(null, provider);

        // Everything else is verified. Just verified that the sent message contains no health code.
        verifyLoggedSmsMessage(null, MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We submit no health data.
        verify(mockHealthDataService, never()).submitHealthData(any(), any(), any());
    }

    @Test
    public void sendSmsMessage_ParticipantHasNoTimeZone() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITHOUT_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(HEALTH_CODE, provider);

        // Everything else is verified. Just verify the timezone in the health data.
        verifyHealthData(PARTICIPANT_WITHOUT_TIME_ZONE, DateTimeZone.UTC, SmsType.PROMOTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendSmsMessage_SchemaDoesNotExist() {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Schema Service has no schema (throws).
        when(mockSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, SmsService.MESSAGE_LOG_SCHEMA_ID,
                SmsService.MESSAGE_LOG_SCHEMA_REV)).thenThrow(EntityNotFoundException.class);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(REVISION)
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(HEALTH_CODE, provider);

        // Everything else is verified. Just verify that we create the new schema.
        ArgumentCaptor<UploadSchema> schemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(mockSchemaService).createSchemaRevisionV4(eq(TEST_APP_ID), schemaCaptor.capture());

        UploadSchema schema = schemaCaptor.getValue();
        assertEquals(schema.getSchemaId(), SmsService.MESSAGE_LOG_SCHEMA_ID);
        assertEquals(schema.getRevision(), SmsService.MESSAGE_LOG_SCHEMA_REV);
        assertEquals(schema.getName(), SmsService.MESSAGE_LOG_SCHEMA_NAME);
        assertEquals(schema.getSchemaType(), UploadSchemaType.IOS_DATA);

        List<UploadFieldDefinition> fieldDefList = schema.getFieldDefinitions();
        assertEquals(fieldDefList.size(), 3);

        assertEquals(fieldDefList.get(0).getName(), SmsService.FIELD_NAME_SENT_ON);
        assertEquals(fieldDefList.get(0).getType(), UploadFieldType.TIMESTAMP);

        assertEquals(fieldDefList.get(1).getName(), SmsService.FIELD_NAME_SMS_TYPE);
        assertEquals(fieldDefList.get(1).getType(), UploadFieldType.STRING);
        assertEquals(fieldDefList.get(1).getMaxLength().intValue(), SmsType.VALUE_MAX_LENGTH);

        assertEquals(fieldDefList.get(2).getName(), SmsService.FIELD_NAME_MESSAGE_BODY);
        assertEquals(fieldDefList.get(2).getType(), UploadFieldType.STRING);
        assertTrue(fieldDefList.get(2).isUnboundedText());
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void sendSMSMessageTooLongInvalid() {
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent(randomAlphabetic(601));
        
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(app)
                .withTemplateRevision(revision)
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(HEALTH_CODE, provider);
    }

    private void verifyLoggedSmsMessage(String expectedHealthCode, String expectedMessage, SmsType expectedSmsType) {
        ArgumentCaptor<SmsMessage> loggedMessageCaptor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(mockMessageDao).logMessage(loggedMessageCaptor.capture());

        SmsMessage loggedMessage = loggedMessageCaptor.getValue();
        assertEquals(loggedMessage.getPhoneNumber(), TestConstants.PHONE.getNumber());
        assertEquals(loggedMessage.getSentOn(), MOCK_NOW_MILLIS);
        assertEquals(loggedMessage.getHealthCode(), expectedHealthCode);
        assertEquals(loggedMessage.getMessageBody(), expectedMessage);
        assertEquals(loggedMessage.getMessageId(), MESSAGE_ID);
        assertEquals(loggedMessage.getSmsType(), expectedSmsType);
        assertEquals(loggedMessage.getStudyId(), TEST_APP_ID);
    }

    private void verifyHealthData(StudyParticipant expectedParticipant, DateTimeZone expectedTimeZone,
            SmsType expectedSmsType, String expectedMessage) throws Exception {
        ArgumentCaptor<HealthDataSubmission> healthDataCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(mockHealthDataService).submitHealthData(eq(TEST_APP_ID), same(expectedParticipant),
                healthDataCaptor.capture());
        HealthDataSubmission healthData = healthDataCaptor.getValue();

        // Verify simple attributes.
        assertEquals(healthData.getAppVersion(), SmsService.BRIDGE_SERVER_APP_VERSION);
        assertEquals(healthData.getPhoneInfo(), SmsService.BRIDGE_SERVER_PHONE_INFO);
        assertEquals(healthData.getSchemaId(), SmsService.MESSAGE_LOG_SCHEMA_ID);
        assertEquals(healthData.getSchemaRevision().intValue(), SmsService.MESSAGE_LOG_SCHEMA_REV);

        DateTime createdOn = healthData.getCreatedOn();
        assertEquals(createdOn.getMillis(), MOCK_NOW_MILLIS);
        assertEquals(createdOn.getZone().getOffset(createdOn), expectedTimeZone.getOffset(createdOn));

        // Assert health data.
        JsonNode healthDataNode = healthData.getData();
        assertEquals(healthDataNode.get(SmsService.FIELD_NAME_SMS_TYPE).textValue(), expectedSmsType.getValue());
        assertEquals(healthDataNode.get(SmsService.FIELD_NAME_MESSAGE_BODY).textValue(), expectedMessage);

        // sentOn is createdOn in string form.
        assertEquals(healthDataNode.get(SmsService.FIELD_NAME_SENT_ON).textValue(), createdOn.toString());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getMostRecentMessage_NullPhoneNumber() {
        svc.getMostRecentMessage(null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getMostRecentMessage_EmptyPhoneNumber() {
        svc.getMostRecentMessage("");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getMostRecentMessage_BlankPhoneNumber() {
        svc.getMostRecentMessage("   ");
    }

    @Test
    public void getMostRecentMessage_NormalCase() {
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        SmsMessage svcOutput = svc.getMostRecentMessage(PHONE_NUMBER);
        assertSame(svcOutput, daoOutput);
    }

    private static SmsMessage makeValidSmsMessage() {
        SmsMessage message = SmsMessage.create();
        message.setPhoneNumber(PHONE_NUMBER);
        message.setSentOn(SENT_ON);
        message.setMessageId(MESSAGE_ID);
        message.setMessageBody(MESSAGE_BODY);
        message.setSmsType(SmsType.PROMOTIONAL);
        message.setStudyId(TEST_APP_ID);
        return message;
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void optInPhoneNumber_NullUserId() {
        svc.optInPhoneNumber(null, TestConstants.PHONE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void optInPhoneNumber_EmptyUserId() {
        svc.optInPhoneNumber("", TestConstants.PHONE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void optInPhoneNumber_BlankUserId() {
        svc.optInPhoneNumber("   ", TestConstants.PHONE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void optInPhoneNumber_NullPhone() {
        svc.optInPhoneNumber(USER_ID, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void optInPhoneNumber_InvalidPhone() {
        svc.optInPhoneNumber(USER_ID, new Phone("NaN", "US"));
    }

    @Test
    public void createPhoneParticipant_PhoneNotOptedOut() {
        // Mock SNS client to return false.
        when(mockSnsClient.checkIfPhoneNumberIsOptedOut(any())).thenReturn(new CheckIfPhoneNumberIsOptedOutResult()
                .withIsOptedOut(false));

        // Execute.
        svc.optInPhoneNumber(USER_ID, TestConstants.PHONE);

        // Verify calls to SNS.
        ArgumentCaptor<CheckIfPhoneNumberIsOptedOutRequest> checkRequestCaptor = ArgumentCaptor.forClass(
                CheckIfPhoneNumberIsOptedOutRequest.class);
        verify(mockSnsClient).checkIfPhoneNumberIsOptedOut(checkRequestCaptor.capture());
        CheckIfPhoneNumberIsOptedOutRequest checkRequest = checkRequestCaptor.getValue();
        assertEquals(checkRequest.getPhoneNumber(), TestConstants.PHONE.getNumber());

        verify(mockSnsClient, never()).optInPhoneNumber(any());
    }

    @Test
    public void createPhoneParticipant_OptPhoneBackIn() {
        // Mock SNS client to return true.
        when(mockSnsClient.checkIfPhoneNumberIsOptedOut(any())).thenReturn(new CheckIfPhoneNumberIsOptedOutResult()
                .withIsOptedOut(true));

        // Execute.
        svc.optInPhoneNumber(USER_ID, TestConstants.PHONE);

        // Verify calls to SNS.
        ArgumentCaptor<CheckIfPhoneNumberIsOptedOutRequest> checkRequestCaptor = ArgumentCaptor.forClass(
                CheckIfPhoneNumberIsOptedOutRequest.class);
        verify(mockSnsClient).checkIfPhoneNumberIsOptedOut(checkRequestCaptor.capture());
        CheckIfPhoneNumberIsOptedOutRequest checkRequest = checkRequestCaptor.getValue();
        assertEquals(checkRequest.getPhoneNumber(), TestConstants.PHONE.getNumber());

        ArgumentCaptor<OptInPhoneNumberRequest> optInRequestCaptor = ArgumentCaptor.forClass(
                OptInPhoneNumberRequest.class);
        verify(mockSnsClient).optInPhoneNumber(optInRequestCaptor.capture());
        OptInPhoneNumberRequest optInRequest = optInRequestCaptor.getValue();
        assertEquals(optInRequest.getPhoneNumber(), TestConstants.PHONE.getNumber());
    }
}
