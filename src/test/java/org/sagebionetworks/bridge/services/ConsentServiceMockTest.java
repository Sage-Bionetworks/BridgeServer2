package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("ConstantConditions")
public class ConsentServiceMockTest extends Mockito {
    private static final String SHORT_URL = "https://ws.sagebridge.org/r/XXXXX";
    private static final String LONG_URL = "http://sagebionetworks.org/platforms/";
    private static final Withdrawal WITHDRAWAL = new Withdrawal("For reasons.");
    private static final String EXTERNAL_ID = "external-id";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long SIGNED_ON = 1446044925219L;
    private static final long WITHDREW_ON = SIGNED_ON + 10000;
    private static final long CONSENT_CREATED_ON = 1446044814108L;
    private static final String ID = "user-id";
    private static final String HEALTH_CODE = "health-code";
    private static final String EMAIL = "email@email.com";
    private static final SubpopulationGuid SECOND_SUBPOP = SubpopulationGuid.create("anotherSubpop");
    private static final ConsentSignature CONSENT_SIGNATURE = new ConsentSignature.Builder().withName("Test User")
            .withBirthdate("1980-01-01").withSignedOn(SIGNED_ON).build();
    private static final ConsentSignature WITHDRAWN_CONSENT_SIGNATURE = new ConsentSignature.Builder()
            .withConsentSignature(CONSENT_SIGNATURE).withSignedOn(SIGNED_ON - 20000).withWithdrewOn(SIGNED_ON - 10000)
            .build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .withId(ID).withEmail(EMAIL).withEmailVerified(Boolean.TRUE)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withExternalId(EXTERNAL_ID).build();
    private static final StudyParticipant PHONE_PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .withId(ID).withPhone(TestConstants.PHONE).withPhoneVerified(Boolean.TRUE)
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withExternalId(EXTERNAL_ID).build();
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder().withUserId(PARTICIPANT.getId())
            .withAppId(TEST_APP_ID).build();

    @Spy
    @InjectMocks
    private ConsentService consentService;

    private App app;

    @Mock
    private EnrollmentService mockEnrollmentService;
    @Mock
    private AccountService accountService;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private SmsService smsService;
    @Mock
    private StudyConsentService studyConsentService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private NotificationsService notificationsService;
    @Mock
    private S3Helper s3Helper;
    @Mock
    private UrlShortenerService urlShortenerService;
    @Mock
    private Subpopulation subpopulation;
    @Mock
    private StudyConsentView studyConsentView;
    @Mock
    private TemplateService templateService;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> emailCaptor;
    @Captor
    private ArgumentCaptor<SmsMessageProvider> smsProviderCaptor;
    @Captor
    private ArgumentCaptor<Account> accountCaptor;
    @Captor
    private ArgumentCaptor<Enrollment> enrollmentCaptor;

    private Account account;

