package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.ID_FIELD_NAME;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TYPE_FIELD_NAME;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.validation.Errors;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.validators.AssessmentConfigValidator;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.validators.AbstractValidator;

public class AssessmentConfigServiceTest extends Mockito {

    @Mock
    AssessmentService mockAssessmentService;
    
    @Mock
    HibernateAssessmentConfigDao mockDao;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @InjectMocks
    @Spy
    AssessmentConfigService service;
    
    @Captor
    ArgumentCaptor<AssessmentConfig> configCaptor;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;
    
    @Captor
    ArgumentCaptor<CacheKey> cacheKeyCaptor;
    
    @BeforeMethod
    public void beforeMethod( ) {
        MockitoAnnotations.initMocks(this);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getAssessmentConfig() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setModifiedOn(MODIFIED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentConfig retValue = service.getAssessmentConfig(TEST_APP_ID, GUID);
        assertSame(retValue, existing);
        
        verify(mockDao).getAssessmentConfig(GUID);
        
        verify(mockCacheProvider).setObject(cacheKeyCaptor.capture(), eq(MODIFIED_ON));
        assertEquals(cacheKeyCaptor.getValue().toString(), "oneGuid:AssessmentConfig:Etag");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Assessment not found.")
    public void getAssessmentConfigAssessmentNotFound() {
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID))
            .thenThrow(new EntityNotFoundException(Assessment.class));
        
        service.getAssessmentConfig(TEST_APP_ID, GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "AssessmentConfig not found.")
    public void getAssessmentConfigAssessmentConfigNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID))
            .thenReturn(assessment);
        
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.empty());
        
        service.getAssessmentConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void getSharedAssessmentConfig() {
        AssessmentConfig existing = new AssessmentConfig();
        existing.setModifiedOn(MODIFIED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentConfig retValue = service.getSharedAssessmentConfig(TEST_APP_ID, GUID);
        assertSame(retValue, existing);
        
        verify(mockDao).getAssessmentConfig(GUID);
        
        verify(mockCacheProvider).setObject(cacheKeyCaptor.capture(), eq(MODIFIED_ON));
        assertEquals(cacheKeyCaptor.getValue().toString(), "oneGuid:AssessmentConfig:Etag");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentConfig not found.")
    public void getSharedAssessmentConfigNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_APP_ID + ":" + TEST_ORG_ID);
        when(mockAssessmentService.getAssessmentByGuid(SHARED_APP_ID, null, GUID))
            .thenReturn(assessment);
        
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.empty());
        
        service.getSharedAssessmentConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void updateAssessmentConfig() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        assessment.setOriginGuid(GUID);
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));

        ObjectNode configNode = createValidConfig();
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(configNode);
        config.setVersion(3L);
        
        service.updateAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, config);
        
        verify(mockDao).updateAssessmentConfig(eq(TEST_APP_ID), assessmentCaptor.capture(), eq(GUID),
                configCaptor.capture());
        
        AssessmentConfig captured = configCaptor.getValue();
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertEquals(captured.getConfig(), configNode);
        assertEquals(captured.getVersion(), 3L);
        assertNull(assessment.getOriginGuid());
        
        verify(mockCacheProvider).setObject(cacheKeyCaptor.capture(), eq(MODIFIED_ON));
        assertEquals(cacheKeyCaptor.getValue().toString(), "oneGuid:AssessmentConfig:Etag");
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*config is required.*")
    public void updateAssessmentConfigInvalid() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOriginGuid(GUID);
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));

        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(null);
        config.setVersion(3L);
        
        service.updateAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, config);
    }
    
    @Test
    public void customizeAssessmentConfig() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build(),
                new PropertyInfo.Builder().withPropName("intValue").build(),
                new PropertyInfo.Builder().withPropName("identifier").build())));
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setConfig(createValidConfig());
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        when(mockDao.customizeAssessmentConfig(GUID, existing)).thenReturn(existing);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        updates.put("anIdentifier", ImmutableMap.of(
                "stringValue", JsonNodeFactory.instance.textNode("updatedValue"),
                "booleanFlag", JsonNodeFactory.instance.booleanNode(false),
                "intValue", JsonNodeFactory.instance.numberNode(10)
        ));
        
        AssessmentConfig retValue = service.customizeAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, updates);
        assertSame(retValue, existing);
        
        // Not changed... it's not in the list of allowable fields to customize.
        assertTrue(existing.getConfig().get("booleanFlag").booleanValue());
        // Changed because it's allowable
        assertEquals(existing.getConfig().get("stringValue").textValue(), "updatedValue");
        assertEquals(existing.getConfig().get("intValue").intValue(), 10);
        // Unchanged because no update was submitted
        assertEquals(existing.getConfig().get("identifier").textValue(), "anIdentifier");
        
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
        
        verify(mockCacheProvider).setObject(cacheKeyCaptor.capture(), eq(MODIFIED_ON));
        assertEquals(cacheKeyCaptor.getValue().toString(), "oneGuid:AssessmentConfig:Etag");
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*identifier is missing.*")
    public void customizeAssessmentConfigInvalid() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        AssessmentConfigValidator val = new AssessmentConfigValidator.Builder()
                .addValidator("*", new AbstractValidator() {
                    public void validate(Object target, Errors errors) {
                        JsonNode node = (JsonNode)target;
                        if (!node.has("identifier")) {
                            errors.reject("identifier is missing");
                        }
                    }
                }).build();
        doReturn(val).when(service).getValidator();
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build(),
                new PropertyInfo.Builder().withPropName("intValue").build(),
                new PropertyInfo.Builder().withPropName("identifier").build())));
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setConfig(createValidConfig());
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        when(mockDao.customizeAssessmentConfig(GUID, existing)).thenReturn(existing);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        HashMap<String, JsonNode> nodeUpdates = new HashMap<>();
        // Note that we need to persist this kind of null and we generally don't
        // with our default serialization. But the controller for this customization call
        // uses a differently configured ObjectMapper to persist these.
        nodeUpdates.put("identifier", null);
        updates.put("anIdentifier", nodeUpdates);
        
        service.customizeAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, updates);
    }
    
    @Test
    public void customizeAssessmentConfigUnchanged() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(TEST_OWNER_ID);
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build()
        )));
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setConfig(createValidConfig());
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        updates.put("anIdentifier", ImmutableMap.of(
                "booleanFlag", JsonNodeFactory.instance.booleanNode(false),
                "intValue", JsonNodeFactory.instance.numberNode(10)
        ));
        updates.put("nonExistentIdentifier", ImmutableMap.of(
                "stringValue", JsonNodeFactory.instance.textNode("some value")
        ));
        
        AssessmentConfig retValue = service.customizeAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, updates);
        assertSame(retValue, existing);
        
        verify(mockDao, never()).customizeAssessmentConfig(any(), any());
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Updates to configuration are missing")
    public void customizeAssessmentConfigNull() {
        service.customizeAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, null);
    }

    // {"booleanFlag":true,"stringValue":"testString","intValue":4,
    //    "identifier":"anIdentifier","type":"ObjectNode"}
    private ObjectNode createValidConfig() {
        ObjectNode configNode = (ObjectNode)TestUtils.getClientData();
        configNode.put(ID_FIELD_NAME, "anIdentifier");
        configNode.put(TYPE_FIELD_NAME, "ObjectNode");
        return configNode;
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentConfig not found.")
    public void getAssessmentConfigEntityNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("orgB");
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, null, GUID))
            .thenReturn(assessment);
        
        service.getAssessmentConfig(TEST_APP_ID, GUID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateAssessmentConfigCheckOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("orgB");
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig config = new AssessmentConfig();
        service.updateAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, config);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void customizeAssessmentConfigCheckOwnership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("orgB");
        when(mockAssessmentService.getAssessmentByGuid(TEST_APP_ID, TEST_OWNER_ID, GUID))
            .thenReturn(assessment);
        
        service.customizeAssessmentConfig(TEST_APP_ID, TEST_OWNER_ID, GUID, new HashMap<>());
    }
}
