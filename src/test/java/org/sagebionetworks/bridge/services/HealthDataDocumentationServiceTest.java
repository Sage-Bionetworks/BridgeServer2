package org.sagebionetworks.bridge.services;

import com.google.common.collect.ImmutableList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.dao.HealthDataDocumentationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.testng.AssertJUnit.assertSame;

public class HealthDataDocumentationServiceTest {

    @Mock
    private HealthDataDocumentationDao mockDao;

    @InjectMocks
    private HealthDataDocumentationService service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateHealthDataDocumentation() {
        HealthDataDocumentation doc = makeValidDoc();
        when(mockDao.createOrUpdateDocumentation(doc)).thenReturn(doc);

        HealthDataDocumentation result = service.createOrUpdateHealthDataDocumentation(doc);
        assertSame(result, doc);

        verify(mockDao).createOrUpdateDocumentation(doc);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrUpdateHealthDataDocumentation_NullDoc() {
        service.createOrUpdateHealthDataDocumentation(null);
    }

    @Test
    public void deleteHealthDataDocumentation() {
        service.deleteHealthDataDocumentation(TEST_APP_ID, IDENTIFIER);
        verify(mockDao).deleteDocumentationForIdentifier(TEST_APP_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Identifier must be specified.")
    public void deleteHealthDataDocumentation_NullIdentifier() {
        service.deleteHealthDataDocumentation(TEST_APP_ID, null);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Parent ID must be specified.")
    public void deleteHealthDataDocumentation_NullParentId() {
        service.deleteHealthDataDocumentation(null, IDENTIFIER);
    }

    @Test
    public void deleteAllHealthDataDocumentation() {
        service.deleteAllHealthDataDocumentation(TEST_APP_ID);
        verify(mockDao).deleteDocumentationForParentId(TEST_APP_ID);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Parent ID must be specified.")
    public void deleteAllHealthDataDocumentation_NullParentId() {
        service.deleteAllHealthDataDocumentation(null);
    }

    @Test
    public void getHealthDataDocumentationForId() {
        HealthDataDocumentation doc = makeValidDoc();
        when(mockDao.getDocumentationByIdentifier(TEST_APP_ID, IDENTIFIER)).thenReturn(doc);

        HealthDataDocumentation result = service.getHealthDataDocumentationForId(TEST_APP_ID, IDENTIFIER);
        assertSame(result, doc);

        verify(mockDao).getDocumentationByIdentifier(TEST_APP_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Identifier must be specified.")
    public void getHealthDataDocumentationForId_NullIdentifier() {
        service.getHealthDataDocumentationForId(TEST_APP_ID, null);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Parent ID must be specified.")
    public void getHealthDataDocumentation_NullParentId() {
        service.getHealthDataDocumentationForId(null, IDENTIFIER);
    }

    @Test
    public void getAllHealthDataDocumentation() {
        // mock dependencies
        ForwardCursorPagedResourceList<HealthDataDocumentation> docList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(makeValidDoc()), null);
        when(mockDao.getDocumentationForParentId(TEST_APP_ID, API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(docList);

        // execute
        ForwardCursorPagedResourceList<HealthDataDocumentation> resultList = service.getAllHealthDataDocumentation(
                TEST_APP_ID, API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
        assertSame(resultList, docList);

        // validate
        verify(mockDao).getDocumentationForParentId(TEST_APP_ID, API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Parent ID must be specified.")
    public void getAllHealthDataDocumentation_NullParentId() {
        service.getAllHealthDataDocumentation(null, API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "pageSize must be from 5-100 records")
    public void getAllHealthDataDocumentation_InvalidPageSize() {
        service.getAllHealthDataDocumentation(TEST_APP_ID, -1, OFFSET_KEY);
    }

    private static HealthDataDocumentation makeValidDoc() {
        HealthDataDocumentation doc = HealthDataDocumentation.create();
        doc.setTitle("title");
        doc.setParentId(TEST_APP_ID);
        doc.setIdentifier(IDENTIFIER);
        doc.setDocumentation("sample-documentation.");
        return doc;
    }
}