    @BeforeMethod
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);

        String documentString = IOUtils.toString(
                new FileInputStream(new ClassPathResource("conf/app-defaults/consent-page.xhtml").getFile()));

        consentService.setConsentTemplate(new ByteArrayResource((documentString).getBytes()));

        app = TestUtils.getValidApp(ConsentServiceMockTest.class);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("signedConsent subject");
        revision.setDocumentContent("signedConsent body");
        revision.setMimeType(MimeType.HTML);
        when(templateService.getRevisionForUser(app, EMAIL_SIGNED_CONSENT)).thenReturn(revision);

        account = Account.create();
        account.setId(ID);

        when(accountService.getAccount(any(AccountId.class))).thenReturn(account);

        when(s3Helper.generatePresignedUrl(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), any(), any(),
                eq(HttpMethod.GET))).thenReturn(new URL(LONG_URL));
        when(urlShortenerService.shortenUrl(LONG_URL, BridgeConstants.SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS))
                .thenReturn(SHORT_URL);

        when(studyConsentView.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentView.getDocumentContent())
                .thenReturn("<p>This is content of the final HTML document we assemble.</p>");
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(studyConsentView);
        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void userCannotGetConsentSignatureForSubpopulationToWhichTheyAreNotMapped() {
        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID))
                .thenThrow(new EntityNotFoundException(Subpopulation.class));

        consentService.getConsentSignature(app, SUBPOP_GUID, PARTICIPANT.getId());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void userCannotGetConsentSignatureWithNoActiveSig() {
        // set signatures in the account... but not the right signatures
        account.setConsentSignatureHistory(SubpopulationGuid.create("second-subpop"),
                ImmutableList.of(CONSENT_SIGNATURE));

        consentService.getConsentSignature(app, SUBPOP_GUID, PARTICIPANT.getId());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void userCannotConsentToSubpopulationToWhichTheyAreNotMapped() {
        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID))
                .thenThrow(new EntityNotFoundException(Subpopulation.class));

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                false);
    }

    @Test
    public void giveConsentSuccess() {
        // Account already has a withdrawn consent, to make sure we're correctly appending consents.
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(WITHDRAWN_CONSENT_SIGNATURE));

        // Consent signature should have a withdrawOn and a dummy consentCreatedOn just to make sure that we're setting
        // those properly in ConsentService.
        ConsentSignature sig = new ConsentSignature.Builder().withConsentSignature(CONSENT_SIGNATURE)
                .withConsentCreatedOn(CONSENT_CREATED_ON + 20000).withWithdrewOn(12345L).build();

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        // verify consents were set on account properly
        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));

        Account updatedAccount = accountCaptor.getValue();
        List<ConsentSignature> updatedConsentList = updatedAccount.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(updatedConsentList.size(), 2);

        // First consent is the same.
        assertEquals(updatedConsentList.get(0), WITHDRAWN_CONSENT_SIGNATURE);

        // Second consent has consentCreatedOn added and withdrawnOn clear, but is otherwise the same.
        assertEquals(updatedConsentList.get(1).getBirthdate(), sig.getBirthdate());
        assertEquals(updatedConsentList.get(1).getName(), sig.getName());
        assertEquals(updatedConsentList.get(1).getSignedOn(), sig.getSignedOn());
        assertEquals(updatedConsentList.get(1).getConsentCreatedOn(), CONSENT_CREATED_ON);
        assertNull(updatedConsentList.get(1).getWithdrewOn());

        // Consent we send to activityEventService is same as the second consent.
        verify(activityEventService).publishEnrollmentEvent(app, PARTICIPANT.getHealthCode(),
                updatedConsentList.get(1));

        verify(sendMailService).sendEmail(emailCaptor.capture());

        // We notify the app administrator and send a copy to the user.
        BasicEmailProvider email = emailCaptor.getValue();
        assertEquals(email.getType(), EmailType.SIGN_CONSENT);

        Set<String> recipients = email.getRecipientEmails();
        assertEquals(recipients.size(), 2);
        assertTrue(recipients.contains(app.getConsentNotificationEmail()));
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }

    @Test
    public void emailConsentAgreementSuccess() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        consentService.resendConsentAgreement(app, SUBPOP_GUID, PARTICIPANT);

        verify(sendMailService).sendEmail(emailCaptor.capture());
        BasicEmailProvider email = emailCaptor.getValue();
        assertEquals(email.getRecipientEmails().size(), 1);
        assertTrue(email.getRecipientEmails().contains(PARTICIPANT.getEmail()));
        assertEquals(email.getType(), EmailType.RESEND_CONSENT);
    }

    @Test
    public void noConsentIfTooYoung() {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withConsentSignature(CONSENT_SIGNATURE)
                .withBirthdate("2018-05-12").build();

        try {
            consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, consentSignature, SharingScope.NO_SHARING,
                    false);
            fail("Exception expected.");
        } catch (InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
            verifyNoMoreInteractions(accountService);
        }
    }

    @Test
    public void noConsentIfAlreadyConsented() {
        ConsentSignature sig = new ConsentSignature.Builder().withConsentCreatedOn(CONSENT_CREATED_ON)
                .withSignedOn(DateTime.now().getMillis()).withName("A Name").withBirthdate("1960-10-10").build();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(sig));

        try {
            consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE,
                    SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch (EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
            verify(accountService).getAccount(any());
            verifyNoMoreInteractions(accountService);
        }
    }

    @Test
    public void noConsentIfDaoFails() {
        try {
            consentService.consentToResearch(app, SubpopulationGuid.create("badGuid"), PARTICIPANT, CONSENT_SIGNATURE,
                    SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch (Throwable e) {
            verifyNoMoreInteractions(activityEventService);
            verify(accountService).getAccount(any());
            verifyNoMoreInteractions(accountService);
        }
    }

    @Test
    public void withdrawConsentWithParticipant() throws Exception {
        when(subpopulation.getStudyIdsAssignedOnConsent()).thenReturn(ImmutableSet.of(TEST_STUDY_ID));
        when(subpopulation.isRequired()).thenReturn(true);
        
        account.setEmail(EMAIL);
        account.setHealthCode(PARTICIPANT.getHealthCode());

        // Add two consents to the account, one withdrawn, one active. This tests to make sure we're not accidentally
        // dropping withdrawn consents from the history.
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "otherStudy", ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, ID);
        account.setEnrollments(ImmutableSet.of(en1, en2));
        
        account.setConsentSignatureHistory(SUBPOP_GUID,
                ImmutableList.of(WITHDRAWN_CONSENT_SIGNATURE, CONSENT_SIGNATURE));

        // Execute and validate.
        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON + 10000);

        verify(accountService).getAccount(CONTEXT.getAccountId());
        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        verify(sendMailService).sendEmail(emailCaptor.capture());

        Account account = accountCaptor.getValue();

        // Both signatures are there, and the second one is now withdrawn.
        assertNull(account.getActiveConsentSignature(SUBPOP_GUID));

        List<ConsentSignature> updatedConsentList = account.getConsentSignatureHistory(SUBPOP_GUID);
        assertEquals(updatedConsentList.size(), 2);

        // First consent is unchanged.
        assertEquals(updatedConsentList.get(0), WITHDRAWN_CONSENT_SIGNATURE);

        // Second consent has withdrawnOn tacked on, but is otherwise the same.
        assertEquals(updatedConsentList.get(1).getBirthdate(), CONSENT_SIGNATURE.getBirthdate());
        assertEquals(updatedConsentList.get(1).getName(), CONSENT_SIGNATURE.getName());
        assertEquals(updatedConsentList.get(1).getSignedOn(), CONSENT_SIGNATURE.getSignedOn());
        assertEquals(updatedConsentList.get(1).getWithdrewOn().longValue(), SIGNED_ON + 10000);

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();

        assertEquals(email.getSenderAddress(),
                "\"Test App [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>");
        assertEquals(email.getRecipientAddresses().get(0), "bridge-testing+consent@sagebase.org");
        assertEquals(email.getSubject(), "Notification of consent withdrawal for Test App [ConsentServiceMockTest]");
        assertEquals(email.getMessageParts().get(0).getContent(), "<p>User   &lt;" + EMAIL
                + "&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>");
        
        verify(mockEnrollmentService, times(1)).unenroll(eq(account), enrollmentCaptor.capture());
        Enrollment withdrawalEnrollment = enrollmentCaptor.getValue();
        assertEquals(withdrawalEnrollment.getWithdrawalNote(), WITHDRAWAL.getReason());
        assertEquals(withdrawalEnrollment.getWithdrawnOn().getMillis(), SIGNED_ON + 10000);
        assertEquals(withdrawalEnrollment.getAppId(), app.getIdentifier());
        assertEquals(withdrawalEnrollment.getStudyId(), TEST_STUDY_ID);
        assertEquals(withdrawalEnrollment.getAccountId(), ID);
    }

    @Test
    public void withdrawConsentRemovesDataGroups() throws Exception {
        Set<String> dataGroups = Sets.newHashSet(USER_DATA_GROUPS);
        dataGroups.add("leaveBehind1");
        dataGroups.add("leaveBehind2");
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        account.setDataGroups(dataGroups);
        when(subpopulation.getDataGroupsAssignedWhileConsented()).thenReturn(TestConstants.USER_DATA_GROUPS);
        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
        when(accountService.getAccount(any())).thenReturn(account);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        assertEquals(account.getDataGroups(), ImmutableSet.of("leaveBehind1", "leaveBehind2"));
        verify(subpopulation).getDataGroupsAssignedWhileConsented();
    }

    @Test
    public void withdrawConsentWithAccount() throws Exception {
        setupWithdrawTest();
        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);

        verify(accountService).updateAccount(account, null);
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));

        // Contents of call are tested in prior test where participant is used
    }

    @Test
    public void withdrawFromAppWithEmail() throws Exception {
        setupWithdrawTest();

        consentService.withdrawFromApp(app, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        assertEquals(account.getSharingScope(), SharingScope.NO_SHARING);

        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();

        assertEquals(email.getSenderAddress(),
                "\"Test App [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>");
        assertEquals(email.getRecipientAddresses().get(0), "bridge-testing+consent@sagebase.org");
        assertEquals(email.getSubject(), "Notification of consent withdrawal for Test App [ConsentServiceMockTest]");
        assertEquals(email.getMessageParts().get(0).getContent(), "<p>User Allen Wrench &lt;" + EMAIL
                + "&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>");

        Account updatedAccount = accountCaptor.getValue();
        assertEquals(account.getSharingScope(), SharingScope.NO_SHARING);
        assertNull(account.getFirstName());
        assertNull(account.getLastName());
        assertFalse(account.getNotifyByEmail());
        assertNull(account.getEmail());
        assertFalse(account.getEmailVerified());
        assertNull(account.getPhone());
        assertFalse(account.getPhoneVerified());
        // This association is not removed
        assertEquals(account.getEnrollments().size(), 1);
        Enrollment enrollment = account.getEnrollments().iterator().next();
        assertEquals(enrollment.getStudyId(), "studyId");
        assertEquals(enrollment.getExternalId(), "anExternalId");
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }
    }

    @Test
    public void withdrawFromAppWithPhone() {
        account.setPhone(TestConstants.PHONE);
        account.setHealthCode(PARTICIPANT.getHealthCode());
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        consentService.withdrawFromApp(app, PHONE_PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        assertEquals(account.getSharingScope(), SharingScope.NO_SHARING);
        verify(sendMailService, never()).sendEmail(any(MimeTypeEmailProvider.class));

        Account updatedAccount = accountCaptor.getValue();
        for (List<ConsentSignature> signatures : updatedAccount.getAllConsentSignatureHistories().values()) {
            for (ConsentSignature sig : signatures) {
                assertNotNull(sig.getWithdrewOn());
            }
        }

        verify(notificationsService).deleteAllRegistrations(app.getIdentifier(), HEALTH_CODE);
    }

    @Test
    public void withdrawFromAppRemovesDataGroups() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        Set<String> dataGroups = new HashSet<>(TestConstants.USER_DATA_GROUPS);
        dataGroups.add("remainingDataGroup1");
        dataGroups.add("remainingDataGroup2");
        account.setDataGroups(dataGroups);

        when(subpopulation.getDataGroupsAssignedWhileConsented()).thenReturn(TestConstants.USER_DATA_GROUPS);
        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
        when(accountService.getAccount(any())).thenReturn(account);

        consentService.withdrawFromApp(app, PARTICIPANT, WITHDRAWAL, WITHDREW_ON);

        assertEquals(account.getDataGroups(), ImmutableSet.of("remainingDataGroup1", "remainingDataGroup2"));
        verify(subpopulation).getDataGroupsAssignedWhileConsented();
        verify(subpopulation, never()).getStudyIdsAssignedOnConsent();
    }

    @Test
    public void accountFailureConsistent() {
        when(accountService.getAccount(any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        try {
            consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
            fail("Should have thrown an exception");
        } catch (BridgeServiceException e) {
            // expected exception
        }
        verifyNoMoreInteractions(sendMailService);
    }

    @Test
    public void withdrawWithNotificationEmailVerifiedNull() throws Exception {
        setupWithdrawTest();
        app.setConsentNotificationEmailVerified(null);
        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        app.setConsentNotificationEmailVerified(false);
        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawFromAppNotificationEmailVerifiedNull() throws Exception {
        setupWithdrawTest();
        app.setConsentNotificationEmailVerified(null);
        consentService.withdrawFromApp(app, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        // For backwards-compatibility, verified=null means the email is verified.
        verify(sendMailService).sendEmail(any(WithdrawConsentEmailProvider.class));
    }

    @Test
    public void withdrawFromAppWithNotificationEmailVerifiedFalse() throws Exception {
        setupWithdrawTest();
        app.setConsentNotificationEmailVerified(false);
        consentService.withdrawFromApp(app, PARTICIPANT, WITHDRAWAL, SIGNED_ON);

        // verified=false means the email is never sent.
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawFromOneRequiredConsentSetsAccountToNoSharing() throws Exception {
        setupWithdrawTest(true, false);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        assertEquals(account.getSharingScope(), SharingScope.NO_SHARING);
        assertEquals(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn(), new Long(WITHDREW_ON));
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService).deleteAllRegistrations(app.getIdentifier(), HEALTH_CODE);
    }

    @Test
    public void withdrawFromOneOfTwoRequiredConsentsSetsAcountToNoSharing() throws Exception {
        setupWithdrawTest(true, true);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        // You must sign all required consents to sign.
        assertEquals(account.getSharingScope(), SharingScope.NO_SHARING);
        assertEquals(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn(), new Long(WITHDREW_ON));
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService).deleteAllRegistrations(app.getIdentifier(), HEALTH_CODE);
    }

    @Test
    public void withdrawFromOneOptionalConsentDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, true);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        // Not changed because all the required consents are still signed.
        assertEquals(account.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);

        assertEquals(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn(), new Long(WITHDREW_ON));
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService, never()).deleteAllRegistrations(any(), any());
    }

    @Test
    public void withdrawFromOneOfTwoOptionalConsentsDoesNotChangeSharing() throws Exception {
        setupWithdrawTest(false, false);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        // Not changed because all the required consents are still signed.
        assertEquals(account.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn(), new Long(WITHDREW_ON));
        assertNull(account.getConsentSignatureHistory(SECOND_SUBPOP).get(0).getWithdrewOn());

        verify(notificationsService, never()).deleteAllRegistrations(any(), any());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void withdrawConsentWhenAllConsentsAreWithdrawn() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(true);

        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(true);

        ConsentSignature withdrawnSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withWithdrewOn(WITHDREW_ON).withSignedOn(SIGNED_ON).build();

        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE, withdrawnSignature));
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(withdrawnSignature));

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);
    }

    @Test
    public void consentToResearchNoConsentAdministratorEmail() {
        app.setConsentNotificationEmail(null);

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(recipients.size(), 1);
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }

    @Test
    public void consentToResearchSuppressEmailNotification() {
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(recipients.size(), 1);
        assertTrue(recipients.contains(app.getConsentNotificationEmail()));
    }

    @Test
    public void consentToResearchNoParticipantEmail() {
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();

        consentService.consentToResearch(app, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);

        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(recipients.size(), 1);
        assertTrue(recipients.contains(app.getConsentNotificationEmail()));
    }

    @Test
    public void consentToResearchNoRecipients() {
        // easiest to test this if we null out the app consent email.
        app.setConsentNotificationEmail(null);
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);

        // sendEmail = true because the system would otherwise send it based on the call, but hasn't looked to suppress
        // yet.
        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.ALL_QUALIFIED_RESEARCHERS, true);

        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void consentToResearchSucceedsWithoutNotification() {
        // In this call, we explicitly override any other settings to suppress notifications
        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                false);

        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawConsentNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        app.setConsentNotificationEmail(null);

        consentService.withdrawConsent(app, SUBPOP_GUID, PARTICIPANT, CONTEXT, WITHDRAWAL, WITHDREW_ON);

        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void withdrawFromAppConsentsNoConsentAdministratorEmail() {
        setupWithdrawTest(true, true);
        app.setConsentNotificationEmail(null);

        when(subpopService.getSubpopulation(app.getIdentifier(), SECOND_SUBPOP)).thenReturn(subpopulation);

        consentService.withdrawFromApp(app, PARTICIPANT, WITHDRAWAL, WITHDREW_ON);

        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void emailConsentAgreementNoConsentAdministratorEmail() {
        app.setConsentNotificationEmail(null);
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        consentService.resendConsentAgreement(app, SUBPOP_GUID, PARTICIPANT);

        verify(sendMailService).sendEmail(emailCaptor.capture());
        assertEquals(emailCaptor.getValue().getRecipientEmails().size(), 1);
        assertEquals(emailCaptor.getValue().getRecipientEmails().iterator().next(), PARTICIPANT.getEmail());
    }

    @Test
    public void emailConsentAgreementDoesNotSuppressEmailNotification() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        consentService.resendConsentAgreement(app, SUBPOP_GUID, PARTICIPANT);

        verify(subpopulation, never()).isAutoSendConsentSuppressed();

        // Despite explicitly suppressing email, if the user makes this call, we will send the email.
        verify(sendMailService).sendEmail(emailCaptor.capture());
        Set<String> recipients = emailCaptor.getValue().getRecipientEmails();
        assertEquals(recipients.size(), 1);
        assertTrue(recipients.contains(PARTICIPANT.getEmail()));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void emailConsentAgreementNoParticipantEmail() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();

        consentService.resendConsentAgreement(app, SUBPOP_GUID, noEmail);
    }

    @Test
    public void emailConsentAgreementNoRecipients() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        // easiest to test this if we null out the app consent email.
        app.setConsentNotificationEmail(null);

        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        try {
            consentService.resendConsentAgreement(app, SUBPOP_GUID, noEmail);
            fail("Should have thrown an exception");
        } catch (BadRequestException e) {
        }
        verify(sendMailService, never()).sendEmail(any());
    }

    // Tests of the construction of recipients for email, originally part of special email builder.

    @Test
    public void emailConsentAgreementNotificationEmailVerifiedFalse() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        app.setConsentNotificationEmailVerified(false);

        consentService.resendConsentAgreement(app, SUBPOP_GUID, PARTICIPANT);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate email recipients does not include consent notification email.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(recipientList.size(), 1);
        assertEquals(recipientList.get(0), "email@email.com");
    }

    @Test
    public void emailConsentAgreementWithoutStudyRecipientsDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        app.setConsentNotificationEmail(null);

        consentService.resendConsentAgreement(app, SUBPOP_GUID, PARTICIPANT);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(emailCaptor.getValue().getRecipientEmails().iterator().next(), PARTICIPANT.getEmail());
    }

    @Test
    public void consentToResearchNoNotificationEmailSends() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        // For backwards-compatibility, consentNotificationEmailVerified=null means we still send it to the consent
        // notification email.
        app.setConsentNotificationEmailVerified(null);

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSubject(), "signedConsent subject");
        assertEquals(email.getSenderAddress(),
                "\"Test App [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>");
        assertEquals(Sets.newHashSet(email.getRecipientAddresses()),
                Sets.newHashSet("email@email.com", "bridge-testing+consent@sagebase.org"));
    }

    @Test
    public void consentToResearchNoNotificationEmailVerifiedSends() throws Exception {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        app.setConsentNotificationEmailVerified(false);

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        MimeTypeEmailProvider provider = emailCaptor.getValue();

        // Validate email recipients does not include consent notification email.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(recipientList.size(), 1);
        assertEquals(recipientList.get(0), "email@email.com");
    }

    @Test
    public void consentToResearchWithNoRecipientsDoesNotSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        app.setConsentNotificationEmail(null);

        // The provider reports that there are no addresses to send to, which is correct
        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();
        consentService.consentToResearch(app, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);

        verify(sendMailService, never()).sendEmail(emailCaptor.capture());
    }

    @Test
    public void consentToResearchWithoutStudyRecipientsDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        app.setConsentNotificationEmail(null);

        consentService.consentToResearch(app, SUBPOP_GUID, PARTICIPANT, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(emailCaptor.getValue().getRecipientEmails().iterator().next(), PARTICIPANT.getEmail());
    }

    @Test
    public void consentToResearchWithoutParticipantEmailDoesSend() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        StudyParticipant noEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT).withEmail(null).build();

        consentService.consentToResearch(app, SUBPOP_GUID, noEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING, true);

        verify(sendMailService).sendEmail(emailCaptor.capture());

        assertFalse(emailCaptor.getValue().getRecipientEmails().isEmpty());
        assertEquals(emailCaptor.getValue().getRecipientEmails().iterator().next(),
                app.getConsentNotificationEmail());
    }

    @Test
    public void consentToResearchWithPhoneOK() throws Exception {
        doReturn("asdf.pdf").when(consentService).getSignedConsentUrl();
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("some test content");
        when(templateService.getRevisionForUser(app, TemplateType.SMS_SIGNED_CONSENT)).thenReturn(revision);

        consentService.consentToResearch(app, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.NO_SHARING, true);

        verify(smsService).sendSmsMessage(eq(ID), smsProviderCaptor.capture());

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Helper).writeBytesToS3(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), eq("asdf.pdf"), any(),
                metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(provider.getPhone(), PHONE_PARTICIPANT.getPhone());
        assertEquals(provider.getApp(), app);
        assertEquals(provider.getSmsType(), "Transactional");
        assertEquals(provider.getTokenMap().get("consentUrl"), SHORT_URL);
        assertEquals(provider.getTemplateRevision().getDocumentContent(), revision.getDocumentContent());
    }

    @Test
    public void consentToResearchWithPhoneAutoSuppressed() {
        when(subpopulation.isAutoSendConsentSuppressed()).thenReturn(true);

        consentService.consentToResearch(app, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.NO_SHARING, true);

        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void consentToResearchPrefersEmailToPhone() {
        StudyParticipant phoneAndEmail = new StudyParticipant.Builder().copyOf(PHONE_PARTICIPANT).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();

        consentService.consentToResearch(app, SUBPOP_GUID, phoneAndEmail, CONSENT_SIGNATURE, SharingScope.NO_SHARING,
                true);

        verify(sendMailService).sendEmail(any());
        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void consentToResearchWithPhoneSuppressedByCallFlag() {
        consentService.consentToResearch(app, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.NO_SHARING, false);

        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void consentToResearchAssignsDataGroupsAndStudies() throws Exception {
        when(subpopulation.getDataGroupsAssignedWhileConsented()).thenReturn(USER_DATA_GROUPS);
        when(subpopulation.getStudyIdsAssignedOnConsent()).thenReturn(USER_STUDY_IDS);

        when(subpopService.getSubpopulation(app.getIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
        when(accountService.getAccount(any())).thenReturn(account);

        consentService.consentToResearch(app, SUBPOP_GUID, PHONE_PARTICIPANT, CONSENT_SIGNATURE,
                SharingScope.NO_SHARING, false);

        assertEquals(account.getDataGroups(), TestConstants.USER_DATA_GROUPS);
        
        verify(mockEnrollmentService, times(2)).enroll(any(), enrollmentCaptor.capture());
        assertEquals(enrollmentCaptor.getAllValues().stream()
            .map(Enrollment::getStudyId)
            .collect(Collectors.toSet()), TestConstants.USER_STUDY_IDS);
    }

    @Test
    public void resendConsentAgreementWithPhoneOK() throws Exception {
        doReturn("asdf.pdf").when(consentService).getSignedConsentUrl();
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("some test content");
        when(templateService.getRevisionForUser(app, TemplateType.SMS_SIGNED_CONSENT)).thenReturn(revision);
        
        consentService.resendConsentAgreement(app, SUBPOP_GUID, PHONE_PARTICIPANT);

        verify(smsService).sendSmsMessage(eq(ID), smsProviderCaptor.capture());

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Helper).writeBytesToS3(eq(ConsentService.USERSIGNED_CONSENTS_BUCKET), eq("asdf.pdf"), any(),
                metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(provider.getPhone(), PHONE_PARTICIPANT.getPhone());
        assertEquals(provider.getApp(), app);
        assertEquals(provider.getSmsType(), "Transactional");
        assertEquals(provider.getTokenMap().get("consentUrl"), SHORT_URL);
        assertEquals(provider.getTemplateRevision().getDocumentContent(), revision.getDocumentContent());
    }

    @Test
    public void resendConsentAgreementWithPhonePrefersEmailToPhone() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        StudyParticipant phoneAndEmail = new StudyParticipant.Builder().copyOf(PHONE_PARTICIPANT).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();

        consentService.resendConsentAgreement(app, SUBPOP_GUID, phoneAndEmail);

        verify(sendMailService).sendEmail(any());
        verify(smsService, never()).sendSmsMessage(any(), any());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void resendConsentAgreementNoVerifiedChannelThrows() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));

        StudyParticipant noPhoneOrEmail = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withPhone(TestConstants.PHONE).withEmailVerified(null).withPhoneVerified(null).build();
        consentService.resendConsentAgreement(app, SUBPOP_GUID, noPhoneOrEmail);
    }

    @Test
    public void getSignedConsentUrl() {
        String url = consentService.getSignedConsentUrl();
        assertTrue(url.endsWith(".pdf"));
        assertEquals(url.length(), 25);
    }

    @Test
    public void getConsentStatuses() {
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(WITHDRAWN_CONSENT_SIGNATURE));

        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(true);

        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(true);

        doReturn(ImmutableList.of(subpop1, subpop2)).when(subpopService).getSubpopulationsForUser(any());

        Map<SubpopulationGuid, ConsentStatus> map = consentService.getConsentStatuses(CONTEXT, account);

        ConsentStatus status1 = map.get(SUBPOP_GUID);
        assertEquals(status1.getName(), SUBPOP_GUID.getGuid());
        assertEquals(status1.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertTrue(status1.isRequired());
        assertTrue(status1.isConsented());
        assertTrue(status1.getSignedMostRecentConsent());
        assertEquals(status1.getSignedOn(), new Long(SIGNED_ON));

        ConsentStatus status2 = map.get(SECOND_SUBPOP);
        assertNull(status2.getSignedOn());
        assertEquals(status2.getName(), SECOND_SUBPOP.getGuid());
        assertEquals(status2.getSubpopulationGuid(), SECOND_SUBPOP.getGuid());
        assertTrue(status2.isRequired());
        assertFalse(status2.isConsented());
        assertFalse(status2.getSignedMostRecentConsent());
        assertNull(status2.getSignedOn());
    }

    private void setupWithdrawTest(boolean subpop1Required, boolean subpop2Required) {
        // two consents, withdrawing one does not turn sharing entirely off.
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setHealthCode(PARTICIPANT.getHealthCode());

        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName(SUBPOP_GUID.getGuid());
        subpop1.setGuid(SUBPOP_GUID);
        subpop1.setRequired(subpop1Required);

        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName(SECOND_SUBPOP.getGuid());
        subpop2.setGuid(SECOND_SUBPOP);
        subpop2.setRequired(subpop2Required);

        doReturn(ImmutableList.of(subpop1, subpop2)).when(subpopService).getSubpopulationsForUser(any());

        ConsentSignature secondConsentSignature = new ConsentSignature.Builder().withName("Test User")
                .withBirthdate("1990-01-01").withSignedOn(SIGNED_ON).build();

        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
        account.setConsentSignatureHistory(SECOND_SUBPOP, ImmutableList.of(secondConsentSignature));
    }

    private void setupWithdrawTest() {
        account.setFirstName("Allen");
        account.setLastName("Wrench");
        account.setEmail(EMAIL);
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(true);
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "studyId", ID, "anExternalId");
        account.getEnrollments().add(enrollment);
        account.setConsentSignatureHistory(SUBPOP_GUID, ImmutableList.of(CONSENT_SIGNATURE));
    }

}