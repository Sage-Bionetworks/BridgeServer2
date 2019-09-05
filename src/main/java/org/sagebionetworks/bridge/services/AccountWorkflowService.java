package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_VERIFY_EMAIL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_PHONE_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_VERIFY_PHONE;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;
import static org.sagebionetworks.bridge.validators.SignInValidator.EMAIL_SIGNIN_REQUEST;
import static org.sagebionetworks.bridge.validators.SignInValidator.PHONE_SIGNIN_REQUEST;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ThrottleRequestType;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.util.TriConsumer;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component
public class AccountWorkflowService {
    private static final String BASE_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    static final String CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS = "channel.throttle.max.requests";
    static final String CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS = "channel.throttle.timeout.seconds";
    static final String PASSWORD_RESET_TOKEN_EXPIRED = "Password reset token has expired (or already been used).";
    static final String VERIFY_TOKEN_EXPIRED = "This verification token is no longer valid.";
    static final String ALREADY_VERIFIED = "That %s has already been verified.";
    
    // These are component tokens we include in URLs, but they are also included as is in the template variables
    // for further customization on a case-by-case basis.
    private static final String EMAIL_KEY = "email";
    private static final String TOKEN_KEY = "token";
    private static final String SPTOKEN_KEY = "sptoken";
    
    // These are older values. These are still included, for now, for existing templates.
    private static final String OLD_URL_KEY = "url";
    private static final String OLD_SHORT_URL_KEY = "shortUrl";
    private static final String OLD_RESET_PASSWORD_URL = "/mobile/resetPassword.html?study=%s&sptoken=%s";
    private static final String OLD_VERIFY_EMAIL_URL = "/mobile/verifyEmail.html?study=%s&sptoken=%s";
    private static final String OLD_EMAIL_SIGNIN_URL = "/mobile/%s/startSession.html?email=%s&study=%s&token=%s";
    private static final String OLD_EXP_WINDOW_TOKEN = "expirationWindow";
    private static final String OLD_EXPIRATION_PERIOD = "expirationPeriod";
    
    // We now have shorter URLs and template variables that can be combined in one template
    private static final String RESET_PASSWORD_URL = "/rp?study=%s&sptoken=%s";
    private static final String VERIFY_EMAIL_URL = "/ve?study=%s&sptoken=%s";
    private static final String EMAIL_SIGNIN_URL = "/s/%s?email=%s&token=%s";
     
    // Keys to reference the URLs
    private static final String RESET_PASSWORD_URL_KEY = "resetPasswordUrl";
    private static final String EMAIL_VERIFICATION_URL_KEY = "emailVerificationUrl";
    private static final String EMAIL_SIGNIN_URL_KEY = "emailSignInUrl";
        
    // Keys to reference an expiration period for each URL or token
    private static final String RESET_PASSWORD_EXPIRATION_PERIOD = "resetPasswordExpirationPeriod";
    private static final String EMAIL_VERIFICATION_EXPIRATION_PERIOD = "emailVerificationExpirationPeriod";
    private static final String PHONE_VERIFICATION_EXPIRATION_PERIOD = "phoneVerificationExpirationPeriod";
    private static final String PHONE_SIGNIN_EXPIRATION_PERIOD = "phoneSignInExpirationPeriod";
    private static final String EMAIL_SIGNIN_EXPIRATION_PERIOD = "emailSignInExpirationPeriod";
    
    private final AtomicLong emailSignInRequestInMillis = new AtomicLong(200L);
    private final AtomicLong phoneSignInRequestInMillis = new AtomicLong(200L);
    
    static final int VERIFY_OR_RESET_EXPIRE_IN_SECONDS = 60*60*2; // 2 hours
    static final int VERIFY_CACHE_IN_SECONDS = 60*60*24*30; // 30 days
    static final int SIGNIN_EXPIRE_IN_SECONDS = 60*60; // 1 hour

