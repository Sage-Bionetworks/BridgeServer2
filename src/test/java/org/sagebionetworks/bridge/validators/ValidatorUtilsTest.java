package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.models.apps.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.INVALID_HEX_TRIPLET;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.INVALID_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_LONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_PERIOD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validatePassword;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.springframework.validation.Errors;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class ValidatorUtilsTest extends Mockito {

    @Test
    public void participantHasValidIdentifierValidEmail() {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void participantHasValidIdentifierValiPhone() {
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(PHONE).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void participantHasValidIdentifierValidExternalIds() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of(TEST_STUDY_ID, "extId")).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void participantHasValidIdentifierValidSynapseUserId() {
        StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void participantHasValidIdentifierInvalid() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalIds(ImmutableMap.of()).build();
        assertFalse(ValidatorUtils.participantHasValidIdentifier(participant));

        participant = new StudyParticipant.Builder().build();
        assertFalse(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void accountHasValidIdentifierValidEmail() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }

    @Test
    public void accountHasValidIdentifierValiPhone() {
        Account account = Account.create();
        account.setPhone(PHONE);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }

    @Test
    public void accountHasValidIdentifierValidEnrollment() {
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, "externalID");
        account.setEnrollments(ImmutableSet.of(en1, en2));

        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }

    @Test
    public void accountHasValidIdentifierValidSynapseUserId() {
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }

    @Test
    public void accountHasValidIdentifierInvalid() {
        Account account = Account.create();
        account.setEnrollments(null);
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));

        account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en1));
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));

        account = Account.create();
        account.setEnrollments(ImmutableSet.of());
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));
    }

    @Test
    public void validateLabels_emptyValid() {
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateLabels(errors, ImmutableList.of());
    }

    @Test
    public void validateLabels_nullValid() {
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateLabels(errors, null);
    }

    @Test
    public void validateLabels_duplicateLang() {
        Errors errors = mock(Errors.class);

        List<Label> list = ImmutableList.of(new Label("en", "foo"), new Label("en", "bar"));
        ValidatorUtils.validateLabels(errors, list);

        verify(errors).pushNestedPath("labels[0]");
        verify(errors).rejectValue("lang", DUPLICATE_LANG);
    }

    @Test
    public void validateLabels_invalidLang() {
        Errors errors = mock(Errors.class);

        List<Label> list = ImmutableList.of(new Label("yyyy", "foo"));
        ValidatorUtils.validateLabels(errors, list);

        verify(errors).pushNestedPath("labels[0]");
        verify(errors).rejectValue("lang", INVALID_LANG);
    }

    @Test
    public void validateLanguageSet_missingLang() {
        Errors errors = mock(Errors.class);

        List<Label> list = ImmutableList.of(new Label("", "foo"));
        ValidatorUtils.validateLabels(errors, list);

        verify(errors).pushNestedPath("labels[0]");
        verify(errors).rejectValue("lang", CANNOT_BE_BLANK);
    }

    @Test
    public void validateLabels_valueBlank() {
        Errors errors = mock(Errors.class);

        List<Label> list = ImmutableList.of(new Label("en", ""));
        ValidatorUtils.validateLabels(errors, list);

        verify(errors, times(2)).pushNestedPath("labels[0]");
        verify(errors).rejectValue("value", CANNOT_BE_BLANK);
    }

    @Test
    public void validateLabels_valueNull() {
        Errors errors = mock(Errors.class);

        List<Label> list = ImmutableList.of(new Label("en", null));
        ValidatorUtils.validateLabels(errors, list);

        verify(errors, times(2)).pushNestedPath("labels[0]");
        verify(errors).rejectValue("value", CANNOT_BE_BLANK);
    }

    // Only minutes, hours, days, and weeks are allowed for the more fine-grained
    // Duration
    // fields, and days or weeks for the longer Duration fields.

    @Test
    public void validateFixedPeriodMonthsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3M");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodSecondsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT180S");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodYearsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedPeriodValidMinutesPeriod() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT30M");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedPeriodMixedWorks() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W3DT30M");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedPeriodMixedFails() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y2W3DT30M");

        validateFixedLengthPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodRequiredIsMissing() {
        Errors errors = mock(Errors.class);

        validateFixedLengthPeriod(errors, null, "period", true);

        verify(errors).rejectValue("period", CANNOT_BE_NULL);
    }

    @Test
    public void validateFixedPeriodCannotBeNegative() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W-3D");

        validateFixedLengthPeriod(errors, period, "period", true);

        // Despite adding up to a positive value, we don't allow it.
        verify(errors).rejectValue("period", CANNOT_BE_NEGATIVE);
    }

    // For the long period, only days and weeks are allowed.

    @Test
    public void validateFixedLongPeriodMonthsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3M");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodSecondsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT180S");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodYearsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongWeeksValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedLongDaysValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P12D");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedLongPeriodMixedWorks() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W3D");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedLongPeriodMixedFails() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y2W3DT30M");

        validateFixedLengthLongPeriod(errors, period, "period", false);

        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodRequiredIsMissing() {
        Errors errors = mock(Errors.class);

        validateFixedLengthLongPeriod(errors, null, "period", true);

        verify(errors).rejectValue("period", CANNOT_BE_NULL);
    }

    @Test
    public void validateFixedLongPeriodCannotBeNegative() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W-3D");

        validateFixedLengthLongPeriod(errors, period, "period", true);

        // Despite adding up to a positive value, we don't allow it.
        verify(errors).rejectValue("period", CANNOT_BE_NEGATIVE);
    }

    @Test
    public void validateFixedLongPeriodCannotBeZero() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT0S"); // actual value submitted by a client

        validateFixedLengthLongPeriod(errors, period, "period", true);

        verify(errors).rejectValue("period", "cannot be of no duration");
    }

    @Test
    public void validateFixedPeriodCannotBeZero() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P0DT0H");

        validateFixedLengthPeriod(errors, period, "period", true);

        verify(errors).rejectValue("period", "cannot be of no duration");
    }

    @Test
    public void periodInMinutes() {
        Period period = Period.parse("P3W2DT10H14M"); // 33,734 minutes
        assertEquals(ValidatorUtils.periodInMinutes(period), 33734);

        period = Period.parse("P0W0DT0H0M"); // 0 minutes
        assertEquals(ValidatorUtils.periodInMinutes(period), 0);
    }

    @Test
    public void periodInDays() {
        Period period = Period.parse("P3W2D"); // 23 days
        assertEquals(ValidatorUtils.periodInDays(period), 23);

        period = Period.parse("P0W0DT24H"); // 1 days
        assertEquals(ValidatorUtils.periodInDays(period), 1);
    }

    @Test
    public void validatePassword_isBlank() {
        Errors errors = mock(Errors.class);
        validatePassword(errors, DEFAULT_PASSWORD_POLICY, "");
        verify(errors).rejectValue("password", "is required");
    }

    @Test
    public void validatePassword_nothingRequired() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(0, false, false, false, false);
        validatePassword(errors, policy, "m");
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validatePassword_minLength() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(2, false, false, false, false);
        validatePassword(errors, policy, "m");
        verify(errors).rejectValue("password", "must be at least 2 characters");
    }

    @Test
    public void validatePassword_numericRequired() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(0, true, false, false, false);
        validatePassword(errors, policy, "m");
        verify(errors).rejectValue("password", "must contain at least one number (0-9)");
    }

    @Test
    public void validatePassword_symbolRequired() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(0, false, true, false, false);
        validatePassword(errors, policy, "m");
        verify(errors).rejectValue("password",
                "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }

    @Test
    public void validatePassword_lowercaseRequired() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(0, false, false, true, false);
        validatePassword(errors, policy, "M");
        verify(errors).rejectValue("password", "must contain at least one lowercase letter (a-z)");
    }

    @Test
    public void validatePassword_uppercaseRequired() {
        Errors errors = mock(Errors.class);
        PasswordPolicy policy = new PasswordPolicy(0, false, false, false, true);
        validatePassword(errors, policy, "2");
        verify(errors).rejectValue("password", "must contain at least one uppercase letter (A-Z)");
    }

    @Test
    public void messagesValid() {
        List<NotificationMessage> messages = ImmutableList.of(
                new NotificationMessage.Builder().withLang("en").withSubject("subject").withMessage("message").build(),
                new NotificationMessage.Builder().withLang("de").withSubject("subject").withMessage("message").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void messagesNullOK() {
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, null);
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void messagesEmptyOK() {
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, ImmutableList.of());
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void messagesMustContainEnglishDefault() {
        List<NotificationMessage> messages = ImmutableList.of(new NotificationMessage.Builder().withLang("fr").build(),
                new NotificationMessage.Builder().withLang("de").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).rejectValue("messages", "must include an English-language message as a default");
    }

    @Test
    public void messageLanguageBlank() {
        List<NotificationMessage> messages = ImmutableList.of(new NotificationMessage.Builder().withLang("").build(),
                new NotificationMessage.Builder().withLang("en").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("lang", CANNOT_BE_BLANK);
    }

    @Test
    public void messageLanguageNull() {
        List<NotificationMessage> messages = ImmutableList.of(new NotificationMessage.Builder().withLang(null).build(),
                new NotificationMessage.Builder().withLang("en").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("lang", CANNOT_BE_BLANK);
    }

    @Test
    public void messageLanguageCodeDuplicated() throws Exception {
        List<NotificationMessage> messages = ImmutableList.of(new NotificationMessage.Builder().withLang("en").build(),
                new NotificationMessage.Builder().withLang("en").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[1]");
        verify(errors).rejectValue("lang", DUPLICATE_LANG);
    }

    @Test
    public void messsageLanguageCodeInvalid() {
        List<NotificationMessage> messages = ImmutableList
                .of(new NotificationMessage.Builder().withLang("yyy").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("lang", INVALID_LANG);
    }

    @Test
    public void messageSubjectBlank() {
        List<NotificationMessage> messages = ImmutableList
                .of(new NotificationMessage.Builder().withLang("en").withSubject("\t\n").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("subject", CANNOT_BE_BLANK);
    }

    @Test
    public void messageSubjectNull() {
        List<NotificationMessage> messages = ImmutableList
                .of(new NotificationMessage.Builder().withLang("en").withSubject(null).build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("subject", CANNOT_BE_BLANK);
    }

    @Test
    public void messageSubjectTooLong() {
        List<NotificationMessage> messages = ImmutableList
                .of(new NotificationMessage.Builder().withLang("en").withSubject(StringUtils.repeat("X", 100)).build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("subject", "must be 40 characters or less");
    }

    @Test
    public void messageBlank() {
        List<NotificationMessage> messages = ImmutableList.of(
                new NotificationMessage.Builder().withLang("en").withSubject("subject").withMessage("\n\t").build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("message", CANNOT_BE_BLANK);
    }

    @Test
    public void messageNull() {
        List<NotificationMessage> messages = ImmutableList
                .of(new NotificationMessage.Builder().withLang("en").withMessage(null).build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("message", CANNOT_BE_BLANK);
    }

    @Test
    public void messageTooLong() {
        List<NotificationMessage> messages = ImmutableList.of(new NotificationMessage.Builder().withLang("en")
                .withSubject("subject").withMessage(StringUtils.repeat("X", 100)).build());
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateMessages(errors, messages);
        verify(errors, times(2)).pushNestedPath("messages[0]");
        verify(errors).rejectValue("message", "must be 60 characters or less");
    }

    @Test
    public void backgroundColorInValid() {
        ColorScheme scheme = new ColorScheme("#FFFF1G", null, null, null);

        Errors errors = mock(Errors.class);
        ValidatorUtils.validateColorScheme(errors, scheme, "colorScheme");
        verify(errors).pushNestedPath("colorScheme");
        verify(errors).rejectValue("background", INVALID_HEX_TRIPLET);
    }

    @Test
    public void foregroundColorInValid() {
        ColorScheme scheme = new ColorScheme(null, "#FFF", null, null);

        Errors errors = mock(Errors.class);
        ValidatorUtils.validateColorScheme(errors, scheme, "colorScheme");
        verify(errors).pushNestedPath("colorScheme");
        verify(errors).rejectValue("foreground", INVALID_HEX_TRIPLET);
    }

    @Test
    public void activatedColorInValid() {
        ColorScheme scheme = new ColorScheme(null, null, "000", null);

        Errors errors = mock(Errors.class);
        ValidatorUtils.validateColorScheme(errors, scheme, "colorScheme");
        verify(errors).pushNestedPath("colorScheme");
        verify(errors).rejectValue("activated", INVALID_HEX_TRIPLET);
    }

    @Test
    public void inactivatedColorInValid() {
        ColorScheme scheme = new ColorScheme(null, null, null, "cccccc");

        Errors errors = mock(Errors.class);
        ValidatorUtils.validateColorScheme(errors, scheme, "colorScheme");
        verify(errors).pushNestedPath("colorScheme");
        verify(errors).rejectValue("inactivated", INVALID_HEX_TRIPLET);
    }
    
    @Test
    public void validateStringLength_valid() {
        String validString = "under 10";
        
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateStringLength(errors, 10, validString, "testFieldName");
    
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateStringLength_tooLong() {
        String tooLong = "more than 10 characters";
        
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateStringLength(errors, 10, tooLong, "testFieldName");
        
        verify(errors).rejectValue("testFieldName", getInvalidStringLengthMessage(10));
    }
    
    @Test
    public void validateStringLength_nullSafe() {
        Errors errors = mock(Errors.class);
        ValidatorUtils.validateStringLength(errors, 10, null, "testFieldName");
        
        verify(errors, never()).rejectValue(any(), any());
    }
}
