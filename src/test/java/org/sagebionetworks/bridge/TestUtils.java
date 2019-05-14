package org.sagebionetworks.bridge;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;

public class TestUtils {

    private static class CustomServletInputStream extends ServletInputStream {
        private ByteArrayInputStream buffer;
        public CustomServletInputStream(String content) {
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("Input stream stub constructed without string input");
            }
            this.buffer = new ByteArrayInputStream(content.getBytes());
        }
        @Override
        public int read() throws IOException {
            return buffer.read();
        }
        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }
        @Override
        public boolean isReady() {
            return true;
        }
        @Override
        public void setReadListener(ReadListener listener) {
            throw new RuntimeException("Not implemented");
        }
    }
    
    public static ServletInputStream toInputStream(String content) {
        return new CustomServletInputStream(content);
    }
    
    public static String createJson(String json, Object... args) {
        return String.format(json.replaceAll("'", "\""), args);
    }
    
    /**
     * The correctness of annotations on controller methods is very important, so here is a utilty 
     * to add verification to tests.
     */
    private static <A extends Annotation, C> A assertMethodAnn(Class<?> controller,
            String methodName, Class<A> annClazz) throws Exception {
        // For simplicity sake, avoid matching arguments. Controllers don't use method overloading.
        Method[] methods = controller.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                A ann = AnnotationUtils.findAnnotation(method, annClazz);
                assertNotNull(ann);
                return ann;
            }
        }
        fail("Did not find method: " + methodName);
        return null;
    }    
    
    public static void assertCrossOrigin(Class<?> controller) {
        Annotation ann = AnnotationUtils.findAnnotation(controller, CrossOrigin.class);
        assertNotNull(ann);
    }
    
    public static void assertGet(Class<?> controller, String methodName, String... paths) throws Exception {
        GetMapping ann = assertMethodAnn(controller, methodName, GetMapping.class);
        if (paths != null && paths.length > 0) {
            for (String path : paths) {
                assertTrue(includesPath(ann.path(), path), "Path not found in paths declared for annotation");
            }
        }
    }
    
    public static void assertPost(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
    }

    public static void assertDelete(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, DeleteMapping.class);
    }
    
    /**
     * Create calls in our API are POSTs that return 201 (Created).
     */
    public static void assertCreate(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
        ResponseStatus status = assertMethodAnn(controller, methodName, ResponseStatus.class);
        assertEquals(status.code(), HttpStatus.CREATED);        
    }
    
    /**
     * Create calls in our API are POSTs that return 202 (Accepted).
     */
    public static void assertAccept(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
        ResponseStatus status = assertMethodAnn(controller, methodName, ResponseStatus.class);
        assertEquals(status.code(), HttpStatus.ACCEPTED);        
    }
    
    public static void mockRequestBody(HttpServletRequest mockRequest, String json) throws Exception {
        ServletInputStream stream = new CustomServletInputStream(json);
        when(mockRequest.getInputStream()).thenReturn(stream);
    }
    
    public static void mockRequestBody(HttpServletRequest mockRequest, Object object) throws Exception {
        // Use BridgeObjectMapper or you will get an error when serializing objects with a filter 
        String json = BridgeObjectMapper.get().writeValueAsString(object);
        ServletInputStream stream = new CustomServletInputStream(json);
        when(mockRequest.getInputStream()).thenReturn(stream);
    }
    
    /**
     * Mocks this DAO method behavior so that you can verify that AccountDao.editAccount() was called, and 
     * that your mock account was correctly edited.
     * @param mockAccountDao
     *      A mocked version of the AccountDao interface
     * @param mockAccount
     *      A mocked version of the Account interface
     */
    @SuppressWarnings("unchecked")
    public static void mockEditAccount(AccountDao mockAccountDao, Account mockAccount) {
        Mockito.mockingDetails(mockAccountDao).isMock();
        Mockito.mockingDetails(mockAccount).isMock();
        doAnswer(invocation -> {
            Consumer<Account> accountEdits = (Consumer<Account>)invocation.getArgumentAt(2, Consumer.class);
            accountEdits.accept(mockAccount);
            return null;
        }).when(mockAccountDao).editAccount(Mockito.any(), Mockito.any(), Mockito.any());
    }
    
    public static void assertDatesWithTimeZoneEqual(DateTime date1, DateTime date2) {
        // I don't know of a one line test for this... maybe just comparing ISO string formats of the date.
        assertTrue(date1.isEqual(date2));
        // This ensures that zones such as "America/Los_Angeles" and "-07:00" are equal 
        assertEquals(date1.getZone().getOffset(date1), date2.getZone().getOffset(date2));
    }
    
    public static String randomName(Class<?> clazz) {
        return "test-" + clazz.getSimpleName().toLowerCase() + "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static final StudyParticipant getStudyParticipant(Class<?> clazz) {
        String randomName = TestUtils.randomName(clazz);
        return new StudyParticipant.Builder()
                .withFirstName("FirstName")
                .withLastName("LastName")
                .withExternalId("externalId")
                .withEmail("bridge-testing+"+randomName+"@sagebase.org")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1"))
                .withAttributes(new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build())
                .withLanguages(ImmutableList.of("fr")).build();
    }
    
    private static boolean includesPath(String[] paths, String path) {
        for (String onePath : paths) {
            if (onePath.equals(path)) {
                return true;
            }
        }
        return false;
    }
    
    public static DynamoStudy getValidStudy(Class<?> clazz) {
        String id = TestUtils.randomName(clazz);
        
        Map<String,String> pushNotificationARNs = Maps.newHashMap();
        pushNotificationARNs.put(OperatingSystem.IOS, "arn:ios:"+id);
        pushNotificationARNs.put(OperatingSystem.ANDROID, "arn:android:"+id);
        
        // This study will save without further modification.
        DynamoStudy study = new DynamoStudy();
        study.setName("Test Study ["+clazz.getSimpleName()+"]");
        study.setShortName("ShortName");
        study.setAutoVerificationEmailSuppressed(true);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setStudyIdExcludedInExport(true);
        study.setVerifyEmailTemplate(new EmailTemplate("verifyEmail subject", "body with ${url}", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("resetPassword subject", "body with ${url}", MimeType.TEXT));
        study.setEmailSignInTemplate(new EmailTemplate("${studyName} link", "Follow link ${url}", MimeType.TEXT));
        study.setAccountExistsTemplate(new EmailTemplate("accountExists subject", "body with ${resetPasswordUrl}", MimeType.TEXT));
        study.setSignedConsentTemplate(new EmailTemplate("signedConsent subject", "body", MimeType.TEXT));
        study.setAppInstallLinkTemplate(new EmailTemplate("app install subject", "body ${appInstallUrl}", MimeType.TEXT));
        study.setResetPasswordSmsTemplate(new SmsTemplate("resetPasswordSmsTemplate ${resetPasswordUrl}"));
        study.setPhoneSignInSmsTemplate(new SmsTemplate("phoneSignInSmsTemplate ${token}"));
        study.setAppInstallLinkSmsTemplate(new SmsTemplate("appInstallLinkSmsTemplate ${appInstallUrl}"));
        study.setVerifyPhoneSmsTemplate(new SmsTemplate("verifyPhoneSmsTemplate ${token}"));
        study.setAccountExistsSmsTemplate(new SmsTemplate("accountExistsSmsTemplate ${token}"));
        study.setSignedConsentSmsTemplate(new SmsTemplate("signedConsent ${consentUrl}"));
        study.setIdentifier(id);
        study.setMinAgeOfConsent(18);
        study.setSponsorName("The Council on Test Studies");
        study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        study.setConsentNotificationEmailVerified(true);
        study.setSynapseDataAccessTeamId(1234L);
        study.setSynapseProjectId("test-synapse-project-id");
        study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        study.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        study.setUsesCustomExportSchedule(true);
        study.setSupportEmail("bridge-testing+support@sagebase.org");
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setTaskIdentifiers(Sets.newHashSet("task1", "task2"));
        study.setActivityEventKeys(Sets.newHashSet("event1", "event2"));
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users"));
        study.setStrictUploadValidationEnabled(true);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(true);
        study.setExternalIdValidationEnabled(true);
        study.setReauthenticationEnabled(true);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        study.setVerifyChannelOnSignInEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        study.setActive(true);
        study.setDisableExport(false);
        study.setAccountLimit(0);
        study.setPushNotificationARNs(pushNotificationARNs);
        study.setAutoVerificationPhoneSuppressed(true);
        return study;
    }
}
