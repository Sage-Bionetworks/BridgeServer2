package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.SUBPOP_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

public class SubpopulationControllerTest extends Mockito {

    private static final TypeReference<ResourceList<Subpopulation>> SUBPOP_TYPE_REF = new TypeReference<ResourceList<Subpopulation>>() {};
    
    @Mock
    SubpopulationService mockSubpopService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    Study mockStudy;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Subpopulation> captor;
    
    @InjectMocks
    @Spy
    SubpopulationController controller;
    
    UserSession session;
    
    StudyParticipant participant;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TEST_STUDY);
        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(mockStudy);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SubpopulationController.class);
        assertGet(SubpopulationController.class, "getAllSubpopulations");
        assertCreate(SubpopulationController.class, "createSubpopulation");
        assertPost(SubpopulationController.class, "updateSubpopulation");
        assertGet(SubpopulationController.class, "getSubpopulation");
        assertDelete(SubpopulationController.class, "deleteSubpopulation");
    }
    
    @Test
    public void getAllSubpopulationsExcludeDeleted() throws Exception {
        List<Subpopulation> list = createSubpopulationList();
        when(mockSubpopService.getSubpopulations(TEST_STUDY, false)).thenReturn(list);
        
        String result = controller.getAllSubpopulations(false);

        JsonNode node = BridgeObjectMapper.get().readTree(result);
        JsonNode oneSubpop = node.get("items").get(0);
        assertNull(oneSubpop.get("studyIdentifier"));
        
        ResourceList<Subpopulation> rList = BridgeObjectMapper.get().readValue(result, SUBPOP_TYPE_REF);
        assertEquals(rList.getItems(), list);
        assertEquals(rList.getItems().size(), 2);
        
        verify(mockSubpopService).getSubpopulations(TEST_STUDY, false);
    }

    @Test
    public void getAllSubpopulationsIncludeDeleted() throws Exception {
        List<Subpopulation> list = createSubpopulationList();
        when(mockSubpopService.getSubpopulations(TEST_STUDY, true)).thenReturn(list);
        
        String result = controller.getAllSubpopulations(true);
        
        ResourceList<Subpopulation> payload = BridgeObjectMapper.get().readValue(result, SUBPOP_TYPE_REF);
        assertEquals(2, payload.getItems().size());
        
        verify(mockSubpopService).getSubpopulations(TEST_STUDY, true);
    }

    @Test
    public void createSubpopulation() throws Exception {
        String json = TestUtils.createJson("{'guid':'junk','name':'Name','defaultGroup':true,'description':'Description','required':true,'criteria':{'minAppVersion':2,'maxAppVersion':10,'allOfGroups':['requiredGroup'],'noneOfGroups':['prohibitedGroup']}}");
        mockRequestBody(mockRequest, json);
        
        Subpopulation createdSubpop = Subpopulation.create();
        createdSubpop.setGuidString("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(mockSubpopService).createSubpopulation(eq(mockStudy), captor.capture());

        GuidVersionHolder result = controller.createSubpopulation();
        assertEquals(result.getGuid(), "AAA");
        assertEquals(result.getVersion(), new Long(1L));
        
        Subpopulation created = captor.getValue();
        assertEquals(created.getName(), "Name");
        assertEquals(created.getDescription(), "Description");
        assertTrue(created.isDefaultGroup());
        Criteria criteria = created.getCriteria();
        assertEquals(criteria.getMinAppVersion(OperatingSystem.IOS), new Integer(2));
        assertEquals(criteria.getMaxAppVersion(OperatingSystem.IOS), new Integer(10));
        assertEquals(criteria.getAllOfGroups(), ImmutableSet.of("requiredGroup"));
        assertEquals(criteria.getNoneOfGroups(), ImmutableSet.of("prohibitedGroup"));
    }
    
    @Test
    public void updateSubpopulation() throws Exception {
        String json = TestUtils.createJson("{'name':'Name','description':'Description','defaultGroup':true,'required':true,'criteria':{'minAppVersion':2,'maxAppVersion':10,'allOfGroups':['requiredGroup'],'noneOfGroups':['prohibitedGroup']}}");
        mockRequestBody(mockRequest, json);
        
        Subpopulation createdSubpop = Subpopulation.create();
        createdSubpop.setGuidString("AAA");
        createdSubpop.setVersion(1L);
        doReturn(createdSubpop).when(mockSubpopService).updateSubpopulation(eq(mockStudy), captor.capture());

        GuidVersionHolder result = controller.updateSubpopulation("AAA");
        assertEquals(result.getGuid(), "AAA");
        assertEquals(result.getVersion(), new Long(1L));
        
        Subpopulation created = captor.getValue();
        assertEquals(created.getGuidString(), "AAA");
        assertEquals(created.getName(), "Name");
        assertEquals(created.getDescription(), "Description");
        assertTrue(created.isDefaultGroup());
        Criteria criteria = created.getCriteria();
        assertEquals(criteria.getMinAppVersion(OperatingSystem.IOS), new Integer(2));
        assertEquals(criteria.getMaxAppVersion(OperatingSystem.IOS), new Integer(10));
        assertEquals(criteria.getAllOfGroups(), ImmutableSet.of("requiredGroup"));
        assertEquals(criteria.getNoneOfGroups(), ImmutableSet.of("prohibitedGroup"));
    }
    
    @Test
    public void getSubpopulation() throws Exception {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("AAA");
        doReturn(subpop).when(mockSubpopService).getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        
        String result = controller.getSubpopulation(SUBPOP_GUID.getGuid());

        // Serialization has been tested elsewhere, we're not testing it all here, we're just
        // verifying the object is returned in the API
        JsonNode node = BridgeObjectMapper.get().readTree(result);
        assertEquals("Subpopulation", node.get("type").asText());
        assertEquals("AAA", node.get("guid").asText());
        assertNull(node.get("studyIdentifier"));
        
        verify(mockSubpopService).getSubpopulation(TEST_STUDY, SUBPOP_GUID);
    }
    
    @Test
    public void getSubpopulationForResearcher() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(RESEARCHER)).build();
        session.setParticipant(participant);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("AAA");
        doReturn(subpop).when(mockSubpopService).getSubpopulation(TEST_STUDY, SUBPOP_GUID);

        // Does not throw UnauthorizedException.
        controller.getSubpopulation(SUBPOP_GUID.getGuid());
    }
    
    @Test
    public void deleteSubpopulationLogically() throws Exception {
        StatusMessage result = controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), false);
        
        assertEquals(result, SubpopulationController.DELETED_MSG);
        verify(mockSubpopService).deleteSubpopulation(TEST_STUDY, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationPhysically() throws Exception {
        participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);
        doReturn(session).when(controller).getSessionIfItExists();
        
        StatusMessage result = controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), true);
        
        assertEquals(result, SubpopulationController.DELETED_MSG);
        verify(mockSubpopService).deleteSubpopulationPermanently(TEST_STUDY, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationPhysicallyIsLogicalForResearcher() throws Exception {
        controller.deleteSubpopulation(SUBPOP_GUID.getGuid(), true);
        
        verify(mockSubpopService).deleteSubpopulation(TEST_STUDY, SUBPOP_GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAllSubpopulationsRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.getAllSubpopulations(false);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.createSubpopulation();
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of(ADMIN)).build());
        
        controller.updateSubpopulation(TEST_STUDY_IDENTIFIER);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of()).build());
        
        controller.getSubpopulation(TEST_STUDY_IDENTIFIER);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteSubpopulationRequiresDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().copyOf(participant)
                .withRoles(ImmutableSet.of()).build());
        
        controller.getSubpopulation(TEST_STUDY_IDENTIFIER);
    }
    
    private List<Subpopulation> createSubpopulationList() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Name 1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Name 2");
        return ImmutableList.of(subpop1, subpop2);
    }
}
