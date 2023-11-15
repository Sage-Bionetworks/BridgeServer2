package org.sagebionetworks.bridge.hibernate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableJob;

public class HibernateUploadTableJobDaoTest {
    private static final String JOB_GUID = "test-job-guid";

    @Mock
    private HibernateHelper mockHibernateHelper;

    @InjectMocks
    private HibernateUploadTableJobDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getUploadTableJob() {
        // Set up mock.
        HibernateUploadTableJob job = new HibernateUploadTableJob();
        when(mockHibernateHelper.getById(eq(HibernateUploadTableJob.class), eq(JOB_GUID))).thenReturn(job);

        // Execute.
        Optional<UploadTableJob> result = dao.getUploadTableJob(JOB_GUID);
        assertTrue(result.isPresent());
        assertSame(result.get(), job);
    }

    @Test
    public void getUploadTableJob_NoValue() {
        // Set up mock.
        when(mockHibernateHelper.getById(eq(HibernateUploadTableJob.class), eq(JOB_GUID))).thenReturn(null);

        // Execute.
        Optional<UploadTableJob> result = dao.getUploadTableJob(JOB_GUID);
        assertFalse(result.isPresent());
    }

    @Test
    public void listUploadTableJobsForStudy() {
        // Set up mock.
        HibernateUploadTableJob job = new HibernateUploadTableJob();
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(1);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(job));

        // Execute.
        PagedResourceList<UploadTableJob> result = dao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 10, 20);
        assertEquals(result.getItems().size(), 1);
        assertSame(result.getItems().get(0), job);

        assertEquals(result.getRequestParams().get("start"), 10);
        assertEquals(result.getRequestParams().get("pageSize"), 20);

        // Verify call to hibernate.
        String expectedQuery = "FROM HibernateUploadTableJob WHERE appId = :appId AND studyId = :studyId ORDER BY requestedOn DESC";
        String expectedCountQuery = "SELECT COUNT(DISTINCT jobGuid) " + expectedQuery;
        Map<String, Object> paramMap = ImmutableMap.<String, Object>builder()
                .put("appId", TestConstants.TEST_APP_ID)
                .put("studyId", TestConstants.TEST_STUDY_ID).build();

        verify(mockHibernateHelper).queryCount(expectedCountQuery, paramMap);
        verify(mockHibernateHelper).queryGet(expectedQuery, paramMap, 10, 20,
                HibernateUploadTableJob.class);
    }

    @Test
    public void saveUploadTableJob() {
        // Execute.
        UploadTableJob job = UploadTableJob.create();
        dao.saveUploadTableJob(job);

        // Verify call to hibernate.
        verify(mockHibernateHelper).saveOrUpdate(job);
    }
}
