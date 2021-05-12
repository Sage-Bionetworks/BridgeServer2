package org.sagebionetworks.bridge;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.LICENSE;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.PUBLICATION;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.services.StudyConsentService.SIGNATURE_BLOCK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.jsoup.safety.Whitelist;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfigValidatorTest;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.Enrollment;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BridgeUtilsTest {
    
    private static final Label LABEL_HI = new Label("hi", "Hindi");
    private static final Label LABEL_EN = new Label("en", "English");
    private static final Label LABEL_JA = new Label("ja", "Japanese");
    private static final Label LABEL_ES = new Label("es", "Spanish");
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.parse("2010-10-10T10:10:10.111");
    
    @AfterMethod
    public void after() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void generateUUID() {
        // create 20 UUIDs, they should all be unique.
        Set<String> uuids = new HashSet<>();
        for (int i=0; i < 20; i++) {
            String uuid = BridgeUtils.generateGuid();
            assertEquals(uuid.length(), 24);
            uuids.add( uuid );
        }
        assertEquals(uuids.size(), 20);
    }
    
    @Test
    public void mapStudyMemberships() {
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", "accountId");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", "accountId", "extB");
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", "accountId", "extC");
        Enrollment en4 = Enrollment.create(TEST_APP_ID, "studyD", "accountId");
        Enrollment en5 = Enrollment.create(TEST_APP_ID, "studyE", "accountId", "extD");
        // withdraw en5 so we verify it is not in the map
        en5.setWithdrawnOn(MODIFIED_ON);
        account.setEnrollments(ImmutableSet.of(en1, en2, en3, en4, en5));
        
        Map<String, String> results = BridgeUtils.mapStudyMemberships(account);
        assertEquals(results.size(), 4);
        assertEquals(results.get("studyA"), "<none>");
        assertEquals(results.get("studyB"), "extB");
        assertEquals(results.get("studyC"), "extC");
        assertEquals(results.get("studyD"), "<none>");
    }
    
    @Test
    public void mapStudyMembershipsOneEntry() {
        Account account = Account.create();
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "studyB", "accountId", "extB");
        account.setEnrollments(ImmutableSet.of(enrollment));
        
        Map<String, String> results = BridgeUtils.mapStudyMemberships(account);
        assertEquals(results.size(), 1);
        assertEquals(results.get("studyB"), "extB");
    }
    
    @Test
    public void mapStudyMembershipsNull() {
        Account account = Account.create();
        account.setEnrollments(null);
        
        Map<String, String> results = BridgeUtils.mapStudyMemberships(account);
        assertTrue(results.isEmpty());
    }
    
    @Test
    public void mapStudyMembershipsBlank() {
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of());
        
        Map<String, String> results = BridgeUtils.mapStudyMemberships(account);
        assertTrue(results.isEmpty());
    }

    @Test
    public void studyIdsVisibleToCallerFilters() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(callerStudies).build());

        Enrollment enA = Enrollment.create(TEST_APP_ID, "studyA", "id");
        Enrollment enB = Enrollment.create(TEST_APP_ID, "studyB", "id");
        Enrollment enC = Enrollment.create(TEST_APP_ID, "studyC", "id");
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(enA, enB, enC));

        Set<String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getStudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of("studyA", "studyB"));
    }
    
    @Test
    public void studyIdsVisibleToCallerNoFilterWhenStudiesEmpty() {
        Enrollment enA = Enrollment.create(TEST_APP_ID, "studyA", "id");
        Enrollment enB = Enrollment.create(TEST_APP_ID, "studyB", "id");
        Enrollment enC = Enrollment.create(TEST_APP_ID, "studyC", "id");
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(enA, enB, enC));

        Set<String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getStudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of("studyA", "studyB", "studyC"));
    }
    
    @Test
    public void studyIdsVisibleToCallerEmpty() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder().withCallerEnrolledStudies(callerStudies).build());
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of());
        Set<String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getStudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of());
    }    
    
    @Test
    public void studyIdsVisibleToCallerNull() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder().withCallerEnrolledStudies(callerStudies).build());
        
        Set<String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(null)
                .getStudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of());
    }
    
    @Test
    public void externalIdsVisibleToCaller() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(callerStudies).build());

        Enrollment enA = Enrollment.create(TEST_APP_ID, "studyA", "id", "extA");
        Enrollment enB = Enrollment.create(TEST_APP_ID, "studyB", "id", "extB");
        Enrollment enC = Enrollment.create(TEST_APP_ID, "studyC", "id", "extC");
        
        Account account = Account.create();
        account.setId("id");
        account.setEnrollments(ImmutableSet.of(enA, enB, enC));

        Map<String, String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of("studyA", "extA", "studyB", "extB"));
    }
    
    @Test
    public void externalIdsVisibleToCallerNoFilterWhenStudiesEmpty() {
        Enrollment enA = Enrollment.create(TEST_APP_ID, "studyA", "id", "extA");
        Enrollment enB = Enrollment.create(TEST_APP_ID, "studyB", "id", "extB");
        Enrollment enC = Enrollment.create(TEST_APP_ID, "studyC", "id", "extC");
        
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(enA, enB, enC));

        Map<String, String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of("studyA", "extA", "studyB", "extB", "studyC", "extC"));
    }    

    @Test
    public void externalIdsVisibleToCallerEmpty() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder().withCallerEnrolledStudies(callerStudies).build());

        Account account = Account.create();
        
        Map<String, String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(account)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of());
    }      
    
    @Test
    public void externalIdsVisibleToCallerNull() {
        Set<String> callerStudies = ImmutableSet.of("studyA", "studyB", "studyD");
        RequestContext.set(new RequestContext.Builder().withCallerEnrolledStudies(callerStudies).build());
        
        Map<String, String> visibles = BridgeUtils.studyAssociationsVisibleToCaller(null)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of());
    }
    
    @Test
    public void collectExternalIds() {
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", "userId", "subAextId");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", "userId", "subBextId");
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", "userId");
        // Create one withdrawn enrollment that is ignored
        Enrollment en4 = Enrollment.create(TEST_APP_ID, "studyD", "userId", "subEextId");
        en4.setWithdrawnOn(MODIFIED_ON);
        account.setEnrollments(ImmutableSet.of(en1, en2, en3, en4));
        
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        assertEquals(externalIds, ImmutableSet.of("subAextId","subBextId"));
    }
    
    @Test
    public void collectExternalIdsNullsAreIgnored() {
        Set<String> externalIds = BridgeUtils.collectExternalIds(Account.create());
        assertEquals(externalIds, ImmutableSet.of());
    } 
    
    @Test
    public void collectStudyIds() {
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", "userId");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", "userId");
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", "userId");
        Enrollment en4 = Enrollment.create(TEST_APP_ID, "studyD", "userId");
        // create one withdrawn enrollment that is ignored
        en4.setWithdrawnOn(MODIFIED_ON);
        account.setEnrollments(ImmutableSet.of(en1, en2, en3, en4));
        
        Set<String> externalIds = BridgeUtils.collectStudyIds(account);
        assertEquals(externalIds, ImmutableSet.of("studyA","studyB", "studyC"));
    }
    
    @Test
    public void collectStudyIdsNullsAreIgnored() {
        Set<String> externalIds = BridgeUtils.collectStudyIds(Account.create());
        assertEquals(externalIds, ImmutableSet.of());
    }
    
    @Test
    public void filterForStudyAccountRemovesUnsharedStudyIds() {
        Set<String> studies = ImmutableSet.of("studyA");
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(studies).build());
        
        Account account = BridgeUtils.filterForStudy(getAccountWithStudy("studyB", "studyA"));
        assertEquals(account.getEnrollments().size(), 1);
        assertEquals(Iterables.getFirst(account.getEnrollments(), null).getStudyId(), "studyA");
        
        RequestContext.set(null);
    }
    
    @Test
    public void filterForStudyAccountReturnsAllUnsharedStudyIdsForNonStudyResearcher() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        Account account = BridgeUtils.filterForStudy(getAccountWithStudy("studyB", "studyA"));
        assertEquals(account.getEnrollments().size(), 2);
    }
    
    @Test
    public void filterForStudyAccountNullReturnsNull() {
        assertNull(BridgeUtils.filterForStudy((Account)null));
    }
    
    @Test
    public void filterForStudyAccountNoContextNoStudyDoesNotReturnAccount() {
        assertNull(BridgeUtils.filterForStudy(getAccountWithStudy()));
    }
    
    @Test
    public void filterForStudyAccountNoContextWithStudyDoesNotReturnAccount() {
        assertNull(BridgeUtils.filterForStudy(getAccountWithStudy("studyA")));
    }
    
    @Test
    public void filterForStudyAccountWithStudiesHidesNormalAccount() {
        RequestContext.set(new RequestContext.Builder().withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        assertNull(BridgeUtils.filterForStudy(getAccountWithStudy()));
        RequestContext.set(null);
    }

    @Test
    public void filterForStudyAccountWhichIsSelfReturnsStudyAccount() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id").build());
        assertNotNull(BridgeUtils.filterForStudy(getAccountWithStudy("studyA")));
    }
    
    @Test
    public void filterForStudyAccountWithMismatchedStudiesHidesStudyAccount() {
        RequestContext.set(new RequestContext.Builder().withOrgSponsoredStudies(ImmutableSet.of("notStudyA")).build());
        assertNull(BridgeUtils.filterForStudy(getAccountWithStudy("studyA")));
    }
    
    @Test
    public void filterForStudyAccountReturnsSelfAccount() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withOrgSponsoredStudies(ImmutableSet.of("A")).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        
        assertNotNull(BridgeUtils.filterForStudy(account));
    }

    @Test
    public void filterForStudyAccountNotSelfReturnsNull() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withOrgSponsoredStudies(ImmutableSet.of("A")).build());
        
        Account account = Account.create();
        account.setId("notTheSameUser");
        
        assertNull(BridgeUtils.filterForStudy(account));
    }
    
    private Account getAccountWithStudy(String... studyIds) {
        Account account = Account.create();
        account.setId("id");
        Set<Enrollment> enrollments = Arrays.asList(studyIds)
                .stream()
                .map(id -> Enrollment.create(TEST_APP_ID, id, "accountId"))
                .collect(toSet());
        account.setEnrollments(enrollments);
        return account;
    }
    
    @Test
    public void isExternalIdAccount() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("id").build();
        assertTrue(BridgeUtils.isExternalIdAccount(participant));
    }
    
    @Test
    public void isNotExternalIdAccount() {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withExternalId("id").build();
        assertFalse(BridgeUtils.isExternalIdAccount(participant));
    }

    @Test
    public void getRequestContext() throws Exception {
        // Can set request ID in this thread.
        RequestContext context = new RequestContext.Builder().withRequestId("main request ID").build();
        RequestContext otherContext = new RequestContext.Builder().withRequestId("other request ID").build();
        
        RequestContext.set(context);
        assertEquals(RequestContext.get().getId(), "main request ID");

        // Request ID is thread local, so a separate thread should see a different request ID.
        Runnable runnable = () -> {
            assertEquals(RequestContext.get(), RequestContext.NULL_INSTANCE);
            RequestContext.set(otherContext);
            assertEquals(RequestContext.get().getId(), "other request ID");
        };
        Thread otherThread = new Thread(runnable);
        otherThread.start();
        otherThread.join();

        // Other thread doesn't affect this thread.
        assertEquals(RequestContext.get().getId(), "main request ID");

        // Setting request ID to null is fine.
        RequestContext.set(null);
        assertEquals(RequestContext.get(), RequestContext.NULL_INSTANCE);
    }

    @Test
    public void secondsToPeriodString() {
        assertEquals(BridgeUtils.secondsToPeriodString(30), "30 seconds");
        assertEquals(BridgeUtils.secondsToPeriodString(60), "1 minute");
        assertEquals(BridgeUtils.secondsToPeriodString(90), "90 seconds");
        assertEquals(BridgeUtils.secondsToPeriodString(60*5), "5 minutes");
        assertEquals(BridgeUtils.secondsToPeriodString(60*25), "25 minutes");
        assertEquals(BridgeUtils.secondsToPeriodString(60*90), "90 minutes");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60), "1 hour");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60*2), "2 hours");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60*36), "36 hours");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60*24), "1 day");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60*24*2), "2 days");
        assertEquals(BridgeUtils.secondsToPeriodString(60*60*24*7), "7 days");
    }
    
    @Test
    public void parseAccountId() {
        // Identifier has upper-case letter to ensure we don't downcase or otherwise change it.
        AccountId accountId = BridgeUtils.parseAccountId("test", "IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "externalid:IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getExternalId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "externalId:IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getExternalId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "healthcode:IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getHealthCode(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "healthCode:IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getHealthCode(), "IdentifierA9");
        
        // Unrecognized prefix is just part of the userId
        accountId = BridgeUtils.parseAccountId("test", "unk:IdentifierA9");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getId(), "unk:IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "synapseUserId:IdentifierA10");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA10");
        
        accountId = BridgeUtils.parseAccountId("test", "synapseuserid:IdentifierA11");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA11");
        
        accountId = BridgeUtils.parseAccountId("test", "syn:IdentifierA12");
        assertEquals(accountId.getAppId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA12");
    }
    
    @Test
    public void appTemplateVariblesWorks() {
        String host = BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws");
        assertTrue(StringUtils.isNotBlank(host));
        
        App app = App.create();
        app.setName("name1");
        app.setShortName("shortName");
        app.setIdentifier(TEST_APP_ID);
        app.setSponsorName("sponsorName1");
        app.setSupportEmail("supportEmail1");
        app.setTechnicalEmail("technicalEmail1");
        app.setConsentNotificationEmail("consentNotificationEmail1");
        Map<String,String> map = BridgeUtils.appTemplateVariables(app, (value) -> {
            return value.replaceAll("1", "2");
        });
        map.put("thisMap", "isMutable");
        
        assertEquals(map.get("studyName"), "name2");
        assertEquals(map.get("studyShortName"), "shortName");
        assertEquals(map.get("studyId"), TEST_APP_ID);
        assertEquals(map.get("appName"), "name2");
        assertEquals(map.get("appShortName"), "shortName");
        assertEquals(map.get("appId"), TEST_APP_ID);
        assertEquals(map.get("sponsorName"), "sponsorName2");
        assertEquals(map.get("supportEmail"), "supportEmail2");
        assertEquals(map.get("technicalEmail"), "technicalEmail2");
        assertEquals(map.get("consentEmail"), "consentNotificationEmail2");
        assertEquals(map.get("thisMap"), "isMutable");
        assertEquals(map.get("host"), host);
    }
    
    @Test
    public void templateResolverHandlesNullConsentEmail() {
        App app = TestUtils.getValidApp(BridgeUtilsTest.class);
        app.setConsentNotificationEmail(null);
        
        Map<String,String> map = BridgeUtils.appTemplateVariables(app);
        assertNull(map.get("consentEmail"));
    }
    
    @Test
    public void templateResolverWorks() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", "Belgium");
        map.put("box", "Albuquerque");
        map.put("foo", "This is unused");
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz} bar ${baz} ${box} ${unused}", map);
        assertEquals(result, "foo Belgium bar Belgium Albuquerque ${unused}");
    }
    
    @Test
    public void templateResolverHandlesSomeJunkValues() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", null);
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz}", map);
        assertEquals(result, "foo ${baz}");
        
        result = BridgeUtils.resolveTemplate(" ", map);
        assertEquals(result, " ");
    }
    
    @Test
    public void periodsNotInterpretedAsRegex() {
        Map<String,String> map = Maps.newHashMap();
        map.put("b.z", "bar");
        
        String result = BridgeUtils.resolveTemplate("${baz}", map);
        assertEquals(result, "${baz}");
    }
    
    @Test
    public void commaListToSet() {
        Set<String> set = BridgeUtils.commaListToOrderedSet("a, b , c");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("a,b,c");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("");
        orderedSetsEqual(TestUtils.newLinkedHashSet(), set);
        
        set = BridgeUtils.commaListToOrderedSet(null);
        assertNotNull(set);
        
        set = BridgeUtils.commaListToOrderedSet(" a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a"), set);
        
        // Does not produce a null value.
        set = BridgeUtils.commaListToOrderedSet("a,,b");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b"), set);
        
        set = BridgeUtils.commaListToOrderedSet("b,a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("b","a"), set);
    }
    
    @Test
    public void setToCommaList() {
        Set<String> set = Sets.newHashSet("a", null, "", "b");
        
        assertEquals(BridgeUtils.setToCommaList(set), "a,b");
        assertNull(BridgeUtils.setToCommaList(null));
        assertNull(BridgeUtils.setToCommaList(Sets.newHashSet()));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void nullsafeImmutableSet() {
        assertEquals(BridgeUtils.nullSafeImmutableSet(null).size(), 0);
        assertEquals(BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A")), Sets.newHashSet("A"));
        
        // This should throw an UnsupportedOperationException
        Set<String> set = BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A"));
        set.add("B");
    }
    
    @Test
    public void nullsAreRemovedFromSet() {
        // nulls are removed. They have to be to create ImmutableSet
        assertEquals(BridgeUtils.nullSafeImmutableSet(Sets.newHashSet(null, "A")), Sets.newHashSet("A"));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void nullsafeImmutableList() {
        assertEquals(BridgeUtils.nullSafeImmutableList(null).size(), 0);
        
        assertEquals(BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B")), Lists.newArrayList("A","B"));
        
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B"));
        list.add("C");
    }
    
    @Test
    public void nullsAreRemovedFromList() {
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList(null,"A",null,"B"));
        assertEquals(list, Lists.newArrayList("A","B"));
    }    
    
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void nullsafeImmutableMap() {
        assertEquals(BridgeUtils.nullSafeImmutableMap(null).size(), 0);
        
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", "D");
        
        assertEquals(map.get("A"), "B");
        assertEquals(map.get("C"), "D");
        assertEquals(BridgeUtils.nullSafeImmutableMap(map), map);
        
        Map<String,String> newMap = BridgeUtils.nullSafeImmutableMap(map);
        newMap.put("E","F");
    }
    
    @Test
    public void nullsAreRemovedFromMap() {
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", null);
        
        Map<String,String> mapWithoutNulls = Maps.newHashMap();
        mapWithoutNulls.put("A", "B");
        
        assertEquals(BridgeUtils.nullSafeImmutableMap(map), mapWithoutNulls);
    }    
    
    @Test
    public void textToErrorKey() {
        assertEquals(BridgeUtils.textToErrorKey("iPhone OS"), "iphone_os");
        assertEquals(BridgeUtils.textToErrorKey("Android"), "android");
        assertEquals(BridgeUtils.textToErrorKey("Tester's Operating System v2"), "testers_operating_system_v2");
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void textToErrorKeyRejectsNull() {
        BridgeUtils.textToErrorKey(null);
    }
            
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void textToErrorKeyRejectsEmptyString() {
        BridgeUtils.textToErrorKey(" ");
    }
    
    @Test
    public void parseIntegerOrDefault() {
        assertEquals(BridgeUtils.getIntOrDefault(null, 3), 3);
        assertEquals(BridgeUtils.getIntOrDefault("  ", 3), 3);
        assertEquals(BridgeUtils.getIntOrDefault("1", 3), 1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void parseIntegerOrDefaultThrowsException() {
        BridgeUtils.getIntOrDefault("asdf", 3);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void withoutNullEntriesNullMap() {
        BridgeUtils.withoutNullEntries(null);
    }

    @Test
    public void withoutNullEntriesEmptyMap() {
        Map<String, String> outputMap = BridgeUtils.withoutNullEntries(ImmutableMap.of());
        assertTrue(outputMap.isEmpty());
    }

    @Test
    public void withoutNullEntries() {
        Map<String, String> inputMap = new HashMap<>();
        inputMap.put("AAA", "111");
        inputMap.put("BBB", null);
        inputMap.put("CCC", "333");

        Map<String, String> outputMap = BridgeUtils.withoutNullEntries(inputMap);
        assertEquals(outputMap.size(), 2);
        assertEquals(outputMap.get("AAA"), "111");
        assertEquals(outputMap.get("CCC"), "333");

        // validate that modifying the input map doesn't affect the output map
        inputMap.put("new key", "new value");
        assertEquals(outputMap.size(), 2);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void putOrRemoveNullMap() {
        BridgeUtils.putOrRemove(null, "key", "value");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void putOrRemoveNullKey() {
        BridgeUtils.putOrRemove(new HashMap<>(), null, "value");
    }

    @Test
    public void putOrRemove() {
        Map<String, String> map = new HashMap<>();

        // put some values and verify
        BridgeUtils.putOrRemove(map, "AAA", "111");
        BridgeUtils.putOrRemove(map, "BBB", "222");
        BridgeUtils.putOrRemove(map, "CCC", "333");
        assertEquals(map.size(), 3);
        assertEquals(map.get("AAA"), "111");
        assertEquals(map.get("BBB"), "222");
        assertEquals(map.get("CCC"), "333");

        // replace a value and verify
        BridgeUtils.putOrRemove(map, "CCC", "not 333");
        assertEquals(map.size(), 3);
        assertEquals(map.get("AAA"), "111");
        assertEquals(map.get("BBB"), "222");
        assertEquals(map.get("CCC"), "not 333");

        // remove a value and verify
        BridgeUtils.putOrRemove(map, "BBB", null);
        assertEquals(map.size(), 2);
        assertEquals(map.get("AAA"), "111");
        assertEquals(map.get("CCC"), "not 333");
    }

    @Test
    public void testGetLongOrDefault() {
        assertNull(BridgeUtils.getLongOrDefault(null, null));
        assertEquals(BridgeUtils.getLongOrDefault(null, 10L), new Long(10));
        assertEquals(BridgeUtils.getLongOrDefault("20", null), new Long(20));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void testGetLongWithNonLongValue() {
        BridgeUtils.getLongOrDefault("asdf20", 10L);
    }
    
    @Test
    public void testGetDateTimeOrDefault() {
        DateTime dateTime = DateTime.now();
        assertNull(BridgeUtils.getDateTimeOrDefault(null, null));
        assertEquals(BridgeUtils.getDateTimeOrDefault(null, dateTime), dateTime);
        assertTrue(dateTime.isEqual(BridgeUtils.getDateTimeOrDefault(dateTime.toString(), null)));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetDateTimeWithInvalidDateTime() {
        BridgeUtils.getDateTimeOrDefault("asdf", null);
    }
    
    @Test
    public void encodeURIComponent() {
        assertEquals(BridgeUtils.encodeURIComponent("tester+4@tester.com"), "tester%2B4%40tester.com");
    }
    
    @Test
    public void encodeURIComponentEmpty() {
        assertEquals(BridgeUtils.encodeURIComponent(""), "");
    }
    
    @Test
    public void encodeURIComponentNull() {
        assertNull(BridgeUtils.encodeURIComponent(null));
    }
    
    @Test
    public void encodeURIComponentNoEscaping() {
        assertEquals(BridgeUtils.encodeURIComponent("foo-bar"), "foo-bar");
    }
    
    @Test
    public void passwordPolicyDescription() {
        PasswordPolicy policy = new PasswordPolicy(8, false, true, false, true);
        String description = BridgeUtils.passwordPolicyDescription(policy);
        assertEquals(description, "Password must be 8 or more characters, and must contain at least one upper-case letter, and one symbolic character (non-alphanumerics like #$%&@).");
        
        policy = new PasswordPolicy(2, false, false, false, false);
        description = BridgeUtils.passwordPolicyDescription(policy);
        assertEquals(description, "Password must be 2 or more characters.");
    }
    
    @Test
    public void returnPasswordInURI() throws Exception {
        URI uri = new URI("redis://rediscloud:thisisapassword@pub-redis-555.us-east-1-4.1.ec2.garantiadata.com:555");
        String password = BridgeUtils.extractPasswordFromURI(uri);
        assertEquals(password, "thisisapassword");
    }
    
    @Test
    public void returnNullWhenNoPasswordInURI() throws Exception {
        URI uri = new URI("redis://pub-redis-555.us-east-1-4.1.ec2.garantiadata.com:555");
        String password = BridgeUtils.extractPasswordFromURI(uri);
        assertNull(password);
    }
    
    @Test
    public void createReferentGuid() {
        Activity activity = TestUtils.getActivity2();
        
        String referent = BridgeUtils.createReferentGuidIndex(activity, LOCAL_DATE_TIME);
        assertEquals(referent, "BBB:survey:2010-10-10T10:10:10.111");
    }
    
    @Test
    public void createReferentGuid2() {
        String referent = BridgeUtils.createReferentGuidIndex(ActivityType.TASK, "foo", LOCAL_DATE_TIME.toString());
        assertEquals(referent, "foo:task:2010-10-10T10:10:10.111");
    }
    
    @Test
    public void getLocalDateWithValue() throws Exception {
        LocalDate localDate = LocalDate.parse("2017-05-10");
        LocalDate parsed = BridgeUtils.getLocalDateOrDefault(localDate.toString(), null);
        
        assertEquals(parsed, localDate);
    }
    
    @Test
    public void getLocalDateWithDefault() {
        LocalDate localDate = LocalDate.parse("2017-05-10");
        LocalDate parsed = BridgeUtils.getLocalDateOrDefault(null, localDate);
        
        assertEquals(parsed, localDate);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getLocalDateWithError() {
        BridgeUtils.getLocalDateOrDefault("2017-05-10T05:05:10.000Z", null);
    }
    
    @Test
    public void toSynapseFriendlyName() {
        assertEquals(BridgeUtils.toSynapseFriendlyName("This (is a).-_ synapse Friendly Name3 "),
                "This is a .-_ synapse Friendly Name3");
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void nullToSynapseFriendlyNameThrowsException() {
        BridgeUtils.toSynapseFriendlyName(null);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptyStringToSynapseFriendlyName() {
        BridgeUtils.toSynapseFriendlyName("  #");
    }
    
    @Test
    public void templateTypeToLabel() {
        String label = BridgeUtils.templateTypeToLabel(SMS_APP_INSTALL_LINK);
        assertEquals(label, "App Install Link Default (SMS)");

        label = BridgeUtils.templateTypeToLabel(EMAIL_SIGNED_CONSENT);
        assertEquals(label, "Signed Consent Default (Email)");
    }
    
    @Test
    public void sanitizeHTML() {
        String content = SIGNATURE_BLOCK + "<p id=remove-me>Test<script>This should be removed</script><img onerror=''>";
        
        String result = BridgeUtils.sanitizeHTML(content);
        
        // 1. The signature is entirely preserved, including stuff like src="cid:consentSignature"
        // 2. HTML is sanitized (tags removed, others closed, etc.)
        assertEquals(result, SIGNATURE_BLOCK + "<p>Test<img onerror=\"\" /></p>");
    }
    
    @Test
    public void sanitizeHTMLWithNull() {
        assertNull(BridgeUtils.sanitizeHTML(null));
    }
    
    @Test
    public void sanitizeHTMLWithWhitelist() {
        String content = "<p id=remove-me>Test<script>This should be removed</script><img onerror=''>";
        String result = BridgeUtils.sanitizeHTML(Whitelist.none(), content);
        assertEquals(result, "Test");
    }
    
    @Test
    public void getEnumOrDefault() {
        ResourceCategory value = BridgeUtils.getEnumOrDefault("publication", ResourceCategory.class, LICENSE);
        assertEquals(value, PUBLICATION);
    }
    
    @Test
    public void getEnumOrDefaultReturnsDefault() {
        ResourceCategory value = BridgeUtils.getEnumOrDefault(null, ResourceCategory.class, LICENSE);
        assertEquals(value, LICENSE);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*nada is not a valid ResourceCategory.*")
    public void getEnumOrDefaultInvalidEnum() {
        BridgeUtils.getEnumOrDefault("nada", ResourceCategory.class, LICENSE);
    }
    
    @Test
    public void getIntegerOrDefault() {
        assertEquals(BridgeUtils.getIntegerOrDefault("3", null), Integer.valueOf(3));
    }

    @Test
    public void getIntegerOrDefaultReturnsDefault() {
        assertEquals(BridgeUtils.getIntegerOrDefault(null, Integer.valueOf(3)), Integer.valueOf(3));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getIntegerOrDefaultThrowsBadRequest() {
        BridgeUtils.getIntegerOrDefault("asdf", null);
    }
    
    @Test
    public void walkWorks() throws Exception {
        JsonNode node = new ObjectMapper().readTree(AssessmentConfigValidatorTest.TEST_JSON);
        
        BridgeUtils.walk(node, 
                (fieldPath, visited) -> ((ObjectNode)visited).put("_test", fieldPath));
        
        assertEquals(node.get("_test").textValue(), "");
        JsonNode el = node.get("elements").get(0);
        assertEquals(el.get("_test").textValue(), "elements[0]");
        assertEquals(el.get("beforeRules").get(0).get("_test").textValue(), "elements[0].beforeRules[0]");
    }
    
    @Test
    public void convertParsingErrorMismatchedInputException() {
        try {
            new ObjectMapper().readValue("{\"password\": {\"value\":3}, \"phone\":\"123\"|", StudyParticipant.class);
        } catch(IOException e) {
            InvalidEntityException retValue = BridgeUtils.convertParsingError(e);
            assertEquals(retValue.getMessage(), 
                    "Error parsing JSON in request body, fields: password");
        }
    }
    
    @Test
    public void convertParsingErrorJsonParseException() throws Exception {
        try {
            new ObjectMapper().readValue("asdf", Integer.class);
        } catch(IOException e) {
            InvalidEntityException retValue = BridgeUtils.convertParsingError(e);
            assertTrue(retValue.getMessage().contains(
                    "Error parsing JSON in request body: Unrecognized token 'asdf'"));
        }
    }
    
    @Test
    public void convertParsingErrorWrappedInvalidEntityException() {
        InvalidEntityException iee = new InvalidEntityException("error");
        RuntimeException re = new RuntimeException(iee);
        
        InvalidEntityException e = BridgeUtils.convertParsingError(re);
        assertSame(e, iee);
    }

    @Test
    public void convertParsingErrorOtherException() {
        IOException ioe = new IOException("error");
        InvalidEntityException e = BridgeUtils.convertParsingError(ioe);
        assertEquals(e.getMessage(), "Error parsing JSON in request body: error");
    }
    
    @Test
    public void formatActivityEventIdIsValidCustomId() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("FOO"), "FOO");
        assertEquals(retValue, "custom:FOO");
        
    }
    
    @Test
    public void formatActivityEventIdIsValidCustomIdWithCustomPrefix() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("foo"), "CUSTOM:foo");
        assertEquals(retValue, "custom:foo");
    }

    @Test
    public void formatActivityEventIdIsValidSystemId() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("foo"), "activities_retrieved");
        assertEquals(retValue, "activities_retrieved");
    }

    @Test
    public void formatActivityEventIdBlank() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("foo"), "");
        assertNull(retValue);
        
    }

    @Test
    public void formatActivityEventIdNull() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("foo"), null);
        assertNull(retValue);
    }
    
    @Test
    public void formatActivityEventIdWithCasing() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of("Event1"), "custom:Event1");
        assertEquals(retValue, "custom:Event1");
    }
    
    @Test
    public void formatActivityEventIdSystemEventWrongCase() {
        String retValue = BridgeUtils.formatActivityEventId(ImmutableSet.of(), "ENROLLMENT");
        assertEquals(retValue, "enrollment");
    }
    
    @Test
    public void selectByLang_selectPreferredLanguage() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_JA);
        
        List<String> langs = ImmutableList.of("hi", "ja");
        
        Label sel = BridgeUtils.selectByLang(items, langs, null);
        assertEquals(sel, LABEL_JA);
    }
    
    @Test
    public void selectByLang_selectDespiteOrder() {
        List<Label> items = ImmutableList.of(LABEL_JA, LABEL_ES);
        
        List<String> langs = ImmutableList.of("es", "ja");
        
        Label sel = BridgeUtils.selectByLang(items, langs, null);
        assertEquals(sel, LABEL_ES);
    }

    @Test
    public void selectByLang_selectEnglish() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_EN);
        
        List<String> langs = ImmutableList.of("hi", "ja");
        
        Label sel = BridgeUtils.selectByLang(items, langs, null);
        assertEquals(sel, LABEL_EN);
    }
    
    @Test
    public void selectByLang_selectDefault() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_JA);
        
        List<String> langs = ImmutableList.of("de");
        
        Label sel = BridgeUtils.selectByLang(items, langs, LABEL_HI);
        assertEquals(sel, LABEL_HI);
    }
    
    @Test
    public void selectByLang_noLangsSelectPreferredLanguage() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_JA);
        
        // There's no English language version, and no default, so null
        // is correctly returned.
        Label sel = BridgeUtils.selectByLang(items, null, null);
        assertNull(sel);
    }

    @Test
    public void selectByLang_noLangsSelectEnglish() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_EN);
        
        Label sel = BridgeUtils.selectByLang(items, null, null);
        assertEquals(sel, LABEL_EN);
    }
    
    @Test
    public void selectByLang_noLangsSelectDefault() {
        List<Label> items = ImmutableList.of(LABEL_ES, LABEL_JA);
        
        Label sel = BridgeUtils.selectByLang(items, null, LABEL_HI);
        assertEquals(sel, LABEL_HI);
    }
    
    @Test
    public void findByEventType_emptyEvents() {
        List<StudyActivityEvent> events = ImmutableList.of();
        assertNull( BridgeUtils.findByEventId(events, TIMELINE_RETRIEVED) );
    }

    @Test
    public void findByEventType_eventMatches() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("timeline_retrieved");
        
        List<StudyActivityEvent> events = ImmutableList.of(event1, event2);
        
        assertEquals( BridgeUtils.findByEventId(events, TIMELINE_RETRIEVED), event2 );
    }
    
    // assertEquals with two sets doesn't verify the order is the same... hence this test method.
    private <T> void orderedSetsEqual(Set<T> first, Set<T> second) {
        assertEquals(second.size(), first.size());
        
        Iterator<T> firstIterator = first.iterator();
        Iterator<T> secondIterator = second.iterator();
        while(firstIterator.hasNext()) {
            assertEquals(firstIterator.next(), secondIterator.next());
        }
    }
    
}