package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_PHONE_SIGN_IN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

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
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public class TemplateRevisionServiceTest extends Mockito {
    
    private static final String SUBJECT = "Test SMS subject line";
    private static final String DOCUMENT_CONTENT = "Test SMS message ${token}";
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final DateTime CREATED_ON = TestConstants.TIMESTAMP;
    private static final String STORAGE_PATH = TEMPLATE_GUID + "." + CREATED_ON.getMillis();
    
    @Mock
    TemplateDao mockTemplateDao;
    
    @Mock
    TemplateRevisionDao mockTemplateRevisionDao;
    
    @InjectMocks
    @Spy
    TemplateRevisionService service;

    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    @Captor
    ArgumentCaptor<TemplateRevision> revisionCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getDateTime()).thenReturn(TIMESTAMP);
        when(service.getUserId()).thenReturn(USER_ID);
    }

    @Test
    public void getTemplateRevisions() {
        mockGetTemplate();
        
        List<? extends TemplateRevision> list = ImmutableList.of(TemplateRevision.create(), TemplateRevision.create());
        PagedResourceList<? extends TemplateRevision> page = new PagedResourceList<>(list, 2);
        doReturn(page).when(mockTemplateRevisionDao).getTemplateRevisions(TEMPLATE_GUID, 400, 20);
        
        PagedResourceList<? extends TemplateRevision> result = service.getTemplateRevisions(TEST_APP_ID,
                TEMPLATE_GUID, 400, 20);
        assertSame(result.getItems(), list);
        assertEquals(result.getTotal(), Integer.valueOf(2));
        
        verify(mockTemplateRevisionDao).getTemplateRevisions(TEMPLATE_GUID, 400, 20);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp="Template not found.")
    public void getTemplateRevisionsTemplateNotFound() {
        service.getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 400, 20);
    }
    
    @Test
    public void getTemplateRevisionsSetsDefaults() {
        mockGetTemplate();
        
        service.getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, null, null);
        
        verify(mockTemplateRevisionDao).getTemplateRevisions(TEMPLATE_GUID, 0, API_DEFAULT_PAGE_SIZE);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Invalid negative offset value")
    public void getTemplateRevisionsOffsetNegative() {
        mockGetTemplate();
        service.getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, -1, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "pageSize must be in range 1-"
            + API_MAXIMUM_PAGE_SIZE)
    public void getTemplateRevisionsExceedMaxPageSize() {
        mockGetTemplate();
        service.getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 0, 1000);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "pageSize must be in range 1-"
            + API_MAXIMUM_PAGE_SIZE)
    public void getTemplateRevisionsMinPageZero() {
        mockGetTemplate();
        service.getTemplateRevisions(TEST_APP_ID, TEMPLATE_GUID, 0, 0);
    }
    
    @Test
    public void createTemplateRevision() throws Exception {
        mockGetTemplate();
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setMimeType(TEXT);
        revision.setSubject(SUBJECT);
        revision.setDocumentContent(DOCUMENT_CONTENT);
        
        CreatedOnHolder holder = service.createTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, revision);
        assertEquals(holder.getCreatedOn(), CREATED_ON);
        
        verify(mockTemplateRevisionDao).createTemplateRevision(revisionCaptor.capture());
        TemplateRevision captured = revisionCaptor.getValue();
        
        assertEquals(captured.getTemplateGuid(), TEMPLATE_GUID);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getCreatedBy(), USER_ID);
        assertEquals(captured.getStoragePath(), STORAGE_PATH);
        assertEquals(captured.getDocumentContent(), DOCUMENT_CONTENT);
        assertEquals(captured.getMimeType(), TEXT);
        assertEquals(captured.getSubject(), SUBJECT);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void createTemplateRevisionTemplateNotFound() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        service.createTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, revision);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createTemplateRevisionInvalid() throws Exception {
        mockGetTemplate();
        
        service.createTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, TemplateRevision.create());        
    }
    
    @Test
    public void getTemplateRevision() throws Exception {
        mockGetTemplate();
        TemplateRevision revision = mockGetTemplateRevision();
        
        TemplateRevision returned = service.getTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
        assertSame(returned, revision);
        
        verify(mockTemplateDao).getTemplate(TEST_APP_ID, TEMPLATE_GUID);
        verify(mockTemplateRevisionDao).getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp = "Template not found.")
    public void getTemplateRevisionTemplateNotFound() throws Exception { 
        service.getTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp = "TemplateRevision not found.")
    public void getTemplateRevisionTemplateRevisionNotFound() throws Exception { 
        mockGetTemplate();
        service.getTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test
    public void publishTemplateRevision() {
        mockGetTemplate();
        mockGetTemplateRevision();
        
        service.publishTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
        
        verify(mockTemplateDao).updateTemplate(templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getPublishedCreatedOn(), CREATED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp = "Template not found.")
    public void publishTemplateRevisionTemplateNotFound() {
        service.publishTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp = "TemplateRevision not found.")
    public void publishTemplateRevisionTemplateRevisionNotFound() { 
        mockGetTemplate();
        
        service.publishTemplateRevision(TEST_APP_ID, TEMPLATE_GUID, CREATED_ON);
    }
    
    private void mockGetTemplate() {
        Template template = Template.create();
        template.setGuid(TEMPLATE_GUID);
        template.setTemplateType(SMS_PHONE_SIGN_IN);
        when(mockTemplateDao.getTemplate(TEST_APP_ID, TEMPLATE_GUID)).thenReturn(Optional.of(template));
    }

    private TemplateRevision mockGetTemplateRevision() {
        TemplateRevision revision = TemplateRevision.create();
        when(mockTemplateRevisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON)).thenReturn(Optional.of(revision));
        return revision;
    }
}
