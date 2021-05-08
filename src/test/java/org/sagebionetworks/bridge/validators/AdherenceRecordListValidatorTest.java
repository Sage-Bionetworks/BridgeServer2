package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.CLIENT_TIME_ZONE_FIELD;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.EVENT_TIMESTAMP_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.INSTANCE_GUID_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.STARTED_ON_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.STUDY_ID_FIELD;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.USER_ID_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.TIME_ZONE_ERROR;

import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;

public class AdherenceRecordListValidatorTest extends Mockito {

    @Test
    public void valid() {
        AdherenceRecord record = TestUtils.getAdherenceRecord(GUID);
        Validate.entityThrowingException(INSTANCE, asList(record));
    }
    
    @Test
    public void userIdBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(" ");
        assertValidatorMessage(INSTANCE, asList(record), asField(USER_ID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void userIdNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(null);
        assertValidatorMessage(INSTANCE, asList(record), asField(USER_ID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStudyId(" ");
        assertValidatorMessage(INSTANCE, asList(record), asField(STUDY_ID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStudyId(null);
        assertValidatorMessage(INSTANCE, asList(record), asField(STUDY_ID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void instanceGuidBlank() {
        AdherenceRecord record = new AdherenceRecord();
        record.setInstanceGuid(" ");
        assertValidatorMessage(INSTANCE, asList(record), asField(INSTANCE_GUID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void instanceGuidNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setInstanceGuid(null);
        assertValidatorMessage(INSTANCE, asList(record), asField(INSTANCE_GUID_FIELD), CANNOT_BE_BLANK);
    }
    
    @Test
    public void eventTimestampNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setEventTimestamp(null);
        assertValidatorMessage(INSTANCE, asList(record), asField(EVENT_TIMESTAMP_FIELD), CANNOT_BE_NULL);
    }
    
    @Test
    public void startedOnNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStartedOn(null);
        assertValidatorMessage(INSTANCE, asList(record), asField(STARTED_ON_FIELD), CANNOT_BE_NULL);
    }
    
    @Test
    public void clientTimeZoneInvalid() {
        AdherenceRecord record = new AdherenceRecord();
        record.setClientTimeZone("East Coast/Arkam");
        assertValidatorMessage(INSTANCE, asList(record), asField(CLIENT_TIME_ZONE_FIELD), TIME_ZONE_ERROR);
    }
    
    @Test
    public void clientTimeZoneNullOK() {
        AdherenceRecord record = TestUtils.getAdherenceRecord(GUID);
        record.setClientTimeZone(null);
        Validate.entityThrowingException(INSTANCE, asList(record));
    }
    
    @Test
    public void validatesMultiple() {
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord(GUID);
        rec1.setStartedOn(null);
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord(GUID);
        rec2.setStudyId(null);
        AdherenceRecordList list = asList(rec1, rec2);
        
        assertValidatorMessage(INSTANCE, list, 
                asField(STARTED_ON_FIELD), CANNOT_BE_NULL);
        assertValidatorMessage(INSTANCE, list, 
                "records[1]."+STUDY_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    private AdherenceRecordList asList(AdherenceRecord... records) {
        return new AdherenceRecordList(ImmutableList.copyOf(records));
    }
    
    private String asField(String fieldName) {
        return "records[0]."+fieldName;
    }
}
