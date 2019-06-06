package org.sagebionetworks.bridge.models.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

@SuppressWarnings("ConstantConditions")
public class UploadFieldDefinitionTest {
    @Test
    public void testBuilder() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals(fieldDef.getName(), "test-field");
        assertTrue(fieldDef.isRequired());
        assertEquals(fieldDef.getType(), UploadFieldType.ATTACHMENT_BLOB);
    }

    @Test
    public void testRequiredTrue() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("test-field")
                .withRequired(true).withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals(fieldDef.getName(), "test-field");
        assertTrue(fieldDef.isRequired());
        assertEquals(fieldDef.getType(), UploadFieldType.ATTACHMENT_BLOB);
    }

    @Test
    public void testRequiredFalse() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("test-field")
                .withRequired(false).withType(UploadFieldType.ATTACHMENT_BLOB).build();
        assertEquals(fieldDef.getName(), "test-field");
        assertFalse(fieldDef.isRequired());
        assertEquals(fieldDef.getType(), UploadFieldType.ATTACHMENT_BLOB);
    }

    @Test
    public void testMultiChoiceAnswerList() {
        // set up original list
        List<String> originalAnswerList = new ArrayList<>();
        originalAnswerList.add("first");
        originalAnswerList.add("second");

        // build and validate
        List<String> expectedAnswerList = ImmutableList.of("first", "second");
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("multi-choice-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList(originalAnswerList).build();
        assertEquals(fieldDef.getMultiChoiceAnswerList(), expectedAnswerList);

        // modify original list, verify that field def stays the same
        originalAnswerList.add("third");
        assertEquals(fieldDef.getMultiChoiceAnswerList(), expectedAnswerList);
    }

    @Test
    public void testMultiChoiceAnswerVarargs() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("multi-choice-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("aa", "bb", "cc").build();
        assertEquals(fieldDef.getMultiChoiceAnswerList(), ImmutableList.of("aa", "bb", "cc"));
    }

    @Test
    public void testMultiChoiceAnswerListNull() {
        // Fields that are not multi_choice usually won't specify this, but we expect it to be, by default, an empty
        // list instead of a null list.
        UploadFieldDefinition fieldDef1 = new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.INT).build();
        assertTrue(fieldDef1.getMultiChoiceAnswerList().isEmpty());

        // Explicitly set it to null, and it still comes up as an empty list.
        UploadFieldDefinition fieldDef2 = new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.INT).withMultiChoiceAnswerList((List<String>) null).build();
        assertTrue(fieldDef2.getMultiChoiceAnswerList().isEmpty());
    }

    @Test
    public void testOptionalFields() {
        List<String> multiChoiceAnswerList = ImmutableList.of("foo", "bar", "baz");

        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withAllowOtherChoices(true)
                .withFileExtension(".test").withMimeType("text/plain")
                .withMaxLength(128).withMultiChoiceAnswerList(multiChoiceAnswerList).withName("optional-stuff")
                .withRequired(false).withType(UploadFieldType.STRING).withUnboundedText(true).build();
        assertTrue(fieldDef.getAllowOtherChoices());
        assertEquals(fieldDef.getFileExtension(), ".test");
        assertEquals(fieldDef.getMimeType(), "text/plain");
        assertEquals(fieldDef.getMaxLength().intValue(), 128);
        assertEquals(fieldDef.getMultiChoiceAnswerList(), multiChoiceAnswerList);
        assertEquals(fieldDef.getName(), "optional-stuff");
        assertFalse(fieldDef.isRequired());
        assertEquals(fieldDef.getType(), UploadFieldType.STRING);
        assertTrue(fieldDef.isUnboundedText());

        // Also test copy constructor.
        UploadFieldDefinition copy = new UploadFieldDefinition.Builder().copyOf(fieldDef).build();
        assertEquals(copy, fieldDef);
    }

    @Test
    public void testSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"allowOtherChoices\":true,\n" +
                "   \"fileExtension\":\".json\",\n" +
                "   \"mimeType\":\"text/json\",\n" +
                "   \"maxLength\":24,\n" +
                "   \"multiChoiceAnswerList\":[\"asdf\", \"jkl\"],\n" +
                "   \"name\":\"test-field\",\n" +
                "   \"required\":false,\n" +
                "   \"type\":\"INT\",\n" +
                "   \"unboundedText\":true\n" +
                "}";

        // convert to POJO
        List<String> expectedMultiChoiceAnswerList = ImmutableList.of("asdf", "jkl");
        UploadFieldDefinition fieldDef = BridgeObjectMapper.get().readValue(jsonText, UploadFieldDefinition.class);
        assertTrue(fieldDef.getAllowOtherChoices());
        assertEquals(fieldDef.getFileExtension(), ".json");
        assertEquals(fieldDef.getMimeType(), "text/json");
        assertEquals(fieldDef.getMaxLength().intValue(), 24);
        assertEquals(fieldDef.getMultiChoiceAnswerList(), expectedMultiChoiceAnswerList);
        assertEquals(fieldDef.getName(), "test-field");
        assertFalse(fieldDef.isRequired());
        assertEquals(fieldDef.getType(), UploadFieldType.INT);
        assertTrue(fieldDef.isUnboundedText());

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(fieldDef);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 9);
        assertTrue((boolean) jsonMap.get("allowOtherChoices"));
        assertEquals(jsonMap.get("fileExtension"), ".json");
        assertEquals(jsonMap.get("mimeType"), "text/json");
        assertEquals(jsonMap.get("maxLength"), 24);
        assertEquals(jsonMap.get("multiChoiceAnswerList"), expectedMultiChoiceAnswerList);
        assertEquals(jsonMap.get("name"), "test-field");
        assertFalse((boolean) jsonMap.get("required"));
        assertEquals(jsonMap.get("type"), "int");
        assertTrue((boolean) jsonMap.get("unboundedText"));
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(UploadFieldDefinition.class).allFieldsShouldBeUsed().verify();
    }
}
