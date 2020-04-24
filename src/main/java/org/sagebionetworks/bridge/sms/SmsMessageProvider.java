package org.sagebionetworks.bridge.sms;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class SmsMessageProvider {
    private final App app;
    private final Map<String,String> tokenMap;
    private final Phone phone;
    private final SmsType smsType;
    private final TemplateRevision revision;

    private SmsMessageProvider(App app, TemplateRevision revision, SmsType smsType, Phone phone,
            Map<String, String> tokenMap) {
        this.app = app;
        this.smsType = smsType;
        this.revision = revision;
        this.phone = phone;
        this.tokenMap = tokenMap;
    }

    public App getStudy() {
        return app;
    }
    public TemplateRevision getTemplateRevision() {
        return revision;
    }
    public Phone getPhone() {
        return phone;
    }

    /**
     * Returns the SMS type as a string. This method exists for backwards compatibility. To get the enum, use
     * {@link #getSmsTypeEnum}.
     */
    public String getSmsType() {
        return smsType.getValue();
    }

    /** Returns the SMS type (Promotional vs Transactional). */
    public SmsType getSmsTypeEnum() {
        return smsType;
    }

    public Map<String,String> getTokenMap() {
        return tokenMap;
    }

    /** SMS message to send, with template variables resolved. */
    public String getFormattedMessage() {
        return BridgeUtils.resolveTemplate(revision.getDocumentContent(), tokenMap).trim();
    }

    public PublishRequest getSmsRequest() {
        Map<String, MessageAttributeValue> smsAttributes = Maps.newHashMap();
        smsAttributes.put(BridgeConstants.AWS_SMS_TYPE, attribute(getSmsType()));
        smsAttributes.put(BridgeConstants.AWS_SMS_SENDER_ID, attribute(tokenMap.get("studyShortName")));
        // Costs seem too low to worry about this, but if need be, this is how we'd cap it.
        // smsAttributes.put("AWS.SNS.SMS.MaxPrice", attribute("0.50")); max price set to $.50

        return new PublishRequest()
                .withMessage(getFormattedMessage())
                .withPhoneNumber(phone.getNumber())
                .withMessageAttributes(smsAttributes);
    }
    
    private MessageAttributeValue attribute(String value) {
        return new MessageAttributeValue().withStringValue(value).withDataType("String");
    }
    
    public static class Builder {
        private App app;
        private Map<String,String> tokenMap = Maps.newHashMap();
        private Phone phone;
        private SmsType smsType;
        private TemplateRevision revision;

        public Builder withStudy(App app) {
            this.app = app;
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
        public Builder withPhone(Phone phone) {
            this.phone = phone;
            return this;
        }
        public Builder withTransactionType() {
            this.smsType = SmsType.TRANSACTIONAL;
            return this;
        }
        public Builder withPromotionType() {
            this.smsType = SmsType.PROMOTIONAL;
            return this;
        }
        public Builder withExpirationPeriod(String name, int expireInSeconds) {
            withToken(name, BridgeUtils.secondsToPeriodString(expireInSeconds));
            return this;
        }
        public SmsMessageProvider build() {
            checkNotNull(app);
            checkNotNull(revision);
            checkNotNull(phone);
            checkNotNull(smsType);
            tokenMap.putAll(BridgeUtils.studyTemplateVariables(app));
            
            // overwriting the study's short name field with a default value, if needed
            String studyShortName = StringUtils.isBlank(app.getShortName()) ? "Bridge" : app.getShortName();
            tokenMap.put("studyShortName", studyShortName);
            // remove nulls, these will cause ImmutableMap.of to fail
            tokenMap.values().removeIf(Objects::isNull);

            return new SmsMessageProvider(app, revision, smsType, phone, ImmutableMap.copyOf(tokenMap));
        }
    }
}
