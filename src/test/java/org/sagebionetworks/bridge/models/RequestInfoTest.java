package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RequestInfoTest {

    private static final String USER_ID = "userId";
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final List<String> LANGUAGES = ImmutableList.of("en", "fr");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone MST = DateTimeZone.forOffsetHours(3);
    private static final DateTime ACTIVITIES_ACCESSED_ON = DateUtils.getCurrentDateTime().withZone(PST);
    private static final DateTime SIGNED_IN_ON = ACTIVITIES_ACCESSED_ON.minusHours(4).withZone(PST);
    private static final DateTime UPLOADED_ON = ACTIVITIES_ACCESSED_ON.minusHours(3).withZone(PST);
    private static final DateTime TIMELINE_ACCESSED_ON = ACTIVITIES_ACCESSED_ON.minusHours(6).withZone(PST);
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(RequestInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        RequestInfo requestInfo = createRequestInfo();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);

        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("activitiesAccessedOn").textValue(), ACTIVITIES_ACCESSED_ON.withZone(MST).toString());
        assertEquals(node.get("timelineAccessedOn").textValue(), TIMELINE_ACCESSED_ON.withZone(MST).toString());
        assertEquals(node.get("uploadedOn").textValue(), UPLOADED_ON.withZone(MST).toString());
        assertEquals(node.get("languages").get(0).textValue(), "en");
        assertEquals(node.get("languages").get(1).textValue(), "fr");
        Set<String> groups = Sets.newHashSet(
            node.get("userDataGroups").get(0).textValue(),
            node.get("userDataGroups").get(1).textValue()
        );
        assertEquals(groups, USER_DATA_GROUPS);
        
        Set<String> studyIds = Sets.newHashSet(
            node.get("userStudyIds").get(0).textValue(),
            node.get("userStudyIds").get(1).textValue()
        );
        assertEquals(studyIds, USER_STUDY_IDS);
        assertEquals(node.get("signedInOn").textValue(), SIGNED_IN_ON.withZone(MST).toString());
        assertEquals(node.get("timeZone").textValue(), "+03:00");
        assertEquals(node.get("type").textValue(), "RequestInfo");
        assertEquals(node.get("userAgent").textValue(), USER_AGENT_STRING);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.size(), 13);
        
        JsonNode clientInfoNode = node.get("clientInfo");
        assertEquals(clientInfoNode.get("appName").textValue(), "app");
        assertEquals(clientInfoNode.get("appVersion").asInt(), 20);
        
        RequestInfo deserClientInfo = BridgeObjectMapper.get().readValue(node.toString(), RequestInfo.class);
        assertEquals(deserClientInfo, requestInfo);
    }
    
    @Test
    public void ifNoTimeZoneUseUTC() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withActivitiesAccessedOn(ACTIVITIES_ACCESSED_ON)
                .withSignedInOn(SIGNED_IN_ON)
                .withTimeZone(null) // this won't reset time zone, still going to use UTC
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);
        
        assertEquals(node.get("activitiesAccessedOn").textValue(), ACTIVITIES_ACCESSED_ON.withZone(DateTimeZone.UTC).toString());
        assertEquals(node.get("signedInOn").textValue(), SIGNED_IN_ON.withZone(DateTimeZone.UTC).toString());
    }
    
    @Test
    public void copyOf() {
        RequestInfo requestInfo = createRequestInfo();
        
        RequestInfo copy = new RequestInfo.Builder().copyOf(requestInfo).build();
        assertEquals(copy.getAppId(), TEST_APP_ID);
        assertEquals(copy.getClientInfo(), CLIENT_INFO);
        assertEquals(copy.getUserAgent(), USER_AGENT_STRING);
        assertEquals(copy.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(copy.getUserStudyIds(), USER_STUDY_IDS);
        assertEquals(copy.getLanguages(), LANGUAGES);
        assertEquals(copy.getUserId(), USER_ID);
        assertEquals(copy.getTimeZone(), MST);
        assertEquals(copy.getActivitiesAccessedOn(), ACTIVITIES_ACCESSED_ON.withZone(copy.getTimeZone()));
        assertEquals(copy.getTimelineAccessedOn(), TIMELINE_ACCESSED_ON.withZone(copy.getTimeZone()));
        assertEquals(copy.getUploadedOn(), UPLOADED_ON.withZone(copy.getTimeZone()));
        assertEquals(copy.getSignedInOn(), SIGNED_IN_ON.withZone(copy.getTimeZone()));
    }

    @Test
    public void truncatesLongUserAgent() {
        String aaaTooBig = StringUtils.repeat('A', 2*BridgeConstants.MAX_USER_AGENT_LENGTH);
        String aaaJustRight = StringUtils.repeat('A', BridgeConstants.MAX_USER_AGENT_LENGTH);

        RequestInfo requestInfo = new RequestInfo.Builder().withUserAgent(aaaTooBig).build();
        assertNotEquals(requestInfo.getUserAgent(), aaaTooBig);
        assertEquals(requestInfo.getUserAgent(), aaaJustRight);
    }

    @Test
    public void deserializesSubstudyIdsCorrectly() throws Exception {
        String json = TestUtils.createJson("{'userSubstudyIds': ['A','B']}");
        
        RequestInfo info = BridgeObjectMapper.get().readValue(json, RequestInfo.class);
        assertEquals(info.getUserStudyIds(), ImmutableSet.of("A", "B"));
    }

    private RequestInfo createRequestInfo() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserAgent(USER_AGENT_STRING)
                .withUserDataGroups(USER_DATA_GROUPS)
                .withUserStudyIds(USER_STUDY_IDS)
                .withLanguages(LANGUAGES)
                .withUserId(USER_ID)
                .withTimeZone(MST)
                .withActivitiesAccessedOn(ACTIVITIES_ACCESSED_ON)
                .withTimelineAccessedOn(TIMELINE_ACCESSED_ON)
                .withUploadedOn(UPLOADED_ON)
                .withSignedInOn(SIGNED_IN_ON).build();
        return requestInfo;
    }
    
}
