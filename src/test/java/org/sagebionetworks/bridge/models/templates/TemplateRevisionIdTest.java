package org.sagebionetworks.bridge.models.templates;

import static nl.jqno.equalsverifier.Warning.NONFINAL_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TemplateRevisionIdTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(TemplateRevisionId.class).allFieldsShouldBeUsed()
            .suppress(NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        TemplateRevisionId templateId = new TemplateRevisionId("templateGuid", TIMESTAMP);
        
        assertEquals(templateId.getTemplateGuid(), "templateGuid");
        assertEquals(templateId.getCreatedOn(), TIMESTAMP);
    }    
}
