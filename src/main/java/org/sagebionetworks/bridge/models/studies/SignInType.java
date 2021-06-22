package org.sagebionetworks.bridge.models.studies;

public enum SignInType {
    /** Study expects participants to sign in with an email address and a password. */
    EMAIL_PASSWORD,
    /** Study expects participants to sign in with a phone number and a password. */
    PHONE_PASSWORD,
    /** Study expects participants to sign in with an external ID and a password. */
    EXTERNAL_ID_PASSWORD,
    /** Study expects participants to sign in by sending an email message to the 
     * participant’s email account with a sign in code or sign in link. 
     */
    EMAIL_MESSAGE,
    /** Study expects participants to sign in by sending an SMS message to the 
     * participant’s phone number with a sign in code or sign in link. 
     */
    PHONE_MESSAGE
}
