package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.HealthDataEx3Dao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.upload.Upload;

@SuppressWarnings("ConstantConditions")
public class HealthDataEx3ServiceTest {
    private static final DateTime CREATED_ON_START = DateTime.parse("2020-07-27T18:48:14.564-0700");
    private static final long CREATED_ON_START_MILLIS = CREATED_ON_START.getMillis();
    private static final DateTime CREATED_ON_END = DateTime.parse("2020-07-29T15:22:58.998-0700");
    private static final long CREATED_ON_END_MILLIS = CREATED_ON_END.getMillis();
    private static final String OFFSET_KEY = "dummy-offset-key";
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";
    private static final String RECORD_BUCKET = "record-bucket";
    private static final String FILE_NAME = "file-name";
    private static final String S3KEY = "test-app/2015-01-26/test-record-file-name";
    private static final int EXPIRATION_IN_MINUTES = 60;

    @Mock
    private HealthDataEx3Dao mockDao;

    @Mock
    AmazonS3 mockS3Client;

    @Mock
    BridgeConfig mockConfig;

    @Mock
    UploadService mockUploadService;

    @InjectMocks
    private HealthDataEx3Service service;

    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        when(mockConfig.getProperty(Exporter3Service.CONFIG_KEY_RAW_HEALTH_DATA_BUCKET)).thenReturn(RECORD_BUCKET);
        service.setConfig(mockConfig);

        when(mockS3Client.generatePresignedUrl(any())).thenAnswer(i -> {
            GeneratePresignedUrlRequest request = i.getArgument(0);
            String filePath = request.getKey();
            return new URL("https://" + RECORD_BUCKET + "/" + filePath);
        });

