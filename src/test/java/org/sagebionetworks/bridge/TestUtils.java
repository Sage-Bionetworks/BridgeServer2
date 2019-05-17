package org.sagebionetworks.bridge;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.RandomStringUtils;
import org.mockito.Mockito;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.time.DateUtils;

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
                // If this is a rest controller and the method returns a String, then it 
                // should be annotated with a produces property that sets the mime type to 
                // JSON. Otherwise it won't return JSON unless the client sends the correct
                // Accept header, and we don't want content negotiation.
                RestController restAnn = controller.getDeclaredAnnotation(RestController.class);
                if (restAnn != null && method.getReturnType() == String.class) {
                    if (annClazz == GetMapping.class) {
                        GetMapping gm = (GetMapping)ann;
                        assertEquals(gm.produces()[0], MediaType.APPLICATION_JSON_UTF8_VALUE);
                    } else if (annClazz == PostMapping.class) {
                        PostMapping pm = (PostMapping)ann;
                        assertEquals(pm.produces()[0], MediaType.APPLICATION_JSON_UTF8_VALUE);
                    }
                }
                return ann;
            }
        }
        fail("Did not find method: " + methodName);
        return null;
    }
    
    public static void assertCrossOrigin(Class<?> controller) {
        Annotation ann = AnnotationUtils.findAnnotation(controller, CrossOrigin.class);
        assertNotNull(ann, "Missing the @CrossOrigin annotation");
        ann = AnnotationUtils.findAnnotation(controller, RestController.class);
        assertNotNull(ann, "Missing the @RestController annotation");
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
        assertEquals(status.code(), CREATED);        
    }
    
    /**
     * Create calls in our API are POSTs that return 202 (Accepted).
     */
    public static void assertAccept(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
        ResponseStatus status = assertMethodAnn(controller, methodName, ResponseStatus.class);
        assertEquals(status.code(), ACCEPTED);        
    }
    
    public static void assertDatesWithTimeZoneEqual(DateTime date1, DateTime date2) {
        // I don't know of a one line test for this... maybe just comparing ISO string formats of the date.
        assertTrue(date1.isEqual(date2));
        // This ensures that zones such as "America/Los_Angeles" and "-07:00" are equal 
        assertEquals(date1.getZone().getOffset(date1), date2.getZone().getOffset(date2));
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
    
    public static List<SchedulePlan> getSchedulePlans(StudyIdentifier studyId) {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("P3D", TestConstants.ACTIVITY_1));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("P1D", TestConstants.ACTIVITY_2));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);
        
        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("P2D", TestConstants.ACTIVITY_3));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);

        return plans;
    }
    
    public static ScheduleStrategy getStrategy(String interval, Activity activity) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + activity.getLabel());
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(activity);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
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
            Consumer<Account> accountEdits = invocation.getArgument(2);
            accountEdits.accept(mockAccount);
            return null;
        }).when(mockAccountDao).editAccount(Mockito.any(), Mockito.any(), Mockito.any());
    }
    
    public static JsonNode getClientData() {
        try {
            String json = TestUtils.createJson("{'booleanFlag':true,'stringValue':'testString','intValue':4}");
            return BridgeObjectMapper.get().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SchedulePlan getSimpleSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do task CCC")
                .withTask("CCC").build());
        schedule.setExpires(Period.parse("PT1H"));
        schedule.setLabel("Test label for the user");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Simple Test Plan");
        plan.setGuid("GGG");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        plan.setStrategy(strategy);
        return plan;
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
