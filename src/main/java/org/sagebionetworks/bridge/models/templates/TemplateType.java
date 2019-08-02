package org.sagebionetworks.bridge.models.templates;

import java.util.EnumSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public enum TemplateType {
    EMAIL_ACCOUNT_EXISTS("${url}", "${emailSignInUrl}", "${resetPasswordUrl}"),
    EMAIL_APP_INSTALL_LINK("${url}", "${appInstallUrl}"),
    EMAIL_RESET_PASSWORD("${url}", "${resetPasswordUrl}"),
    EMAIL_SIGN_IN("${url}", "${emailSignInUrl}", "${token}"),
    EMAIL_SIGNED_CONSENT(),
    EMAIL_VERIFY_EMAIL("${url}", "${emailVerificationUrl}"),
    SMS_ACCOUNT_EXISTS("${token}", "${resetPasswordUrl}"),
    SMS_APP_INSTALL_LINK("${url}", "${appInstallUrl}"),
    SMS_PHONE_SIGN_IN("${token}"),
    SMS_RESET_PASSWORD("${url}", "${resetPasswordUrl}"),
    SMS_SIGNED_CONSENT("${consentUrl}"),
    SMS_VERIFY_PHONE("${token}");
    
    private final Set<String> requiredVars;
    
    private TemplateType(String... requiredVars) {
        this.requiredVars = ImmutableSet.copyOf(requiredVars);
    }
    
    public Set<String> getRequiredVariables() {
        return requiredVars;
    }
    public final static EnumSet<TemplateType> EMAIL_TYPES = EnumSet.of(EMAIL_ACCOUNT_EXISTS, EMAIL_APP_INSTALL_LINK,
            EMAIL_RESET_PASSWORD, EMAIL_SIGN_IN, EMAIL_SIGNED_CONSENT, EMAIL_VERIFY_EMAIL);
}