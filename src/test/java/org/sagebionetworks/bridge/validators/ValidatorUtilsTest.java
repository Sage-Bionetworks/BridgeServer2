package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.validators.Validate.WRONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedPeriod;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.springframework.validation.Errors;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class ValidatorUtilsTest extends Mockito {
    
    @Test
    public void participantHasValidIdentifierValidEmail() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }
    
    @Test
    public void participantHasValidIdentifierValiPhone() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(PHONE).build();
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
        StudyParticipant participant = new StudyParticipant.Builder()
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }
    
    @Test
    public void participantHasValidIdentifierInvalid() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of()).build();
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
    
    // There are more duration units in Joda than there are specified in the ISO 8601 Duration 
    // specification, so we're only testing ISO 8601 units here. There are values in the 
    // prohibition list that I don't think you can trigger from a Joda Period. Only minutes, 
    // hours, days, and weeks are allowed.
    
    @Test
    public void monthsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3M");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void secondsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT180S");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void yearsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }
    
    @Test
    public void validPeriod() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void validMinutesPeriod() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void mixedWorks() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W3DT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void mixedFails() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y2W3DT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }
    
    // TODO: tests for required, and for negative values. No, we don't allow mixed
    // positive and negative that add up to a positive value.
}
