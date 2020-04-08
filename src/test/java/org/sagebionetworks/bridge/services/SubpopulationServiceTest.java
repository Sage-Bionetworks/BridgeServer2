package org.sagebionetworks.bridge.services;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class SubpopulationServiceTest {
    
    private static final String SUBPOP_1 = "Subpop 1";
    private static final String SUBPOP_2 = "Subpop 2";
    private static final String SUBPOP_3 = "Subpop 3";
    private static final String SUBPOP_4 = "Subpop 4";
    
    private static final Criteria CRITERIA = TestUtils.createCriteria(2, 10, 
            ImmutableSet.of("a", "b"), ImmutableSet.of("c", "d"));
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("AAA");
    private static final long CONSENT_CREATED_ON = DateTime.now().getMillis();
    
    SubpopulationService service;
    
    @Mock
    SubpopulationDao subpopDao;
    
    @Mock
    SubstudyService substudyService;
    
    @Mock
    Study study;
    
    @Mock
    StudyConsentDao studyConsentDao;
    
    @Mock
    StudyConsentService studyConsentService;
    
    @Mock
    StudyConsentView view; 
    
    @Mock
    StudyConsentForm form;
    
    @Mock
    StudyConsent consent;
    
    @Mock
    CacheProvider cacheProvider;
    
    Subpopulation subpop;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        service = new SubpopulationService();
        service.setSubpopulationDao(subpopDao);
        service.setStudyConsentService(studyConsentService);
        service.setStudyConsentDao(studyConsentDao);
        service.setDefaultConsentForm(form);
        service.setSubstudyService(substudyService);
        service.setCacheProvider(cacheProvider);
        
        subpop = Subpopulation.create();
        subpop.setGuidString(BridgeUtils.generateGuid());
        
        Set<String> dataGroups = ImmutableSet.of("group1","group2");
        when(study.getDataGroups()).thenReturn(dataGroups);
        when(study.getIdentifier()).thenReturn(API_APP_ID);
        when(study.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        when(subpopDao.createSubpopulation(any())).thenAnswer(returnsFirstArg());
        when(subpopDao.updateSubpopulation(any())).thenAnswer(returnsFirstArg());
        
        when(view.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        
        when(studyConsentService.addConsent(any(), any())).thenReturn(view);
        when(studyConsentService.publishConsent(any(), any(), eq(CONSENT_CREATED_ON))).thenReturn(view);
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expectedExceptions = InvalidEntityException.class)
    public void creationIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    @Test
    public void createSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuidString("cannot-set-guid");
        subpop.setDefaultGroup(false);
        
        when(subpopDao.createSubpopulation(any())).thenReturn(subpop);
        
        Subpopulation result = service.createSubpopulation(study, subpop);
        assertEquals(result.getName(), "Name");
        assertNotNull(result.getGuidString());
        assertNotEquals(result.getGuidString(), "cannot-set-guid");
        assertFalse(result.isDeleted());
        assertEquals(result.getStudyIdentifier(), API_APP_ID);
        
        verify(subpopDao).createSubpopulation(subpop);
        verify(studyConsentService).addConsent(eq(result.getGuid()), any());
        verify(studyConsentService).publishConsent(study, result, CONSENT_CREATED_ON);
        verify(substudyService).getSubstudyIds(TEST_STUDY);
    }
    
    @Test
    public void createDefaultSubpopulationWhereNoConsents() {
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "");
        Subpopulation subpop = Subpopulation.create();
        SubpopulationGuid defaultGuid = SubpopulationGuid.create("test-study");
        subpop.setGuid(defaultGuid);
        
        ArgumentCaptor<StudyConsentForm> captor = ArgumentCaptor.forClass(StudyConsentForm.class);
        
        when(studyConsentService.getAllConsents(defaultGuid)).thenReturn(ImmutableList.of());
        when(studyConsentService.addConsent(eq(defaultGuid), any())).thenReturn(view);
        when(subpopDao.createDefaultSubpopulation(study.getStudyIdentifier())).thenReturn(subpop);
        
        // No consents, so we add and publish one.
        Subpopulation returnValue = service.createDefaultSubpopulation(study);
        verify(studyConsentService).addConsent(any(), captor.capture());
        verify(studyConsentService).publishConsent(eq(study), eq(subpop), any(Long.class));
        assertEquals(returnValue, subpop);
        
        // This used the default document.
        assertEquals(captor.getValue(), form);
    }
    
    @Test
    public void createDefaultSubpopulationWhereConsentsExist() {
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        
        SubpopulationGuid defaultGuid = SubpopulationGuid.create("test-study");
        Subpopulation subpop = Subpopulation.create();
        when(studyConsentService.getAllConsents(defaultGuid)).thenReturn(ImmutableList.of(new DynamoStudyConsent1()));
        when(subpopDao.createDefaultSubpopulation(study.getStudyIdentifier())).thenReturn(subpop);
        
        Subpopulation returnValue = service.createDefaultSubpopulation(study);
        assertEquals(returnValue, subpop);
        
        // Consents exist... don't add any
        verify(studyConsentService, never()).addConsent(any(), any());
    }
    
    @Test
    public void updateSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuidString("guid");
        subpop.setDefaultGroup(false);
        subpop.setDeleted(true);

        doReturn(consent).when(studyConsentDao).getConsent(any(), anyLong());
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(subpop);
        
        Subpopulation result = service.updateSubpopulation(study, subpop);
        assertEquals(result.getName(), "Name");
        assertEquals(result.getGuidString(), "guid");
        assertEquals(result.getStudyIdentifier(), API_APP_ID);
        
        verify(subpopDao).updateSubpopulation(subpop);
        verify(substudyService).getSubstudyIds(TEST_STUDY);
    }
    
    @Test
    public void updateSubpopulationVerifiesStudyConsent() {
        // doesn't even get to validation, so no need to fill this out.
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("test-guid");
        subpop.setPublishedConsentCreatedOn(DateTime.now().getMillis());
        
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(subpop);
        
        try {
            service.updateSubpopulation(study, subpop);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals(e.getMessage(), "StudyConsent not found.");
        }
    }
    
    @Test
    public void updateSubpopulationSetsConsentCreatedOn() {
        when(studyConsentDao.getConsent(any(), anyLong())).thenReturn(consent);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setGuidString("test-guid");
        
        Subpopulation existing = Subpopulation.create();
        existing.setPublishedConsentCreatedOn(1000L);
        existing.setGuidString("guidString");
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(existing);
        
        Subpopulation updated = service.updateSubpopulation(study, subpop);
        assertEquals(updated.getPublishedConsentCreatedOn(), 1000L);
    }
    
    @Test
    public void getSubpopulations() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Name 1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Name 2");

        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(ImmutableList.of(subpop1, subpop2));
        
        List<Subpopulation> results = service.getSubpopulations(TEST_STUDY, false);
        assertEquals(results.size(), 2);
        assertEquals(results.get(0), subpop1);
        assertEquals(results.get(1), subpop2);
        verify(subpopDao).getSubpopulations(TEST_STUDY, true, false);
    }
    
    @Test
    public void getSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("guid");
        
        when(subpopDao.getSubpopulation(TEST_STUDY, SUBPOP_GUID)).thenReturn(subpop);

        Subpopulation result = service.getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        assertEquals(result, subpop);
        verify(subpopDao).getSubpopulation(TEST_STUDY, SUBPOP_GUID);
    }

    @Test
    public void deleteSubpopulation() {
        service.deleteSubpopulation(TEST_STUDY, SUBPOP_GUID);
        
        verify(subpopDao).deleteSubpopulation(TEST_STUDY, SUBPOP_GUID);
        verify(cacheProvider).removeObject(CacheKey.subpop(SUBPOP_GUID, TEST_STUDY));
        verify(cacheProvider).removeObject(CacheKey.subpopList(TEST_STUDY));
    }
    
    @Test
    public void deleteSubpopulationPermanently() {
        service.deleteSubpopulationPermanently(TEST_STUDY, SUBPOP_GUID);

        verify(studyConsentService).deleteAllConsentsPermanently(SUBPOP_GUID);
        verify(subpopDao).deleteSubpopulationPermanently(TEST_STUDY, SUBPOP_GUID);
        verify(cacheProvider).removeObject(CacheKey.subpop(SUBPOP_GUID, TEST_STUDY));
        verify(cacheProvider).removeObject(CacheKey.subpopList(TEST_STUDY));
    }
    
    @Test
    public void getSubpopulationsForUserRetrievesCriteria() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setCriteria(CRITERIA);
        
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(ImmutableList.of(subpop));
        
        CriteriaContext context = createContext();
        
        List<Subpopulation> subpops = service.getSubpopulationsForUser(context);
        Subpopulation retrieved = subpops.get(0);
        Criteria criteria = retrieved.getCriteria();
        assertEquals(criteria, CRITERIA);
        
        verify(subpopDao).getSubpopulations(TEST_STUDY, true, false);
    }

    @Test
    public void getSubpopulationsForUserConstructsCriteriaIfNotSaved() {
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(ImmutableList.of(subpop));
        CriteriaContext context = createContext();
        
        List<Subpopulation> subpops = service.getSubpopulationsForUser(context);
        Subpopulation retrieved = subpops.get(0);
        Criteria criteria = retrieved.getCriteria();
        assertNotNull(criteria);
        
        verify(subpopDao).getSubpopulations(TEST_STUDY, true, false);
    }
    
    @Test
    public void getSubpopulationsForUser() {
        Subpopulation subpop1 = createSubpop(SUBPOP_1, 0, 6, "group1"); // match up to version 6 and data group1, specificity 3
        Subpopulation subpop2 = createSubpop(SUBPOP_2, null, 6, null); // match version 0-6, specificity 2
        Subpopulation subpop3 = createSubpop(SUBPOP_3, null, null, "group1"); // match group1, specificity 1
        Subpopulation subpop4 = createSubpop(SUBPOP_4, null, null, null); // match anything, specificity 0
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(
                ImmutableList.of(subpop1, subpop2, subpop3, subpop4));
        
        // version 12, no tags == Subpop 4
        List<Subpopulation> results = service.getSubpopulationsForUser(criteriaContext(12, null));
        assertEquals(Sets.newHashSet(results), ImmutableSet.of(subpop4));
        
        // version 12, tag group1 == Subpops 3, 4
        results = service.getSubpopulationsForUser(criteriaContext(12, "group1"));
        assertEquals(Sets.newHashSet(results), ImmutableSet.of(subpop3, subpop4));
        
        // version 4, no tag == Subpops 2, 4
        results = service.getSubpopulationsForUser(criteriaContext(4, null));
        assertEquals(Sets.newHashSet(results), ImmutableSet.of(subpop2, subpop4));
        
        // version 4, tag group1 == Subpops 1,2,3,4, returns 1 in this case (most specific)
        results = service.getSubpopulationsForUser(criteriaContext(4, "group1"));
        assertEquals(Sets.newHashSet(results), ImmutableSet.of(subpop1, subpop2, subpop3, subpop4));
    }
    
    @Test
    public void getSubpopulationsForUserReturnsSubpopulations() {
        Subpopulation subpop1 = createSubpop(SUBPOP_1, null, null, null);
        subpop1.setDefaultGroup(true);
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(ImmutableList.of(subpop1));
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(TEST_STUDY)
                .build();
        List<Subpopulation> results = service.getSubpopulationsForUser(context);

        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getName(), "Subpop 1");
    }

    /**
     * Here the research designer has created an error when creating subpopulations 
     * such that there's no match for this user... in this case, we want to return null.
     */
    @Test
    public void getSubpopulationsForUserDoesNotMatchSubpopulationReturnsNull() {
        Subpopulation subpop1 = createSubpop(SUBPOP_1, null, null, "unmatcheableGroup");
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(ImmutableList.of(subpop1));

        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(TEST_STUDY)
                .build();
        List<Subpopulation> results = service.getSubpopulationsForUser(context);
        assertTrue(results.isEmpty());
    }    
    
    @Test
    public void deleteAllSubpopulationsDeletesConsents() {
        Subpopulation subpop1 = createSubpop(SUBPOP_1, null, null, null);
        subpop1.setDefaultGroup(true);
        Subpopulation subpop2 = createSubpop(SUBPOP_2, null, null, null);
        
        when(subpopDao.getSubpopulations(TEST_STUDY, true, true)).thenReturn(ImmutableList.of(subpop1, subpop2));
        
        service.deleteAllSubpopulations(TEST_STUDY);

        verify(studyConsentService).deleteAllConsentsPermanently(subpop1.getGuid());
        verify(subpopDao).deleteSubpopulationPermanently(TEST_STUDY, subpop1.getGuid());
        verify(cacheProvider).removeObject(CacheKey.subpop(subpop1.getGuid(), TEST_STUDY));

        verify(studyConsentService).deleteAllConsentsPermanently(subpop2.getGuid());
        verify(subpopDao).deleteSubpopulationPermanently(TEST_STUDY, subpop2.getGuid());
        verify(cacheProvider).removeObject(CacheKey.subpop(subpop2.getGuid(), TEST_STUDY));

        verify(cacheProvider, times(2)).removeObject(CacheKey.subpopList(TEST_STUDY));
    }
    
    private CriteriaContext createContext() {
        return new CriteriaContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withUserDataGroups(CRITERIA.getAllOfGroups())
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .build();
    }
    
    private CriteriaContext criteriaContext(int version, String tag) {
        CriteriaContext.Builder builder = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/"+version+" (Unknown iPhone; iPhone OS/9.0.2) BridgeSDK/4"))
                .withStudyIdentifier(TEST_STUDY);
        if (tag != null) {
            builder.withUserDataGroups(ImmutableSet.of(tag));    
        }
        return builder.build();
    }
    
    private Subpopulation createSubpop(String name, Integer min, Integer max, String group) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(API_APP_ID);
        subpop.setName(name);
        subpop.setGuidString(BridgeUtils.generateGuid());
        
        Criteria criteria = Criteria.create();
        if (min != null) {
            criteria.setMinAppVersion(OperatingSystem.IOS, min);
        }
        if (max != null) {
            criteria.setMaxAppVersion(OperatingSystem.IOS, max);
        }
        if (group != null) {
            criteria.setAllOfGroups(ImmutableSet.of(group));
        }
        subpop.setCriteria(criteria);
        return subpop;
    }

}
