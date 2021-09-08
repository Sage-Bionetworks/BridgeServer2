package org.sagebionetworks.bridge.models.appconfig;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;


public class AppConfigEnumEntryTest {
    
    @AfterTest
    public void afterTest() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AppConfigEnumEntry.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        // select the French language label
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("fr")).build());
        
        AppConfigEnumEntry entry = new AppConfigEnumEntry();
        entry.setValue("enumValue");
        entry.setLabels(LABELS);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(entry);
        assertEquals(node.get("value").textValue(), "enumValue");
        assertEquals(node.get("label").textValue(), "French");
        assertEquals(node.get("labels").get(0).get("lang").textValue(), "en");
        assertEquals(node.get("labels").get(0).get("value").textValue(), "English");
        assertEquals(node.get("labels").get(1).get("lang").textValue(), "fr");
        assertEquals(node.get("labels").get(1).get("value").textValue(), "French");
        assertEquals(node.get("type").textValue(), "AppConfigEnumEntry");
        
        AppConfigEnumEntry deser = BridgeObjectMapper.get().readValue(node.toString(), AppConfigEnumEntry.class);
        assertEquals(deser.getValue(), "enumValue");
        assertEquals(deser.getLabels(), LABELS);
    }

    @Test
    public void selectsLabelFallsbackToValue() {
        AppConfigEnumEntry entry = new AppConfigEnumEntry();
        entry.setValue("enumValue");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(entry);
        System.out.println(node);
        assertEquals(node.get("value").textValue(), "enumValue");
        assertEquals(node.get("label").textValue(), "enumValue");
    }
}
