package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.models.schedules2.NotificationTest.MESSAGE;
import static org.sagebionetworks.bridge.models.schedules2.NotificationType.AFTER_WINDOW_START;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules2.Notification;

public class NotificationInfoTest extends Mockito {

    @Test
    public void canSerialize() { 
        Notification notification = new Notification();
        notification.setNotifyAt(AFTER_WINDOW_START);
        notification.setOffset(Period.parse("PT1H"));
        notification.setInterval(Period.parse("P2D"));
        notification.setMessages(ImmutableList.of(MESSAGE));
        notification.setAllowSnooze(true);
        
        NotificationInfo info = NotificationInfo.create(notification, null);
        assertEquals(info.getNotifyAt(), AFTER_WINDOW_START);
        assertEquals(info.getOffset(), Period.parse("PT1H"));
        assertEquals(info.getInterval(), Period.parse("P2D"));
        assertEquals(info.getMessage(), MESSAGE);
        assertTrue(info.getAllowSnooze());
    }
    
    @Test
    public void selectsLanguage() {
        NotificationMessage frMessage = new NotificationMessage.Builder()
                .withLang("fr").withSubject("le subject").withMessage("le msg").build();
        
        Notification notification = new Notification();
        notification.setMessages(ImmutableList.of(MESSAGE, frMessage));
        
        NotificationInfo info = NotificationInfo.create(notification, ImmutableList.of("fr"));
        assertEquals(info.getMessage().getLang(), "fr");
    }
    
}
