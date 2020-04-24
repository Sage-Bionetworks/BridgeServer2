package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BasicEmailProvider extends MimeTypeEmailProvider {
    private final String overrideSenderEmail;
    private final Set<String> recipientEmails;
    private final Map<String,String> tokenMap;
    private final TemplateRevision revision;
    private final List<MimeBodyPart> attachments;
    private final EmailType type;
    
    private BasicEmailProvider(App app, String overrideSenderEmail, Map<String,String> tokenMap,
            Set<String> recipientEmails, TemplateRevision revision, List<MimeBodyPart> attachments, EmailType type) {
        super(app);
        this.overrideSenderEmail = overrideSenderEmail;
        this.recipientEmails = recipientEmails;
        this.tokenMap = tokenMap;
        this.revision = revision;
        this.attachments = attachments;
        this.type = type;
    }

    /**
     * If overrideSenderEmail is specified, returns that. Otherwise, returns the plain sender email from the study,
     * as specified in {@link MimeTypeEmailProvider#getPlainSenderEmail}.
     */
    @Override
    public String getPlainSenderEmail() {
        if (isNotBlank(overrideSenderEmail)) {
            return overrideSenderEmail;
        } else {
            return super.getPlainSenderEmail();
        }
    }

    public Set<String> getRecipientEmails() {
        return recipientEmails;
    }
    public Map<String,String> getTokenMap() {
        return tokenMap;
    }
    public TemplateRevision getTemplateRevision() {
        return revision;
    }

    /** Email type. Examples include email verification, sign-in, consent, etc. */
    public EmailType getType() {
        return type;
    }

    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        final MimeTypeEmailBuilder emailBuilder = new MimeTypeEmailBuilder();

        final String formattedSubject = BridgeUtils.resolveTemplate(revision.getSubject(), tokenMap);
        emailBuilder.withSubject(formattedSubject);

        final String sendFromEmail = getFormattedSenderEmail();
        emailBuilder.withSender(sendFromEmail);

        for (String recipientEmail : recipientEmails) {
            emailBuilder.withRecipient(recipientEmail);    
        }
        
        final MimeBodyPart bodyPart = new MimeBodyPart();
        final String formattedBody = BridgeUtils.resolveTemplate(revision.getDocumentContent(), tokenMap);
        bodyPart.setContent(formattedBody, revision.getMimeType().toString() + "; charset=utf-8");
        emailBuilder.withMessageParts(bodyPart);
        
        for (MimeBodyPart attachment : attachments) {
            emailBuilder.withMessageParts(attachment);
        }

        // Set type.
        emailBuilder.withType(type);

        return emailBuilder.build();
    }

    public static class Builder {
        private App app;
        private String overrideSenderEmail;
        private Map<String,String> tokenMap = Maps.newHashMap();
        private Set<String> recipientEmails = Sets.newHashSet();
        private List<MimeBodyPart> attachments = Lists.newArrayList();
        private TemplateRevision revision;
        private EmailType type;

        public Builder withStudy(App app) {
            this.app = app;
            return this;
        }

        public Builder withBinaryAttachment(String partName, MimeType mimeType, byte[] data) {
            checkArgument(isNotBlank(partName));
            checkNotNull(mimeType);
            checkArgument(data != null && data.length > 0);
            try {
                final MimeBodyPart attachment = new MimeBodyPart();
                DataSource source = new ByteArrayDataSource(data, mimeType.toString());
                attachment.setDataHandler(new DataHandler(source));
                attachment.setFileName(partName);
                attachments.add(attachment);
                return this;
            } catch(MessagingException me) {
                throw new RuntimeException(me);
            }
        }
        
        /**
         * Specify the sender email, instead of getting it from the study. This is the plain, unformmated email, for
         * example "example@example.com".
         */
        public Builder withOverrideSenderEmail(String overrideSenderEmail) {
            this.overrideSenderEmail = overrideSenderEmail;
            return this;
        }

        public Builder withRecipientEmail(String recipientEmail) {
            if (recipientEmail != null) {
                this.recipientEmails.add(recipientEmail);    
            }
            return this;
        }
        public Builder withTemplateRevision(TemplateRevision revision) {
            this.revision = revision;
            return this;
        }
        public Builder withToken(String name, String value) {
            tokenMap.put(name, value);
            return this;
        }
        public Builder withExpirationPeriod(String name, int expireInSeconds) {
            withToken(name, BridgeUtils.secondsToPeriodString(expireInSeconds));
            return this;
        }

        /** @see BasicEmailProvider#getType */
        public Builder withType(EmailType type) {
            this.type = type;
            return this;
        }

        public BasicEmailProvider build() {
            checkNotNull(app);
            checkNotNull(revision);
            
            tokenMap.putAll(BridgeUtils.studyTemplateVariables(app));
            // Nulls will cause ImmutableMap.of to fail
            tokenMap.values().removeIf(Objects::isNull);
            
            return new BasicEmailProvider(app, overrideSenderEmail, ImmutableMap.copyOf(tokenMap), recipientEmails,
                    revision, attachments, type);
        }
    }
}
