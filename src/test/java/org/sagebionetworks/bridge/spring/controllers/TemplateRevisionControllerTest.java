package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.apps.MimeType.TEXT;
import static org.sagebionetworks.bridge.spring.controllers.TemplateRevisionController.PUBLISHED_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
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

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.TemplateRevisionService;

public class TemplateRevisionControllerTest extends Mockito {
    
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final String SUBJECT = "Test SMS subject line";
    private static final String DOCUMENT_CONTENT = "Test SMS message";
    private static final DateTime CREATED_ON = TestConstants.TIMESTAMP;
    
    @Mock
    TemplateRevisionService mockRevisionService;
    
    @Mock
    HttpServletRequest request;
    
    @Mock
    HttpServletResponse response;
    
    @InjectMocks
    @Spy
    TemplateRevisionController controller;
    
    @Captor
    ArgumentCaptor<TemplateRevision> revisionCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        doReturn(request).when(controller).request();
        doReturn(response).when(controller).response();
        
        UserSession session = new UserSession(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(TemplateRevisionController.class);
        assertGet(TemplateRevisionController.class, "getTemplateRevisions");
        assertCreate(TemplateRevisionController.class, "createTemplateRevision");
        assertGet(TemplateRevisionController.class, "getTemplateRevision");
        assertPost(TemplateRevisionController.class, "publishTemplateRevision");
    }
    
    @Test
    public void getTemplateRevisions() {
        List<TemplateRevision> list = ImmutableList.of(TemplateRevision.create(), TemplateRevision.create(), TemplateRevision.create());
        PagedResourceList<? extends TemplateRevision> page = new PagedResourceList<>(list, 3);
        doReturn(page).when(mockRevisionService).getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 1000, 100);
        
        PagedResourceList<? extends TemplateRevision> result = controller.getTemplateRevisions(TEMPLATE_GUID, "1000", "100");
        assertSame(result, page);
        
        verify(mockRevisionService).getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 1000, 100);
    }

    @Test
    public void getTemplateRevisionsSetsDefaults() {
        controller.getTemplateRevisions(TEMPLATE_GUID, null, null);
        
        verify(mockRevisionService).getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 0, API_DEFAULT_PAGE_SIZE);
    }
    
    @Test
    public void createTemplateRevision() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        revision.setMimeType(TEXT);
        revision.setSubject(SUBJECT);
        revision.setDocumentContent(DOCUMENT_CONTENT);
        mockRequestBody(request, revision);
        
        CreatedOnHolder holder = new CreatedOnHolder(CREATED_ON);
        when(mockRevisionService.createTemplateRevision(eq(TEST_APP_ID), eq(TEMPLATE_GUID), any())).thenReturn(holder);
        
        controller.createTemplateRevision(TEMPLATE_GUID);
        
        verify(mockRevisionService).createTemplateRevision(eq(TEST_APP_ID), eq(TEMPLATE_GUID), revisionCaptor.capture());
        TemplateRevision captured = revisionCaptor.getValue();
        assertEquals(captured.getMimeType(), TEXT);
        assertEquals(captured.getSubject(), SUBJECT);
        assertEquals(captured.getDocumentContent(), DOCUMENT_CONTENT);
    }
    
    @Test
    public void getTemplateRevision() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        when(mockRevisionService.getTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON)).thenReturn(revision);
        
        TemplateRevision result = controller.getTemplateRevision(TEMPLATE_GUID, CREATED_ON.toString());
        assertSame(result, revision);
        
        verify(mockRevisionService).getTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test
    public void publishTemplateRevision() {
        StatusMessage message = controller.publishTemplateRevision(TEMPLATE_GUID, CREATED_ON.toString());
        assertSame(message, PUBLISHED_MSG);
        
        verify(mockRevisionService).publishTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "junk is not a DateTime value")
    public void publishTemplateRevisionBadDate() {
        controller.publishTemplateRevision(TEMPLATE_GUID, "junk");
        verify(mockRevisionService).publishTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, null);
    }
}
