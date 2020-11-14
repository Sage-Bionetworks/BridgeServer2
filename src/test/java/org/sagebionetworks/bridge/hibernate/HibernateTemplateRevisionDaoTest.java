package org.sagebionetworks.bridge.hibernate;

import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.GET_ALL;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.SELECT_COUNT;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.SELECT_TEMPLATE;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.TEMPLATE_GUID_PARAM_NAME;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateRevisionId;
import org.sagebionetworks.bridge.s3.S3Helper;

public class HibernateTemplateRevisionDaoTest extends Mockito {
    private static final DateTime CREATED_ON = TestConstants.TIMESTAMP;
    private static final String USER_ID = "123456";
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final String SUBJECT = "Test SMS subject line";
    private static final String DOCUMENT_CONTENT = "Test SMS message";
    private static final String PUB_BUCKET = "oneS3Bucket";
    private static final String STORAGE_PATH = TEMPLATE_GUID + "." + CREATED_ON.getMillis();

    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    S3Helper mockS3Helper;
    
    @Mock
    BridgeConfig bridgeConfig;

    @InjectMocks
    HibernateTemplateRevisionDao revisionDao;
    
    @Captor
    ArgumentCaptor<ObjectMetadata> metadataCaptor;

    @Captor
    ArgumentCaptor<TemplateRevisionId> revisionIdCaptor;
    
    @Captor
    ArgumentCaptor<TemplateRevision> revisionCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(bridgeConfig.getHostnameWithPostfix("docs")).thenReturn(PUB_BUCKET);
        revisionDao.setBridgeConfig(bridgeConfig);
    }
    
    @Test
    public void getTemplateRevisions() {
        String expectedCountQuery = SELECT_COUNT + GET_ALL;
        String expectedGetQuery = SELECT_TEMPLATE + GET_ALL;
        
        List<HibernateTemplateRevision> list = ImmutableList.of(new HibernateTemplateRevision(),
                new HibernateTemplateRevision());
        
        when(mockHelper.queryCount(eq(expectedCountQuery), any())).thenReturn(2);
        when(mockHelper.queryGet(eq(expectedGetQuery), any(), eq(250), eq(50), eq(HibernateTemplateRevision.class))).thenReturn(list);
        
        PagedResourceList<? extends TemplateRevision> result = revisionDao.getTemplateRevisions(TEMPLATE_GUID, 250, 50);
        assertSame(result.getItems(), list);
        assertEquals(result.getRequestParams().get(TOTAL), 2);
        assertEquals(result.getRequestParams().get(PAGE_SIZE), 50);
        assertEquals(result.getRequestParams().get(OFFSET_BY), 250);
        
        verify(mockHelper).queryCount(eq(expectedCountQuery), paramsCaptor.capture());
        verify(mockHelper).queryGet(eq(expectedGetQuery), paramsCaptor.capture(), 
                eq(250), eq(50), eq(HibernateTemplateRevision.class));
        
        Map<String,Object> params = paramsCaptor.getAllValues().get(0);
        assertEquals(params.get(TEMPLATE_GUID_PARAM_NAME), TEMPLATE_GUID);
        
        params = paramsCaptor.getAllValues().get(1);
        assertEquals(params.get(TEMPLATE_GUID_PARAM_NAME), TEMPLATE_GUID);
    }

    @Test
    public void getTemplateRevision() throws Exception {
        HibernateTemplateRevision existing = new HibernateTemplateRevision();
        when(mockHelper.getById(eq(HibernateTemplateRevision.class), any())).thenReturn(existing);
        when(mockS3Helper.readS3FileAsString(PUB_BUCKET, STORAGE_PATH)).thenReturn(DOCUMENT_CONTENT);
        
        Optional<TemplateRevision> optional = revisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
        assertTrue(optional.isPresent());
        assertSame(optional.get(), existing);
        assertEquals(optional.get().getDocumentContent(), DOCUMENT_CONTENT);
        
        verify(mockS3Helper).readS3FileAsString(PUB_BUCKET, STORAGE_PATH);
        verify(mockHelper).getById(eq(HibernateTemplateRevision.class), revisionIdCaptor.capture());
        assertEquals(revisionIdCaptor.getValue().getTemplateGuid(), TEMPLATE_GUID);
        assertEquals(revisionIdCaptor.getValue().getCreatedOn(), CREATED_ON);
    }

    @Test
    public void getTemplateRevisionNotFound() {
        Optional<TemplateRevision> optional = revisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
        assertFalse(optional.isPresent());
        
        verify(mockHelper).getById(eq(HibernateTemplateRevision.class), revisionIdCaptor.capture());
        assertEquals(revisionIdCaptor.getValue().getTemplateGuid(), TEMPLATE_GUID);
        assertEquals(revisionIdCaptor.getValue().getCreatedOn(), CREATED_ON);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void getTemplateRevisionDocumentContentThrowsException() throws Exception {
        HibernateTemplateRevision existing = new HibernateTemplateRevision();
        when(mockHelper.getById(eq(HibernateTemplateRevision.class), any())).thenReturn(existing);
        when(mockS3Helper.readS3FileAsString(PUB_BUCKET, STORAGE_PATH)).thenThrow(new IOException("bad"));
        
        revisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
    }
    
    @Test
    public void getTemplateRevisionDocumentContentReturnsNull() throws Exception {
        HibernateTemplateRevision existing = new HibernateTemplateRevision();
        when(mockHelper.getById(eq(HibernateTemplateRevision.class), any())).thenReturn(existing);
        when(mockS3Helper.readS3FileAsString(PUB_BUCKET, STORAGE_PATH)).thenReturn(null);
        
        Optional<TemplateRevision> opt = revisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
        assertNull(opt.get().getDocumentContent()); // this is ok
    }
    
    @Test
    public void createTemplateRevision() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        revision.setTemplateGuid(TEMPLATE_GUID);
        revision.setCreatedOn(CREATED_ON);
        revision.setCreatedBy(USER_ID);
        revision.setStoragePath(STORAGE_PATH);
        revision.setMimeType(HTML);
        revision.setSubject(SUBJECT);
        revision.setDocumentContent(DOCUMENT_CONTENT);
        
        revisionDao.createTemplateRevision(revision);
        
        verify(mockS3Helper).writeBytesToS3(eq(PUB_BUCKET), eq(STORAGE_PATH), eq(DOCUMENT_CONTENT.getBytes()), metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), AES_256_SERVER_SIDE_ENCRYPTION);
        
        verify(mockHelper).create(revisionCaptor.capture());
        assertSame(revisionCaptor.getValue(), revision);
    }
}
