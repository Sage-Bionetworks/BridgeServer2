package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.CLIENT_TIME_ZONE_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.EVENT_TIMESTAMP_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.INSTANCE_GUID_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.STARTED_ON_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.STUDY_ID_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.TIME_ZONE_ERROR;
import static org.sagebionetworks.bridge.validators.AdherenceRecordValidator.USER_ID_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;

public class AdherenceRecordValidatorTest extends Mockito {

    @Test
    public void valid() {
        AdherenceRecord record = createRecord();
        Validate.entityThrowingException(INSTANCE, record);
    }
    
    @Test
    public void userIdBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(" ");
        assertValidatorMessage(INSTANCE, record, USER_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void userIdNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(null);
        assertValidatorMessage(INSTANCE, record, USER_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStudyId(" ");
        assertValidatorMessage(INSTANCE, record, STUDY_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStudyId(null);
        assertValidatorMessage(INSTANCE, record, STUDY_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void instanceGuidBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setInstanceGuid(" ");
        assertValidatorMessage(INSTANCE, record, INSTANCE_GUID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void instanceGuidNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setInstanceGuid(null);
        assertValidatorMessage(INSTANCE, record, INSTANCE_GUID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void eventTimestampNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setEventTimestamp(null);
        assertValidatorMessage(INSTANCE, record, EVENT_TIMESTAMP_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void startedOnNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStartedOn(null);
        assertValidatorMessage(INSTANCE, record, STARTED_ON_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void clientTimeZoneInvalid() {
        AdherenceRecord record = new AdherenceRecord();
        record.setClientTimeZone("East Coast/Arkam");
        assertValidatorMessage(INSTANCE, record, CLIENT_TIME_ZONE_FIELD, TIME_ZONE_ERROR);
    }
    
    @Test
    public void clientTimeZoneNullOK() {
        AdherenceRecord record = createRecord();
        record.setClientTimeZone(null);
        Validate.entityThrowingException(INSTANCE, record);
    }
    
    private AdherenceRecord createRecord() { 
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(TEST_USER_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setInstanceGuid(GUID);
        record.setEventTimestamp(CREATED_ON);
        record.setStartedOn(MODIFIED_ON);
        record.setClientTimeZone("America/Los_Angeles");
        return record;
    }
}
