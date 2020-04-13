package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class SchedulePlanServiceMockTest {

    private Study study;
    private String surveyGuid1;
    private String surveyGuid2;
    private SchedulePlanService service;
    
    private SchedulePlanDao mockSchedulePlanDao;
    private SurveyService mockSurveyService;
    private SubstudyService mockSubstudyService;
    
    @BeforeMethod
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(API_APP_ID);
        study.setTaskIdentifiers(ImmutableSet.of("tapTest", "taskGuid", "CCC"));
        study.setDataGroups(ImmutableSet.of("AAA"));
        
        mockSchedulePlanDao = mock(SchedulePlanDao.class);
        mockSurveyService = mock(SurveyService.class);
        mockSubstudyService = mock(SubstudyService.class);
        
        service = new SchedulePlanService();
        service.setSchedulePlanDao(mockSchedulePlanDao);
        service.setSurveyService(mockSurveyService);
        service.setSubstudyService(mockSubstudyService);
        
        Survey survey1 = new TestSurvey(SchedulePlanServiceMockTest.class, false);
        survey1.setIdentifier("identifier1");
        Survey survey2 = new TestSurvey(SchedulePlanServiceMockTest.class, false);
        survey2.setIdentifier("identifier2");
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(any(), any(), anyBoolean())).thenReturn(survey1);
        when(mockSurveyService.getSurvey(eq(API_APP_ID), any(), eq(false), eq(true))).thenReturn(survey2);
        surveyGuid1 = survey1.getGuid();
        surveyGuid2 = survey2.getGuid();
        
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createSchedulePlanWithoutStrategy() {
        SchedulePlan schedulePlan = constructSchedulePlan();
        schedulePlan.setStrategy(null);

        // The key thing here is that we make it to validation.
        service.createSchedulePlan(study, schedulePlan);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateSchedulePlanWithoutStrategy() {
        SchedulePlan schedulePlan = constructSchedulePlan();
        schedulePlan.setStrategy(null);
        
        when(mockSchedulePlanDao.getSchedulePlan(eq(API_APP_ID), any())).thenReturn(schedulePlan);
        
        // The key thing here is that we make it to validation.
        service.updateSchedulePlan(study, schedulePlan);
    }
    
    @Test
    public void getSchedulePlan() {
        SchedulePlan schedulePlan = SchedulePlan.create();
        when(mockSchedulePlanDao.getSchedulePlan(API_APP_ID, "oneGuid")).thenReturn(schedulePlan);
        
        SchedulePlan result = service.getSchedulePlan(API_APP_ID, "oneGuid");
        assertSame(result, schedulePlan);
        
        verify(mockSchedulePlanDao).getSchedulePlan(API_APP_ID, "oneGuid");
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnCreate() {
        SchedulePlan plan = constructSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        
        service.createSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any(), anyBoolean());
        verify(mockSurveyService).getSurvey(eq(API_APP_ID), any(), eq(false), eq(true));
        verify(mockSchedulePlanDao).createSchedulePlan(any(), spCaptor.capture());
        
        List<Activity> activities = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities();
        assertEquals(activities.get(0).getSurvey().getIdentifier(), "identifier1");
        assertNotNull(activities.get(1).getTask());
        assertEquals(activities.get(2).getSurvey().getIdentifier(), "identifier2");
    }
    
    @Test
    public void surveyReferenceIdentifierFilledOutOnUpdate() {
        SchedulePlan plan = constructSchedulePlan();
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        when(mockSchedulePlanDao.getSchedulePlan(study.getIdentifier(), plan.getGuid())).thenReturn(plan);
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        service.updateSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any(), anyBoolean());
        verify(mockSurveyService).getSurvey(eq(API_APP_ID), any(), eq(false), eq(true));
        verify(mockSchedulePlanDao).getSchedulePlan(study.getIdentifier(), plan.getGuid());
        verify(mockSchedulePlanDao).updateSchedulePlan(any(), spCaptor.capture());
        
        List<Activity> activities = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities();
        assertEquals(activities.get(0).getSurvey().getIdentifier(), "identifier1");
        assertNotNull(activities.get(1).getTask());
        assertEquals(activities.get(2).getSurvey().getIdentifier(), "identifier2");
    }

    @Test
    public void doNotUseIdentifierFromClient() {
        // The survey GUID/createdOn identify a survey, but the identifier from the client can just be 
        // mismatched by the client, so ignore it and look it up from the DB using the primary keys.
        Activity activity = new Activity.Builder().withGuid("guid").withLabel("A survey activity")
                .withPublishedSurvey("junkIdentifier", surveyGuid1).build();
        SchedulePlan plan = constructSchedulePlan();
        plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().set(0, activity);
        
        when(mockSchedulePlanDao.getSchedulePlan(study.getIdentifier(), plan.getGuid())).thenReturn(plan);
        
        // Verify that this was set.
        String identifier = plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertEquals(identifier, "junkIdentifier");
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        service.updateSchedulePlan(study, plan);
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(any(), any(), anyBoolean());
        verify(mockSurveyService).getSurvey(eq(API_APP_ID), any(), eq(false), eq(true));
        verify(mockSchedulePlanDao).getSchedulePlan(study.getIdentifier(), plan.getGuid());
        verify(mockSchedulePlanDao).updateSchedulePlan(any(), spCaptor.capture());
        
        // It was not used.
        identifier = spCaptor.getValue().getStrategy().getAllPossibleSchedules().get(0).getActivities().get(0)
                .getSurvey().getIdentifier();
        assertNotEquals(identifier, "junkIdentifier");
        
    }
    
    @Test
    public void verifyCreateDoesNotUseProvidedGUIDs() throws Exception {
        SchedulePlan plan = constructSchedulePlan();
        plan.setVersion(2L);
        plan.setGuid("AAA");
        Set<String> existingActivityGUIDs = Sets.newHashSet();
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (Activity activity : schedule.getActivities()) {
                existingActivityGUIDs.add(activity.getGuid());
            }
        }
        
        ArgumentCaptor<SchedulePlan> spCaptor = ArgumentCaptor.forClass(SchedulePlan.class);
        service.createSchedulePlan(study, plan);
        
        verify(mockSchedulePlanDao).createSchedulePlan(any(), spCaptor.capture());
        
        SchedulePlan updatedPlan = spCaptor.getValue();
        assertNotEquals(updatedPlan.getGuid(), "AAA");
        assertNotEquals(updatedPlan.getVersion(), new Long(2L));
        for (Schedule schedule : plan.getStrategy().getAllPossibleSchedules()) {
            for (Activity activity : schedule.getActivities()) {
                assertFalse( existingActivityGUIDs.contains(activity.getGuid()) );
            }
        }
        
    }
    @Test
    public void schedulePlanSetsStudyIdentifierOnCreate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = constructSimpleSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(mockSchedulePlanDao.createSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.createSchedulePlan(anotherStudy, plan);
        assertEquals(plan.getStudyKey(), "another-study");
    }
    
    @Test
    public void schedulePlanSetsStudyIdentifierOnUpdate() {
        DynamoStudy anotherStudy = getAnotherStudy();
        SchedulePlan plan = constructSimpleSchedulePlan();
        // Just pass it back, the service should set the studyKey
        when(mockSchedulePlanDao.getSchedulePlan(anotherStudy.getIdentifier(), plan.getGuid())).thenReturn(plan);
        when(mockSchedulePlanDao.updateSchedulePlan(any(), any())).thenReturn(plan);
        
        plan = service.updateSchedulePlan(anotherStudy, plan);
        assertEquals(plan.getStudyKey(), "another-study");
    }
    
    @Test
    public void validatesOnCreate() {
        // Check that 1) validation is called and 2) the study's enumerations are used in the validation
        SchedulePlan plan = constructorInvalidSchedulePlan();
        try {
            service.createSchedulePlan(study, plan);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(
                    e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0),
                    "strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: tapTest, taskGuid, CCC");
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].criteria.allOfGroups").get(0),
                    "strategy.scheduleCriteria[0].criteria.allOfGroups 'FFF' is not in enumeration: AAA");
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].criteria.noneOfSubstudyIds").get(0),
                    "strategy.scheduleCriteria[0].criteria.noneOfSubstudyIds 'substudyD' is not in enumeration: <empty>");
        }
    }

    @Test
    public void validatesOnUpdate() {
        // Check that 1) validation is called and 2) the study's enumerations are used in the validation
        SchedulePlan plan = constructorInvalidSchedulePlan();
        when(mockSchedulePlanDao.getSchedulePlan(study.getIdentifier(), plan.getGuid())).thenReturn(plan);
        try {
            service.updateSchedulePlan(study, plan);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(
                    e.getErrors().get("strategy.scheduleCriteria[0].schedule.activities[0].task.identifier").get(0),
                    "strategy.scheduleCriteria[0].schedule.activities[0].task.identifier 'DDD' is not in enumeration: tapTest, taskGuid, CCC");
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].criteria.allOfGroups").get(0),
                    "strategy.scheduleCriteria[0].criteria.allOfGroups 'FFF' is not in enumeration: AAA");
            assertEquals(e.getErrors().get("strategy.scheduleCriteria[0].criteria.noneOfSubstudyIds").get(0),
                    "strategy.scheduleCriteria[0].criteria.noneOfSubstudyIds 'substudyD' is not in enumeration: <empty>");
        }
    }
    
    @Test
    public void getSchedulePlansExcludeDeleted() throws Exception {
        List<SchedulePlan> plans = Lists.newArrayList(SchedulePlan.create());
        when(mockSchedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, false)).thenReturn(plans);
        
        List<SchedulePlan> returned = service.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, false);
        assertEquals(returned, plans);
        
        verify(mockSchedulePlanDao).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, false);
    }
    
    @Test
    public void getSchedulePlansIncludeDeleted() throws Exception {
        List<SchedulePlan> plans = Lists.newArrayList(SchedulePlan.create());
        when(mockSchedulePlanDao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, true)).thenReturn(plans);
        
        List<SchedulePlan> returned = service.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, true);
        assertEquals(returned, plans);
        
        verify(mockSchedulePlanDao).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, API_APP_ID, true);
    }
    
    @Test
    public void deleteSchedulePlan() {
        service.deleteSchedulePlan(API_APP_ID, "planGuid");
        
        verify(mockSchedulePlanDao).deleteSchedulePlan(API_APP_ID, "planGuid");
    }
    
    @Test
    public void deleteSchedulePlanPermanently() {
        service.deleteSchedulePlanPermanently(API_APP_ID, "planGuid");
        
        verify(mockSchedulePlanDao).deleteSchedulePlanPermanently(API_APP_ID, "planGuid");
    }
    
    private SchedulePlan constructorInvalidSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withTask("DDD").build());
        
        Criteria criteria = TestUtils.createCriteria(null, null, Sets.newHashSet("FFF"), null);
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyD"));
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        strategy.addCriteria(scheduleCriteria);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setStrategy(strategy);
        return plan;
    }
    
    private DynamoStudy getAnotherStudy() {
        DynamoStudy anotherStudy = new DynamoStudy();
        anotherStudy.setIdentifier("another-study");
        anotherStudy.setTaskIdentifiers(Sets.newHashSet("CCC"));
        return anotherStudy;
    }
    
    private SchedulePlan constructSimpleSchedulePlan() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(API_APP_ID);
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.getStrategy().getAllPossibleSchedules().get(0).setExpires("P3D");
        return plan;
    }
    
    private SchedulePlan constructSchedulePlan() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        // No identifier, which is the key here. This is valid, but we fill it out during saves as a convenience 
        // for the client. No longer required in the API.
        // Create a schedule plan with 3 activities to verify all activities are processed.
        schedule.addActivity(new Activity.Builder().withGuid("A").withLabel("Activity 1")
                .withPublishedSurvey(null, surveyGuid1).build());
        schedule.addActivity(new Activity.Builder().withGuid("B").withLabel("Activity 2").withTask("taskGuid").build());
        schedule.addActivity(new Activity.Builder().withGuid("C").withLabel("Activity 3")
                .withSurvey(null, surveyGuid2, DateTime.now()).build());
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("This is a label");
        plan.setStrategy(strategy);
        plan.setStudyKey("study-key");
        plan.setGuid("BBB");
        return plan;
    }
    
}
