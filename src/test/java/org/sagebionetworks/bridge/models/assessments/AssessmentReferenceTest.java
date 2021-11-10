package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.mockConfigResolver;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AssessmentReferenceTest extends Mockito {

    @Mock
    BridgeConfig mockConfig;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void equalsVerifier() {
        // The reference may or may not have been resolved to include identifiers, the GUID
        // should be globally unique for assessement references and is sufficient for 
        // equality. This makes detecting duplicates easier during validation.
        EqualsVerifier.forClass(AssessmentReference.class)
            .allFieldsShouldBeUsedExcept("resolver", "appId", "id", "originSharedId").verify();
    }

    @Test
    public void succeeds() throws Exception {
        ConfigResolver resolver = mockConfigResolver(UAT, "ws");
        AssessmentReference ref = new AssessmentReference(resolver, "appId", "oneGuid", "id", "originSharedId");
        
        assertEquals(ref.getAppId(), "appId");
        assertEquals(ref.getGuid(), "oneGuid");
        assertEquals(ref.getId(), "id");
        assertEquals(ref.getOriginSharedId(), "originSharedId");
        assertEquals(ref.getConfigHref(),
                "https://ws-uat.bridge.org/v1/assessments/oneGuid/config");
    }
    
    @Test
    public void noIdentifiers() {
        ConfigResolver resolver = mockConfigResolver(LOCAL, "ws");
        AssessmentReference ref = new AssessmentReference(resolver, null, "oneGuid", null, null);
        
        assertNull(ref.getAppId());
        assertEquals(ref.getGuid(), "oneGuid");
        assertNull(ref.getId());
        assertNull(ref.getOriginSharedId());
        assertEquals(ref.getConfigHref(),
                "http://ws-local.bridge.org/v1/assessments/oneGuid/config");
    }
    
    @Test
    public void noGuid() {
        AssessmentReference ref = new AssessmentReference(null, null);
        assertNull(ref.getConfigHref());
    }
    
    @Test
    public void canSerialise() throws Exception {
        ConfigResolver resolver = mockConfigResolver(LOCAL, "ws");
        AssessmentReference ref = new AssessmentReference(resolver, "appId", "oneGuid", "id", "originSharedId");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.get("appId").textValue(), "appId");
        assertEquals(node.get("guid").textValue(), "oneGuid");
        assertEquals(node.get("id").textValue(), "id");
        assertEquals(node.get("originSharedId").textValue(), "originSharedId");
        assertEquals(node.get("configHref").textValue(),
            "http://ws-local.bridge.org/v1/assessments/oneGuid/config");
        assertEquals(node.get("type").textValue(), "AssessmentReference");
        
        AssessmentReference deser = BridgeObjectMapper.get().readValue(
                node.toString(), AssessmentReference.class);
        assertEquals(deser, ref);
        assertEquals(deser.getAppId(), "appId");
        assertEquals(deser.getGuid(), "oneGuid");
        assertNull(deser.getId());
        assertNull(deser.getOriginSharedId());
    }

    @Test
    public void configHrefChangesWithSharedAppId() {
        ConfigResolver resolver = mockConfigResolver(LOCAL, "ws");
        AssessmentReference ref = new AssessmentReference(resolver, SHARED_APP_ID, "oneGuid", null, null);

        assertEquals(ref.getConfigHref(),
                "http://ws-local.bridge.org/v1/sharedassessments/oneGuid/config");
    }
}
