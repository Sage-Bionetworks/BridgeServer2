package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.models.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.TemplateType.EMAIL_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.TemplateType.EMAIL_SIGN_IN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.TemplateType;
import org.sagebionetworks.bridge.models.studies.Study;

public class TemplateServiceTest extends Mockito {
    
    private static final String GUID = "oneGuid";
    
    @Mock
    TemplateDao mockTemplateDao;
    
    @Mock
    CriteriaDao mockCriteriaDao;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    SubstudyService mockSubstudyService;
    
    @InjectMocks
    @Spy
    TemplateService service;
    
    @Captor
    ArgumentCaptor<Criteria> criteriaCaptor;
    
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    Study study;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        when(service.generateGuid()).thenReturn(GUID);
        when(service.getTimestamp()).thenReturn(TIMESTAMP);
        
        study = Study.create();
        study.setDataGroups(USER_DATA_GROUPS);
        study.setDefaultTemplates(new HashMap<>());
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        when(mockSubstudyService.getSubstudyIds(TEST_STUDY)).thenReturn(USER_SUBSTUDY_IDS);
    }
    
    private Criteria makeCriteria(String lang) {
        Criteria criteria = Criteria.create();
        criteria.setLanguage(lang);
        criteria.setAllOfGroups(ImmutableSet.of());
        criteria.setNoneOfGroups(ImmutableSet.of());
        criteria.setAllOfSubstudyIds(ImmutableSet.of());
        criteria.setNoneOfSubstudyIds(ImmutableSet.of());
        return criteria;
    }

    @Test
    public void getTemplateForUserMatchesOne() {
        when(mockCriteriaDao.getCriteria("template:guidOne")).thenReturn(makeCriteria("en"));
        when(mockCriteriaDao.getCriteria("template:guidTwo")).thenReturn(makeCriteria("fr"));
        
        Template t1 = Template.create();
        t1.setGuid("guidOne");
        
        Template t2 = Template.create();
        t2.setGuid("guidTwo");
        
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(t1, t2), 2);
        doReturn(page).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, null, null, false);

        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("de", "fr")).build();
        
        Template template = service.getTemplateForUser(context, EMAIL_RESET_PASSWORD);
        assertSame(template, t2);
    }
    
    @Test
    public void getTemplateForUserFallsbackToDefault() {
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(), 2);
        doReturn(page).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, null, null, false);

        Study study = Study.create();
        study.setDefaultTemplates(ImmutableMap.of(EMAIL_RESET_PASSWORD.name().toLowerCase(), GUID));
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        Template t1 = Template.create();
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(t1));
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY).build();
        
        Template template = service.getTemplateForUser(context, EMAIL_RESET_PASSWORD);
        assertSame(template, t1);        
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTemplateForUserNotFound() {
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(), 2);
        doReturn(page).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, null, null, false);

        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TEST_STUDY).build();
        
        service.getTemplateForUser(context, EMAIL_RESET_PASSWORD);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTemplateForUserTooManyFound() {
        when(mockCriteriaDao.getCriteria("template:guidOne")).thenReturn(makeCriteria("fr"));
        when(mockCriteriaDao.getCriteria("template:guidTwo")).thenReturn(makeCriteria("fr"));
        
        Template t1 = Template.create();
        t1.setGuid("guidOne");
        
        Template t2 = Template.create();
        t2.setGuid("guidTwo");
        
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(t1, t2), 2);
        doReturn(page).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, null, null, false);

        // This is going to match two templates, because both declare French as the language
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
                .withLanguages(ImmutableList.of("de", "fr")).build();
        
        service.getTemplateForUser(context, EMAIL_RESET_PASSWORD);
    }
    
    @Test
    public void getTemplatesForType() {
        Template t1 = Template.create();
        t1.setGuid("guidOne");
        
        Template t2 = Template.create();
        t2.setGuid("guidTwo");
        
        List<Template> list = ImmutableList.of(t1, t2);
        PagedResourceList<? extends Template> resourceList = new PagedResourceList<>(list, 150);
        doReturn(resourceList).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, 50, true);
        
        Criteria criteria = Criteria.create();
        when(mockCriteriaDao.getCriteria(any())).thenReturn(criteria);
        
        PagedResourceList<? extends Template> results = service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, 50, true);
        assertSame(results, resourceList);
        
        for (Template template : results.getItems()) {
            assertNotNull(template.getCriteria());
            assertNotNull(template.getCriteria().getKey());
        }
        verify(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, 50, true);
        verify(mockCriteriaDao).getCriteria("template:guidOne");
        verify(mockCriteriaDao).getCriteria("template:guidTwo");
    }
    
    @Test
    public void getTemplatesForTypeDefaultsCriteriaObject() {
        Template t1 = Template.create();
        t1.setGuid("guidOne");
        
        Template t2 = Template.create();
        t2.setGuid("guidTwo");
        
        List<Template> list = ImmutableList.of(t1, t2);
        PagedResourceList<? extends Template> resourceList = new PagedResourceList<>(list, 150);
        doReturn(resourceList).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, 50, true);
        
        PagedResourceList<? extends Template> results = service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, 50, true);
        
        for (Template template : results.getItems()) {
            assertNotNull(template.getCriteria());
            assertNotNull(template.getCriteria().getKey());
        }
    }
    
    @Test
    public void getTemplatesForTypeDefaultsOffset() {
        PagedResourceList<? extends Template> resourceList = new PagedResourceList<>(ImmutableList.of(), 150);
        doReturn(resourceList).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 0, 50, true);
        
        service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, null, 50, true);
        
        verify(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 0, 50, true);
    }

    @Test
    public void getTemplatesForTypeDefaultsPageSize() {
        PagedResourceList<? extends Template> resourceList = new PagedResourceList<>(ImmutableList.of(), 150);
        doReturn(resourceList).when(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, API_DEFAULT_PAGE_SIZE, true);
        
        service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, null, true);
        
        verify(mockTemplateDao).getTemplates(TEST_STUDY, EMAIL_RESET_PASSWORD, 5, API_DEFAULT_PAGE_SIZE, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getTemplatesOffsetLessThanZero() {
        service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, -5, null, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getTemplatesPageSizeBelowMin() {
        service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, null, 3, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getTemplatesPageSizeAboveMax() {
        service.getTemplatesForType(TEST_STUDY, EMAIL_RESET_PASSWORD, null, 1000, true);
    }

    @Test
    public void getTemplate() {
        Template template = Template.create();
        template.setStudyId(TEST_STUDY.getIdentifier());
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(template));
        
        Template result = service.getTemplate(TEST_STUDY, GUID);
        assertSame(result, template);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getTemplateNoGuid() {
        service.getTemplate(TEST_STUDY, null);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTemplateNotFound() {
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.empty());
        
        service.getTemplate(TEST_STUDY, GUID);
    }
    
    @Test
    public void createTemplate() {
        doAnswer(answer -> {
            Template captured = answer.getArgument(0);
            captured.setVersion(10);
            return null;
        }).when(mockTemplateDao).createTemplate(any());
        
        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(ImmutableSet.of("group1", "group2"));
        
        Template template = Template.create();
        template.setName("Test name");
        template.setTemplateType(EMAIL_RESET_PASSWORD);
        template.setDeleted(true);
        template.setVersion(3);
        template.setCriteria(criteria);
        
        GuidVersionHolder holder = service.createTemplate(TEST_STUDY, template);
        assertEquals(holder.getGuid(), GUID);
        assertEquals(holder.getVersion(), new Long(10));
        
        assertEquals(template.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertFalse(template.isDeleted());
        assertEquals(template.getVersion(), 10);
        assertEquals(template.getGuid(), GUID);
        assertEquals(template.getCreatedOn(), TIMESTAMP);
        assertEquals(template.getModifiedOn(), TIMESTAMP);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteria);
        verify(mockTemplateDao).createTemplate(template);
    }
        
    @Test
    public void createTemplateDefaultsCriteria() {
        Template template = Template.create();
        template.setName("Test");
        template.setTemplateType(EMAIL_RESET_PASSWORD);
        
        service.createTemplate(TEST_STUDY, template);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(any(Criteria.class));
    }
    
    @Test
    public void updateTemplate() {
        doAnswer(answer -> {
            Template captured = answer.getArgument(0);
            captured.setVersion(10);
            return null;
        }).when(mockTemplateDao).updateTemplate(any());
        
        Template existing = Template.create();
        existing.setTemplateType(EMAIL_RESET_PASSWORD);
        existing.setCreatedOn(TIMESTAMP);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));
        
        Criteria criteria = TestUtils.createCriteria(1, 4, null, null);
        
        Template template = Template.create();
        template.setStudyId("some-other-study-id");
        template.setGuid(GUID);
        template.setName("Test");
        // Change these... they will be changed back
        template.setCreatedOn(DateTime.now().plusHours(1));
        template.setTemplateType(EMAIL_SIGN_IN);
        template.setCriteria(criteria);
        
        GuidVersionHolder result = service.updateTemplate(TEST_STUDY, template);
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), new Long(10));
        
        assertEquals(template.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(template.getGuid(), GUID);
        assertEquals(template.getVersion(), 10);
        // cannot be changed by an update.
        assertEquals(template.getTemplateType(), EMAIL_RESET_PASSWORD);
        assertEquals(template.getCreatedOn(), TIMESTAMP);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteria);
        verify(mockTemplateDao).updateTemplate(template);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateTemplateFailsIfDeleted() { 
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setDeleted(true);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));
        
        Template template = Template.create();
        template.setStudyId(TEST_STUDY_IDENTIFIER);
        template.setGuid(GUID);
        template.setDeleted(true);
        
        service.updateTemplate(TEST_STUDY, template);
    }
    
    @Test
    public void deleteTemplate() {
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setGuid(GUID);
        existing.setTemplateType(EMAIL_ACCOUNT_EXISTS);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));

        service.deleteTemplate(TEST_STUDY, GUID);
        
        verify(mockTemplateDao).updateTemplate(templateCaptor.capture());
        
        Template persisted = templateCaptor.getValue();
        assertTrue(persisted.isDeleted());
        assertEquals(persisted.getModifiedOn(), TIMESTAMP);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTemplateNotFound() { 
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setGuid(GUID);
        existing.setDeleted(true);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));

        service.deleteTemplate(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteTemplatePermanently() {
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setGuid(GUID);
        existing.setTemplateType(EMAIL_ACCOUNT_EXISTS);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));

        service.deleteTemplatePermanently(TEST_STUDY, GUID);

        verify(mockTemplateDao).deleteTemplatePermanently(TEST_STUDY, GUID);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void cannotUpdateToDeleteDefaultTemplate() {
        study.getDefaultTemplates().put(EMAIL_RESET_PASSWORD.name().toLowerCase(), GUID);
        
        Template existing = Template.create();
        existing.setTemplateType(EMAIL_RESET_PASSWORD);
        existing.setCreatedOn(TIMESTAMP);
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));
        
        Template template = Template.create();
        template.setGuid(GUID);
        template.setTemplateType(EMAIL_RESET_PASSWORD);
        template.setName("Test");
        
        service.updateTemplate(TEST_STUDY, template);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void cannotLogicallyDeleteDefaultTemplate() {
        study.getDefaultTemplates().put(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), GUID);
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setGuid(GUID);
        existing.setTemplateType(EMAIL_ACCOUNT_EXISTS);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));

        service.deleteTemplate(TEST_STUDY, GUID);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void cannotPhysicallyDeleteDefaultTemplate() {
        study.getDefaultTemplates().put(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), GUID);
        Template existing = Template.create();
        existing.setStudyId(TEST_STUDY_IDENTIFIER);
        existing.setGuid(GUID);
        existing.setTemplateType(EMAIL_ACCOUNT_EXISTS);
        when(mockTemplateDao.getTemplate(TEST_STUDY, GUID)).thenReturn(Optional.of(existing));

        service.deleteTemplatePermanently(TEST_STUDY, GUID);
    }
}
