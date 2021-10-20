package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.commaListToOrderedSet;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.BridgeConstants.EXPIRATION_PERIOD_KEY;
import static org.sagebionetworks.bridge.BridgeConstants.SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
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
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.WithdrawConsentEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.ConsentSignatureValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Methods to consent a user to one of the subpopulations of an app. After calling most of these methods, the user's
 * session should be updated.
 */
@Component
public class ConsentService {
    
    protected static final String USERSIGNED_CONSENTS_BUCKET = BridgeConfigFactory.getConfig()
            .get("usersigned.consents.bucket");
    private AccountService accountService;
    private SendMailService sendMailService;
    private SmsService smsService;
    private NotificationsService notificationsService;
    private StudyConsentService studyConsentService;
    private SubpopulationService subpopService;
    private String xmlTemplateWithSignatureBlock;
    private S3Helper s3Helper;
    private UrlShortenerService urlShortenerService;
    private TemplateService templateService;
    private EnrollmentService enrollmentService;
    
    @Value("classpath:conf/app-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.xmlTemplateWithSignatureBlock = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    /** SMS Service, used to send consent via text message. */
    @Autowired
    final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    @Autowired
    final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Resource(name = "s3Helper")
    final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }
    @Autowired
    final void setUrlShortenerService(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }
    @Autowired
    final void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }
    @Autowired
    final void setEnrollmentService(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }
    
    /**
     * Get the user's active consent signature (a signature that has not been withdrawn).
     * @throws EntityNotFoundException if no consent exists
     */
    public ConsentSignature getConsentSignature(App app, SubpopulationGuid subpopGuid, String userId) {
        checkNotNull(app);
        checkNotNull(subpopGuid);
        checkNotNull(userId);
        
        // This will throw an EntityNotFoundException if the subpopulation is not in the user's app
        subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);
        
        Account account = accountService.getAccount(AccountId.forId(app.getIdentifier(), userId))
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        ConsentSignature signature = account.getActiveConsentSignature(subpopGuid);
        if (signature == null) {
            throw new EntityNotFoundException(ConsentSignature.class);    
        }
        return signature;
    }
    
    /**
     * Consent this user to research. User will be updated to reflect consent. This method will ensure the 
     * user is not already consented to this subpopulation, but it does not validate that the user is a 
     * validate member of this subpopulation (that is checked in the controller). Will optionally send 
     * a signed copy of the consent to the user via email or phone (whichever is verified).
     * 
     * @param sendSignedConsent
     *      if true, send the consent document to the user's email address
     * @throws EntityNotFoundException
     *      if the subpopulation is not part of the app
     * @throws InvalidEntityException
     *      if the user is not old enough to participate in the app (based on birthdate declared in signature)
     * @throws EntityAlreadyExistsException
     *      if the user has already signed the consent for this subpopulation
     */
    public void consentToResearch(App app, SubpopulationGuid subpopGuid, StudyParticipant participant,
            ConsentSignature consentSignature, SharingScope sharingScope, boolean sendSignedConsent) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "app");
        checkNotNull(subpopGuid, Validate.CANNOT_BE_NULL, "subpopulationGuid");
        checkNotNull(participant, Validate.CANNOT_BE_NULL, "participant");
        checkNotNull(consentSignature, Validate.CANNOT_BE_NULL, "consentSignature");
        checkNotNull(sharingScope, Validate.CANNOT_BE_NULL, "sharingScope");

        ConsentSignatureValidator validator = new ConsentSignatureValidator(app.getMinAgeOfConsent());
        Validate.entityThrowingException(validator, consentSignature);

        Subpopulation subpop = subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);
        StudyConsentView studyConsent = studyConsentService.getActiveConsent(subpop);
        
        // If there's a signature to the current and active consent, user cannot consent again. They can sign
        // any other consent, including more recent consents.
        Account account = accountService.getAccount(AccountId.forId(app.getIdentifier(), participant.getId()))
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        ConsentSignature active = account.getActiveConsentSignature(subpopGuid);
        if (active != null && active.getConsentCreatedOn() == studyConsent.getCreatedOn()) {
            throw new EntityAlreadyExistsException(ConsentSignature.class, null);
        }

        // Add the consent creation timestamp and clear the withdrewOn timestamp, as some tests copy signatures
        // that contain this. As with all builders, order of with* calls matters here.
        ConsentSignature withConsentCreatedOnSignature = new ConsentSignature.Builder()
                .withConsentSignature(consentSignature).withWithdrewOn(null)
                .withConsentCreatedOn(studyConsent.getCreatedOn()).build();
        
        // Add consent signature to the list of signatures, save account.
        List<ConsentSignature> consentListCopy = new ArrayList<>(account.getConsentSignatureHistory(subpopGuid));
        consentListCopy.add(withConsentCreatedOnSignature);
        account.setConsentSignatureHistory(subpopGuid, consentListCopy);
        account.setSharingScope(sharingScope);
        
        account.getDataGroups().addAll(subpop.getDataGroupsAssignedWhileConsented());
        
        // Supplemental consents do not need to enroll users in a study, and so they do not
        // declare a study ID. 
        if (subpop.getStudyId() != null) {
            Enrollment newEnrollment = Enrollment.create(app.getIdentifier(), subpop.getStudyId(), account.getId());
            enrollmentService.addEnrollment(account, newEnrollment);
            
            RequestContext context = RequestContext.get();
            RequestContext.set(context.toBuilder().withCallerEnrolledStudies(
                    addToSet(context.getCallerEnrolledStudies(), subpop.getStudyId())).build());            
        }
        accountService.updateAccount(account);

        // Administrative actions, almost exclusively for testing, will send no consent documents
        if (sendSignedConsent) {
            ConsentPdf consentPdf = new ConsentPdf(app, participant, withConsentCreatedOnSignature, sharingScope,
                    studyConsent.getDocumentContent(), xmlTemplateWithSignatureBlock);
            
            boolean verifiedEmail = (participant.getEmail() != null
                    && Boolean.TRUE.equals(participant.getEmailVerified()));
            boolean verifiedPhone = (participant.getPhone() != null
                    && Boolean.TRUE.equals(participant.getPhoneVerified()));
            
            // Send an email to the user if they have an email address and we're not suppressing the send, 
            // and/or to any app consent administrators.
            Set<String> recipientEmails = Sets.newHashSet();
            if (verifiedEmail && !subpop.isAutoSendConsentSuppressed()) {
                recipientEmails.add(participant.getEmail());    
            }
            addStudyConsentRecipients(app, recipientEmails);
            if (!recipientEmails.isEmpty()) {
                TemplateRevision revision = templateService.getRevisionForUser(app, EMAIL_SIGNED_CONSENT);
                
                BasicEmailProvider.Builder consentEmailBuilder = new BasicEmailProvider.Builder()
                        .withApp(app)
                        .withParticipant(participant)
                        .withTemplateRevision(revision)
                        .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                        .withType(EmailType.SIGN_CONSENT);
                for (String recipientEmail : recipientEmails) {
                    consentEmailBuilder.withRecipientEmail(recipientEmail);
                }
                sendMailService.sendEmail(consentEmailBuilder.build());
            }
            // Otherwise if there's no verified email but there is a phone and we're not suppressing, send it there
            if (!subpop.isAutoSendConsentSuppressed() && !verifiedEmail && verifiedPhone) {
                sendConsentViaSMS(app, subpop, participant, consentPdf);    
            }
        }
    }

    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the app, and whether or not those 
     * consents are up-to-date.
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context) {
        checkNotNull(context);
        
        Account account = accountService.getAccount(context.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        return getConsentStatuses(context, account);
    }
    
    /**
     * Get all the consent status objects for this user. From these, we determine if the user 
     * has consented to the right consents to have access to the app, and whether or not those 
     * consents are up-to-date. 
     */
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses(CriteriaContext context, Account account) {
        checkNotNull(context);
        
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<>();
        for (Subpopulation subpop : subpopService.getSubpopulationsForUser(context)) {
            
            ConsentSignature signature = account.getActiveConsentSignature(subpop.getGuid());
            boolean hasConsented = (signature != null);
            boolean hasSignedActiveConsent = (hasConsented && 
                    signature.getConsentCreatedOn() == subpop.getPublishedConsentCreatedOn());
            
            ConsentStatus status = new ConsentStatus.Builder().withName(subpop.getName())
                    .withGuid(subpop.getGuid()).withRequired(subpop.isRequired())
                    .withConsented(hasConsented).withSignedMostRecentConsent(hasSignedActiveConsent)
                    .withSignedOn(hasConsented ? signature.getSignedOn() : null)
                    .build();
            builder.put(subpop.getGuid(), status);
        }
        return builder.build();
    }
    
    /**
     * Withdraw consent in this app. The withdrawal date is recorded and the user can no longer 
     * access any APIs that require consent, although the user's account (along with the history of 
     * the user's participation) will not be deleted.
     */
    public Map<SubpopulationGuid, ConsentStatus> withdrawConsent(App app, SubpopulationGuid subpopGuid,
            StudyParticipant participant, CriteriaContext context, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(subpopGuid);
        checkNotNull(participant);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);
        
        Subpopulation subpop = subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);
        Account account = accountService.getAccount(context.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        if(!withdrawSignatures(account, subpopGuid, withdrewOn)) {
            throw new EntityNotFoundException(ConsentSignature.class);
        }
        Map<SubpopulationGuid,ConsentStatus> statuses = getConsentStatuses(context, account);
        if (!ConsentStatus.isUserConsented(statuses)) {
            notificationsService.deleteAllRegistrations(app.getIdentifier(), participant.getHealthCode());
            account.setSharingScope(SharingScope.NO_SHARING);
        }
        account.getDataGroups().removeAll(subpop.getDataGroupsAssignedWhileConsented());

        for (Enrollment enrollment : account.getActiveEnrollments()) {
            // Only subpopulations that are required enroll a user in a study, so we only check withdrawal from required
            // subpopulations to withdraw the enrollment. This is temporary until we have a more fully integrated v2
            // consent system.
            if (subpop.isRequired() && subpop.getStudyIdsAssignedOnConsent().contains(enrollment.getStudyId())) {
                Enrollment withdrawnEnrollment = Enrollment.create(app.getIdentifier(), enrollment.getStudyId(), account.getId());
                withdrawnEnrollment.setWithdrawnOn(new DateTime(withdrewOn));
                withdrawnEnrollment.setWithdrawalNote(withdrawal.getReason());
                enrollmentService.unenroll(account, withdrawnEnrollment);
            }
        }
        
        accountService.updateAccount(account);
        
        sendWithdrawEmail(app, account, withdrawal, withdrewOn);

        return statuses;
    }
    
    /**
     * Withdraw user from any and all consents, turn off sharing, unregister the device from any notifications, and 
     * delete the identifiers of the account. Because a user's criteria for being included in a consent can change 
     * over time, this is really the best method for ensuring a user is withdrawn from everything. But in cases where 
     * there are apps with distinct and separate consents, you can also selectively withdraw from the consent for 
     * a specific subpopulation without dropping out of the app.
     */
    public void withdrawFromApp(App app, StudyParticipant participant, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(app);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        AccountId accountId = AccountId.forId(app.getIdentifier(), participant.getId());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        for (SubpopulationGuid subpopGuid : account.getAllConsentSignatureHistories().keySet()) {
            if (withdrawSignatures(account, subpopGuid, withdrewOn)) {
                Subpopulation subpop = subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);
                account.getDataGroups().removeAll(subpop.getDataGroupsAssignedWhileConsented());
            }
        }
        sendWithdrawEmail(app, account, withdrawal, withdrewOn);
        
        // Forget this person. If the user registers again at a later date, it is as if they have 
        // created a new account. But we hold on to this record so we can still retrieve the consent 
        // records for a given healthCode. We also don't delete external ID/study relationships
        // so studies can continue to view withdrawals by health code.
        account.setSharingScope(SharingScope.NO_SHARING);
        account.setFirstName(null);
        account.setLastName(null);
        account.setNotifyByEmail(false);
        account.setEmail(null);
        account.setEmailVerified(false);
        account.setPhone(null);
        account.setPhoneVerified(false);
        
        for (Enrollment enrollment : account.getActiveEnrollments()) {
            Enrollment withdrawnEnrollment = Enrollment.create(enrollment.getAppId(), enrollment.getStudyId(),
                    enrollment.getAccountId());
            withdrawnEnrollment.setWithdrawnOn(new DateTime(withdrewOn));
            withdrawnEnrollment.setWithdrawalNote(withdrawal.getReason());
            enrollmentService.unenroll(account, withdrawnEnrollment);
        }
        accountService.updateAccount(account);

        notificationsService.deleteAllRegistrations(app.getIdentifier(), participant.getHealthCode());
    }

    // Helper method, which abstracts away logic for sending withdraw notification email.
    private void sendWithdrawEmail(App app, Account account, Withdrawal withdrawal,
            long withdrewOn) {
        if (account.getEmail() == null) {
            // Withdraw email provider currently doesn't support non-email accounts. Skip.
            return;
        }
        if (FALSE.equals(app.isConsentNotificationEmailVerified())) {
            // For backwards-compatibility, a null value means the email is verified.
            return;
        }
        WithdrawConsentEmailProvider consentEmail = new WithdrawConsentEmailProvider(app, account,
                withdrawal, withdrewOn);
        if (!consentEmail.getRecipients().isEmpty()) {
            sendMailService.sendEmail(consentEmail);    
        }
    }

    /**
     * Resend the participant's signed consent agreement via the user's email address or their phone number. 
     * It is an error to call this method if no channel exists to send the consent to the user.
     */
    public void resendConsentAgreement(App app, SubpopulationGuid subpopGuid, StudyParticipant participant) {
        checkNotNull(app);
        checkNotNull(subpopGuid);
        checkNotNull(participant);

        ConsentSignature consentSignature = getConsentSignature(app, subpopGuid, participant.getId());
        SharingScope sharingScope = participant.getSharingScope();
        Subpopulation subpop = subpopService.getSubpopulation(app.getIdentifier(), subpopGuid);
        String studyConsentDocument = studyConsentService.getActiveConsent(subpop).getDocumentContent();

        boolean verifiedEmail = (participant.getEmail() != null
                && Boolean.TRUE.equals(participant.getEmailVerified()));
        boolean verifiedPhone = (participant.getPhone() != null
                && Boolean.TRUE.equals(participant.getPhoneVerified()));
        
        ConsentPdf consentPdf = new ConsentPdf(app, participant, consentSignature, sharingScope, studyConsentDocument,
                xmlTemplateWithSignatureBlock);
        
        if (verifiedEmail) {
            TemplateRevision revision = templateService.getRevisionForUser(app, EMAIL_SIGNED_CONSENT);
            
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                    .withApp(app)
                    .withParticipant(participant)
                    .withTemplateRevision(revision)
                    .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                    .withRecipientEmail(participant.getEmail())
                    .withType(EmailType.RESEND_CONSENT).build();
            sendMailService.sendEmail(provider);
        } else if (verifiedPhone) {
            sendConsentViaSMS(app, subpop, participant, consentPdf);
        } else {
            throw new BadRequestException("Participant does not have a valid email address or phone number");
        }
    }
    
    private void sendConsentViaSMS(App app, Subpopulation subpop, StudyParticipant participant,
            ConsentPdf consentPdf) {
        String shortUrl;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            
            String fileName = getSignedConsentUrl();
            DateTime expiresOn = getDownloadExpiration();
            s3Helper.writeBytesToS3(USERSIGNED_CONSENTS_BUCKET, fileName, consentPdf.getBytes(), metadata);
            URL url = s3Helper.generatePresignedUrl(USERSIGNED_CONSENTS_BUCKET, fileName, expiresOn, HttpMethod.GET);
            shortUrl = urlShortenerService.shortenUrl(url.toString(), SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS);
        } catch(IOException e) {
            throw new BridgeServiceException(e);
        }
        TemplateRevision revision = templateService.getRevisionForUser(app, SMS_SIGNED_CONSENT);

        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withApp(app)
                .withPhone(participant.getPhone())
                .withExpirationPeriod(EXPIRATION_PERIOD_KEY, SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS)
                .withTransactionType()
                .withTemplateRevision(revision)
                .withToken(BridgeConstants.CONSENT_URL, shortUrl)
                .build();
        smsService.sendSmsMessage(participant.getId(), provider);
    }
    
    protected String getSignedConsentUrl() {
        return SecureTokenGenerator.INSTANCE.nextToken() + ".pdf";
    }
    
    protected DateTime getDownloadExpiration() {
        return DateUtils.getCurrentDateTime().plusSeconds(SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS);
    }
    
    private void addStudyConsentRecipients(App app, Set<String> recipientEmails) {
        Boolean consentNotificationEmailVerified = app.isConsentNotificationEmailVerified();
        if (consentNotificationEmailVerified == null || consentNotificationEmailVerified) {
            Set<String> studyRecipients = commaListToOrderedSet(app.getConsentNotificationEmail());
            recipientEmails.addAll(studyRecipients);
        }
    }

    private boolean withdrawSignatures(Account account, SubpopulationGuid subpopGuid, long withdrewOn) {
        boolean withdrewConsent = false;
        
        List<ConsentSignature> signatures = account.getConsentSignatureHistory(subpopGuid);
        List<ConsentSignature> withdrawnSignatureList = new ArrayList<>();
        // Withdraw every signature to this subpopulation that has not been withdrawn.
        for (ConsentSignature signature : signatures) {
            if (signature.getWithdrewOn() == null) {
                withdrewConsent = true;
                ConsentSignature withdrawn = new ConsentSignature.Builder()
                        .withConsentSignature(signature)
                        .withWithdrewOn(withdrewOn).build();
                withdrawnSignatureList.add(withdrawn);
            } else {
                withdrawnSignatureList.add(signature);
            }
        }

        account.setConsentSignatureHistory(subpopGuid, withdrawnSignatureList);

        return withdrewConsent;
    }
}
