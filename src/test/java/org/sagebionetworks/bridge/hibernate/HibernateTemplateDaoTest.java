package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TEMPLATE_TYPE;
import static org.sagebionetworks.bridge.models.ResourceList.TOTAL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_ACCOUNT_EXISTS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.Template;

public class HibernateTemplateDaoTest extends Mockito {
    private static String GUID = "oneGuid";
    
    @Mock
    HibernateHelper mockHelper;
    
    @InjectMocks
    HibernateTemplateDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getTemplatesIncludeDeleted() throws Exception {
        when(mockHelper.queryCount(any(), any())).thenReturn(150);
        when(mockHelper.queryGet(any(), any(), eq(5), eq(50), eq(HibernateTemplate.class)))
                .thenReturn(ImmutableList.of(new HibernateTemplate(), new HibernateTemplate()));
        
        PagedResourceList<? extends Template> paged = dao.getTemplates(TEST_APP_ID, SMS_ACCOUNT_EXISTS, 5, 50, true);
        Map<String,Object> params = paged.getRequestParams();
        assertEquals(params.get(TEMPLATE_TYPE), SMS_ACCOUNT_EXISTS);
        assertEquals(params.get(TOTAL), 150);
        assertEquals(params.get(PAGE_SIZE), 50);
        assertEquals(params.get(INCLUDE_DELETED), true);
        assertEquals(params.get(OFFSET_BY), 5);
        assertEquals(paged.getItems().size(), 2);
        
        verify(mockHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHelper).queryGet(queryCaptor.capture(), any(), eq(5), eq(50), eq(HibernateTemplate.class));
        
        Map<String,Object> queryParams = paramsCaptor.getValue();
        assertEquals(queryParams.get("appId"), TEST_APP_ID);
        assertEquals(queryParams.get("templateType"), SMS_ACCOUNT_EXISTS);
        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);
        assertEquals(countQuery, "SELECT count(guid) FROM HibernateTemplate as template WHERE templateType = " + 
                ":templateType AND appId = :appId ORDER BY createdOn DESC");
        assertEquals(getQuery, "SELECT template FROM HibernateTemplate as template WHERE templateType = " + 
                ":templateType AND appId = :appId ORDER BY createdOn DESC");
    }

    @Test
    public void getTemplatesExcludeDeleted() {
        dao.getTemplates(TEST_APP_ID, SMS_ACCOUNT_EXISTS, 5, 50, false);
        
        verify(mockHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        
        String query = queryCaptor.getValue();
        assertEquals(query, "SELECT count(guid) FROM HibernateTemplate as template WHERE templateType = " + 
                ":templateType AND appId = :appId AND deleted = 0 ORDER BY createdOn DESC");
    }
    
    @Test
    public void getTemplate() { 
        HibernateTemplate template = new HibernateTemplate();
        template.setAppId(TEST_APP_ID);
        when(mockHelper.getById(HibernateTemplate.class, GUID)).thenReturn(template);
        
        Optional<Template> result = dao.getTemplate(TEST_APP_ID, GUID);
        assertNotNull(result.get());
    }
    
    @Test
    public void getTemplateMissing() {
        Optional<Template> template = dao.getTemplate(TEST_APP_ID, GUID);
        assertFalse(template.isPresent());
    }
    
    @Test
    public void getTemplateWrongApp() {
        HibernateTemplate template = new HibernateTemplate();
        template.setAppId("not the app we're looking for");
        when(mockHelper.getById(HibernateTemplate.class, GUID)).thenReturn(template);
        
        Optional<Template> result = dao.getTemplate(TEST_APP_ID, GUID);
        assertFalse(result.isPresent());
    }

    @Test
    public void createTemplate() {
        Template template = Template.create();
                
        dao.createTemplate(template, null);
        
        verify(mockHelper).create(template, null);
    }

    @Test
    public void updateTemplate() {
        Template template = Template.create();
        
        dao.updateTemplate(template);
        
        verify(mockHelper).update(template, null);
    }

    @Test
    public void deleteTemplatePermanently() {
        HibernateTemplate template = new HibernateTemplate();
        template.setAppId(TEST_APP_ID);
        when(mockHelper.getById(HibernateTemplate.class, GUID)).thenReturn(template);
        
        dao.deleteTemplatePermanently(TEST_APP_ID, GUID);
        
        verify(mockHelper).deleteById(HibernateTemplate.class, GUID);
    }
    
    @Test
    public void deleteTemplatePermanentlyMissing() {
        // should silently do nothing
        dao.deleteTemplatePermanently(TEST_APP_ID, GUID);
        
        verify(mockHelper, never()).deleteById(HibernateTemplate.class, GUID);
    }
    
    @Test
    public void deleteTemplatesForApp() { 
        dao.deleteTemplatesForApp(TEST_APP_ID);
        
        verify(mockHelper).query(eq("DELETE FROM HibernateTemplate WHERE appId = :appId"),
                paramsCaptor.capture());
        
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
    }
}
