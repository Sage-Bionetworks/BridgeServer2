package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.AppVersionHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoCriteriaTest {

    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoCriteria.class).suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        Criteria criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setAllOfSubstudyIds(SET_A);
        criteria.setNoneOfSubstudyIds(SET_B);
        criteria.setKey("subpopulation:AAA");
        criteria.setLanguage("fr");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertEquals(node.get("language").asText(), "fr");
        assertEquals(JsonUtils.asStringSet(node, "allOfGroups"), SET_A);
        assertEquals(JsonUtils.asStringSet(node, "noneOfGroups"), SET_B);
        assertEquals(JsonUtils.asStringSet(node, "allOfSubstudyIds"), SET_A);
        assertEquals(JsonUtils.asStringSet(node, "noneOfSubstudyIds"), SET_B);
        
        JsonNode minValues = node.get("minAppVersions");
        assertEquals(minValues.get(IOS).asInt(), 2);
        assertEquals(minValues.get(ANDROID).asInt(), 10);
        
        JsonNode maxValues = node.get("maxAppVersions");
        assertEquals(maxValues.get(IOS).asInt(), 8);
        assertEquals(maxValues.get(ANDROID).asInt(), 15);
        
        assertEquals(node.get("type").asText(), "Criteria");
        assertNull(node.get("key"));
        assertEquals(node.size(), 8); // Nothing else is serialized here. (That's important.)
        
        // However, we will except the older variant of JSON for the time being
        String json = makeJson("{'minAppVersion':2,'maxAppVersion':8,'language':'de','allOfGroups':['a','b'],"+
        "'noneOfGroups':['c','d'],'allOfSubstudyIds':['a','b'],'noneOfSubstudyIds':['c','d']}");
        
        Criteria crit = BridgeObjectMapper.get().readValue(json, Criteria.class);
        assertEquals(crit.getMinAppVersion(IOS), new Integer(2));
        assertEquals(crit.getMaxAppVersion(IOS), new Integer(8));
        assertEquals(crit.getLanguage(), "de");
        assertEquals(crit.getAllOfGroups(), SET_A);
        assertEquals(crit.getNoneOfGroups(), SET_B);
        assertEquals(crit.getAllOfSubstudyIds(), SET_A);
        assertEquals(crit.getNoneOfSubstudyIds(), SET_B);
        assertNull(crit.getKey());
    }

    @Test
    public void getSetMinMaxAppVersions() throws Exception {
        AppVersionHelper.testAppVersionHelper(DynamoCriteria.class);
    }

    @Test
    public void canRemoveMinMaxAttributes() {
        Criteria criteria = Criteria.create();
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);

        criteria.setMinAppVersion(IOS, null);
        criteria.setMaxAppVersion(ANDROID, null);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(criteria);
        assertNull(node.get("minAppVersions").get(IOS));
        assertNull(node.get("maxAppVersions").get(ANDROID));
    }
    
    @Test
    public void newerPlatformVersionAttributesTakePrecedenceOverLegacyProperties() {
        // Use DynamoCriteria directly so you can set legacy values
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setMinAppVersion(IOS, 8);
        criteria.setMinAppVersion(4);
        // Using legacy setter does not set value if it already exists in the map
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(8));
        
        // But of course you can update the value in the map
        criteria.setMinAppVersion(IOS, 10);
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(10));
    }

    @Test
    public void cannotSetNullValuesInPlatformVersionMap() {
        Map<String, Integer> map = Maps.newHashMap();
        map.put(IOS, null);
        
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setMinAppVersions(map);
        criteria.setMaxAppVersions(map);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
        
        criteria.setMinAppVersion(IOS, null);
        criteria.setMaxAppVersion(IOS, null);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
        
        criteria.setMinAppVersion(null);
        criteria.setMaxAppVersion(null);
        assertFalse(criteria.getMinAppVersions().containsKey(IOS));
        assertFalse(criteria.getMaxAppVersions().containsKey(IOS));
    }
    
    @Test
    public void nullForSetFieldsConvertedToEmptySets() {
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setAllOfGroups(null);
        criteria.setNoneOfGroups(null);
        criteria.setAllOfSubstudyIds(null);
        criteria.setNoneOfSubstudyIds(null);
        
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
        assertTrue(criteria.getAllOfSubstudyIds().isEmpty());
        assertTrue(criteria.getNoneOfSubstudyIds().isEmpty());
    }
    
    @Test
    public void originalMinMaxValuesAreMigratedToPlatformMap() {
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setKey("key1");
        criteria.setMinAppVersion(1);
        criteria.setMaxAppVersion(4);
        
        assertEquals(new Integer(1), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(4), criteria.getMaxAppVersion(IOS));
    }
    
    private String makeJson(String string) {
        return string.replaceAll("'", "\"");
    }
    
}
