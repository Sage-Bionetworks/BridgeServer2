package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.GET_ALL;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.SELECT_COUNT;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.SELECT_TEMPLATE;
import static org.sagebionetworks.bridge.hibernate.HibernateTemplateRevisionDao.TEMPLATE_GUID_PARAM_NAME;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;
import static org.sagebionetworks.bridge.models.studies.MimeType.HTML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateRevisionId;

public class HibernateTemplateRevisionDaoTest extends Mockito {
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final DateTime CREATED_ON = TestConstants.TIMESTAMP;
    private static final String STORAGE_PATH = TEMPLATE_GUID + "." + CREATED_ON.getMillis();

    @Mock
    HibernateHelper mockHelper;
    
    @InjectMocks
    HibernateTemplateRevisionDao revisionDao;
    
    @Captor
    ArgumentCaptor<TemplateRevisionId> revisionIdCaptor;
    
    @Captor
    ArgumentCaptor<TemplateRevision> revisionCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
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
    public void getTemplateRevision() {
        HibernateTemplateRevision existing = new HibernateTemplateRevision();
        when(mockHelper.getById(eq(HibernateTemplateRevision.class), any())).thenReturn(existing);
        
        Optional<TemplateRevision> optional = revisionDao.getTemplateRevision(TEMPLATE_GUID, CREATED_ON);
        assertTrue(optional.isPresent());
        assertSame(optional.get(), existing);
        
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
    
    @Test
    public void createTemplateRevision() {
        TemplateRevision revision = TemplateRevision.create();
        revision.setTemplateGuid(TEMPLATE_GUID);
        revision.setCreatedOn(CREATED_ON);
        revision.setCreatedBy("123456");
        revision.setStoragePath(STORAGE_PATH);
        revision.setMimeType(HTML);
        revision.setSubject("A subject line");        
        
        revisionDao.createTemplateRevision(revision);
        
        verify(mockHelper).create(revisionCaptor.capture(), isNull());
        assertSame(revisionCaptor.getValue(), revision);
    }
}
