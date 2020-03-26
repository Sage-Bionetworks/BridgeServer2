package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.ID_FIELD_NAME;
import static org.sagebionetworks.bridge.BridgeConstants.TYPE_FIELD_NAME;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SHARED_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.PropertyInfo;

public class AssessmentConfigServiceTest extends Mockito {

    @Mock
    AssessmentService mockAssessmentService;
    
    @Mock
    HibernateAssessmentConfigDao mockDao;
    
    @InjectMocks
    @Spy
    AssessmentConfigService service;
    
    @Captor
    ArgumentCaptor<AssessmentConfig> configCaptor;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;
    
    @BeforeMethod
    public void beforeMethod( ) {
        MockitoAnnotations.initMocks(this);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void getAssessmentConfig() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentConfig retValue = service.getAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID);
        assertSame(retValue, existing);
        
        verify(mockDao).getAssessmentConfig(GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Assessment not found.")
    public void getAssessmentConfigAssessmentNotFound() {
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenThrow(new EntityNotFoundException(Assessment.class));
        
        service.getAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "AssessmentConfig not found.")
    public void getAssessmentConfigAssessmentConfigNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.empty());
        
        service.getAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID);
    }
    
    @Test
    public void getSharedAssessmentConfig() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("api:substudyA");
        when(mockAssessmentService.getAssessmentByGuid(SHARED_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentConfig retValue = service.getSharedAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID);
        assertSame(retValue, existing);
        
        verify(mockDao).getAssessmentConfig(GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentConfig not found.")
    public void getSharedAssessmentConfigNotFound() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId("api:substudyA");
        when(mockAssessmentService.getAssessmentByGuid(SHARED_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.empty());
        
        service.getSharedAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID);
    }
    
    @Test
    public void updateAssessmentConfig() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOriginGuid(GUID);
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));

        ObjectNode configNode = createValidConfig();
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(configNode);
        config.setVersion(3L);
        
        service.updateAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, config);
        
        verify(mockDao).updateAssessmentConfig(eq(TEST_STUDY_IDENTIFIER), assessmentCaptor.capture(), eq(GUID),
                configCaptor.capture());
        
        AssessmentConfig captured = configCaptor.getValue();
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertEquals(captured.getConfig(), configNode);
        assertEquals(captured.getVersion(), 3L);
        assertNull(assessment.getOriginGuid());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*identifier is missing.*")
    public void updateAssessmentConfigInvalid() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOriginGuid(GUID);
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setCreatedOn(CREATED_ON);
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));

        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(TestUtils.getClientData());
        config.setVersion(3L);
        
        service.updateAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, config);
    }
    
    @Test
    public void customizeAssessmentConfig() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build(),
                new PropertyInfo.Builder().withPropName("intValue").build(),
                new PropertyInfo.Builder().withPropName("identifier").build())));
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
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
        
        AssessmentConfig retValue = service.customizeAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, updates);
        assertSame(retValue, existing);
        
        // Not changed... it's not in the list of allowable fields to customize.
        assertTrue(existing.getConfig().get("booleanFlag").booleanValue());
        // Changed because it's allowable
        assertEquals(existing.getConfig().get("stringValue").textValue(), "updatedValue");
        assertEquals(existing.getConfig().get("intValue").intValue(), 10);
        // Unchanged because no update was submitted
        assertEquals(existing.getConfig().get("identifier").textValue(), "anIdentifier");
        
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*identifier is missing.*")
    public void customizeAssessmentConfigInvalid() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build(),
                new PropertyInfo.Builder().withPropName("intValue").build(),
                new PropertyInfo.Builder().withPropName("identifier").build())));
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
            .thenReturn(assessment);
        
        AssessmentConfig existing = new AssessmentConfig();
        existing.setConfig(createValidConfig());
        when(mockDao.getAssessmentConfig(GUID)).thenReturn(Optional.of(existing));
        when(mockDao.customizeAssessmentConfig(GUID, existing)).thenReturn(existing);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        HashMap<String, JsonNode> nodeUpdates = new HashMap<>();
        // TODO: Note that we need to persist this kind of null and we generally don't
        // with our default serialization. But the controller for customization calls
        // handles this with its own ObjectMapper.
        nodeUpdates.put("identifier", null);
        updates.put("anIdentifier", nodeUpdates);
        
        service.customizeAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, updates);
    }
    
    @Test
    public void customizeAssessmentConfigUnchanged() {
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setCustomizationFields(ImmutableMap.of("anIdentifier", ImmutableSet.of(
                new PropertyInfo.Builder().withPropName("stringValue").build()
        )));
        when(mockAssessmentService.getAssessmentByGuid(TEST_STUDY_IDENTIFIER, GUID))
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
        
        AssessmentConfig retValue = service.customizeAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, updates);
        assertSame(retValue, existing);
        
        verify(mockDao, never()).customizeAssessmentConfig(any(), any());
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Updates to configuration are missing")
    public void customizeAssessmentConfigNull() {
        service.customizeAssessmentConfig(TEST_STUDY_IDENTIFIER, GUID, null);
    }

    // {"booleanFlag":true,"stringValue":"testString","intValue":4,
    //    "identifier":"anIdentifier","type":"ObjectNode"}
    private ObjectNode createValidConfig() {
        ObjectNode configNode = (ObjectNode)TestUtils.getClientData();
        configNode.put(ID_FIELD_NAME, "anIdentifier");
        configNode.put(TYPE_FIELD_NAME, "ObjectNode");
        return configNode;
    }
}
