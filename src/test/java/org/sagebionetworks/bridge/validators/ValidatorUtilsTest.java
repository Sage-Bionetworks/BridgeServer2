package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_LANG;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.springframework.validation.Errors;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Label;
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
    
    @Test
    public void validateLanguageSet_Null() {
        Errors errors = mock(Errors.class);
        List<Label> labels = null;
        
        ValidatorUtils.validateLanguageSet(errors, labels, "fields");
        
        verifyNoMoreInteractions(errors);
    }

    @Test
    public void validateLanguageSet_Empty() {
        Errors errors = mock(Errors.class);
        List<Label> labels = ImmutableList.of();
        
        ValidatorUtils.validateLanguageSet(errors, labels, "fields");
        
        verifyNoMoreInteractions(errors);
    }

    @Test
    public void validateLanguageSet_LangInvalid() {
        Errors errors = mock(Errors.class);
        List<Label> labels = ImmutableList.of(new Label("yyy", "Bad label"));
        
        ValidatorUtils.validateLanguageSet(errors, labels, "fields");
        
        verify(errors).pushNestedPath("fields[0]");
        verify(errors).rejectValue("lang", INVALID_LANG);
    }

    @Test
    public void validateLanguageSet_LangMissing() {
        Errors errors = mock(Errors.class);
        List<Label> labels = ImmutableList.of(new Label("", "Bad label"));
        
        ValidatorUtils.validateLanguageSet(errors, labels, "fields");
        
        verify(errors).pushNestedPath("fields[0]");
        verify(errors).rejectValue("lang", CANNOT_BE_BLANK);
    }
    
    @Test
    public void validateLanguageSet_LangDuplicated() {
        Errors errors = mock(Errors.class);
        List<Label> labels = ImmutableList.of(new Label("en", "First label"), 
                new Label("en", "Duplicate label"));
        
        ValidatorUtils.validateLanguageSet(errors, labels, "fields");
        
        verify(errors).pushNestedPath("fields[1]");
        verify(errors).rejectValue("lang", DUPLICATE_LANG);
    }
}
