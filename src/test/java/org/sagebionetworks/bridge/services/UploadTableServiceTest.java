package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.Optional;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class UploadTableServiceTest {
    private static final String RECORD_ID = "test-record";

    @Mock
    private StudyService mockStudyService;

    @Mock
    private UploadTableRowDao mockUploadTableRowDao;

    @InjectMocks
    private UploadTableService service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void deleteUploadTableRow() {
        // Execute and verify.
        service.deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
        verify(mockUploadTableRowDao).deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
    }

    @Test
    public void deleteUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void getUploadTableRow() {
        // Set up mocks.
        UploadTableRow row = UploadTableRow.create();
        when(mockUploadTableRowDao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID)).thenReturn(Optional.of(row));

        // Execute and verify.
        UploadTableRow result = service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
        assertSame(result, row);
        verify(mockUploadTableRowDao).getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
    }

    @Test
    public void getUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void getUploadTableRow_rowDoesntExist() {
        // Set up mocks.
        when(mockUploadTableRowDao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID)).thenReturn(Optional.empty());


        // Execute - This throws.
        try {
            service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "UploadTableRow");
        }
    }

    @Test
    public void queryUploadTableRows() {
        // Execute and verify.
        UploadTableRowQuery query = new UploadTableRowQuery();
        service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, query);
        verify(mockUploadTableRowDao).queryUploadTableRows(same(query));

        // Verify that the query was updated with appId and studyId.
        assertEquals(query.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(query.getStudyId(), TestConstants.TEST_STUDY_ID);
    }

    @Test
    public void queryUploadTableRows_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                    new UploadTableRowQuery());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void queryUploadTableRows_invalidQuery() {
        // Simplest way to make an invalid query is with a negative start.
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setStart(-1);

        // Execute - This throws.
        try {
            service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, query);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }
    }

    @Test
    public void saveUploadTableRow() {
        // Execute and verify.
        UploadTableRow row = makeValidRow();
        service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
        verify(mockUploadTableRowDao).saveUploadTableRow(same(row));

        // Verify that the row was updated with appId and studyId and a non-null createdOn.
        assertEquals(row.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(row.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertNotNull(row.getCreatedOn());
    }

    @Test
    public void saveUploadTableRow_optionalCreatedOn() {
        // Execute and verify.
        UploadTableRow row = makeValidRow();
        row.setCreatedOn(TestConstants.CREATED_ON);
        service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
        verify(mockUploadTableRowDao).saveUploadTableRow(same(row));

        // Verify that the row was updated with appId and studyId, but createdOn is unchanged.
        assertEquals(row.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(row.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(row.getCreatedOn(), TestConstants.CREATED_ON);
    }

    @Test
    public void saveUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, makeValidRow());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void saveUploadTableRow_invalidRow() {
        // Simplest way to make an invalid ror is with a blank recordId.
        UploadTableRow row = makeValidRow();
        row.setRecordId("   ");

        // Execute - This throws.
        try {
            service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }
    }

    private static UploadTableRow makeValidRow() {
        UploadTableRow row = UploadTableRow.create();
        row.setRecordId(RECORD_ID);
        row.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        row.setHealthCode(TestConstants.HEALTH_CODE);
        return row;
    }
}