        DateTimeUtils.setCurrentMillisFixed(TestConstants.TIMESTAMP.getMillis());
    }

    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void createOrUpdateRecord() {
        HealthDataRecordEx3 record = makeValidRecord();
        when(mockDao.createOrUpdateRecord(record)).thenReturn(record);

        HealthDataRecordEx3 result = service.createOrUpdateRecord(record);
        assertSame(result, record);

        verify(mockDao).createOrUpdateRecord(same(record));
    }

    @Test(expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp =
            "Health data record must not be null")
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

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Health code must be specified")
    public void deleteRecordsForHealthCode_NullHealthCode() {
        service.deleteRecordsForHealthCode(null);
    }

    @Test
    public void getRecord_downloadFalse() {
        HealthDataRecordEx3 record = makeValidRecord();
        when(mockDao.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        HealthDataRecordEx3 result = service.getRecord(RECORD_ID, false).get();
        assertSame(result, record);

        verify(mockDao).getRecord(RECORD_ID);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "ID must be specified")
    public void getRecord_NullRecordId() {
        service.getRecord(null, false);
    }

    @Test
    public void getRecord_downloadTrue() {
        String downloadUrl = "https://" + RECORD_BUCKET + "/" + S3KEY;
        HealthDataRecordEx3 record = makeValidRecord();
        when(mockDao.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        Upload upload = makeValidUpload();
        when(mockUploadService.getUpload(RECORD_ID)).thenReturn(upload);

        HealthDataRecordEx3 result = service.getRecord(RECORD_ID, true).get();

        assertEquals(result.getDownloadUrl(), downloadUrl);
        assertEquals(result.getId(), RECORD_ID);
        assertEquals(result.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(result.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(result.getCreatedOn(), new Long(TestConstants.CREATED_ON.getMillis()));
        assertEquals(result.getDownloadExpiration(), DateTime.now().plusMinutes(EXPIRATION_IN_MINUTES).getMillis());

        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), RECORD_BUCKET);
        assertEquals(request.getMethod(), HttpMethod.GET);
        assertEquals(request.getKey(), S3KEY);
        assertEquals(request.getExpiration(), DateTime.now().plusMinutes(EXPIRATION_IN_MINUTES).toDate());
    }

    @Test
    public void getRecord_emptyRecord() {
        when(mockDao.getRecord(RECORD_ID)).thenReturn(Optional.empty());
        Optional<HealthDataRecordEx3> result = service.getRecord(RECORD_ID, true);
        assertEquals(result, Optional.empty());

        verify(mockDao).getRecord(RECORD_ID);
    }

    @Test
    public void getRecordsForHealthCode() {
        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(makeValidRecord()), null);
        when(mockDao.getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        ForwardCursorPagedResourceList<HealthDataRecordEx3> resultList = service.getRecordsForHealthCode(
                TestConstants.HEALTH_CODE, CREATED_ON_START, CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                OFFSET_KEY);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Health code must be specified")
    public void getRecordsForHealthCode_NullHealthCode() {
        service.getRecordsForHealthCode(null, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be specified")
    public void getRecordsForHealthCode_InvalidTimeRange() {
        service.getRecordsForHealthCode(TestConstants.HEALTH_CODE, null, null,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void getRecordsForHealthCode_InvalidPageSize() {
        service.getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START, CREATED_ON_END, -1,
                OFFSET_KEY);
    }

    @Test
    public void getRecordsForApp() {
        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(makeValidRecord()), null);
        when(mockDao.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_MILLIS, CREATED_ON_END_MILLIS,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        ForwardCursorPagedResourceList<HealthDataRecordEx3> resultList = service.getRecordsForApp(
                TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                OFFSET_KEY);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_MILLIS, CREATED_ON_END_MILLIS,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "App ID must be specified")
    public void getRecordsForApp_NullAppId() {
        service.getRecordsForApp(null, CREATED_ON_START, CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be specified")
    public void getRecordsForApp_InvalidTimeRange() {
        service.getRecordsForApp(TestConstants.TEST_APP_ID, null, null,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void getRecordsForApp_InvalidPageSize() {
        service.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END, -1, OFFSET_KEY);
    }

    @Test
    public void getRecordsForAppAndStudy() {
        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(makeValidRecord()), null);
        when(mockDao.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        ForwardCursorPagedResourceList<HealthDataRecordEx3> resultList = service.getRecordsForAppAndStudy(
                TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
        assertSame(resultList, recordList);

        verify(mockDao).getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START_MILLIS,
                CREATED_ON_END_MILLIS, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "App ID must be specified")
    public void getRecordsForAppAndStudy_NullAppId() {
        service.getRecordsForAppAndStudy(null, STUDY_ID, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Study ID must be specified")
    public void getRecordsForAppAndStudy_NullStudyId() {
        service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, null, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be specified")
    public void getRecordsForAppAndStudy_InvalidTimeRange() {
        service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,  null, null,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void getRecordsForAppAndStudy_InvalidPageSize() {
        service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START, CREATED_ON_END,
                -1, OFFSET_KEY);
    }

    @Test
    public void validateCreatedOnParameters_Valid() {
        // Does not throw.
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be specified")
    public void validateCreatedOnParameters_NullStart() {
        HealthDataEx3Service.validateCreatedOnParameters(null, CREATED_ON_END);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "End time must be specified")
    public void validateCreatedOnParameters_NullEnd() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, null);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be before end time")
    public void validateCreatedOnParameters_StartAndEndSame() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_START);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Start time must be before end time")
    public void validateCreatedOnParameters_StartAfterEnd() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_END, CREATED_ON_START);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Maximum time range is " + HealthDataEx3Service.MAX_DATE_RANGE_DAYS + " days")
    public void validateCreatedOnParameters_TimeRangeTooLarge() {
        HealthDataEx3Service.validateCreatedOnParameters(CREATED_ON_START, CREATED_ON_START.plusDays(61));
    }

    @Test
    public void validatePageSize_defaultPageSize() {
        int result = HealthDataEx3Service.validatePageSize(null);
        assertEquals(result, BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void validatePageSize_negativePageSize() {
        HealthDataEx3Service.validatePageSize(-1);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void validatePageSize_zeroPageSize() {
        HealthDataEx3Service.validatePageSize(0);
    }

    @Test
    public void validatePageSize_onePageSizeOkay() {
        int result = HealthDataEx3Service.validatePageSize(1);
        assertEquals(result, 1);
    }

    @Test
    public void validatePageSize_maxPageSizeOkay() {
        int result = HealthDataEx3Service.validatePageSize(BridgeConstants.API_MAXIMUM_PAGE_SIZE);
        assertEquals(result, BridgeConstants.API_MAXIMUM_PAGE_SIZE);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Page size must be between 1 and " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void validatePageSize_pageSizeTooLarge() {
        HealthDataEx3Service.validatePageSize(BridgeConstants.API_MAXIMUM_PAGE_SIZE+1);
    }

    private static HealthDataRecordEx3 makeValidRecord() {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId(TestConstants.TEST_APP_ID);
        record.setHealthCode(TestConstants.HEALTH_CODE);
        record.setCreatedOn(TestConstants.CREATED_ON.getMillis());
        record.setId(RECORD_ID);
        return record;
    }

    private static Upload makeValidUpload() {
        Upload upload = Upload.create();
        upload.setAppId(TestConstants.TEST_APP_ID);
        upload.setHealthCode(TestConstants.HEALTH_CODE);
        upload.setUploadId(RECORD_ID);
        upload.setFilename(FILE_NAME);

        return upload;
    }
}
