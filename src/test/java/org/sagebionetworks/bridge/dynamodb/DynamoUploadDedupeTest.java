package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class DynamoUploadDedupeTest {
    @Test
    public void getDdbKeyFromHealthCodeAndMd5() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setHealthCode("test-healthcode");
        dedupe.setUploadMd5("test-md5");
        assertEquals(dedupe.getDdbKey(), "test-healthcode:test-md5");
    }

    @Test
    public void getHealthCodeAndMd5FromDdbKey() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setDdbKey("test-healthcode:test-md5");
        assertEquals(dedupe.getHealthCode(), "test-healthcode");
        assertEquals(dedupe.getUploadMd5(), "test-md5");
    }
}
