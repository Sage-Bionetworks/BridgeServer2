package org.sagebionetworks.bridge.models.reports;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ReportDataKeyTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ReportDataKey.class).allFieldsShouldBeUsed().verify();
    }    
    
    @Test
    public void constructParticipantKey() {
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode")
                .withStudyIdentifier(TEST_STUDY_IDENTIFIER).withReportType(ReportType.PARTICIPANT)
                .withIdentifier("report").build();

        assertEquals(key.getKeyString(), "healthCode:report:api");
        assertEquals(key.getIndexKeyString(), "api:PARTICIPANT");
        assertEquals(key.getHealthCode(), "healthCode");
        assertEquals(key.getIdentifier(), "report");
        assertEquals(key.getReportType(), ReportType.PARTICIPANT);
    }
    
    @Test
    public void constructStudyKey() {
        ReportDataKey key = new ReportDataKey.Builder().withReportType(ReportType.STUDY)
                .withStudyIdentifier(TEST_STUDY_IDENTIFIER).withIdentifier("report").build();
        
        // This was constructed, the date is valid. It's not part of the key, it's validated in the builder
        // so validation errors are combined with key validation errors.
        assertEquals(key.getKeyString(), "report:api");
        assertEquals(key.getIndexKeyString(), "api:STUDY");
        assertNull(key.getHealthCode());
        assertEquals(key.getIdentifier(), "report");
        assertEquals(key.getReportType(), ReportType.STUDY);
    }

    @Test
    public void canConstructKeyWithoutValidatingDate() {
        ReportDataKey key = new ReportDataKey.Builder().withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(TEST_STUDY_IDENTIFIER).withHealthCode("AAA").withIdentifier("report").build();
        
        assertEquals(key.getKeyString(), "AAA:report:api");
        assertEquals(key.getIndexKeyString(), "api:PARTICIPANT");
        assertEquals(key.getHealthCode(), "AAA");
        assertEquals(key.getIdentifier(), "report");
        assertEquals(key.getReportType(), ReportType.PARTICIPANT);
    }
    
    @Test
    public void canSerialize() throws Exception {
        // NOTE: Although we use @JsonIgnore annotations, we never serialize this value and return it via the API,
        // so arguably none of this is necessary.
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("healthCode")
                .withStudyIdentifier(TEST_STUDY_IDENTIFIER).withReportType(ReportType.PARTICIPANT)
                .withIdentifier("report").build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(key);
        assertEquals(node.get("identifier").asText(), "report");
        assertEquals(node.get("reportType").asText(), "participant");
        assertEquals(node.get("type").asText(), "ReportDataKey");
        assertEquals(node.size(), 3); // no healthCode, no studyId.
    }
    
    // Validator test verify the key cannot be constructed in an invalid state.
}
