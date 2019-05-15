package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

public class SubstudyControllerTest extends Mockito {
    
    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private static final List<Substudy> SUBSTUDIES = ImmutableList.of(Substudy.create(),
            Substudy.create());
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);

    @Mock
    SubstudyService service;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Substudy> substudyCaptor;
    
    @Spy
    @InjectMocks
    SubstudyController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        session = new UserSession();
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        session.setStudyIdentifier(TEST_STUDY);
        
        controller.setSubstudyService(service);
        
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SubstudyController.class);
        assertGet(SubstudyController.class, "getSubstudies");
        assertCreate(SubstudyController.class, "createSubstudy");
        assertGet(SubstudyController.class, "getSubstudy");
        assertPost(SubstudyController.class, "updateSubstudy");
        assertDelete(SubstudyController.class, "deleteSubstudy");
    }
    
    @Test
    public void getSubstudiesExcludeDeleted() throws Exception {
        when(service.getSubstudies(TEST_STUDY, false)).thenReturn(SUBSTUDIES);
        
        ResourceList<Substudy> result = controller.getSubstudies(false);
        
        assertEquals(result.getItems().size(), 2);
        assertFalse((boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getSubstudies(TEST_STUDY, false);
    }

    @Test
    public void getSubstudiesIncludeDeleted() throws Exception {
        when(service.getSubstudies(TEST_STUDY, true)).thenReturn(SUBSTUDIES);
        
        ResourceList<Substudy> result = controller.getSubstudies(true);
        
        assertEquals(result.getItems().size(), 2);
        assertTrue((boolean)result.getRequestParams().get(INCLUDE_DELETED_PARAM));
        
        verify(service).getSubstudies(TEST_STUDY, true);
    }
    
    @Test
    public void createSubstudy() throws Exception {
        when(service.createSubstudy(any(), any())).thenReturn(VERSION_HOLDER);
        
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        mockRequestBody(mockRequest, substudy);
        
        VersionHolder result = controller.createSubstudy();
        assertEquals(result, VERSION_HOLDER);

        verify(service).createSubstudy(eq(TEST_STUDY), substudyCaptor.capture());
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");
    }

    @Test
    public void getSubstudy() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        when(service.getSubstudy(TEST_STUDY, "id", true)).thenReturn(substudy);
        
        Substudy result = controller.getSubstudy("id");
        assertEquals(result, substudy);
        
        assertEquals(result.getId(), "oneId");
        assertEquals(result.getName(), "oneName");

        verify(service).getSubstudy(TEST_STUDY, "id", true);
    }
    
    @Test
    public void updateSubstudy() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setName("oneName");
        mockRequestBody(mockRequest, substudy);
        
        when(service.updateSubstudy(eq(TEST_STUDY), any())).thenReturn(VERSION_HOLDER);
        
        VersionHolder result = controller.updateSubstudy("id");
        
        assertEquals(result, VERSION_HOLDER);
        
        verify(service).updateSubstudy(eq(TEST_STUDY), substudyCaptor.capture());
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");
    }
    
    @Test
    public void deleteSubstudyLogical() throws Exception {
        StatusMessage result = controller.deleteSubstudy("id", false);
        assertEquals(result, SubstudyController.DELETED_MSG);
        
        verify(service).deleteSubstudy(TEST_STUDY, "id");
    }
    
    @Test
    public void deleteSubstudyPhysical() throws Exception {
        StatusMessage result = controller.deleteSubstudy("id", true);
        assertEquals(result, SubstudyController.DELETED_MSG);
        
        verify(service).deleteSubstudyPermanently(TEST_STUDY, "id");
    }

    @Test
    public void deleteSubstudyDeveloperCannotPhysicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        StatusMessage result = controller.deleteSubstudy("id", true);
        assertEquals(result, SubstudyController.DELETED_MSG);
        
        verify(service).deleteSubstudy(TEST_STUDY, "id");
    }
}
