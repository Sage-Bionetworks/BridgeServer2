package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class FileRevisionIdTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(FileRevisionId.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .verify();
    }
    
    @Test
    public void test() { 
        FileRevisionId id = new FileRevisionId(GUID, TIMESTAMP);
        assertEquals(id.getFileGuid(), GUID);
        assertEquals(id.getCreatedOn(), TIMESTAMP);
    }
}