    private static class VerificationData {
        private final String studyId;
        private final String userId;
        private final ChannelType type;
        private final long expiresOn;
        @JsonCreator
        public VerificationData(@JsonProperty("studyId") String studyId, @JsonProperty("type") ChannelType type,
                @JsonProperty("userId") String userId, @JsonProperty("createdOn") long expiresOn) {
            checkArgument(isNotBlank(studyId));
            checkArgument(isNotBlank(userId));
            this.studyId = studyId;
            this.userId = userId;
            // On deployment, this value will be missing, and by inference is for email verifications
            // in process, since phone verification won't have existed until the deployment.
            this.type = (type == null) ? ChannelType.EMAIL : type;
            this.expiresOn = expiresOn;
        }
        public String getStudyId() {
            return studyId;
        }
        public String getUserId() {
            return userId;
        }
        public ChannelType getType() {
            return type;
        }
        public long getExpiresOn() {
            return expiresOn;
        }
    }

    // Config values
    private int channelThrottleMaxRequests;
    private int channelThrottleTimeoutSeconds;

    // Dependent services
    private SmsService smsService;
    private StudyService studyService;
    private SendMailService sendMailService;
    private AccountDao accountDao;
    private CacheProvider cacheProvider;
    private TemplateService templateService;

    /** Bridge config, used to get config values such as throttle configuration. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.channelThrottleMaxRequests = bridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS);
        this.channelThrottleTimeoutSeconds = bridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS);
    }

    /** SMS Service, used to send account workflow text messages. */
    @Autowired
    public final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    final AtomicLong getEmailSignInRequestInMillis() {
        return emailSignInRequestInMillis;
    }
    
    final AtomicLong getPhoneSignInRequestInMillis() {
        return phoneSignInRequestInMillis;
    }

    /**
     * Send email verification token as part of creating an account that requires an email address be
     * verified. We assume that an account has been created and that email verification should be sent
     * (neither is verified in this method).
     */
    public void sendEmailVerificationToken(Study study, String userId, String recipientEmail) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        
        if (recipientEmail == null) {
            // Can't send email verification if there's no email.
            return;
        }

        if (isRequestThrottled(ThrottleRequestType.VERIFY_EMAIL, userId)) {
            // Too many requests. Throttle.
            return;
        }

        String sptoken = getNextToken();
        long expiresOn = getDateTimeInMillis() + (VERIFY_OR_RESET_EXPIRE_IN_SECONDS*1000);

        saveVerification(sptoken, new VerificationData(study.getIdentifier(), ChannelType.EMAIL, userId, expiresOn));

        String oldUrl = getVerifyEmailURL(study, sptoken);
        String newUrl = getShortVerifyEmailURL(study, sptoken);

        TemplateRevision revision = templateService.getRevisionForUser(study, EMAIL_VERIFY_EMAIL);
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withTemplateRevision(revision)
                .withRecipientEmail(recipientEmail)
                .withToken(SPTOKEN_KEY, sptoken)
                .withToken(OLD_URL_KEY, oldUrl)
                .withToken(OLD_SHORT_URL_KEY, newUrl) // new URL is short
                .withToken(EMAIL_VERIFICATION_URL_KEY, newUrl)
                .withExpirationPeriod(EMAIL_VERIFICATION_EXPIRATION_PERIOD, VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
                .withType(EmailType.VERIFY_EMAIL)
                .build();
        sendMailService.sendEmail(provider);
    }
    
