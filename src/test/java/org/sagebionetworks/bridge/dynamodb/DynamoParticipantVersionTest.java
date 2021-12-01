package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;

public class DynamoParticipantVersionTest {
    private static final long CREATED_ON = DateTime.parse("2021-10-03T11:25:17.617-0700").getMillis();
    private static final Set<String> DATA_GROUPS = ImmutableSet.of("test-data-group");
    private static final String KEY = TestConstants.TEST_APP_ID + ':' + TestConstants.HEALTH_CODE;
    private static final List<String> LANGUAGES = ImmutableList.of("en-us");
    private static final long MODIFIED_ON = DateTime.parse("2021-10-12T16:34:54.587-0700").getMillis();
    private static final int PARTICIPANT_VERSION = 42;
    private static final Map<String, String> STUDY_MEMBERSHIPS = ImmutableMap.of("test-study", "test-ext-id");
    private static final String TIME_ZONE = "America/Los_Angeles";
    private static final long VERSION = 2L;

    @Test
    public void getKey() {
        // Null app, null health code.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        participantVersion.setAppId(null);
        participantVersion.setHealthCode(null);
        assertNull(participantVersion.getKey());

        // Null app, non-null health code.
        participantVersion.setAppId(null);
        participantVersion.setHealthCode(TestConstants.HEALTH_CODE);
        assertNull(participantVersion.getKey());

        // Non-null app, null health code.
        participantVersion.setAppId(TestConstants.TEST_APP_ID);
        participantVersion.setHealthCode(null);
        assertNull(participantVersion.getKey());

        // Non-null app, non-null health code.
        participantVersion.setAppId(TestConstants.TEST_APP_ID);
        participantVersion.setHealthCode(TestConstants.HEALTH_CODE);
        assertEquals(participantVersion.getKey(), KEY);
    }

    @Test
    public void setKey() {
        // Initially null.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        assertNull(participantVersion.getAppId());
        assertNull(participantVersion.getHealthCode());

        // Set with the correct format.
        participantVersion.setKey(KEY);
        assertEquals(participantVersion.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersion.getHealthCode(), TestConstants.HEALTH_CODE);

        // Ignore null.
        participantVersion.setKey(null);
        assertEquals(participantVersion.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersion.getHealthCode(), TestConstants.HEALTH_CODE);

        // Ignore incorrect format.
        participantVersion.setKey("wrong-format");
        assertEquals(participantVersion.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersion.getHealthCode(), TestConstants.HEALTH_CODE);
    }

    @Test
    public void getSetDataGroups() {
        assertCollectionCannotBeEmpty(DynamoParticipantVersion::getDataGroups, DynamoParticipantVersion::setDataGroups,
                DATA_GROUPS, ImmutableSet.of());
    }

    @Test
    public void getSetLanguages() {
        assertCollectionCannotBeEmpty(DynamoParticipantVersion::getLanguages, DynamoParticipantVersion::setLanguages,
                LANGUAGES, ImmutableList.of());
    }

    @Test
    public void getSetStudyMemberships() {
        assertCollectionCannotBeEmpty(DynamoParticipantVersion::getStudyMemberships, DynamoParticipantVersion::setStudyMemberships,
                STUDY_MEMBERSHIPS, ImmutableMap.of());
    }

    private static <T> void assertCollectionCannotBeEmpty(Function<DynamoParticipantVersion, T> getter,
            BiConsumer<DynamoParticipantVersion, T> setter, T nonEmptyValue, T emptyValue) {
        // Initially null.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        assertNull(getter.apply(participantVersion));

        // Set non-empty.
        setter.accept(participantVersion, nonEmptyValue);
        assertEquals(getter.apply(participantVersion), nonEmptyValue);

        // Set empty, gets changed to null.
        setter.accept(participantVersion, emptyValue);
        assertNull(getter.apply(participantVersion));

        // Set non-empty, then set null. Null is null.
        setter.accept(participantVersion, nonEmptyValue);
        assertEquals(getter.apply(participantVersion), nonEmptyValue);

        setter.accept(participantVersion, null);
        assertNull(getter.apply(participantVersion));
    }

    @Test
    public void jsonSerialization() throws Exception {
        // Make Participant Version.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        participantVersion.setAppId(TestConstants.TEST_APP_ID);
        participantVersion.setHealthCode(TestConstants.HEALTH_CODE);
        participantVersion.setParticipantVersion(PARTICIPANT_VERSION);
        participantVersion.setCreatedOn(CREATED_ON);
        participantVersion.setModifiedOn(MODIFIED_ON);
        participantVersion.setDataGroups(DATA_GROUPS);
        participantVersion.setLanguages(LANGUAGES);
        participantVersion.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        participantVersion.setStudyMemberships(STUDY_MEMBERSHIPS);
        participantVersion.setTimeZone(TIME_ZONE);
        participantVersion.setVersion(VERSION);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(participantVersion, JsonNode.class);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("healthCode").textValue(), TestConstants.HEALTH_CODE);
        assertEquals(jsonNode.get("participantVersion").intValue(), PARTICIPANT_VERSION);
        assertEquals(DateTime.parse(jsonNode.get("createdOn").textValue()).getMillis(), CREATED_ON);
        assertEquals(DateTime.parse(jsonNode.get("modifiedOn").textValue()).getMillis(), MODIFIED_ON);
        assertEquals(jsonNode.get("sharingScope").textValue(), "all_qualified_researchers");
        assertEquals(jsonNode.get("timeZone").textValue(), TIME_ZONE);
        assertEquals(jsonNode.get("type").textValue(), "ParticipantVersion");

        JsonNode dataGroupsNode = jsonNode.get("dataGroups");
        assertTrue(dataGroupsNode.isArray());
        assertEquals(dataGroupsNode.size(), 1);
        assertEquals(dataGroupsNode.get(0).textValue(), Iterables.getOnlyElement(DATA_GROUPS));

        JsonNode languagesNode = jsonNode.get("languages");
        assertTrue(languagesNode.isArray());
        assertEquals(languagesNode.size(), 1);
        assertEquals(languagesNode.get(0).textValue(), LANGUAGES.get(0));

        JsonNode studyMembershipsNode = jsonNode.get("studyMemberships");
        assertTrue(studyMembershipsNode.isObject());
        assertEquals(studyMembershipsNode.size(), 1);
        assertEquals(studyMembershipsNode.get("test-study").textValue(), "test-ext-id");

        // These fields don't get converted to JSON.
        assertFalse(jsonNode.has("key"));
        assertFalse(jsonNode.has("version"));

        // Convert back to POJO.
        participantVersion = (DynamoParticipantVersion) BridgeObjectMapper.get().treeToValue(jsonNode,
                ParticipantVersion.class);
        assertEquals(participantVersion.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(participantVersion.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(participantVersion.getParticipantVersion(), PARTICIPANT_VERSION);
        assertEquals(participantVersion.getCreatedOn(), CREATED_ON);
        assertEquals(participantVersion.getModifiedOn(), MODIFIED_ON);
        assertEquals(participantVersion.getDataGroups(), DATA_GROUPS);
        assertEquals(participantVersion.getLanguages(), LANGUAGES);
        assertEquals(participantVersion.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(participantVersion.getStudyMemberships(), STUDY_MEMBERSHIPS);
        assertEquals(participantVersion.getTimeZone(), TIME_ZONE);

        // Version wasn't converted to JSON, and thus isn't in the de-serialized POJO either.
        assertNull(participantVersion.getVersion());
    }
}
