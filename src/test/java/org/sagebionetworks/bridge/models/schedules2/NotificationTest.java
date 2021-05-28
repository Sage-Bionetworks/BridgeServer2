package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.models.schedules2.NotificationType.AFTER_WINDOW_START;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class NotificationTest extends Mockito {

    public static final NotificationMessage MESSAGE = new NotificationMessage.Builder()
            .withLang("en").withSubject("subject").withMessage("msg").build();
    
    @Test
    public void canSerialize() throws Exception {
        Notification notification = new Notification();
        notification.setNotifyAt(AFTER_WINDOW_START);
        notification.setOffset(Period.parse("PT1H"));
        notification.setInterval(Period.parse("P2D"));
        notification.setMessages(ImmutableList.of(MESSAGE));
        notification.setAllowSnooze(true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(notification);
        assertEquals(node.get("notifyAt").textValue(), "after_window_start");
        assertEquals(node.get("offset").textValue(), "PT1H");
        assertEquals(node.get("interval").textValue(), "P2D");
        assertEquals(node.get("messages").get(0).get("lang").textValue(), MESSAGE.getLang());
        assertEquals(node.get("messages").get(0).get("subject").textValue(), MESSAGE.getSubject());
        assertEquals(node.get("messages").get(0).get("message").textValue(), MESSAGE.getMessage());
        assertEquals(node.get("type").textValue(), "Notification");
        
        Notification deser = BridgeObjectMapper.get().readValue(node.toString(), Notification.class);
        assertEquals(deser.getNotifyAt(), AFTER_WINDOW_START);
        assertEquals(deser.getOffset(), Period.parse("PT1H"));
        assertEquals(deser.getInterval(), Period.parse("P2D"));
        NotificationMessage deserMsg = deser.getMessages().get(0);
        assertEquals(deserMsg.getLang(), MESSAGE.getLang());
        assertEquals(deserMsg.getSubject(), MESSAGE.getSubject());
        assertEquals(deserMsg.getMessage(), MESSAGE.getMessage());
    }
}
