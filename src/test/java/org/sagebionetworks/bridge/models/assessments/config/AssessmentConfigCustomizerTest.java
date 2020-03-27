package org.sagebionetworks.bridge.models.assessments.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AssessmentConfigCustomizerTest extends Mockito {

    private static final NullNode NULL_NODE = JsonNodeFactory.instance.nullNode();
    private static final NumericNode TEN_NUMBER_NODE = JsonNodeFactory.instance.numberNode(10);
    private static final TextNode BAR_TEXT_NODE = JsonNodeFactory.instance.textNode("bar");
    private static final BooleanNode FALSE_BOOLEAN_NODE = JsonNodeFactory.instance.booleanNode(false);
    private static final TextNode FOO_TEXT_NODE = JsonNodeFactory.instance.textNode("foo");
    private static final PropertyInfo NAME_PROP = new PropertyInfo.Builder().withPropName("name").build();
    private static final PropertyInfo PUBLISHED_PROP = new PropertyInfo.Builder().withPropName("published").build();

    AssessmentConfigCustomizer customizer;
    
    Map<String, Set<PropertyInfo>> fields;
    
    Map<String, Map<String, JsonNode>> updates;
    
    JsonNode node;
    
    @BeforeMethod
    public void beforeMethod() throws Exception {
        fields = new HashMap<>();
        updates = new HashMap<>();
        node = new ObjectMapper().readTree(AssessmentConfigValidatorTest.TEST_JSON);
    }
    
    @Test
    public void validChanges() {
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE, "published", FALSE_BOOLEAN_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, node);
        
        assertEquals(node.get("name").textValue(), "foo");
        assertFalse(node.get("published").booleanValue());
        assertTrue(customizer.hasUpdated());
    }
    
    @Test
    public void ignoresUnchangedNode() {
        fields.put("Demographics", ImmutableSet.of(PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        ((ObjectNode)node).put("identifier", "NotDemographics");
        customizer.accept(null, node);
        
        assertFalse(customizer.hasUpdated());
    }
    
    @Test
    public void ignoresUnspecifiedProperties() {
        fields.put("Demographics", ImmutableSet.of(PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, node);
        
        assertEquals(node.get("name").textValue(), "Demographics");
        assertFalse(customizer.hasUpdated());
    }
    
    @Test
    public void skipsNonObjectNodes() {
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE, "published", FALSE_BOOLEAN_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, TEN_NUMBER_NODE);
        
        assertFalse(customizer.hasUpdated());
    }
    
    @Test
    public void skipsObjectNodesWithoutIdentifiers() {
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE, "published", FALSE_BOOLEAN_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        ((ObjectNode)node).remove("identifier");
        customizer.accept(null, node);
        
        assertEquals(node.get("name").textValue(), "Demographics");
        assertTrue(node.get("published").booleanValue());
        assertFalse(customizer.hasUpdated());
    }
    
    @Test
    public void skipsNodesThatAreNotSpecified() {
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        updates.put("studyBurstCompletion", ImmutableMap.of("prompt", FOO_TEXT_NODE, "promptDetail", BAR_TEXT_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, node);
        
        assertFalse(customizer.hasUpdated());
    }
    
    @Test
    public void skipsPropertiesNotSpecified() {
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        updates.put("Demographics", ImmutableMap.of("name", FOO_TEXT_NODE, "published", FALSE_BOOLEAN_NODE,
                "schemaRevision", TEN_NUMBER_NODE));
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, node);
        
        assertEquals(node.get("name").textValue(), "foo");
        assertFalse(node.get("published").booleanValue());
        assertEquals(node.get("schemaRevision").intValue(), 1);
        assertTrue(customizer.hasUpdated());        
    }
    
    @Test
    public void removesProperties() { 
        fields.put("Demographics", ImmutableSet.of(NAME_PROP, PUBLISHED_PROP));
        
        Map<String, JsonNode> propMap = new HashMap<>();
        propMap.put("name", null);
        propMap.put("published", NULL_NODE);
        updates.put("Demographics", propMap);
        
        customizer = new AssessmentConfigCustomizer(fields, updates);
        customizer.accept(null, node);
        
        assertNull(node.get("name"));
        assertNull(node.get("published"));
        assertTrue(customizer.hasUpdated());
    }
}
