package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.HealthDataEx3Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

@SuppressWarnings("ConstantConditions")
public class HealthDataEx3ServiceTest {
    private static final DateTime CREATED_ON_START = DateTime.parse("2020-07-27T18:48:14.564-0700");
    private static final long CREATED_ON_START_MILLIS = CREATED_ON_START.getMillis();
    private static final DateTime CREATED_ON_END = DateTime.parse("2020-07-29T15:22:58.998-0700");
    private static final long CREATED_ON_END_MILLIS = CREATED_ON_END.getMillis();
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";

    @Mock
    private HealthDataEx3Dao mockDao;

    @InjectMocks
    private HealthDataEx3Service service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateRecord() {
        HealthDataRecordEx3 record = makeValidRecord();
        when(mockDao.createOrUpdateRecord(record)).thenReturn(record);

        HealthDataRecordEx3 result = service.createOrUpdateRecord(record);
        assertSame(result, record);

        verify(mockDao).createOrUpdateRecord(same(record));
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrUpdateRecord_NullRecord() {
        service.createOrUpdateRecord(null);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrUpdateRecord_InvalidRecord() {
        service.createOrUpdateRecord(HealthDataRecordEx3.create());
    }

    @Test
    public void deleteRecordsForHealthCode() {
        service.deleteRecordsForHealthCode(TestConstants.HEALTH_CODE);
        verify(mockDao).deleteRecordsForHealthCode(TestConstants.HEALTH_CODE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deleteRecordsForHealthCode_NullHealthCode() {
        service.deleteRecordsForHealthCode(null);
    }

    @Test
    public void getRecord() {
        HealthDataRecordEx3 record = makeValidRecord();
        when(mockDao.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        HealthDataRecordEx3 result = service.getRecord(RECORD_ID).get();
        assertSame(result, record);

        verify(mockDao).getRecord(RECORD_ID);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecord_NullRecordId() {
        service.getRecord(null);
    }

    @Test
    public void getRecordsForHealthCode() {
        List<HealthDataRecordEx3> recordList = ImmutableList.of(makeValidRecord());
        when(mockDao.getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS)).thenReturn(recordList);

        List<HealthDataRecordEx3> resultList = service.getRecordsForHealthCode(TestConstants.HEALTH_CODE,
                CREATED_ON_START, CREATED_ON_END);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForHealthCode_NullHealthCode() {
        service.getRecordsForHealthCode(null, CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForHealthCode_InvalidTimeRange() {
        service.getRecordsForHealthCode(TestConstants.HEALTH_CODE, null, null);
    }

    @Test
    public void getRecordsForApp() {
        List<HealthDataRecordEx3> recordList = ImmutableList.of(makeValidRecord());
        when(mockDao.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_MILLIS, CREATED_ON_END_MILLIS))
                .thenReturn(recordList);

        List<HealthDataRecordEx3> resultList = service.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START,
                CREATED_ON_END);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_MILLIS, CREATED_ON_END_MILLIS);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForApp_NullAppId() {
        service.getRecordsForApp(null, CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForApp_InvalidTimeRange() {
        service.getRecordsForApp(TestConstants.TEST_APP_ID, null, null);
    }

    @Test
    public void getRecordsForAppAndStudy() {
        List<HealthDataRecordEx3> recordList = ImmutableList.of(makeValidRecord());
        when(mockDao.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS)).thenReturn(recordList);

        List<HealthDataRecordEx3> resultList = service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,
                CREATED_ON_START, CREATED_ON_END);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForAppAndStudy_NullAppId() {
        service.getRecordsForAppAndStudy(null, STUDY_ID, CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForAppAndStudy_NullStudyId() {
        service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, null, CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getRecordsForAppAndStudy_InvalidTimeRange() {
        service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,  null, null);
    }

    @Test
    public void validateCreatedOnParameters_Valid() {
        // Does not throw.
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void validateCreatedOnParameters_NullStart() {
        HealthDataEx3Service.validateCreatedOnParameters(null, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void validateCreatedOnParameters_NullEnd() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void validateCreatedOnParameters_StartAndEndSame() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_START);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void validateCreatedOnParameters_StartAfterEnd() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_END, CREATED_ON_START);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void validateCreatedOnParameters_TimeRangeTooLarge() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_START.plusDays(61));
    }

    private static HealthDataRecordEx3 makeValidRecord() {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId(TestConstants.TEST_APP_ID);
        record.setHealthCode(TestConstants.HEALTH_CODE);
        record.setCreatedOn(TestConstants.CREATED_ON.getMillis());
        return record;
    }
}
