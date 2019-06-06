package org.sagebionetworks.bridge.services.email;

import static org.testng.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import com.google.common.collect.ImmutableList;

import org.testng.annotations.Test;

public class MimeTypeEmailTest {
    private static final String SUBJECT = "subject";

    private static MimeTypeEmail makeEmailWithSender(String sender) throws MessagingException {
        String recipient = "bridge-testing@sagebase.org";
        return new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender(sender).withRecipient(recipient)
                .withMessageParts(makeBodyPart("dummy content")).withType(EmailType.EMAIL_SIGN_IN).build();
    }
    
    private static MimeTypeEmail makeEmailWithRecipient(String recipient) throws MessagingException {
        String sender = "bridge-testing@sagebase.org";
        return new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender(sender).withRecipient(recipient)
                .withMessageParts(makeBodyPart("dummy content")).withType(EmailType.EMAIL_SIGN_IN).build();
    }

    private static MimeBodyPart makeBodyPart(String content) throws MessagingException {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(content, StandardCharsets.UTF_8.name(), "text/plain");
        return bodyPart;
    }

    @Test
    public void testAttributes() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("test@example.com");
        assertEquals(email.getSubject(), SUBJECT);
        assertEquals(email.getType(), EmailType.EMAIL_SIGN_IN);

        assertEquals(email.getMessageParts().size(), 1);
        assertEquals(email.getMessageParts().get(0).getContent(), "dummy content");
    }

    @Test
    public void senderUnadornedEmailNotChanged() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("test@test.com");
        assertEquals(email.getSenderAddress(), "test@test.com");
    }
    
    @Test
    public void senderOddlyFormattedButLegal() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("<test@test.com>");
        assertEquals(email.getSenderAddress(), "<test@test.com>");
    }
    
    @Test
    public void senderAddressWithNameQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("A, B, and C <test@test.com>");
        assertEquals(email.getSenderAddress(), "\"A, B, and C\" <test@test.com>");
    }
    
    @Test
    public void senderAddressWithNameWithQuotesItsAllQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithSender("The \"Fun Guys\" at UofW <test@test.com>");
        assertEquals(email.getSenderAddress(), "\"The \\\"Fun Guys\\\" at UofW\" <test@test.com>");
    }

    @Test
    public void recipientUnadornedEmailNotChanged() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("test@test.com");
        assertEquals(email.getRecipientAddresses().get(0), "test@test.com");
    }
    
    @Test
    public void recipientAddressWithNameQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("A, B, and C <test@test.com>");
        assertEquals(email.getRecipientAddresses().get(0), "\"A, B, and C\" <test@test.com>");
    }
    
    @Test
    public void recipientAddressWithNameWithQuotesItsAllQuoted() throws Exception {
        MimeTypeEmail email = makeEmailWithRecipient("The \"Fun Guys\" at UofW <test@test.com>");
        assertEquals(email.getRecipientAddresses().get(0), "\"The \\\"Fun Guys\\\" at UofW\" <test@test.com>");
    }

    @Test
    public void multipleRecipients() throws Exception {
        List<String> recipientList = ImmutableList.of("recipient1@example.com", "recipient2@example.com",
                "recipient3@example.com");
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipients(recipientList).withMessageParts(makeBodyPart("dummy content"))
                .withType(EmailType.EMAIL_SIGN_IN).build();
        assertEquals(email.getRecipientAddresses(), recipientList);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void filterOutNullMessagePartVarargs() {
        // This will throw an IllegalArgumentException, since we don't allow null message parts list.
        new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com").withMessageParts((MimeBodyPart[]) null)
                .withType(EmailType.EMAIL_SIGN_IN).build();
    }

    @Test
    public void filterOutNullMessageParts() throws Exception {
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com")
                .withMessageParts(null, makeBodyPart("foo"), null, makeBodyPart("bar"), null)
                .withType(EmailType.EMAIL_SIGN_IN).build();

        List<MimeBodyPart> partList = email.getMessageParts();
        assertEquals(partList.size(), 2);
        assertEquals(partList.get(0).getContent(), "foo");
        assertEquals(partList.get(1).getContent(), "bar");
    }

    @Test
    public void defaultType() throws Exception {
        MimeTypeEmail email = new MimeTypeEmailBuilder().withSubject(SUBJECT).withSender("sender@example.com")
                .withRecipient("recipient@example.com").withMessageParts(makeBodyPart("dummy content"))
                .withType(null).build();
        assertEquals(email.getType(), EmailType.UNKNOWN);
    }
}
