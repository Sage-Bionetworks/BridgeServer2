package org.sagebionetworks.bridge.models.healthdata;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.IntNode;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.validation.MapBindingResult;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.validators.HealthDataRecordValidator;
import org.sagebionetworks.bridge.validators.Validate;

@SuppressWarnings("unchecked")
public class HealthDataRecordTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final long CREATED_ON_MILLIS = 1502498010000L;
    private static final JsonNode DUMMY_DATA = BridgeObjectMapper.get().createObjectNode();
    private static final JsonNode DUMMY_METADATA = BridgeObjectMapper.get().createObjectNode();
    private static final JsonNode DUMMY_USER_METADATA = BridgeObjectMapper.get().createObjectNode();
    private static final String PHONE_INFO = "Unit Tests";
    private static final LocalDate UPLOAD_DATE = LocalDate.parse("2017-08-11");

    @Test
    public void normalCase() {
        // build
        HealthDataRecord record = makeValidRecord();

        // validate
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        assertEquals(record.getCreatedOn().longValue(), CREATED_ON_MILLIS);
        assertSame(record.getData(), DUMMY_DATA);
        assertEquals(record.getHealthCode(), "dummy healthcode");
        assertNull(record.getId());
        assertSame(record.getMetadata(), DUMMY_METADATA);
        assertEquals(record.getAppId(), "dummy app");
        assertEquals(record.getUploadDate(), UPLOAD_DATE);
        assertEquals(record.getUserDataGroups(), TestConstants.USER_DATA_GROUPS);
        assertEquals(record.getUserSharingScope(), SharingScope.NO_SHARING);
    }

    @Test
    public void optionalValues() {
        // optional values
        long uploadedOn = 1462575525894L;

        // build
        HealthDataRecord record = makeValidRecord();
        record.setId("optional record ID");
        record.setAppVersion(APP_VERSION);
        record.setCreatedOnTimeZone("+0900");
        record.setDayInStudy(42);
        record.setPhoneInfo(PHONE_INFO);
        record.setRawDataAttachmentId("raw.zip");
        record.setSchemaId("dummy schema");
        record.setSchemaRevision(3);
        record.setSynapseExporterStatus(HealthDataRecord.ExporterStatus.NOT_EXPORTED);
        record.setUploadId("optional upload ID");
        record.setUploadedOn(uploadedOn);
        record.setUserExternalId("optional external ID");
        record.setUserMetadata(DUMMY_USER_METADATA);
        record.setValidationErrors("dummy validation errors");
        record.setVersion(42L);

        // validate
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);

        assertEquals(record.getId(), "optional record ID");
        assertEquals(record.getAppVersion(), APP_VERSION);
        assertEquals(record.getCreatedOnTimeZone(), "+0900");
        assertEquals(record.getDayInStudy().intValue(), 42);
        assertEquals(record.getPhoneInfo(), PHONE_INFO);
        assertEquals(record.getRawDataAttachmentId(), "raw.zip");
        assertEquals(record.getSchemaId(), "dummy schema");
        assertEquals(record.getSchemaRevision().intValue(), 3);
        assertEquals(record.getSynapseExporterStatus(), HealthDataRecord.ExporterStatus.NOT_EXPORTED);
        assertEquals(record.getUploadId(), "optional upload ID");
        assertEquals(record.getUploadedOn().longValue(), uploadedOn);
        assertEquals(record.getUserExternalId(), "optional external ID");
        assertSame(record.getUserMetadata(), DUMMY_USER_METADATA);
        assertEquals(record.getValidationErrors(), "dummy validation errors");
        assertEquals(record.getVersion().longValue(), 42);
    }
    
    @Test
    public void emptyDataGroupSetConvertedToNull() {
        HealthDataRecord record = makeValidRecord();
        record.setUserDataGroups(new HashSet<>());
        assertNull(record.getUserDataGroups());
    }
    
    @Test
    public void emptyStudyMembershipsConvertedToNull() {
        HealthDataRecord record = makeValidRecord();
        record.setUserStudyMemberships(ImmutableMap.of());
        assertNull(record.getUserStudyMemberships());
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void jsonNullData() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("null");
        HealthDataRecord record = makeValidRecord();
        record.setData(data);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void dataIsNotMap() throws Exception {
        JsonNode data = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        HealthDataRecord record = makeValidRecord();
        record.setData(data);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void validatorWithNullData() {
        HealthDataRecord record = makeValidRecord();
        record.setData(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullHealthCode() {
        HealthDataRecord record = makeValidRecord();
        record.setHealthCode(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void emptyHealthCode() {
        HealthDataRecord record = makeValidRecord();
        record.setHealthCode("");
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void emptyId() {
        HealthDataRecord record = makeValidRecord();
        record.setId("");
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void validatorWithNullCreatedOn() {
        HealthDataRecord record = makeValidRecord();
        record.setCreatedOn(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void jsonNullMetadata() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("null");
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(metadata);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void metadataIsNotMap() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("\"This is not a map.\"");
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(metadata);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void validatorWithNullMetadata() {
        HealthDataRecord record = makeValidRecord();
        record.setMetadata(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test
    public void emptySchemaId() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaId("");
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "schemaId",
                "can't be blank if specified");
    }

    @Test
    public void blankSchemaId() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaId("   ");
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "schemaId",
                "can't be blank if specified");
    }

    @Test
    public void zeroSchemaRevision() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaRevision(0);
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "schemaRevision",
                "can't be zero or negative");
    }

    @Test
    public void negativeSchemaRevision() {
        HealthDataRecord record = makeValidRecord();
        record.setSchemaRevision(-1);
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "schemaRevision",
                "can't be zero or negative");
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void validatorWithNullUploadDate() {
        HealthDataRecord record = makeValidRecord();
        record.setUploadDate(null);
        Validate.entityThrowingException(HealthDataRecordValidator.INSTANCE, record);
    }

    @Test
    public void validateUserMetadataNotAnObject() {
        HealthDataRecord record = makeValidRecord();
        record.setUserMetadata(IntNode.valueOf(42));
        assertValidatorMessage(HealthDataRecordValidator.INSTANCE, record, "userMetadata", "must be an object node");
    }

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(HealthDataRecordValidator.INSTANCE.supports(HealthDataRecord.class));
    }

    // branch coverage
    @Test
    public void validatorSupportsSubclass() {
        assertTrue(HealthDataRecordValidator.INSTANCE.supports(DynamoHealthDataRecord.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportWrongClass() {
        assertFalse(HealthDataRecordValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "HealthDataRecord");
        HealthDataRecordValidator.INSTANCE.validate("This is not a HealthDataRecord", errors);
        assertTrue(errors.hasErrors());
    }

    private static HealthDataRecord makeValidRecord() {
        HealthDataRecord record = HealthDataRecord.create();
        record.setCreatedOn(CREATED_ON_MILLIS);
        record.setData(DUMMY_DATA);
        record.setHealthCode("dummy healthcode");
        record.setMetadata(DUMMY_METADATA);
        record.setAppId("dummy app");
        record.setUploadDate(UPLOAD_DATE);
        record.setUserDataGroups(TestConstants.USER_DATA_GROUPS);
        record.setUserSharingScope(SharingScope.NO_SHARING);
        return record;
    }

    @Test
    public void testSerialization() throws Exception {
        String uploadedOnStr = "2016-05-06T16:01:22.307-0700";
        long uploadedOn = DateTime.parse(uploadedOnStr).getMillis();

        // start with JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"2014-02-12T13:45-0800\",\n" +
                "   \"createdOnTimeZone\":\"-0800\",\n" +
                "   \"data\":{\"myData\":\"myDataValue\"},\n" +
                "   \"dayInStudy\":42,\n" +
                "   \"healthCode\":\"json healthcode\",\n" +
                "   \"id\":\"json record ID\",\n" +
                "   \"metadata\":{\"myMetadata\":\"myMetaValue\"},\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"rawDataAttachmentId\":\"raw.zip\",\n" +
                "   \"schemaId\":\"json schema\",\n" +
                "   \"schemaRevision\":3,\n" +
                "   \"appId\":\""+TEST_APP_ID+"\",\n" +
                "   \"synapseExporterStatus\":\"not_exported\",\n" +
                "   \"uploadDate\":\"2014-02-12\",\n" +
                "   \"uploadId\":\"json upload\",\n" +
                "   \"uploadedOn\":\"" + uploadedOnStr + "\",\n" +
                "   \"userMetadata\":{\"userMetadata\":\"userMetaValue\"},\n" +
                "   \"userSharingScope\":\"all_qualified_researchers\",\n" +
                "   \"userExternalId\":\"ABC-123-XYZ\",\n" +
                "   \"validationErrors\":\"dummy validation errors\",\n" +
                "   \"version\":42,\n" +
                "   \"dayInEachStudy\":{\"studyA\":10,\"studyB\":5}" +
                "}";
        long measuredTimeMillis = new DateTime(2014, 2, 12, 13, 45, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        // convert to POJO
        HealthDataRecord record = BridgeObjectMapper.get().readValue(jsonText, HealthDataRecord.class);
        assertEquals(record.getAppVersion(), APP_VERSION);
        assertEquals(record.getCreatedOn().longValue(), measuredTimeMillis);
        assertEquals(record.getCreatedOnTimeZone(), "-0800");
        assertEquals(record.getDayInStudy().intValue(), 42);
        assertEquals(record.getHealthCode(), "json healthcode");
        assertEquals(record.getId(), "json record ID");
        assertEquals(record.getPhoneInfo(), PHONE_INFO);
        assertEquals(record.getRawDataAttachmentId(), "raw.zip");
        assertEquals(record.getSchemaId(), "json schema");
        assertEquals(record.getSchemaRevision().intValue(), 3);
        assertEquals(record.getAppId(), TEST_APP_ID);
        assertEquals(record.getSynapseExporterStatus(), HealthDataRecord.ExporterStatus.NOT_EXPORTED);
        assertEquals(record.getUploadDate().toString(ISODateTimeFormat.date()), "2014-02-12");
        assertEquals(record.getUploadId(), "json upload");
        assertEquals(record.getUploadedOn().longValue(), uploadedOn);
        assertEquals(record.getUserSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(record.getUserExternalId(), "ABC-123-XYZ");
        assertEquals(record.getValidationErrors(), "dummy validation errors");
        assertEquals(record.getVersion().longValue(), 42);

        assertEquals(record.getData().size(), 1);
        assertEquals(record.getData().get("myData").textValue(), "myDataValue");

        assertEquals(record.getMetadata().size(), 1);
        assertEquals(record.getMetadata().get("myMetadata").textValue(), "myMetaValue");

        assertEquals(record.getUserMetadata().size(), 1);
        assertEquals(record.getUserMetadata().get("userMetadata").textValue(), "userMetaValue");
        
        assertEquals(record.getDayInEachStudy().get("studyA"), Integer.valueOf(10));
        assertEquals(record.getDayInEachStudy().get("studyB"), Integer.valueOf(5));

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(record);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 25);
        assertEquals(jsonMap.get("appVersion"), APP_VERSION);
        assertEquals(jsonMap.get("createdOnTimeZone"), "-0800");
        assertEquals(jsonMap.get("dayInStudy"), 42);
        assertEquals(jsonMap.get("healthCode"), "json healthcode");
        assertEquals(jsonMap.get("id"), "json record ID");
        assertEquals(jsonMap.get("phoneInfo"), PHONE_INFO);
        assertEquals(jsonMap.get("rawDataAttachmentId"), "raw.zip");
        assertEquals(jsonMap.get("schemaId"), "json schema");
        assertEquals(jsonMap.get("schemaRevision"), 3);
        assertEquals(jsonMap.get("studyId"), TEST_APP_ID);
        assertEquals(jsonMap.get("appId"), TEST_APP_ID);
        assertEquals(jsonMap.get("synapseExporterStatus"), "not_exported");
        assertEquals(jsonMap.get("uploadDate"), "2014-02-12");
        assertEquals(jsonMap.get("uploadId"), "json upload");
        assertEquals(DateTime.parse((String) jsonMap.get("uploadedOn")).getMillis(), uploadedOn);
        assertEquals(jsonMap.get("userSharingScope"), "all_qualified_researchers");
        assertEquals(jsonMap.get("userExternalId"), "ABC-123-XYZ");
        assertEquals(jsonMap.get("validationErrors"), "dummy validation errors");
        assertEquals(jsonMap.get("version"), 42);
        assertEquals(jsonMap.get("dayInEachStudy").toString(), "{studyA=10, studyB=5}");
        assertEquals(jsonMap.get("type"), "HealthData");

        DateTime convertedMeasuredTime = DateTime.parse((String) jsonMap.get("createdOn"));
        assertEquals(convertedMeasuredTime.getMillis(), measuredTimeMillis);

        Map<String, String> data = (Map<String, String>) jsonMap.get("data");
        assertEquals(data.size(), 1);
        assertEquals(data.get("myData"), "myDataValue");

        Map<String, String> metadata = (Map<String, String>) jsonMap.get("metadata");
        assertEquals(metadata.size(), 1);
        assertEquals(metadata.get("myMetadata"), "myMetaValue");

        Map<String, String> userMetadata = (Map<String, String>) jsonMap.get("userMetadata");
        assertEquals(userMetadata.size(), 1);
        assertEquals(userMetadata.get("userMetadata"), "userMetaValue");

        // convert back to JSON with PUBLIC_RECORD_WRITER
        String publicJson = HealthDataRecord.PUBLIC_RECORD_WRITER.writeValueAsString(record);

        // Convert back to map again. Only validate a few key fields are present and the filtered fields are absent.
        Map<String, Object> publicJsonMap = BridgeObjectMapper.get().readValue(publicJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(publicJsonMap.size(), 24);
        assertFalse(publicJsonMap.containsKey("healthCode"));
        assertEquals(publicJsonMap.get("id"), "json record ID");
    }

    @Test
    public void testTimeZoneFormatter() {
        testTimeZoneFormatter("+0000", DateTimeZone.UTC);
        testTimeZoneFormatter("+0000", DateTimeZone.forOffsetHours(0));
        testTimeZoneFormatter("+0900", DateTimeZone.forOffsetHours(+9));
        testTimeZoneFormatter("-0800", DateTimeZone.forOffsetHours(-8));
        testTimeZoneFormatter("+1345", DateTimeZone.forOffsetHoursMinutes(+13, +45));
        testTimeZoneFormatter("-0330", DateTimeZone.forOffsetHoursMinutes(-3, -30));

        testTimeZoneFormatterForString("+0000", "Z");
        testTimeZoneFormatterForString("+0000", "+00:00");
        testTimeZoneFormatterForString("+0900", "+09:00");
        testTimeZoneFormatterForString("-0800", "-08:00");
        testTimeZoneFormatterForString("+1345", "+13:45");
        testTimeZoneFormatterForString("-0330", "-03:30");
    }

    private static void testTimeZoneFormatter(String expected, DateTimeZone timeZone) {
        // The formatter only takes in DateTimes, not TimeZones. To test this, create a dummy DateTime with the given
        // TimeZone
        DateTime dateTime = new DateTime(2017, 1, 25, 2, 29, timeZone);
        assertEquals(HealthDataRecord.TIME_ZONE_FORMATTER.print(dateTime), expected);
    }

    private static void testTimeZoneFormatterForString(String expected, String timeZoneStr) {
        // DateTimeZone doesn't have an API to parse an ISO timezone representation, so we have to parse an entire date
        // just to parse the timezone.
        DateTime dateTime = DateTime.parse("2017-01-25T02:29" + timeZoneStr);
        assertEquals(HealthDataRecord.TIME_ZONE_FORMATTER.print(dateTime), expected);
    }
}
