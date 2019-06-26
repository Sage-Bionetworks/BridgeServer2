package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

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

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.TemplateService;

public class TemplateControllerTest extends Mockito {
    
    private static final String GUID = "oneGuid";

    @Mock
    TemplateService mockTemplateService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    TemplateController controller;
    
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
    }

    @Test
    public void getTemplates() {
        List<? extends Template> items = ImmutableList.of(Template.create(), Template.create());
        PagedResourceList<? extends Template> page = new PagedResourceList<>(items, 2);
        doReturn(page).when(mockTemplateService).getTemplatesForType(TEST_STUDY, EMAIL_ACCOUNT_EXISTS, 100, 50, true);
        
        PagedResourceList<? extends Template> result = controller
                .getTemplates(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), "100", "50", "true");
        assertEquals(result.getItems().size(), 2);
        
        verify(mockTemplateService).getTemplatesForType(TEST_STUDY, EMAIL_ACCOUNT_EXISTS, 100, 50, true);
    }
    
    @Test
    public void getTemplatesDefaults() {
        controller.getTemplates(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), null, null, null);
        verify(mockTemplateService).getTemplatesForType(TEST_STUDY, EMAIL_ACCOUNT_EXISTS, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Template type is required")
    public void getTemplatesTypeIsRequired() {
        controller.getTemplates(null, null, null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Invalid template type.*")
    public void getTemplatesInvalidType() {
        controller.getTemplates("not-a-type", null, null, null);
    }
    
    @Test
    public void createTemplate() throws Exception {
        Template template = Template.create();
        template.setName("This is a name");
        mockRequestBody(mockRequest, template);
        
        GuidVersionHolder holder = new GuidVersionHolder(GUID, 1L);
        when(mockTemplateService.createTemplate(eq(TEST_STUDY), any())).thenReturn(holder);
        
        GuidVersionHolder result = controller.createTemplate();
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), new Long(1));
        
        verify(mockTemplateService).createTemplate(eq(TEST_STUDY), templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "This is a name");
    }
    
    @Test
    public void getTemplate() {
        Template template = Template.create();
        template.setName("This is a name");
        when(mockTemplateService.getTemplate(TEST_STUDY, GUID)).thenReturn(template);
        
        Template result = controller.getTemplate(GUID);
        assertSame(result, template);
        
        verify(mockTemplateService).getTemplate(TEST_STUDY, GUID);
    }

    @Test
    public void updateTemplate() throws Exception {
        Template template = Template.create();
        template.setName("This is a name");
        mockRequestBody(mockRequest, template);
        
        GuidVersionHolder holder = new GuidVersionHolder(GUID, 2L);
        when(mockTemplateService.updateTemplate(eq(TEST_STUDY), any())).thenReturn(holder);
        
        GuidVersionHolder result = controller.updateTemplate(GUID);
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), new Long(2));
        
        verify(mockTemplateService).updateTemplate(eq(TEST_STUDY), templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "This is a name");
        assertEquals(templateCaptor.getValue().getGuid(), GUID);
    }
    
    @Test
    public void deleteTemplateDefault() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, null);
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_STUDY, GUID);
    }

    @Test
    public void deleteTemplate() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, "false");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_STUDY, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, "true");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_STUDY, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        StatusMessage message = controller.deleteTemplate(GUID, "true");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplatePermanently(TEST_STUDY, GUID);
    }
}
