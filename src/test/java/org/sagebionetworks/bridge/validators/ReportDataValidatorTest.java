package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

public class ReportDataValidatorTest {
    
    private ReportDataValidator validator;
    
    @BeforeMethod
    public void before() { 
        validator = new ReportDataValidator(null);
    }

    @Test
    public void validWorks() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        Validate.entityThrowingException(validator, data);
    }
    
    @Test
    public void cannotHaveTwoDates() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        try {
            Validate.entityThrowingException(validator, data);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ReportData must include a localDate or dateTime, but not both"));
        }
    }
    
    @Test
    public void mustHaveOneDate() {
        ReportData data = ReportData.create();
        data.setData(TestUtils.getClientData());
        try {
            Validate.entityThrowingException(validator, data);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ReportData must include a localDate or dateTime"));
        }
    }
    
    @Test
    public void keyIsRequired() {
        ReportData data = ReportData.create();
        data.setData(TestUtils.getClientData());
        
        TestUtils.assertValidatorMessage(validator, data, "key", "is required");
    }
    
    @Test
    public void dataIsRequired() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setDateTime(DateTime.parse("2017-09-06T10:10:10.000Z"));
        
        TestUtils.assertValidatorMessage(validator, data, "data", "is required");
    }
    
    @Test
    public void keyIsValidated() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        
        TestUtils.assertValidatorMessage(validator, data, "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void existingIndexNoStudyChangeOK() {
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(TestConstants.USER_STUDY_IDS);

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        data.setStudyIds(TestConstants.USER_STUDY_IDS);
        
        validator = new ReportDataValidator(index);
        
        Validate.entityThrowingException(validator, data);
    }
    
    @Test
    public void existingIndexChangedStudiesInvalid() {
        ReportIndex index = ReportIndex.create();
        index.setStudyIds(TestConstants.USER_STUDY_IDS);

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier("foo")
                .withAppId(TEST_APP_ID).build();
        
        ReportData data = ReportData.create();
        data.setReportDataKey(key);
        data.setData(TestUtils.getClientData());
        data.setLocalDate(LocalDate.parse("2017-09-06"));
        data.setStudyIds(ImmutableSet.of("studyA", "studyC"));
        
        validator = new ReportDataValidator(index);
        
        TestUtils.assertValidatorMessage(validator, data, "studyIds", "cannot be changed once created for a report");
    }
}
