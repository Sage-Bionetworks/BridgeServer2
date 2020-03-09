package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.APP_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.LICENSE;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.PUBLICATION;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.services.StudyConsentService.SIGNATURE_BLOCK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BridgeUtilsTest {
    
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.parse("2010-10-10T10:10:10.111");
    private static final String SHARED_OWNER_ID = "api:" + OWNER_ID;
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
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
    public void mapSubstudyMemberships() {
        Account account = Account.create();
        AccountSubstudy substudy1 = AccountSubstudy.create("studyId", "subA", "accountId");
        AccountSubstudy substudy2 = AccountSubstudy.create("studyId", "subB", "accountId");
        substudy2.setExternalId("extB");
        AccountSubstudy substudy3 = AccountSubstudy.create("studyId", "subC", "accountId");
        substudy3.setExternalId("extC");
        AccountSubstudy substudy4 = AccountSubstudy.create("studyId", "subD", "accountId");
        account.setAccountSubstudies(ImmutableSet.of(substudy1, substudy2, substudy3, substudy4));
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertEquals(results.size(), 4);
        assertEquals(results.get("subA"), "<none>");
        assertEquals(results.get("subB"), "extB");
        assertEquals(results.get("subC"), "extC");
        assertEquals(results.get("subD"), "<none>");
    }
    
    @Test
    public void mapSubstudyMembershipsOneEntry() {
        Account account = Account.create();
        AccountSubstudy substudy2 = AccountSubstudy.create("studyId", "subB", "accountId");
        substudy2.setExternalId("extB");
        account.setAccountSubstudies(ImmutableSet.of(substudy2));
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertEquals(results.size(), 1);
        assertEquals(results.get("subB"), "extB");
    }
    
    @Test
    public void mapSubstudyMembershipsNull() {
        Account account = Account.create();
        account.setAccountSubstudies(null);
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertTrue(results.isEmpty());
    }
    
    @Test
    public void mapSubstudyMembershipsBlank() {
        Account account = Account.create();
        account.setAccountSubstudies(ImmutableSet.of());
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertTrue(results.isEmpty());
    }

    @Test
    public void substudyIdsVisibleToCallerFilters() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of("substudyA", "substudyB"));
    }
    
    @Test
    public void substudyIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of("substudyA", "substudyB", "substudyC"));
    }
    
    @Test
    public void substudyIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of());
    }    
    
    @Test
    public void substudyIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(null)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableSet.of());
    }
    
    @Test
    public void externalIdsVisibleToCaller() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of("substudyA", "extA", "substudyB", "extB"));
    }
    
    @Test
    public void externalIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of("substudyA", "extA", "substudyB", "extB", "substudyC", "extC"));
    }    

    @Test
    public void externalIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of());
    }      
    
    @Test
    public void externalIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(null)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(visibles, ImmutableMap.of());
    }
    
    @Test
    public void collectExternalIds() {
        Account account = Account.create();
        AccountSubstudy as1 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyA", "userId");
        as1.setExternalId("subAextId");
        AccountSubstudy as2 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyB", "userId");
        as2.setExternalId("subBextId");
        AccountSubstudy as3 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyC", "userId");
        account.setAccountSubstudies(ImmutableSet.of(as1, as2, as3));
        
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        assertEquals(externalIds, ImmutableSet.of("subAextId","subBextId"));
    }
    
    @Test
    public void collectExternalIdsNullsAreIgnored() {
        Set<String> externalIds = BridgeUtils.collectExternalIds(Account.create());
        assertEquals(externalIds, ImmutableSet.of());
    } 
    
    @Test
    public void collectSubstudyIds() {
        Account account = Account.create();
        AccountSubstudy as1 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyA", "userId");
        AccountSubstudy as2 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyB", "userId");
        AccountSubstudy as3 = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, "substudyC", "userId");
        account.setAccountSubstudies(ImmutableSet.of(as1, as2, as3));
        
        Set<String> externalIds = BridgeUtils.collectSubstudyIds(account);
        assertEquals(externalIds, ImmutableSet.of("substudyA","substudyB", "substudyC"));
    }
    
    @Test
    public void collectSubstudyIdsNullsAreIgnored() {
        Set<String> externalIds = BridgeUtils.collectSubstudyIds(Account.create());
        assertEquals(externalIds, ImmutableSet.of());
    }
    
    @Test
    public void filterForSubstudyAccountRemovesUnsharedSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA");
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(substudies).build());
        
        Account account = BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(account.getAccountSubstudies().size(), 1);
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getSubstudyId(), "substudyA");
        
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void filterForSubstudyAccountReturnsAllUnsharedSubstudyIdsForNonSubstudyCaller() {
        Account account = BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(account.getAccountSubstudies().size(), 2);
    }
    
    @Test
    public void filterForSubstudyAccountNullReturnsNull() {
        assertNull(BridgeUtils.filterForSubstudy((Account)null));
    }
    
    @Test
    public void filterForSubstudyAccountNoContextReturnsNormalAccount() {
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy()));
    }
    
    @Test
    public void filterForSubstudyAccountNoContextReturnsSubstudyAccount() {
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyAccountWithSubstudiesHidesNormalAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy()));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyAccountWithMatchingSubstudiesReturnsSubstudyAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyAccountWithMismatchedSubstudiesHidesSubstudyAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("notSubstudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyExtIdNullReturnsNull() {
        assertNull(BridgeUtils.filterForSubstudy((ExternalIdentifier)null));
    }
    
    @Test
    public void filterForSubstudyExtIdNoContextReturnsExtId() {
        assertNotNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyExtIdWithSubstudiesHidesNormalExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy(null)));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyExtIdWithMatchingSubstudiesReturnsExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyExtIdWithMismatchedSubstudiesHidesExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyB")));
    }
    
    private Account getAccountWithSubstudy(String... substudyIds) {
        Account account = Account.create();
        Set<AccountSubstudy> accountSubstudies = Arrays.asList(substudyIds)
                .stream().map((id) -> {
            return AccountSubstudy.create("studyId", id, "accountId");
        }).collect(BridgeCollectors.toImmutableSet());
        account.setAccountSubstudies(accountSubstudies);
        return account;
    }
    
    private ExternalIdentifier getExternalIdentifierWithSubstudy(String substudyId) {
        ExternalIdentifier id = ExternalIdentifier.create(TestConstants.TEST_STUDY, "identifier");
        id.setSubstudyId(substudyId);
        return id;
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
        
        BridgeUtils.setRequestContext(context);
        assertEquals(BridgeUtils.getRequestContext().getId(), "main request ID");

        // Request ID is thread local, so a separate thread should see a different request ID.
        Runnable runnable = () -> {
            assertEquals(BridgeUtils.getRequestContext(), RequestContext.NULL_INSTANCE);
            BridgeUtils.setRequestContext(otherContext);
            assertEquals(BridgeUtils.getRequestContext().getId(), "other request ID");
        };
        Thread otherThread = new Thread(runnable);
        otherThread.start();
        otherThread.join();

        // Other thread doesn't affect this thread.
        assertEquals(BridgeUtils.getRequestContext().getId(), "main request ID");

        // Setting request ID to null is fine.
        BridgeUtils.setRequestContext(null);
        assertEquals(BridgeUtils.getRequestContext(), RequestContext.NULL_INSTANCE);
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
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "externalid:IdentifierA9");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getExternalId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "externalId:IdentifierA9");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getExternalId(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "healthcode:IdentifierA9");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getHealthCode(), "IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "healthCode:IdentifierA9");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getHealthCode(), "IdentifierA9");
        
        // Unrecognized prefix is just part of the userId
        accountId = BridgeUtils.parseAccountId("test", "unk:IdentifierA9");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getId(), "unk:IdentifierA9");
        
        accountId = BridgeUtils.parseAccountId("test", "synapseUserId:IdentifierA10");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA10");
        
        accountId = BridgeUtils.parseAccountId("test", "synapseuserid:IdentifierA11");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA11");
        
        accountId = BridgeUtils.parseAccountId("test", "syn:IdentifierA12");
        assertEquals(accountId.getStudyId(), "test");
        assertEquals(accountId.getSynapseUserId(), "IdentifierA12");
    }
    
    @Test
    public void studyTemplateVariblesWorks() {
        String host = BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws");
        assertTrue(StringUtils.isNotBlank(host));
        
        Study study = Study.create();
        study.setName("name1");
        study.setShortName("shortName");
        study.setIdentifier("identifier1");
        study.setSponsorName("sponsorName1");
        study.setSupportEmail("supportEmail1");
        study.setTechnicalEmail("technicalEmail1");
        study.setConsentNotificationEmail("consentNotificationEmail1");
        Map<String,String> map = BridgeUtils.studyTemplateVariables(study, (value) -> {
            return value.replaceAll("1", "2");
        });
        map.put("thisMap", "isMutable");
        
        assertEquals(map.get("studyName"), "name2");
        assertEquals(map.get("studyShortName"), "shortName");
        assertEquals(map.get("studyId"), "identifier2");
        assertEquals(map.get("sponsorName"), "sponsorName2");
        assertEquals(map.get("supportEmail"), "supportEmail2");
        assertEquals(map.get("technicalEmail"), "technicalEmail2");
        assertEquals(map.get("consentEmail"), "consentNotificationEmail2");
        assertEquals(map.get("thisMap"), "isMutable");
        assertEquals(map.get("host"), host);
    }
    
    @Test
    public void templateResolverHandlesNullConsentEmail() {
        Study study = TestUtils.getValidStudy(BridgeUtilsTest.class);
        study.setConsentNotificationEmail(null);
        
        Map<String,String> map = BridgeUtils.studyTemplateVariables(study);
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
    public void isInRoleMethodsAreNullSafe() {
        assertFalse(BridgeUtils.isInRole(null, (Roles)null));
        assertFalse(BridgeUtils.isInRole(null, (Set<Roles>)null));
    }
    
    @Test
    public void isInRoleForSuperadminMatchesEverything() {
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), DEVELOPER));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), RESEARCHER));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ADMIN));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), WORKER));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(ADMIN)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(WORKER)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }
    
    @Test
    public void isInRole() {
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), DEVELOPER));
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), RESEARCHER));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ADMIN));
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), WORKER));
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER)));
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(ADMIN)));
        assertFalse(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(WORKER)));
        assertTrue(BridgeUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkOwnershipOwnerIdIsBlank() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        BridgeUtils.checkOwnership(APP_ID, null);
    }
    
    @Test
    public void checkOwnershipGlobalUser() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        BridgeUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test
    public void checkOwnershipScopedUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        BridgeUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkOwnershipScopedUserOrgIdIsMissing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("notValidOwner")).build());
        BridgeUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipOwnerIdIsBlank() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        BridgeUtils.checkSharedOwnership(TEST_STUDY_IDENTIFIER, GUID, null);
    }
    
    @Test
    public void checkSharedOwnershipGlobalUser() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        BridgeUtils.checkSharedOwnership(TEST_STUDY_IDENTIFIER, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipAgainstNonGlobalOwnerId() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        BridgeUtils.checkSharedOwnership(TEST_STUDY_IDENTIFIER, GUID, OWNER_ID);
    }
    
    @Test
    public void sharedOwnershipScopedUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        BridgeUtils.checkSharedOwnership(TEST_STUDY_IDENTIFIER, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipScopedUserOrgIdIsMissing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("notValidOwner")).build());
        BridgeUtils.checkSharedOwnership(TEST_STUDY_IDENTIFIER, GUID, SHARED_OWNER_ID);
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