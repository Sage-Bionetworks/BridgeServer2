package org.sagebionetworks.bridge.models.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadRedriveListTest {
    private static final String UPLOAD_ID_1 = "upload1";
    private static final String UPLOAD_ID_2 = "upload2";

    @Test
    public void canSerialize() throws Exception {
        UploadRedriveList list = new UploadRedriveList(ImmutableList.of(UPLOAD_ID_1, UPLOAD_ID_2));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);

        assertEquals(node.get("uploadIds").size(), 2);
        // just verify these are adherence records, which we test separately
        assertEquals(node.get("uploadIds").get(0).textValue(), UPLOAD_ID_1);
        assertEquals(node.get("uploadIds").get(1).textValue(), UPLOAD_ID_2);
        assertEquals(node.get("type").textValue(), "UploadRedriveList");

        UploadRedriveList deser = BridgeObjectMapper.get().readValue(node.toString(), UploadRedriveList.class);
        assertEquals(deser.getUploadIds().size(), 2);
        assertEquals(deser.getUploadIds().get(0), UPLOAD_ID_1);
        assertEquals(deser.getUploadIds().get(1), UPLOAD_ID_2);
    }
    
    @Test
    public void nullList() {
        UploadRedriveList list = new UploadRedriveList(null);
        assertEquals(list.getUploadIds(), ImmutableList.of());
    }
}
