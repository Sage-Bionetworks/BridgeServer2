package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.TreeSet;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyRuleTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(SurveyRule.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void cannotSetEndSurveyToFalse() throws Exception {
        String json = TestUtils.createJson("{'operator':'eq','value':'No',"+
                "'skipTo':'theend','endSurvey':false}");
        SurveyRule rule = BridgeObjectMapper.get().readValue(json, SurveyRule.class);
        assertNull(rule.getEndSurvey());
        assertEquals(rule.getOperator(), Operator.EQ);
        assertEquals(rule.getSkipToTarget(), "theend");
        assertEquals(rule.getValue(), "No");
    }
    
    @Test
    public void canSerializeSkipTo() throws Exception {
        SurveyRule skipToRule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue("value")
                .withSkipToTarget("test").withEndSurvey(Boolean.FALSE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(skipToRule);
        assertEquals(node.get("operator").textValue(), "eq");
        assertEquals(node.get("value").textValue(), "value");
        assertEquals(node.get("skipTo").textValue(), "test");
        assertNull(node.get("endSurvey"));
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, skipToRule);
    }

    @Test
    public void canSerializeEndsurvey() throws Exception {
        SurveyRule endRule = new SurveyRule.Builder().withOperator(SurveyRule.Operator.EQ).withValue("value")
                .withEndSurvey(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(endRule);
        assertEquals(node.get("operator").textValue(), "eq");
        assertEquals(node.get("value").textValue(), "value");
        assertNull(node.get("skipTo"));
        assertTrue(node.get("endSurvey").booleanValue());
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, endRule);
    }
    
    @Test
    public void canSerializeAlwaysRule() throws Exception {
        SurveyRule alwaysRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(alwaysRule);
        assertEquals(node.get("operator").textValue(), "always");
        assertNull(node.get("value"));
        assertNull(node.get("skipTo"));
        assertTrue(node.get("endSurvey").booleanValue());
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, alwaysRule);
    }
    
    @Test
    public void canSerializeAssignDataGroupRule() throws Exception {
        SurveyRule dataGroupRule = new SurveyRule.Builder().withValue("foo").withOperator(Operator.EQ)
                .withAssignDataGroup("bar").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(dataGroupRule);
        assertEquals(node.get("operator").textValue(), "eq");
        assertEquals(node.get("value").textValue(), "foo");
        assertNull(node.get("skipTo"));
        assertEquals(node.get("assignDataGroup").textValue(), "bar");
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, dataGroupRule);
    }
    
    @Test
    public void canSerializeDisplayIf() throws Exception {
        TreeSet<String> displayGroups = new TreeSet<>();
        displayGroups.add("bar");
        displayGroups.add("foo");
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(Operator.ANY)
                .withDataGroups(displayGroups).withDisplayIf(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(displayIf);
        assertEquals(node.get("operator").textValue(), "any");
        assertEquals(node.get("dataGroups").get(0).textValue(), "bar");
        assertEquals(node.get("dataGroups").get(1).textValue(), "foo");
        assertTrue(node.get("displayIf").booleanValue());
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, displayIf);
    }
    
    @Test
    public void canSerializeDisplayUnless() throws Exception {
        SurveyRule displayIf = new SurveyRule.Builder().withOperator(Operator.ANY)
                .withDataGroups(Sets.newHashSet("foo")).withDisplayUnless(Boolean.TRUE).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(displayIf);
        assertEquals(node.get("operator").textValue(), "any");
        assertEquals(node.get("dataGroups").get(0).textValue(), "foo");
        assertTrue(node.get("displayUnless").booleanValue());
        assertEquals(node.get("type").textValue(), "SurveyRule");
        
        SurveyRule deser = BridgeObjectMapper.get().treeToValue(node, SurveyRule.class);
        assertEquals(deser, displayIf);
    }
    
    // If the user sends a property set to false, ensure the field is set to null and
    // that serialization of the rule excludes that property. Only one boolean property
    // can be true at a time and only the true property will be in JSON representations.
    @Test
    public void sendingFalseDeserializesToNullProperty() throws Exception {
        String json = TestUtils.createJson("{'displayIf':false,"+
                "'displayUnless':false,'endSurvey':false}");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(json);
        assertNull(node.get("displayIf"));
        assertNull(node.get("displayUnless"));
        assertNull(node.get("endSurvey"));
    }
    
    @Test
    public void settingBooleanActionToFalseNullifiesIt() {
        SurveyRule.Builder builder = new SurveyRule.Builder().withDisplayIf(true).withDisplayUnless(true).withEndSurvey(true);
        
        SurveyRule rule = builder.withDisplayIf(false).withDisplayUnless(false).withEndSurvey(false).build();
        assertNull(rule.getDisplayIf());
        assertNull(rule.getDisplayUnless());
        assertNull(rule.getEndSurvey());
    }
}
