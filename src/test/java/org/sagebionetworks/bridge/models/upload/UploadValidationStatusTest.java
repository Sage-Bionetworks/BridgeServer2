package org.sagebionetworks.bridge.models.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.springframework.validation.MapBindingResult;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.validators.UploadValidationStatusValidator;

@SuppressWarnings({ "ConstantConditions", "unchecked" })
public class UploadValidationStatusTest {
    @Test
    public void builder() {
        UploadValidationStatus status = new UploadValidationStatus.Builder().withId("test-upload")
                .withMessageList(Collections.<String>emptyList()).withStatus(UploadStatus.SUCCEEDED).build();
        assertEquals(status.getId(), "test-upload");
        assertTrue(status.getMessageList().isEmpty());
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);
        assertNull(status.getRecord());
    }

    @Test
    public void withOptionalValues() {
        HealthDataRecord dummyRecord = HealthDataRecord.create();

        UploadValidationStatus status = new UploadValidationStatus.Builder().withId("happy-case-2")
                .withMessageList(ImmutableList.of("foo", "bar", "baz")).withStatus(UploadStatus.VALIDATION_FAILED)
                .withRecord(dummyRecord).build();
        assertEquals(status.getId(), "happy-case-2");
        assertEquals(status.getStatus(), UploadStatus.VALIDATION_FAILED);
        assertSame(status.getRecord(), dummyRecord);

        List<String> messageList = status.getMessageList();
        assertEquals(messageList.size(), 3);
        assertEquals(messageList.get(0), "foo");
        assertEquals(messageList.get(1), "bar");
        assertEquals(messageList.get(2), "baz");
    }

    @Test
    public void fromUpload() {
        // make upload
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("from-upload");
        upload2.appendValidationMessages(Collections.singletonList("foo"));
        upload2.setStatus(UploadStatus.SUCCEEDED);

        // construct and validate
        UploadValidationStatus status = UploadValidationStatus.from(upload2, null);
        assertEquals(status.getId(), "from-upload");
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);
        assertNull(status.getRecord());

        assertEquals(status.getMessageList().size(), 1);
        assertEquals(status.getMessageList().get(0), "foo");
    }

    @Test
    public void fromUploadWithRecord() {
        // make upload
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("from-upload-with-record");
        upload2.appendValidationMessages(Collections.singletonList("hasRecord"));
        upload2.setStatus(UploadStatus.SUCCEEDED);

        HealthDataRecord dummyRecord = HealthDataRecord.create();

        // construct and validate
        UploadValidationStatus status = UploadValidationStatus.from(upload2, dummyRecord);
        assertEquals(status.getId(), "from-upload-with-record");
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);
        assertSame(status.getRecord(), dummyRecord);

        assertEquals(status.getMessageList().size(), 1);
        assertEquals(status.getMessageList().get(0), "hasRecord");
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void fromNullUpload() {
        UploadValidationStatus.from(null, null);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullMessageList() {
        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(null)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void messageListWithNullString() {
        List<String> list = new ArrayList<>();
        list.add(null);

        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(list)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void messageListWithEmptyString() {
        List<String> list = new ArrayList<>();
        list.add("");

        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(list)
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullId() {
        new UploadValidationStatus.Builder().withId(null).withMessageList(Collections.singletonList("foo"))
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void emptyId() {
        new UploadValidationStatus.Builder().withId("").withMessageList(Collections.singletonList("foo"))
                .withStatus(UploadStatus.SUCCEEDED).build();
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullStatus() {
        new UploadValidationStatus.Builder().withId("test-upload").withMessageList(Collections.singletonList("foo"))
                .withStatus(null).build();
    }

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(UploadValidationStatusValidator.INSTANCE.supports(UploadValidationStatus.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportWrongClass() {
        assertFalse(UploadValidationStatusValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "UploadValidationStatus");
        UploadValidationStatusValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "UploadValidationStatus");
        UploadValidationStatusValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void serialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"json-upload\",\n" +
                "   \"status\":\"SUCCEEDED\",\n" +
                "   \"messageList\":[\n" +
                "       \"foo\",\n" +
                "       \"bar\",\n" +
                "       \"baz\"\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        UploadValidationStatus status = BridgeObjectMapper.get().readValue(jsonText, UploadValidationStatus.class);
        assertEquals(status.getId(), "json-upload");
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);

        List<String> messageList = status.getMessageList();
        assertEquals(messageList.size(), 3);
        assertEquals(messageList.get(0), "foo");
        assertEquals(messageList.get(1), "bar");
        assertEquals(messageList.get(2), "baz");

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(status);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 4);
        assertEquals(jsonMap.get("type"), "UploadValidationStatus");
        assertEquals(jsonMap.get("id"), "json-upload");
        assertEquals(jsonMap.get("status"), "succeeded");

        List<String> messageJsonList = (List<String>) jsonMap.get("messageList");
        assertEquals(messageJsonList.size(), 3);
        assertEquals(messageJsonList.get(0), "foo");
        assertEquals(messageJsonList.get(1), "bar");
        assertEquals(messageJsonList.get(2), "baz");
    }
}
