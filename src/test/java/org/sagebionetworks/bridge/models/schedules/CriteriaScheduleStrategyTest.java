package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.validators.SchedulePlanValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CriteriaScheduleStrategyTest {
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(
            "Cardio Health/1 (Unknown iPhone; iPhone OS/9.0.2) BridgeSDK/4");
    
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS = makeValidSchedule(
            "Strategy With App Versions");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP = makeValidSchedule(
            "Strategy With One Required Data Group");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS = makeValidSchedule(
            "Strategy With Required Data Groups");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP = makeValidSchedule(
            "Strategy With One Prohibited Data Group");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS = makeValidSchedule(
            "Strategy With One Prohibited Data Groups");
    private static final Schedule SCHEDULE_FOR_STRATEGY_NO_CRITERIA = makeValidSchedule("Strategy No Criteria");
    private static final Schedule SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS = makeValidSchedule(
            "Strategy with all requirements");
    
    private static final SchedulePlanValidator VALIDATOR = new SchedulePlanValidator(ImmutableSet.of(),
            ImmutableSet.of(), Sets.newHashSet(TestUtils.getActivity3().getTask().getIdentifier()));;
    private static final SchedulePlan PLAN = new DynamoSchedulePlan();
    static {
        PLAN.setLabel("Schedule plan label");
        PLAN.setStudyKey(TEST_STUDY_IDENTIFIER);
    }
    
    private CriteriaScheduleStrategy strategy;
    
    @BeforeMethod
    public void before() {
        strategy = new CriteriaScheduleStrategy();
        PLAN.setStrategy(strategy);
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(CriteriaScheduleStrategy.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        
        setUpStrategyWithAppVersions();
        setUpStrategyWithRequiredAndProhibitedSets();
        
        String json = mapper.writeValueAsString(strategy);
        JsonNode node = mapper.readTree(json);

        assertEquals(node.get("type").asText(), "CriteriaScheduleStrategy");
        assertNotNull(node.get("scheduleCriteria"));
        
        ArrayNode array = (ArrayNode)node.get("scheduleCriteria");
        JsonNode schCriteria1 = array.get(0);
        assertEquals(schCriteria1.get("type").asText(), "ScheduleCriteria");
        
        JsonNode criteriaNode = schCriteria1.get("criteria");
        
        JsonNode minVersionsNode = criteriaNode.get("minAppVersions");
        assertEquals(minVersionsNode.get(OperatingSystem.IOS).asInt(), 4);
        JsonNode maxVersionsNode = criteriaNode.get("maxAppVersions");
        assertEquals(maxVersionsNode.get(OperatingSystem.IOS).asInt(), 12);
        
        assertNotNull(criteriaNode.get("allOfGroups"));
        assertNotNull(criteriaNode.get("noneOfGroups"));
        assertNotNull(schCriteria1.get("schedule"));
        
        JsonNode schCriteria2 = array.get(1);
        JsonNode criteriaNode2 = schCriteria2.get("criteria");
        Set<String> allOfGroups = arrayToSet(criteriaNode2.get("allOfGroups"));
        assertTrue(allOfGroups.contains("req1"));
        assertTrue(allOfGroups.contains("req2"));
        Set<String> noneOfGroups = arrayToSet(criteriaNode2.get("noneOfGroups"));
        assertTrue(noneOfGroups.contains("proh1"));
        assertTrue(noneOfGroups.contains("proh2"));
        
        Set<String> allOfSubstudyIds = arrayToSet(criteriaNode2.get("allOfSubstudyIds"));
        assertTrue(allOfSubstudyIds.contains("substudyA"));
        assertTrue(allOfSubstudyIds.contains("substudyB"));
        Set<String> noneOfSubstudyIds = arrayToSet(criteriaNode2.get("noneOfSubstudyIds"));
        assertTrue(noneOfSubstudyIds.contains("substudyC"));
        
        // But mostly, if this isn't all serialized, and then deserialized, these won't be equal
        CriteriaScheduleStrategy newStrategy = mapper.readValue(json, CriteriaScheduleStrategy.class);
        assertEquals(newStrategy, strategy);
    }
    
    @Test
    public void filtersOnMinAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyEmptyCriteria();
        
        // First returned because context has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS);
        
        // Context version info outside minimum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(CLIENT_INFO);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
    }
    
    @Test
    public void filtersOnMaxAppVersion() {
        setUpStrategyWithAppVersions();
        setUpStrategyEmptyCriteria();
        
        // First one is returned because client has no version info
        Schedule schedule = getScheduleFromStrategy(ClientInfo.UNKNOWN_CLIENT);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS);
        
        // Context version info outside maximum range of first criteria, last one returned
        schedule = getScheduleFromStrategy(CLIENT_INFO);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
    }
    
    @Test
    public void filtersOnRequiredDataGroup() {
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyEmptyCriteria();
        
        // context has a group required by first group, it's returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP);
        
        // context does not have a required group, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("someRandomToken"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
    }
    
    @Test
    public void filtersOnRequiredDataGroups() {
        setUpStrategyWithRequiredDataGroups();
        setUpStrategyEmptyCriteria();
        
        // context has all the required groups so the first one is returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1","group2","group3"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS);
        
        // context does not have *any* the required groups, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("someRandomToken"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
        
        // context does not have *all* the required groups, last one returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
    }
    
    @Test
    public void filtersOnProhibitedDataGroup() {
        setUpStrategyWithOneProhibitedDataGroup();
        setUpStrategyEmptyCriteria();
        
        // Group not prohibited so first schedule returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("groupNotProhibited"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP);
        
        // this group is prohibited so second schedule is returned
        schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
    }
    
    @Test
    public void filtersOnProhibitedDataGroups() {
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyEmptyCriteria();
        
        // context has a prohibited group, so the last schedule is returned
        Schedule schedule = getScheduleFromStrategy(Sets.newHashSet("group1"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
        
        // context has one of the prohibited groups, same thing
        schedule = getScheduleFromStrategy(Sets.newHashSet("foo","group1"));
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_NO_CRITERIA);
        
        // context has no prohibited groups, first schedule is returned
        schedule = getScheduleFromStrategy(Sets.newHashSet());
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS);
    }
    
    @Test
    public void noMatchingFilterReturnsNull() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithProhibitedDataGroups();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TEST_STUDY)
                .withCallerClientInfo(CLIENT_INFO)
                .withCallerDataGroups(Sets.newHashSet("group1"))
                .withCallerHealthCode("BBB").build());

        Schedule schedule = strategy.getScheduleForCaller(PLAN);
        assertNull(schedule);
    }
    
    @Test
    public void canMixMultipleCriteria() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TEST_STUDY)
                .withCallerClientInfo(CLIENT_INFO)
                .withCallerHealthCode("AAA").build());
        
        // First two ScheduleCriteria don't match; the first because the app version is wrong 
        // and the second because the user does not have a required data group. The last ScheduleCriteria 
        // matches and returns the last schedule in the list
        Schedule schedule = strategy.getScheduleForCaller(PLAN);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS);
    }
    
    @Test
    public void willMatchScheduleWithMultipleCriteria() {
        setUpStrategyWithAllRequirements();
        setUpStrategyWithAppVersions(); // certainly should not match this one, although criteria are valid
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerDataGroups(ImmutableSet.of("req1", "req2"))
                .withCallerStudyId(TEST_STUDY)
                .withCallerClientInfo(ClientInfo.fromUserAgentCache("app/6")) // in range
                .withCallerHealthCode("AAA").build());
        
        // Matches the first schedule, not the second schedule (although it also matches)
        Schedule schedule = strategy.getScheduleForCaller(PLAN);
        assertEquals(schedule, SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS);
    }

    @Test
    public void canGetAllPossibleScheduled() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();

        List<Schedule> schedules = strategy.getAllPossibleSchedules();
        assertEquals(schedules.get(0), SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS);
        assertEquals(schedules.get(1), SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP);
        assertEquals(schedules.get(2), SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS);
        assertEquals(schedules.size(), 3);
    }
    
    @Test
    public void validatesInvalidScheduleCriteria() {
        setUpStrategyWithAppVersions();
        setUpStrategyWithOneRequiredDataGroup();
        setUpStrategyWithProhibitedDataGroups();
        setUpStrategyEmptyCriteria();
        
        Criteria criteria = TestUtils.createCriteria(-2, -10, null, null);
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyC"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyC", "substudyD"));
        // We're looking here specifically for errors generated by the strategy, not the plan
        // it's embedded in or the schedules. I've made those valid so they don't add errors.
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, criteria));
        
        // Has an invalid schedule
        Schedule schedule = new Schedule();
        strategy.addCriteria(new ScheduleCriteria(schedule, criteria));
        
        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);     
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertError(e, "strategy.scheduleCriteria[1].criteria.allOfGroups", 0, " 'group1' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[2].criteria.noneOfGroups", 0, " 'group2' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[2].criteria.noneOfGroups", 1, " 'group1' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[4].criteria.maxAppVersions.iphone_os", 0, " cannot be less than minAppVersions.iphone_os");
            assertError(e, "strategy.scheduleCriteria[4].criteria.maxAppVersions.iphone_os", 1, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[4].criteria.minAppVersions.iphone_os", 0, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[4].criteria.allOfSubstudyIds", 0, " 'substudyC' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[4].criteria.allOfSubstudyIds", 1, " includes these excluded substudies: substudyC");
            assertError(e, "strategy.scheduleCriteria[4].criteria.noneOfSubstudyIds", 0, " 'substudyC' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[4].criteria.noneOfSubstudyIds", 1, " 'substudyD' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[5].criteria.maxAppVersions.iphone_os", 0, " cannot be less than minAppVersions.iphone_os");
            assertError(e, "strategy.scheduleCriteria[5].criteria.maxAppVersions.iphone_os", 1, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[5].criteria.minAppVersions.iphone_os", 0, " cannot be negative");
            assertError(e, "strategy.scheduleCriteria[5].schedule.activities", 0, " are required");
            assertError(e, "strategy.scheduleCriteria[5].schedule.scheduleType", 0, " is required");
            assertError(e, "strategy.scheduleCriteria[5].criteria.allOfSubstudyIds", 0, " 'substudyC' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[5].criteria.allOfSubstudyIds", 1, " includes these excluded substudies: substudyC");
            assertError(e, "strategy.scheduleCriteria[5].criteria.noneOfSubstudyIds", 0, " 'substudyC' is not in enumeration: <empty>");
            assertError(e, "strategy.scheduleCriteria[5].criteria.noneOfSubstudyIds", 1, " 'substudyD' is not in enumeration: <empty>");
         }
    }
    
    @Test
    public void validScheduleCriteriaPassesValidation() {
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("Label")
                .withTask(TestUtils.getActivity3().getTask()).build();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(activity);

        Criteria criteria = TestUtils.createCriteria(2, 12, null, null);
        
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.addCriteria(scheduleCriteria);

        Validate.entityThrowingException(VALIDATOR, PLAN);
    }
    
    @Test
    public void validateScheduleCriteriaMissing() throws Exception {
        // This doesn't contain the property, it's just deserialized to an empty list
        String json = TestUtils.createJson("{'label':'Schedule plan label','studyKey':'api','strategy':{'type':'CriteriaScheduleStrategy'},'type':'SchedulePlan'}");
        
        SchedulePlan plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertTrue(((CriteriaScheduleStrategy)plan.getStrategy()).getScheduleCriteria().isEmpty());
        
        // Null is safe too
        json = TestUtils.createJson("{'label':'Schedule plan label','studyKey':'api','strategy':{'type':'CriteriaScheduleStrategy','scheduleCriteria':null},'type':'SchedulePlan'}");
        plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertTrue(((CriteriaScheduleStrategy)plan.getStrategy()).getScheduleCriteria().isEmpty());
    }
    
    @Test
    public void validateScheduleCriteriaScheduleMissing() {
        Criteria criteria = TestUtils.createCriteria(2, 12, null, null);
        
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(null, criteria);
        strategy.addCriteria(scheduleCriteria);

        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].schedule").get(0),
                    "strategy.scheduleCriteria[0].schedule is required");
        }
    }
    
    @Test
    public void validateScheduleCriteriaCriteriaMissing() {
        Activity activity = new Activity.Builder().withLabel("Label").withTask(TestUtils.getActivity3().getTask())
                .build();
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(activity);

        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, null);
        strategy.addCriteria(scheduleCriteria);

        try {
            Validate.entityThrowingException(VALIDATOR, PLAN);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].criteria").get(0),
                    "strategy.scheduleCriteria[0].criteria is required");
        }
    }
    
    private void assertError(InvalidEntityException e, String fieldName, int index, String errorMsg) {
        assertEquals(e.getErrors().get(fieldName).get(index), fieldName+errorMsg);
    }
    
    private Set<String> arrayToSet(JsonNode array) {
        Set<String> set = Sets.newHashSet();
        for (int i=0; i < array.size(); i++) {
            set.add(array.get(i).asText());
        }
        return set;
    }
    
    private static Schedule makeValidSchedule(String label) {
        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(TestUtils.getActivity3());
        return schedule;
    }

    private Schedule getScheduleFromStrategy(ClientInfo info) {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(new StudyIdentifierImpl("test-study"))
                .withCallerClientInfo(info).build());
        return strategy.getScheduleForCaller(PLAN);
    }
    
    private Schedule getScheduleFromStrategy(Set<String> dataGroups) {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(new StudyIdentifierImpl("test-study"))
                .withCallerDataGroups(dataGroups).build());
        return strategy.getScheduleForCaller(PLAN);
    }
    
    private void setUpStrategyWithAppVersions() {
        Criteria criteria = TestUtils.createCriteria(4, 12, null, null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, criteria));
    }

    private void setUpStrategyEmptyCriteria() {
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_NO_CRITERIA, Criteria.create()));
    }

    private void setUpStrategyWithOneRequiredDataGroup() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("group1"), null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_REQUIRED_DATA_GROUP, criteria));
    }
    
    private void setUpStrategyWithRequiredDataGroups() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("group1", "group2"), null);
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_REQUIRED_DATA_GROUPS, criteria));
    }
    
    private void setUpStrategyWithOneProhibitedDataGroup() {
        Criteria criteria = TestUtils.createCriteria(null, null, null, Sets.newHashSet("group1"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ONE_PROHIBITED_DATA_GROUP, criteria));
    }
    
    private void setUpStrategyWithProhibitedDataGroups() {
        Criteria criteria = TestUtils.createCriteria(null, null, null, Sets.newHashSet("group1", "group2"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_PROHIBITED_DATA_GROUPS, criteria));
    }

    private void setUpStrategyWithRequiredAndProhibitedSets() {
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("req1", "req2"), Sets.newHashSet("proh1","proh2"));
        criteria.setAllOfSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyC"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_APP_VERSIONS, criteria));
    }
    
    private void setUpStrategyWithAllRequirements() {
        Criteria criteria = TestUtils.createCriteria(4, 12, Sets.newHashSet("req1", "req2"), Sets.newHashSet("proh1","proh2"));
        strategy.addCriteria(new ScheduleCriteria(SCHEDULE_FOR_STRATEGY_WITH_ALL_REQUIREMENTS, criteria));
    }
}
