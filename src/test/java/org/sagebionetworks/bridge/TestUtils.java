package org.sagebionetworks.bridge;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.RandomStringUtils;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.Validate;

public class TestUtils {
    private static final DateTime TEST_CREATED_ON = DateTime.parse("2015-01-27T00:38:32.486Z");

    public static class CustomServletInputStream extends ServletInputStream {
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
    
    public static void assertContentType(Class<?> controller, String methodName, String contentType) throws Exception {
        GetMapping ann = assertMethodAnn(controller, methodName, GetMapping.class);
        assertEquals(contentType, ann.produces()[0]);
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

    private static boolean includesPath(String[] paths, String path) {
        for (String onePath : paths) {
            if (onePath.equals(path)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Using @Test(expected=SomeException.class) has led us to create tests that pass because
     * exceptions are being thrown from the wrong place in the code under test. This utility method
     * will also verify the message in the exception, which can help us find and fix these misleading
     * succeeding tests.
     */
    public static void assertException(Class<? extends Exception> cls, String message, Runnable runnable) {
        try {
            runnable.run();
        } catch(Exception e) {
            if (!e.getClass().isAssignableFrom(cls)) {
                throw e;
            } else if (!e.getMessage().equals(message)) {
                throw e;
            }
            return;
        }
        fail("Should have thrown exception: " + cls.getName() + ", message: '" + message + "'");
    }

    /**
     * Mocks this DAO method behavior so that you can verify that AccountDao.editAccount() was called, and
     * that your mock account was correctly edited.
     * @param mockAccountService
     *      A mocked version of the AccountService interface
     * @param mockAccount
     *      A mocked version of the Account interface
     */
    @SuppressWarnings("unchecked")
    public static void mockEditAccount(AccountService mockAccountService, Account mockAccount) {
        Mockito.mockingDetails(mockAccountService).isMock();
        Mockito.mockingDetails(mockAccount).isMock();
        doAnswer(invocation -> {
            Consumer<Account> accountEdits = (Consumer<Account>)invocation.getArgument(2);
            accountEdits.accept(mockAccount);
            return null;
        }).when(mockAccountService).editAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    public static void assertDatesWithTimeZoneEqual(DateTime date1, DateTime date2) {
        // I don't know of a one line test for this... maybe just comparing ISO string formats of the date.
        assertTrue(date1.isEqual(date2));
        // This ensures that zones such as "America/Los_Angeles" and "-07:00" are equal 
        assertEquals(date2.getZone().getOffset(date2), date1.getZone().getOffset(date1));
    }

    public static <E> void assertListIsImmutable(List<E> list, E sampleElement) {
        try {
            list.add(sampleElement);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    public static <K, V> void assertMapIsImmutable(Map<K, V> map, K sampleKey, V sampleValue) {
        try {
            map.put(sampleKey, sampleValue);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    public static <E> void assertSetIsImmutable(Set<E> set, E sampleElement) {
        try {
            set.add(sampleElement);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    /**
     * Asserts that on validation, InvalidEntityException has been thrown with an error key that is the nested path to
     * the object value that is invalid, and the correct error message.
     */
    public static void assertValidatorMessage(Validator validator, Object object, String fieldName, String error) {
        if (error.contains("%s")) {
            error = String.format(error, fieldName);
        } else if (!error.startsWith(" ")) {
            error = fieldName + " " + error;
        } else {
            error = fieldName + error;
        }
        try {
            Validate.entityThrowingException(validator, object);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            if (e.getErrors().get(fieldName).contains(error)) {
                return;
            }
            fail("Did not find error message in errors object");
        }
    }

    public static Map<SubpopulationGuid,ConsentStatus> toMap(ConsentStatus... statuses) {
        return TestUtils.toMap(Lists.newArrayList(statuses));
    }

    public static Map<SubpopulationGuid,ConsentStatus> toMap(Collection<ConsentStatus> statuses) {
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>();
        if (statuses != null) {
            for (ConsentStatus status : statuses) {
                builder.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
            }
        }
        return builder.build();
    }

    public static String randomName(Class<?> clazz) {
        return "test-" + clazz.getSimpleName().toLowerCase() + "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static final NotificationMessage getNotificationMessage() {
        return new NotificationMessage.Builder()
                .withSubject("a subject").withMessage("a message").build();
    }

    public static final SubscriptionRequest getSubscriptionRequest() {
        return new SubscriptionRequest(Sets.newHashSet("topicA", "topicB"));
    }

    public static NotificationTopic getNotificationTopic() {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("topicGuid");
        topic.setName("Test Topic Name");
        topic.setShortName("Short Name");
        topic.setDescription("Test Description");
        topic.setAppId(TEST_APP_ID);
        topic.setTopicARN("atopicArn");
        return topic;
    }

    public static final IntentToParticipate.Builder getIntentToParticipate(long timestamp) {
        ConsentSignature consentSignature = new ConsentSignature.Builder()
                .withName("Gladlight Stonewell")
                .withBirthdate("1980-10-10")
                .withConsentCreatedOn(timestamp)
                .withImageData("image-data")
                .withImageMimeType("image/png").build();
        return new IntentToParticipate.Builder()
                .withAppId(TEST_APP_ID)
                .withScope(SharingScope.SPONSORS_AND_PARTNERS)
                .withPhone(TestConstants.PHONE)
                .withSubpopGuid("subpopGuid")
                .withConsentSignature(consentSignature);
    }

    public static NotificationRegistration getNotificationRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId("deviceId");
        registration.setEndpoint("endpoint");
        registration.setGuid("registrationGuid");
        registration.setHealthCode("healthCode");
        registration.setOsName("osName");
        registration.setCreatedOn(1484173675648L);
        return registration;
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

    public static List<ScheduledActivity> runSchedulerForActivities(List<SchedulePlan> plans, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            // It's become possible for a user to match no schedule
            if (schedule != null) {
                scheduledActivities.addAll(schedule.getScheduler().getScheduledActivities(plan, context));
            }
        }
        Collections.sort(scheduledActivities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return scheduledActivities;
    }

    public static List<ScheduledActivity> runSchedulerForActivities(ScheduleContext context) {
        return runSchedulerForActivities(getSchedulePlans(context.getCriteriaContext().getAppId()), context);
    }

    public static List<SchedulePlan> getSchedulePlans(String appId) {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);

        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("P3D", getActivity1()));
        plan.setAppId(appId);
        plans.add(plan);

        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("P1D", getActivity2()));
        plan.setAppId(appId);
        plans.add(plan);

        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("P2D", getActivity3()));
        plan.setAppId(appId);
        plans.add(plan);

        return plans;
    }

    public static Activity getActivity1() {
        return new Activity.Builder().withGuid("activity1guid").withLabel("Activity1")
                .withPublishedSurvey("identifier1", "AAA").build();
    }

    public static Activity getActivity2() {
        return new Activity.Builder().withGuid("activity2guid").withLabel("Activity2")
                .withPublishedSurvey("identifier2", "BBB").build();
    }

    public static Activity getActivity3() {
        return new Activity.Builder().withLabel("Activity3").withGuid("AAA").withTask("tapTest").build();
    }

    public static SchedulePlan getSimpleSchedulePlan(String appId) {
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
        plan.setAppId(appId);
        plan.setStrategy(strategy);
        return plan;
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

    public static DynamoApp getValidApp(Class<?> clazz) {
        String id = TestUtils.randomName(clazz);

        Map<String,String> pushNotificationARNs = Maps.newHashMap();
        pushNotificationARNs.put(OperatingSystem.IOS, "arn:ios:"+id);
        pushNotificationARNs.put(OperatingSystem.ANDROID, "arn:android:"+id);

        // This app will save without further modification.
        DynamoApp app = new DynamoApp();
        app.setName("Test App ["+clazz.getSimpleName()+"]");
        app.setShortName("ShortName");
        app.setAutoVerificationEmailSuppressed(true);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.setAppIdExcludedInExport(true);
        app.setIdentifier(id);
        app.setMinAgeOfConsent(18);
        app.setSponsorName("The Council on Test Studies");
        app.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        app.setConsentNotificationEmailVerified(true);
        app.setSynapseDataAccessTeamId(1234L);
        app.setSynapseProjectId("test-synapse-project-id");
        app.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        app.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        app.setUsesCustomExportSchedule(true);
        app.setSupportEmail("bridge-testing+support@sagebase.org");
        app.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        app.setTaskIdentifiers(Sets.newHashSet("task1", "task2"));
        app.setActivityEventKeys(Sets.newHashSet("event1", "event2"));
        app.setDataGroups(Sets.newHashSet("beta_users", "production_users"));
        app.setStrictUploadValidationEnabled(true);
        app.setHealthCodeExportEnabled(true);
        app.setEmailVerificationEnabled(true);
        app.setReauthenticationEnabled(true);
        app.setEmailSignInEnabled(true);
        app.setPhoneSignInEnabled(true);
        app.setVerifyChannelOnSignInEnabled(true);
        app.setExternalIdRequiredOnSignup(true);
        app.setActive(true);
        app.setDisableExport(false);
        app.setAccountLimit(0);
        app.setPushNotificationARNs(pushNotificationARNs);
        app.setAutoVerificationPhoneSuppressed(true);
        Map<String,String> defaultTemplates = new HashMap<>();
        for (TemplateType type : TemplateType.values()) {
            String typeName = type.name().toLowerCase();
            defaultTemplates.put(typeName, "ABC-DEF");
        }
        app.setDefaultTemplates(defaultTemplates);
        return app;
    }

    public static SchedulePlan getABTestSchedulePlan(String appId) {
        Schedule schedule1 = new Schedule();
        schedule1.setScheduleType(ScheduleType.RECURRING);
        schedule1.setCronTrigger("0 0 8 ? * TUE *");
        schedule1.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do AAA task")
                .withTask("AAA").build());
        schedule1.setExpires(Period.parse("PT1H"));
        schedule1.setLabel("Schedule 1");

        Schedule schedule2 = new Schedule();
        schedule2.setScheduleType(ScheduleType.RECURRING);
        schedule2.setCronTrigger("0 0 8 ? * TUE *");
        schedule2.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do BBB task")
                .withTask("BBB").build());
        schedule2.setExpires(Period.parse("PT1H"));
        schedule2.setLabel("Schedule 2");

        Schedule schedule3 = new Schedule();
        schedule3.setScheduleType(ScheduleType.RECURRING);
        schedule3.setCronTrigger("0 0 8 ? * TUE *");
        schedule3.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do CCC task")
                .withTask("CCC").build());
        schedule3.setExpires(Period.parse("PT1H"));
        schedule3.setLabel("Schedule 3");

        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("AAA");
        plan.setLabel("Test A/B Schedule");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setAppId(appId);

        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, schedule1);
        strategy.addGroup(40, schedule2);
        strategy.addGroup(20, schedule3);
        plan.setStrategy(strategy);

        return plan;
    }

    public static Schedule getSchedule(String label) {
        Activity activity = new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Test survey")
                .withSurvey("identifier", "ABC", TEST_CREATED_ON).build();

        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        return schedule;
    }

    public static AppConfigElement getAppConfigElement() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("id");
        element.setRevision(3L);
        element.setDeleted(false);
        element.setData(getClientData());
        element.setCreatedOn(DateTime.now().minusHours(2).getMillis());
        element.setModifiedOn(DateTime.now().minusHours(1).getMillis());
        element.setVersion(1L);
        return element;
    }
    
    public static MasterSchedulerConfig getMasterSchedulerConfig() {
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        
        MasterSchedulerConfig config = MasterSchedulerConfig.create();
        config.setScheduleId("test-schedule-id");
        config.setCronSchedule("testCronSchedule");
        config.setRequestTemplate(objNode);
        config.setSqsQueueUrl("testSysQueueUrl");
        config.setVersion(1L);
        
        return config;
    }
    public static JsonNode getClientData() {
        try {
            String json = TestUtils.createJson("{'booleanFlag':true,'stringValue':'testString','intValue':4}");
            return BridgeObjectMapper.get().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode getOtherClientData() {
        JsonNode clientData = TestUtils.getClientData();
        ((ObjectNode)clientData).put("newField", "newValue");
        return clientData;
    }

    /**
     * Converts single quote marks to double quote marks to convert JSON using single quotes to valid JSON.
     * Useful to create more readable inline JSON in tests, because double quotes must be escaped in Java.
     */
    public static String createJson(String json) {
        return json.replaceAll("'", "\"");
    }

    public static Criteria createCriteria(Integer minAppVersion, Integer maxAppVersion, Set<String> allOfGroups, Set<String> noneOfGroups) {
        DynamoCriteria crit = new DynamoCriteria();
        crit.setMinAppVersion(OperatingSystem.IOS, minAppVersion);
        crit.setMaxAppVersion(OperatingSystem.IOS, maxAppVersion);
        crit.setAllOfGroups(allOfGroups);
        crit.setNoneOfGroups(noneOfGroups);
        return crit;
    }

    public static Criteria copyCriteria(Criteria criteria) {
        DynamoCriteria crit = new DynamoCriteria();
        if (criteria != null) {
            crit.setKey(criteria.getKey());
            crit.setLanguage(criteria.getLanguage());
            for (String osName : criteria.getAppVersionOperatingSystems()) {
                crit.setMinAppVersion(osName, criteria.getMinAppVersion(osName));
                crit.setMaxAppVersion(osName, criteria.getMaxAppVersion(osName));
            }
            crit.setNoneOfGroups(criteria.getNoneOfGroups());
            crit.setAllOfGroups(criteria.getAllOfGroups());
        }
        return crit;
    }

    /**
     * Guava does not have a version of this method that also lets you add items.
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> newLinkedHashSet(T... items) {
        LinkedHashSet<T> set = new LinkedHashSet<T>();
        for (T item : items) {
            set.add(item);
        }
        return set;
    }

    public static String makeRandomTestEmail(Class<?> cls) {
        String devPart = BridgeConfigFactory.getConfig().getUser();
        String rndPart = TestUtils.randomName(cls);
        return String.format("bridge-testing+%s-%s@sagebase.org", devPart, rndPart);
    }
}