    public void sendPhoneVerificationToken(Study study, String userId, Phone phone) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));
        
        if (phone == null) {
            return;
        }
        if (isRequestThrottled(ThrottleRequestType.VERIFY_PHONE, userId)) {
            // Too many requests. Throttle.
            return;
        }
        String sptoken = getNextPhoneToken();
        long expiresOn = getDateTimeInMillis() + (VERIFY_OR_RESET_EXPIRE_IN_SECONDS*1000);

        saveVerification(sptoken, new VerificationData(study.getIdentifier(), ChannelType.PHONE, userId, expiresOn));
        
        String formattedSpToken = sptoken.substring(0,3) + "-" + sptoken.substring(3,6);
        
        TemplateRevision revision = templateService.getRevisionForUser(study, SMS_VERIFY_PHONE);
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withToken("token", formattedSpToken)
                .withTemplateRevision(revision)
                .withTransactionType()
                .withExpirationPeriod(PHONE_VERIFICATION_EXPIRATION_PERIOD, VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
                .withPhone(phone).build();
        smsService.sendSmsMessage(userId, provider);
    }
        
    /**
     * Send another verification token via email or phone. This creates and sends a new verification token 
     * using the specified channel (an email or SMS message).
     */
    public void resendVerificationToken(ChannelType type, AccountId accountId) {
        checkNotNull(accountId);
        
        Study study = studyService.getStudy(accountId.getStudyId());
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            if (type == ChannelType.EMAIL) {
                sendEmailVerificationToken(study, account.getId(), account.getEmail());
            } else if (type == ChannelType.PHONE) {
                sendPhoneVerificationToken(study, account.getId(), account.getPhone());
            } else {
                throw new UnsupportedOperationException("Channel type not implemented");
            }
        }
    }
    
    /**
     * Using the verification token that was sent to the user, verify the email address 
     * or phone number. If an account is returned, the email address or phone number has been 
     * verified, but the AccountDao must be called in order to persist the state change.
     */
    public Account verifyChannel(ChannelType type, Verification verification) {
        checkNotNull(verification);

        VerificationData data = restoreVerification(verification.getSptoken());
        if (data == null || data.getType() != type) {
            throw new BadRequestException(VERIFY_TOKEN_EXPIRED);
        }
        Study study = studyService.getStudy(data.getStudyId());
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), data.getUserId()));
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (type == ChannelType.EMAIL && TRUE.equals(account.getEmailVerified())) {
            throw new BadRequestException(String.format(ALREADY_VERIFIED, "email address"));
        } else if (type == ChannelType.PHONE && TRUE.equals(account.getPhoneVerified())) {
            throw new BadRequestException(String.format(ALREADY_VERIFIED, "phone number"));
        }
        if (data.getExpiresOn() < getDateTimeInMillis()) {
            throw new BadRequestException(VERIFY_TOKEN_EXPIRED);
        }
        return account;
    }
    
    /**
     * Send an email message or SMS to the user notifying them that the account already exists. Message can provide a link 
     * to reset a password, or a link to sign in via email or phone (if either is enabled). Account exists notifications 
     * won't be sent out if auto-verification is disabled (or if the user doesn't have a verified communication channel). 
     * This is because account exist notifications are only sent out when users are trying to sign up.
     */
    public void notifyAccountExists(Study study, AccountId accountId) {
        checkNotNull(study);
        checkNotNull(accountId);

        Account account = accountDao.getAccount(accountId);
        
        boolean verifiedEmail = account.getEmail() != null && Boolean.TRUE.equals(account.getEmailVerified());
        boolean verifiedPhone = account.getPhone() != null && Boolean.TRUE.equals(account.getPhoneVerified());
        boolean sendEmail = study.isEmailVerificationEnabled() && !study.isAutoVerificationEmailSuppressed();
        boolean sendPhone = !study.isAutoVerificationPhoneSuppressed();
        
        if (verifiedEmail && sendEmail) {
            TemplateRevision revision = templateService.getRevisionForUser(study, EMAIL_ACCOUNT_EXISTS);
            sendPasswordResetRelatedEmail(study, account.getEmail(), true, revision);
        } else if (verifiedPhone && sendPhone) {
            TemplateRevision revision = templateService.getRevisionForUser(study, SMS_ACCOUNT_EXISTS);
            sendPasswordResetRelatedSMS(study, account, true, revision);
        }
    }
    
    /**
     * Request that a token be sent to the user's email address or phone number that can be 
     * used to submit a password change to the server. Users who administer participants in 
     * the study can trigger this request whether the channel used is verified or not, 
     * but normal users must have already verified the channel to prevent abuse. In addition, 
     * this method fails silently if the email or phone number cannot be found in the system, 
     * to prevent account enumeration attacks. 
     */
    public void requestResetPassword(Study study, boolean isStudyAdmin, AccountId accountId) {
        checkNotNull(accountId);
        checkArgument(study.getIdentifier().equals(accountId.getStudyId()));
        
        Account account = accountDao.getAccount(accountId);
        // We are going to change the status of the account if this succeeds, so we must also
        // ignore disabled accounts.
        if (account != null && account.getStatus() != AccountStatus.DISABLED) {
            boolean emailVerified = isStudyAdmin || Boolean.TRUE.equals(account.getEmailVerified());
            boolean phoneVerified = isStudyAdmin || Boolean.TRUE.equals(account.getPhoneVerified());
            if (account.getEmail() != null && emailVerified) {
                TemplateRevision revision = templateService.getRevisionForUser(study, EMAIL_RESET_PASSWORD);
                sendPasswordResetRelatedEmail(study, account.getEmail(), false, revision);
            } else if (account.getPhone() != null && phoneVerified) {
                TemplateRevision revision = templateService.getRevisionForUser(study, SMS_RESET_PASSWORD);
                sendPasswordResetRelatedSMS(study, account, false, revision);
            }
        }
    }
    
    private void sendPasswordResetRelatedEmail(Study study, String email, boolean includeEmailSignIn,
            TemplateRevision revision) {
        String sptoken = getNextToken();
        
        CacheKey cacheKey = CacheKey.passwordResetForEmail(sptoken, study.getIdentifier());
        cacheProvider.setObject(cacheKey, email, VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        
        String url = getResetPasswordURL(study, sptoken);
        String shortUrl = getShortResetPasswordURL(study, sptoken);
        
        BasicEmailProvider.Builder builder = new BasicEmailProvider.Builder()
            .withStudy(study)
            .withTemplateRevision(revision)
            .withRecipientEmail(email)
            .withToken(SPTOKEN_KEY, sptoken)
            .withToken(OLD_URL_KEY, url)
            .withToken(OLD_SHORT_URL_KEY, shortUrl)
            .withToken(OLD_EXP_WINDOW_TOKEN, Integer.toString(VERIFY_OR_RESET_EXPIRE_IN_SECONDS/60/60))
            .withExpirationPeriod(OLD_EXPIRATION_PERIOD, VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
            .withToken(RESET_PASSWORD_URL_KEY, shortUrl)
            .withExpirationPeriod(RESET_PASSWORD_EXPIRATION_PERIOD, VERIFY_OR_RESET_EXPIRE_IN_SECONDS)
            .withType(EmailType.RESET_PASSWORD);
            
        if (includeEmailSignIn && study.isEmailSignInEnabled()) {
            SignIn signIn = new SignIn.Builder().withEmail(email).withStudy(study.getIdentifier()).build();
            requestChannelSignIn(EMAIL, EMAIL_SIGNIN_REQUEST, emailSignInRequestInMillis,
                signIn, false, this::getNextToken, (theStudy, account, token) -> {
                    // get and add the sign in URLs.
                    String emailShortUrl = getShortEmailSignInURL(signIn.getEmail(), theStudy.getIdentifier(), token);
                    
                    // Put the components in separately, in case we want to alter the URL in a specific template.
                    builder.withToken(EMAIL_KEY, BridgeUtils.encodeURIComponent(signIn.getEmail()));
                    builder.withToken(TOKEN_KEY, token);
                    builder.withToken(OLD_SHORT_URL_KEY, emailShortUrl);
                    builder.withToken(EMAIL_SIGNIN_URL_KEY, emailShortUrl);
                    builder.withExpirationPeriod(EMAIL_SIGNIN_EXPIRATION_PERIOD, SIGNIN_EXPIRE_IN_SECONDS);
                });
        }
        sendMailService.sendEmail(builder.build());
    }
    
    private void sendPasswordResetRelatedSMS(Study study, Account account, boolean includePhoneSignIn,
            TemplateRevision revision) {
        Phone phone = account.getPhone();
        String sptoken = getNextToken();
        
        CacheKey cacheKey = CacheKey.passwordResetForPhone(sptoken, study.getIdentifier());
        cacheProvider.setObject(cacheKey, getPhoneString(phone), VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        
        String url = getShortResetPasswordURL(study, sptoken);
        
        SmsMessageProvider.Builder builder = new SmsMessageProvider.Builder();
        builder.withTemplateRevision(revision);
        builder.withTransactionType();
        builder.withStudy(study);
        builder.withPhone(phone);
        builder.withToken(SPTOKEN_KEY, sptoken);
        builder.withToken(RESET_PASSWORD_URL_KEY, url);
        builder.withExpirationPeriod(RESET_PASSWORD_EXPIRATION_PERIOD, VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        
        if (includePhoneSignIn && study.isPhoneSignInEnabled()) {
            SignIn signIn = new SignIn.Builder().withPhone(phone).withStudy(study.getIdentifier()).build();
            requestChannelSignIn(PHONE, PHONE_SIGNIN_REQUEST, phoneSignInRequestInMillis,
                signIn, false, this::getNextPhoneToken, (theStudy, account2, token) -> {
                    String formattedToken = token.substring(0,3) + "-" + token.substring(3,6);
                    builder.withToken(TOKEN_KEY, formattedToken);
                    builder.withExpirationPeriod(PHONE_SIGNIN_EXPIRATION_PERIOD, SIGNIN_EXPIRE_IN_SECONDS);
                });
        }
        smsService.sendSmsMessage(account.getId(), builder.build());
    }

    /**
     * Use a supplied password reset token to change the password on an account. If the supplied 
     * token is not valid, this method throws an exception. If the token is valid but the account 
     * does not exist, an exception is also thrown (this would be unusual).
     */
    public void resetPassword(PasswordReset passwordReset) {
        checkNotNull(passwordReset);
        
        // This pathway is unusual as the token may have been sent via email or phone, so test for both.
        CacheKey emailCacheKey = CacheKey.passwordResetForEmail(passwordReset.getSptoken(), passwordReset.getStudyIdentifier());
        CacheKey phoneCacheKey = CacheKey.passwordResetForPhone(passwordReset.getSptoken(), passwordReset.getStudyIdentifier());
        
        String email = cacheProvider.getObject(emailCacheKey, String.class);
        Phone phone = cacheProvider.getObject(phoneCacheKey, Phone.class);
        if (email == null && phone == null) {
            throw new BadRequestException(PASSWORD_RESET_TOKEN_EXPIRED);
        }
        cacheProvider.removeObject(emailCacheKey);
        cacheProvider.removeObject(phoneCacheKey);
        
        Study study = studyService.getStudy(passwordReset.getStudyIdentifier());
        ChannelType channelType = null;
        AccountId accountId = null;
        if (email != null) {
            accountId = AccountId.forEmail(study.getIdentifier(), email);
            channelType = ChannelType.EMAIL;
        } else if (phone != null) {
            accountId = AccountId.forPhone(study.getIdentifier(), phone);
            channelType = ChannelType.PHONE;
        } else {
            throw new BridgeServiceException("Could not reset password");
        }
        Account account = accountDao.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        accountDao.changePassword(account, channelType, passwordReset.getPassword());
    }
    
    /**
     * Request a token to be sent via SMS to the user, that can be used to start a session on the Bridge server.
     * Returns the userId, or null if the user doesn't exist.
     */
    public String requestPhoneSignIn(SignIn signIn) {
        return requestChannelSignIn(PHONE, PHONE_SIGNIN_REQUEST, phoneSignInRequestInMillis,
                signIn, true, this::getNextPhoneToken, (study, account, token) -> {
            // Put a dash in the token so it's easier to enter into the UI. All this should
            // eventually come from a template
            String formattedToken = token.substring(0,3) + "-" + token.substring(3,6); 
            
            TemplateRevision revision = templateService.getRevisionForUser(study, SMS_PHONE_SIGN_IN);
            SmsMessageProvider provider = new SmsMessageProvider.Builder()
                    .withStudy(study)
                    .withTemplateRevision(revision)
                    .withTransactionType()
                    .withPhone(signIn.getPhone())
                    .withExpirationPeriod(PHONE_SIGNIN_EXPIRATION_PERIOD, SIGNIN_EXPIRE_IN_SECONDS)
                    .withToken(TOKEN_KEY, formattedToken).build();
            smsService.sendSmsMessage(account.getId(), provider);
        });
    }
    
    /**
     * Request a token to be sent via a link in an email message, that can be used to start a session on the Bridge server. 
     * The installed application should intercept this link in order to complete the transaction within the app, where the 
     * returned session can be captured. If the link is not captured, it retrieves a test page on the Bridge server as 
     * configured by default. That test page will complete the transaction and return a session token.
     *
     * Returns the userId, or null if the user doesn't exist.
     */
    public String requestEmailSignIn(SignIn signIn) {
        return requestChannelSignIn(EMAIL, EMAIL_SIGNIN_REQUEST, emailSignInRequestInMillis,
                signIn, true, this::getNextToken, (study, account, token) -> {
            String url = getEmailSignInURL(signIn.getEmail(), study.getIdentifier(), token);
            String shortUrl = getShortEmailSignInURL(signIn.getEmail(), study.getIdentifier(), token);
            
            // Email is URL encoded, which is probably a mistake. We're now providing an URL that's will be 
            // opaque to the user, like the other APIs (where the templates just have a ${url} variable), but we 
            // need to provide host/email/studyId/token variables for earlier versions of the email sign in template 
            // that had the URL spelled out with substitutions. The email was encoded so it could be substituted 
            // into that template.
            TemplateRevision revision = templateService.getRevisionForUser(study, EMAIL_SIGN_IN);
            
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withTemplateRevision(revision)
                .withStudy(study)
                .withRecipientEmail(signIn.getEmail())
                .withToken(EMAIL_KEY, BridgeUtils.encodeURIComponent(signIn.getEmail()))
                .withToken(TOKEN_KEY, token)
                .withToken(OLD_URL_KEY, url)
                .withToken(OLD_SHORT_URL_KEY, shortUrl)
                .withToken(EMAIL_SIGNIN_URL_KEY, shortUrl)
                .withExpirationPeriod(EMAIL_SIGNIN_EXPIRATION_PERIOD, SIGNIN_EXPIRE_IN_SECONDS)
                .withType(EmailType.EMAIL_SIGN_IN)
                .build();
            sendMailService.sendEmail(provider);
        });
    }

    /** Requests channel sign-in. Returns the userId, or null if the user does not exist. */
    private String requestChannelSignIn(ChannelType channelType, Validator validator, AtomicLong atomicLong,
            SignIn signIn, boolean shouldThrottle, Supplier<String> tokenSupplier,
            TriConsumer<Study, Account, String> messageSender) {
        long startTime = System.currentTimeMillis();
        Validate.entityThrowingException(validator, signIn);

        // We use the study so it's existence is verified. We retrieve the account so we verify it
        // exists as well. If the token is returned to the server, we can safely use the credentials 
        // in the persisted SignIn object.
        Study study = studyService.getStudy(signIn.getStudyId());

        // Do we want the same flag for phone? Do we want to eliminate this flag?
        if (channelType == EMAIL && !study.isEmailSignInEnabled()) {
            throw new UnauthorizedException("Email-based sign in not enabled for study: " + study.getName());
        } else if (channelType == PHONE && !study.isPhoneSignInEnabled()) {
            throw new UnauthorizedException("Phone-based sign in not enabled for study: " + study.getName());
        }

        // check that the account exists, return quietly if not to prevent account enumeration attacks
        Account account = accountDao.getAccount(signIn.getAccountId());
        if (account == null) {
            try {
                // The not found case returns *much* faster than the normal case. To prevent account enumeration 
                // attacks, measure time of a successful case and delay for that period before returning.
                TimeUnit.MILLISECONDS.sleep(atomicLong.get());            
            } catch(InterruptedException e) {
                // Just return, the thread was killed by the connection, the server died, etc.
            }
            return null;
        }

        ThrottleRequestType throttleType = channelType == EMAIL ? ThrottleRequestType.EMAIL_SIGNIN :
                ThrottleRequestType.PHONE_SIGNIN;
        if (shouldThrottle && isRequestThrottled(throttleType, account.getId())) {
            // Too many requests. Throttle.
            return account.getId();
        }

        CacheKey cacheKey = null;
        if (channelType == EMAIL) {
            cacheKey = CacheKey.emailSignInRequest(signIn); 
        } else if (channelType == PHONE) {
            cacheKey = CacheKey.phoneSignInRequest(signIn);
        } else {
            throw new RuntimeException("Invalid channelType: " + channelType);
        }

        String token = cacheProvider.getObject(cacheKey, String.class);
        if (token == null) {
            token = tokenSupplier.get();
            cacheProvider.setObject(cacheKey, token, SIGNIN_EXPIRE_IN_SECONDS);
        }

        messageSender.accept(study, account, token);
        atomicLong.set(System.currentTimeMillis()-startTime);

        return account.getId();
    }

    private void saveVerification(String sptoken, VerificationData data) {
        checkArgument(isNotBlank(sptoken));
        checkNotNull(data);
                 
        try {
            CacheKey cacheKey = CacheKey.verificationToken(sptoken);
            cacheProvider.setObject(cacheKey, BridgeObjectMapper.get().writeValueAsString(data),
                    VERIFY_CACHE_IN_SECONDS);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }
             
    private VerificationData restoreVerification(String sptoken) {
        checkArgument(isNotBlank(sptoken));
                 
        CacheKey cacheKey = CacheKey.verificationToken(sptoken);
        String json = cacheProvider.getObject(cacheKey, String.class);
        if (json != null) {
            try {
                // Do not remove. Even when expired we want to map back to an account
                return BridgeObjectMapper.get().readValue(json, VerificationData.class);
            } catch (IOException e) {
                throw new BridgeServiceException(e);
            }
        }
        return null;
    }    
    
    private String getPhoneString(Phone phone) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(phone);
        } catch (JsonProcessingException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    // Provided via accessor so it can be mocked for tests
    protected String getNextToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
    
    // Provided via accessor so it can be mocked for tests
    protected String getNextPhoneToken() {
        return SecureTokenGenerator.PHONE_CODE_INSTANCE.nextToken();
    }
    
    private String getEmailSignInURL(String email, String studyId, String token) {
        return formatWithEncodedArgs(OLD_EMAIL_SIGNIN_URL, studyId, email, studyId, token);
    }
    
    private String getVerifyEmailURL(Study study, String sptoken) {
        return formatWithEncodedArgs(OLD_VERIFY_EMAIL_URL, study.getIdentifier(), sptoken);
    }
    
    private String getResetPasswordURL(Study study, String sptoken) {
        return formatWithEncodedArgs(OLD_RESET_PASSWORD_URL, study.getIdentifier(), sptoken);
    }
    
    private String getShortEmailSignInURL(String email, String studyId, String token) {
        return formatWithEncodedArgs(EMAIL_SIGNIN_URL, studyId, email, token);
    }
    
    private String getShortVerifyEmailURL(Study study, String sptoken) {
        return formatWithEncodedArgs(VERIFY_EMAIL_URL, study.getIdentifier(), sptoken);
    }
    
    private String getShortResetPasswordURL(Study study, String sptoken) {
        return formatWithEncodedArgs(RESET_PASSWORD_URL, study.getIdentifier(), sptoken);
    }
    
    private String formatWithEncodedArgs(String formatString, String... strings) {
        for (int i=0; i < strings.length; i++) {
            strings[i] = BridgeUtils.encodeURIComponent(strings[i]);
        }
        return BASE_URL + String.format(formatString, (Object[])strings);
    }

    // Check if the request is throttled. Key is either email address or phone, depending on the type.
    private boolean isRequestThrottled(ThrottleRequestType type, String userId) {
        // Generate key, which is in the form of channel-throttling:[type]:[userId].
        CacheKey cacheKey = CacheKey.channelThrottling(type, userId);

        Integer numRequests = cacheProvider.getObject(cacheKey, Integer.class);
        if (numRequests == null) {
            // Fall back to 0.
            numRequests = 0;
        }

        if (numRequests < channelThrottleMaxRequests) {
            // We've seen less than the maximum number of requests. We can let this request through without throttling.
            // But we should increment the request count in Redis. Reset the expiration so that participants can't
            // exceed the throttle limit by making a bunch of requests at the end of the throttle window.
            cacheProvider.setObject(cacheKey, numRequests + 1, channelThrottleTimeoutSeconds);
            return false;
        } else {
            // We've seen at least as many requests as our limit (or more), so this next request will push us over the
            // limit. Throttle this.
            // Don't update Redis, since we throttle email/SMS sent, not requests. This allows the expiration to expire
            // naturally.
            return true;
        }
    }
    
    long getDateTimeInMillis() {
        return DateTime.now().getMillis();
    }
}
