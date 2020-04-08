package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
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

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

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
    }

    @Test
    public void getMostRecentElementsIncludesDeleted() {
        when(dao.getMostRecentElements(TEST_STUDY, true)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_STUDY, true);
        
        assertEquals(returnedElements.size(), 2);
        verify(dao).getMostRecentElements(TEST_STUDY, true);
    }
    
    @Test
    public void getMostRecentElementsExcludesDeleted() {
        when(dao.getMostRecentElements(TEST_STUDY, false)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getMostRecentElements(TEST_STUDY, false);
        
        assertEquals(returnedElements.size(), 2);
        verify(dao).getMostRecentElements(TEST_STUDY, false);
    }
    
    @Test
    public void createElement() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setRevision(null);
        element.setDeleted(true);
        
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.createElement(TEST_STUDY, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        
        // These have been correctly reset
        assertEquals(elementCaptor.getValue().getRevision(), new Long(1));
        AppConfigElement captured = elementCaptor.getValue();
        assertNull(captured.getVersion());
        assertFalse(captured.isDeleted());
        assertEquals(captured.getStudyId(), API_APP_ID);
        assertEquals(captured.getKey(), API_APP_ID + ":id");
        assertEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createElementValidates() {
        service.createElement(TEST_STUDY, AppConfigElement.create());
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createElementThatExists() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), 3L)).thenReturn(element);
        
        service.createElement(TEST_STUDY, element);
    }
    
    @Test
    public void createElementWithArbitraryRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement(); // revision = 3
        
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.createElement(TEST_STUDY, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        
        // Revision was maintained because it was set, and doesn't exist
        assertEquals(elementCaptor.getValue().getRevision(), new Long(3));
    }

    @Test
    public void getElementRevisionsIncludeDeleted() {
        when(dao.getElementRevisions(TEST_STUDY, "id", true)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_STUDY, "id", true);
        assertEquals(returnedElements.size(), 2);
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", true);
    }
    
    @Test
    public void getElementRevisionsExcludeDeleted() {
        when(dao.getElementRevisions(TEST_STUDY, "id", false)).thenReturn(elements);
        
        List<AppConfigElement> returnedElements = service.getElementRevisions(TEST_STUDY, "id", false);
        assertEquals(returnedElements.size(), 2);
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", false);
    }

    @Test
    public void getMostRecentElement() {
        AppConfigElement element = AppConfigElement.create();
        when(dao.getMostRecentElement(TEST_STUDY, "id")).thenReturn(element);
        
        AppConfigElement returned = service.getMostRecentElement(TEST_STUDY, "id");
        assertEquals(returned, element);
        
        verify(dao).getMostRecentElement(TEST_STUDY, "id");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getMostRecentElementDoesNotExist() {
        service.getMostRecentElement(TEST_STUDY, "id");
        
        verify(dao).getMostRecentElement(TEST_STUDY, "id");
    }

    @Test
    public void getElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        when(dao.getElementRevision(TEST_STUDY, "id", 3L)).thenReturn(AppConfigElement.create());
        
        AppConfigElement returned = service.getElementRevision(TEST_STUDY, "id", 3L);
        assertEquals(returned, element);
        
        verify(dao).getElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getElementRevisionDoesNotExist() {
        service.getElementRevision(TEST_STUDY, "id", 3L);
        
        verify(dao).getElementRevision(TEST_STUDY, "id", 3L);
    }

    @Test
    public void updateElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        
        AppConfigElement existing = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(existing);
        when(dao.saveElementRevision(element)).thenReturn(VERSION_HOLDER);
        
        VersionHolder returned = service.updateElementRevision(TEST_STUDY, element);
        assertEquals(returned, VERSION_HOLDER);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        AppConfigElement captured = elementCaptor.getValue(); 
        assertEquals(captured.getStudyId(), API_APP_ID);
        assertEquals(captured.getKey(), API_APP_ID + ":id");
        assertNotEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateElementRevisionValidates() {
        service.updateElementRevision(TEST_STUDY, AppConfigElement.create());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateElementRevisionThatIsLogicallyDeleted() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // true as persisted and updated, should throw ENFE
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.updateElementRevision(TEST_STUDY, element);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateElementRevisionThatDoesNotExist() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(null);
        
        service.updateElementRevision(TEST_STUDY, element);
    }
    
    @Test
    public void updateElementRevisionDeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(false);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateElementRevisionUndeletesRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        
        AppConfigElement persisted = TestUtils.getAppConfigElement();
        persisted.setDeleted(true);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(persisted);
        
        service.updateElementRevision(TEST_STUDY, element);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        assertFalse(elementCaptor.getValue().isDeleted());
    }
    
    @Test
    public void deleteElementAllRevisions() {
        when(dao.getElementRevisions(TEST_STUDY, "id", false)).thenReturn(elements);        
        
        service.deleteElementAllRevisions(TEST_STUDY, "id");
        
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
        when(dao.getElementRevisions(TEST_STUDY, "id", true)).thenReturn(elements);        
        
        service.deleteElementAllRevisionsPermanently(TEST_STUDY, "id");
        
        verify(dao).getElementRevisions(TEST_STUDY, "id", true);
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 1);
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 2);
    }
    
    @Test
    public void deleteElementRevision() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(false);
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevision(TEST_STUDY, "id", 3L);
        
        verify(dao).saveElementRevision(elementCaptor.capture());
        AppConfigElement captured = elementCaptor.getValue();
        assertNotEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
        assertTrue(elementCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteElementRevisionThatDoesNotExist() {
        service.deleteElementRevision(TEST_STUDY, "id", 3L);
    }
    
    @Test
    public void deleteElementRevisionPermanently() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        element.setDeleted(true); // this does not matter. You can permanently delete logically deleted entities.
        when(dao.getElementRevision(TEST_STUDY, element.getId(), element.getRevision())).thenReturn(element);
        
        service.deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
        
        verify(dao).deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteElementRevisionPermanentlyThatDoesNotExist() {
        service.deleteElementRevisionPermanently(TEST_STUDY, "id", 3L);
    }    
}
