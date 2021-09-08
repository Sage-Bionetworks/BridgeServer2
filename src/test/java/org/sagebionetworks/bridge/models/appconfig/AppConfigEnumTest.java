package org.sagebionetworks.bridge.models.appconfig;

import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AppConfigEnumTest {
    
    @Test
    public void hashCodeEquals() { 
        EqualsVerifier.forClass(AppConfigEnum.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }

    @Test
    public void canSerialize() throws Exception {
        AppConfigEnumEntry entry1 = new AppConfigEnumEntry();
        entry1.setValue("Disease");
        entry1.setLabels(LABELS);
        AppConfigEnumEntry entry2 = new AppConfigEnumEntry();
        entry2.setValue("B");
        AppConfigEnumEntry entry3 = new AppConfigEnumEntry();
        entry3.setValue("C");
        AppConfigEnum configEnum = new AppConfigEnum();
        configEnum.setValidate(true);
        configEnum.setEntries(ImmutableList.of(entry1, entry2, entry3));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(configEnum);
        
        assertTrue(node.get("validate").booleanValue());
        assertEquals(node.get("type").textValue(), "AppConfigEnum");
        
        JsonNode e1 = node.get("entries").get(0);
        assertEquals(e1.get("value").textValue(), "Disease");
        assertEquals(e1.get("label").textValue(), "English");
        assertEquals(e1.get("labels").get(0).get("lang").textValue(), "en");
        assertEquals(e1.get("labels").get(1).get("lang").textValue(), "fr");
        assertEquals(e1.get("type").textValue(), "AppConfigEnumEntry");
        
        JsonNode e2 = node.get("entries").get(1);
        assertEquals(e2.get("value").textValue(), "B");
        
        JsonNode e3 = node.get("entries").get(2);
        assertEquals(e3.get("value").textValue(), "C");
        
        AppConfigEnum deser = BridgeObjectMapper.get().readValue(node.toString(), AppConfigEnum.class);
        assertTrue(deser.getValidate());
        assertEquals(deser.getEntries().get(0).getValue(), "Disease");
        assertEquals(deser.getEntries().get(0).getLabels().get(0).getLang(), "en");
        assertEquals(deser.getEntries().get(0).getLabels().get(0).getValue(), "English");
        assertEquals(deser.getEntries().get(0).getLabels().get(1).getLang(), "fr");
        assertEquals(deser.getEntries().get(0).getLabels().get(1).getValue(), "French");
        assertEquals(deser.getEntries().get(1).getValue(), "B");
        assertEquals(deser.getEntries().get(2).getValue(), "C");
    }
    
    @Test
    public void getEntryValues() throws Exception {
        String json = TestUtils.createJson("{ 'validate': false, 'entries': [ { 'value': 'Disease', "
                + "'labels': [ { 'lang': 'fr', 'value': 'Le Disease' }, { 'lang': 'en', 'value': "
                + "'Disease' } ], 'label': 'Le Disease' }, { 'value': 'B' }, { 'value': 'C' } , { "
                + "'value': null }, null ] }");
        
        AppConfigEnum deser = BridgeObjectMapper.get().readValue(json, AppConfigEnum.class);
        
        assertEquals(deser.getEntryValues(), ImmutableList.of("Disease", "B", "C"));
    }
    
    @Test
    public void getEntryValues_nullEntries() throws Exception {
        AppConfigEnum configEnum = new AppConfigEnum();
        assertTrue(configEnum.getEntryValues().isEmpty());
        
        String json = TestUtils.createJson("{ 'validate': false, 'entries': [] }");
        AppConfigEnum deser = BridgeObjectMapper.get().readValue(json, AppConfigEnum.class);
        assertTrue(deser.getEntryValues().isEmpty());
    }
}
