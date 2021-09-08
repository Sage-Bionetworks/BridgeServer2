package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId.STUDY_DISEASES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnum;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnumEntry;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

public class AppConfigElementServiceTest {
    
    private static final DateTime TIMESTAMP = DateTime.now();
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(TestUtils.getAppConfigElement().getVersion());
    
    private List<AppConfigElement> elements;
    
    @Mock
    private AppConfigElementDao dao;
    
    @Captor
    private ArgumentCaptor<AppConfigElement> elementCaptor;
    
    @Spy
    private AppConfigElementService service;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        service.setAppConfigElementDao(dao);
        elements = ImmutableList.of(AppConfigElement.create(), AppConfigElement.create());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        RequestContext.set(NULL_INSTANCE);
    }

    @Test
    public void getMostRecentElementsIncludesDeleted() {
        when(dao.getMostRecentElements(TEST_APP_ID, true)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_APP_ID, true);
        
        assertEquals(returnedElements.size(), 2);
        verify(dao).getMostRecentElements(TEST_APP_ID, true);
    }
    
    @Test
    public void getMostRecentElementsExcludesDeleted() {
        when(dao.getMostRecentElements(TEST_APP_ID, false)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_APP_ID, false);
        
        assertEquals(returnedElements.size(), 2);
        verify(dao).getMostRecentElements(TEST_APP_ID, false);
    }
    
    @Test
    public void createElement() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setRevision(null);
        element.setDeleted(true);
        
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.createElement(TEST_APP_ID, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        
        // These have been correctly reset
        assertEquals(elementCaptor.getValue().getRevision(), new Long(1));
        AppConfigElement captured = elementCaptor.getValue();
        assertNull(captured.getVersion());
        assertFalse(captured.isDeleted());
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getKey(), TEST_APP_ID + ":id");
        assertEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createElementValidates() {
        service.createElement(TEST_APP_ID, AppConfigElement.create());
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createElementThatExists() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), 3L)).thenReturn(element);
        
        service.createElement(TEST_APP_ID, element);
    }
    
    @Test
    public void createElementWithArbitraryRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement(); // revision = 3
        
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.createElement(TEST_APP_ID, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        
        // Revision was maintained because it was set, and doesn't exist
        assertEquals(elementCaptor.getValue().getRevision(), new Long(3));
    }

    @Test
    public void getElementRevisionsIncludeDeleted() {
        when(dao.getElementRevisions(TEST_APP_ID, "id", true)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_APP_ID, "id", true);
        assertEquals(returnedElements.size(), 2);
        
        verify(dao).getElementRevisions(TEST_APP_ID, "id", true);
    }
    
    @Test
    public void getElementRevisionsExcludeDeleted() {
        when(dao.getElementRevisions(TEST_APP_ID, "id", false)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_APP_ID, "id", false);
        assertEquals(returnedElements.size(), 2);
        
        verify(dao).getElementRevisions(TEST_APP_ID, "id", false);
    }

    @Test
    public void getMostRecentElement() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("id");
        when(dao.getMostRecentElement(TEST_APP_ID, "id")).thenReturn(element);
        
        AppConfigElement returned = service.getMostRecentElement(TEST_APP_ID, "id");
        assertEquals(returned, element);
        
        verify(dao).getMostRecentElement(TEST_APP_ID, "id");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getMostRecentElementDoesNotExist() {
        service.getMostRecentElement(TEST_APP_ID, "id");
        
        verify(dao).getMostRecentElement(TEST_APP_ID, "id");
    }
    
    @Test
    public void getMostRecentElementLocalizesAppConfigEnum() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("bridge:id");
        element.setData(new ObjectMapper().valueToTree(createAppConfigEnum()));
        
        when(dao.getMostRecentElement(TEST_APP_ID, "id")).thenReturn(element);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("fr")).build());
        
        AppConfigElement returned = service.getMostRecentElement(TEST_APP_ID, "id");
        
        JsonNode data = returned.getData();
        assertEquals(data.get("entries").get(0).get("label").textValue(), "French");
    }

    @Test
    public void getElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("id");
        when(dao.getElementRevision(TEST_APP_ID, "id", 3L)).thenReturn(element);
        
        AppConfigElement returned = service.getElementRevision(TEST_APP_ID, "id", 3L);
        assertEquals(returned, element);
        
        verify(dao).getElementRevision(TEST_APP_ID, "id", 3L);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getElementRevisionDoesNotExist() {
        service.getElementRevision(TEST_APP_ID, "id", 3L);
        
        verify(dao).getElementRevision(TEST_APP_ID, "id", 3L);
    }
    
    @Test
    public void getElementRevisionLocalizesAppConfigEnum() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("bridge:id");
        element.setData(new ObjectMapper().valueToTree(createAppConfigEnum()));
        
        when(dao.getElementRevision(TEST_APP_ID, "id", 1)).thenReturn(element);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("fr")).build());
        
        AppConfigElement returned = service.getElementRevision(TEST_APP_ID, "id", 1);
        
        JsonNode data = returned.getData();
        assertEquals(data.get("entries").get(0).get("label").textValue(), "French");
    }


    @Test
    public void updateElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        
        AppConfigElement existing = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(existing);
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.updateElementRevision(TEST_APP_ID, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        AppConfigElement captured = elementCaptor.getValue(); 
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getKey(), TEST_APP_ID + ":id");
        assertNotEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateElementRevisionValidates() {
        service.updateElementRevision(TEST_APP_ID, AppConfigElement.create());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateElementRevisionThatIsLogicallyDeleted() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // true as persisted and updated, should throw ENFE
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(element);
        
        service.updateElementRevision(TEST_APP_ID, element);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateElementRevisionThatDoesNotExist() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(null);
        
        service.updateElementRevision(TEST_APP_ID, element);
    }
    
    @Test
    public void updateElementRevisionDeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(false);
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_APP_ID, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateElementRevisionUndeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(true);
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_APP_ID, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertFalse(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void deleteElementAllRevisions() {
        when(dao.getElementRevisions(TEST_APP_ID, "id", false)).thenReturn(elements);        
        
        service.deleteElementAllRevisions(TEST_APP_ID, "id");
        
        for(AppConfigElement element : elements) {
            assertTrue(element.isDeleted());
            assertNotEquals(element.getCreatedOn(), TIMESTAMP.getMillis());
            assertEquals(element.getModifiedOn(), TIMESTAMP.getMillis());
        }
        verify(dao, times(2)).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getAllValues().get(0).isDeleted());
        assertTrue(elementCaptor.getAllValues().get(1).isDeleted());
    }
    
    @Test
    public void deleteElementAllRevisionsPermanently() {
        elements.get(0).setId("id");
        elements.get(0).setRevision(1L);
        elements.get(1).setId("id");
        elements.get(1).setRevision(2L);
        when(dao.getElementRevisions(TEST_APP_ID, "id", true)).thenReturn(elements);        
        
        service.deleteElementAllRevisionsPermanently(TEST_APP_ID, "id");
        
        verify(dao).getElementRevisions(TEST_APP_ID, "id", true);
        verify(dao).deleteElementRevisionPermanently(TEST_APP_ID, "id", 1);
        verify(dao).deleteElementRevisionPermanently(TEST_APP_ID, "id", 2);
    }
    
    @Test
    public void deleteElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevision(TEST_APP_ID, "id", 3L);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        AppConfigElement captured = elementCaptor.getValue();
        assertNotEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteElementRevisionThatDoesNotExist() {
        service.deleteElementRevision(TEST_APP_ID, "id", 3L);
    }
    
    @Test
    public void deleteElementRevisionPermanently() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // this does not matter. You can permanently delete logically deleted entities.
        when(dao.getElementRevision(TEST_APP_ID, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevisionPermanently(TEST_APP_ID, "id", 3L);
        
        verify(dao).deleteElementRevisionPermanently(TEST_APP_ID, "id", 3L);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteElementRevisionPermanentlyThatDoesNotExist() {
        service.deleteElementRevisionPermanently(TEST_APP_ID, "id", 3L);
    }
    
    @Test
    public void getAppConfigEnum() {
        AppConfigEnum configEnum = createAppConfigEnum();

        JsonNode node = new ObjectMapper().valueToTree(configEnum);
        
        AppConfigElement element = AppConfigElement.create();
        element.setData(node);
        when(dao.getMostRecentElement(TEST_APP_ID, STUDY_DISEASES.getAppConfigKey())).thenReturn(element);
        
        AppConfigEnum retValue = service.getAppConfigEnum(TEST_APP_ID, STUDY_DISEASES);
        assertEquals(retValue, configEnum);
    }
    
    @Test
    public void getAppConfigEnum_notFound() {
        when(dao.getMostRecentElement(TEST_APP_ID, STUDY_DISEASES.getAppConfigKey())).thenReturn(null);
        
        AppConfigEnum retValue = service.getAppConfigEnum(TEST_APP_ID, STUDY_DISEASES);
        assertEquals(retValue, new AppConfigEnum());
        
        assertFalse(retValue.getValidate());
    }

    private AppConfigEnum createAppConfigEnum() {
        AppConfigEnumEntry entry1 = new AppConfigEnumEntry();
        entry1.setValue("Disease");
        entry1.setLabels(LABELS);
        AppConfigEnumEntry entry2 = new AppConfigEnumEntry();
        entry2.setValue("B");
        AppConfigEnumEntry entry3 = new AppConfigEnumEntry();
        entry3.setValue("C");
        AppConfigEnum configEnum = new AppConfigEnum();
        configEnum.setValidate(true);
        configEnum.setEntries(ImmutableList.of(entry1, entry2, entry3));
        return configEnum;
    }
}
