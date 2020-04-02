package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class RequestInfoTest {

    private static final String STUDY_ID = "test-study";
    private static final String USER_ID = "userId";
    private static final ClientInfo CLIENT_INFO = ClientInfo.parseUserAgentString("app/20");
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
    private static final List<String> LANGUAGES = ImmutableList.of("en", "fr");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    private static final DateTimeZone MST = DateTimeZone.forOffsetHours(3);
    private static final DateTime ACTIVITIES_REQUESTED_ON = DateUtils.getCurrentDateTime().withZone(PST);
    private static final DateTime SIGNED_IN_ON = ACTIVITIES_REQUESTED_ON.minusHours(4).withZone(PST);
    private static final DateTime UPLOADED_ON = ACTIVITIES_REQUESTED_ON.minusHours(3).withZone(PST);
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(RequestInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        RequestInfo requestInfo = createRequestInfo();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);

        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("activitiesAccessedOn").textValue(), ACTIVITIES_REQUESTED_ON.withZone(MST).toString());
        assertEquals(node.get("uploadedOn").textValue(), UPLOADED_ON.withZone(MST).toString());
        assertEquals(node.get("languages").get(0).textValue(), "en");
        assertEquals(node.get("languages").get(1).textValue(), "fr");
        Set<String> groups = Sets.newHashSet(
            node.get("userDataGroups").get(0).textValue(),
            node.get("userDataGroups").get(1).textValue()
        );
        assertEquals(groups, USER_DATA_GROUPS);
        
        Set<String> substudyIds = Sets.newHashSet(
            node.get("userSubstudyIds").get(0).textValue(),
            node.get("userSubstudyIds").get(1).textValue()
        );
        assertEquals(substudyIds, USER_SUBSTUDY_IDS);
        assertEquals(node.get("signedInOn").textValue(), SIGNED_IN_ON.withZone(MST).toString());
        assertEquals(node.get("timeZone").textValue(), "+03:00");
        assertEquals(node.get("type").textValue(), "RequestInfo");
        assertEquals(node.get("userAgent").textValue(), USER_AGENT_STRING);
        assertEquals(node.get("studyIdentifier").textValue(), "test-study");
        assertEquals(node.size(), 12);
        
        JsonNode clientInfoNode = node.get("clientInfo");
        assertEquals(clientInfoNode.get("appName").textValue(), "app");
        assertEquals(clientInfoNode.get("appVersion").asInt(), 20);
        
        RequestInfo deserClientInfo = BridgeObjectMapper.get().readValue(node.toString(), RequestInfo.class);
        assertEquals(deserClientInfo, requestInfo);
    }
    
    @Test
    public void ifNoTimeZoneUseUTC() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withSignedInOn(SIGNED_IN_ON)
                .withTimeZone(null) // this won't reset time zone, still going to use UTC
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(requestInfo);
        
        assertEquals(node.get("activitiesAccessedOn").textValue(), ACTIVITIES_REQUESTED_ON.withZone(DateTimeZone.UTC).toString());
        assertEquals(node.get("signedInOn").textValue(), SIGNED_IN_ON.withZone(DateTimeZone.UTC).toString());
    }
    
    @Test
    public void copyOf() {
        RequestInfo requestInfo = createRequestInfo();
        
        RequestInfo copy = new RequestInfo.Builder().copyOf(requestInfo).build();
        assertEquals(copy.getStudyIdentifier(), STUDY_ID);
        assertEquals(copy.getClientInfo(), CLIENT_INFO);
        assertEquals(copy.getUserAgent(), USER_AGENT_STRING);
        assertEquals(copy.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(copy.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(copy.getLanguages(), LANGUAGES);
        assertEquals(copy.getUserId(), USER_ID);
        assertEquals(copy.getTimeZone(), MST);
        assertEquals(copy.getActivitiesAccessedOn(), ACTIVITIES_REQUESTED_ON.withZone(copy.getTimeZone()));
        assertEquals(copy.getUploadedOn(), UPLOADED_ON.withZone(copy.getTimeZone()));
        assertEquals(copy.getSignedInOn(), SIGNED_IN_ON.withZone(copy.getTimeZone()));
    }

    private RequestInfo createRequestInfo() {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withStudyIdentifier(STUDY_ID)
                .withClientInfo(CLIENT_INFO)
                .withUserAgent(USER_AGENT_STRING)
                .withUserDataGroups(USER_DATA_GROUPS)
                .withUserSubstudyIds(USER_SUBSTUDY_IDS)
                .withLanguages(LANGUAGES)
                .withUserId(USER_ID)
                .withTimeZone(MST)
                .withActivitiesAccessedOn(ACTIVITIES_REQUESTED_ON)
                .withUploadedOn(UPLOADED_ON)
                .withSignedInOn(SIGNED_IN_ON).build();
        return requestInfo;
    }
    
}
