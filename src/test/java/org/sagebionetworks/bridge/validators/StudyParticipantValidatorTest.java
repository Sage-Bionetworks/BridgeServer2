package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.testng.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.SubstudyService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class StudyParticipantValidatorTest {
    
    private static final Set<String> APP_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> APP_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2,bluebell");
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TEST_APP_ID, "id");
    private App app;

    private StudyParticipantValidator validator;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Mock
    private SubstudyService substudyService;
    
    @Mock
    private OrganizationDao mockOrganizationDao;
    
    private Substudy substudy;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        substudy = Substudy.create();
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setHealthCodeExportEnabled(true);
        app.setUserProfileAttributes(APP_PROFILE_ATTRS);
        app.setDataGroups(APP_DATA_GROUPS);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.getUserProfileAttributes().add("phone");
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void validatesNew() throws Exception {
        validator = makeValidator(true);
        app.setExternalIdRequiredOnSignup(true);
        
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .build();
        assertValidatorMessage(validator, participant, "StudyParticipant", "email, phone, synapseUserId or externalId is required");
        assertValidatorMessage(validator, participant, "externalId", "is required");
        assertValidatorMessage(validator, participant, "dataGroups", "'badGroup' is not defined for app (use group1, group2, bluebell)");
        assertValidatorMessage(validator, participant, "attributes", "'badValue' is not defined for app (use attr1, attr2, phone)");
        assertValidatorMessage(validator, participant, "password", "must be at least 8 characters");
        assertValidatorMessage(validator, participant, "password", "must contain at least one number (0-9)");
        assertValidatorMessage(validator, participant, "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
        assertValidatorMessage(validator, participant, "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    // Password, email address, and externalId (if being validated) cannot be updated, so these don't need to be validated.
    @Test
    public void validatesUpdate() {
        validator = makeValidator(false);
        
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .build();
        
        try {
            Validate.entityThrowingException(validator, participant);
        } catch(InvalidEntityException e) {
            assertNull(e.getErrors().get("email"));
            assertNull(e.getErrors().get("externalId"));
            assertNull(e.getErrors().get("password"));
        }
        assertValidatorMessage(validator, participant, "dataGroups", "'badGroup' is not defined for app (use group1, group2, bluebell)");
        assertValidatorMessage(validator, participant, "attributes", "'badValue' is not defined for app (use attr1, attr2, phone)");
    }
    
    @Test
    public void validatesIdForNew() {
        // not new, this succeeds
        validator = makeValidator(true); 
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void validatesIdForExisting() {
        // not new, this should fail, as there's no ID in participant.
        validator = makeValidator(false); 
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
    }
    
    @Test
    public void validPasses() {
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
        Validate.entityThrowingException(validator, withDataGroup("bluebell"));
        Validate.entityThrowingException(validator, withSynapseUserId(SYNAPSE_USER_ID));
    }
    
    @Test
    public void emailPhoneSynapseUserIdOrExternalIdRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withEmail(null), "StudyParticipant", "email, phone, synapseUserId or externalId is required");
    }
    
    @Test
    public void emailCannotBeEmptyString() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withEmail(""), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeBlankString() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withEmail("    \n    \t "), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeInvalid() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withEmail("a"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void externalIdOnlyOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("external-id").build();

        when(externalIdService.getExternalId(TEST_APP_ID, "external-id"))
                .thenReturn(Optional.of(ExternalIdentifier.create(TEST_APP_ID, "external-id")));
        
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    
    @Test
    public void synapseUserIdOnlyOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID).build();

        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    
    @Test
    public void emptyStringPasswordRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword(""), "password", "is required");
    }
    
    @Test
    public void emptyStringUserSynapseIdRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withSynapseUserId(""), "synapseUserId", "cannot be blank");
    }
    
    @Test
    public void nullPasswordOK() {
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, withPassword(null));
    }
    
    @Test
    public void validEmail() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withEmail("belgium"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void minLength() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword("a1A~"), "password", "must be at least 8 characters");
    }
    
    @Test
    public void numberRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword("aaaaaaaaA~"), "password", "must contain at least one number (0-9)");
    }
    
    @Test
    public void symbolRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword("aaaaaaaaA1"), "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }
    
    @Test
    public void lowerCaseRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword("AAAAA!A1"), "password", "must contain at least one lowercase letter (a-z)");
    }
    
    @Test
    public void upperCaseRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPassword("aaaaa!a1"), "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSupplied() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withDataGroup("squirrel"), "dataGroups", "'squirrel' is not defined for app (use group1, group2, bluebell)");
    }
    
    @Test
    public void validatePhoneRegionRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPhone("1234567890", null), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRegionIsCode() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPhone("1234567890", "esg"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRequired() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPhone(null, "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhonePattern() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPhone("234567890", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhone() {
        validator = makeValidator(true);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withPassword("pAssword1@").withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(validator, participant);
    }
    
    @Test
    public void validateTotallyWrongPhone() {
        validator = makeValidator(true);
        assertValidatorMessage(validator, withPhone("this isn't a phone number", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void createWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(app.getIdentifier(), "foo")).thenReturn(Optional.of(EXT_ID));
        StudyParticipant participant = withExternalId("foo");

        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        StudyParticipant participant = withExternalId("wrong-external-id");
        
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "externalId", "is not a valid external ID");
    }
    @Test
    public void createWithoutExternalIdManagedOk() {
        StudyParticipant participant = withEmail("email@email.com");
        
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithoutExternalIdManagedInvalid() {
        app.setExternalIdRequiredOnSignup(true);
        StudyParticipant participant = withEmail("email@email.com");
        
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "externalId", "is required");
    }
    @Test
    public void createWithoutExternalIdManagedButHasRolesOK() {
        app.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(app.getIdentifier(), "foo")).thenReturn(Optional.of(EXT_ID));
        StudyParticipant participant = withExternalIdAndId("foo");
        
        validator = makeValidator(false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        StudyParticipant participant = withExternalId("does-not-exist");
        
        validator = makeValidator(false);
        assertValidatorMessage(validator, participant, "externalId", "is not a valid external ID");
    }
    @Test
    public void updateWithoutExternalIdManagedOk() {
        StudyParticipant participant = withEmailAndId("email@email.com");
        
        validator = makeValidator(false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void emptyExternalIdInvalidOnCreate() {
        StudyParticipant participant = withExternalId(" ");
        
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "externalId", "cannot be blank");
    }
    @Test
    public void emptyExternalIdInvalidOnUpdate() {
        StudyParticipant participant = withExternalId(" ");
        
        validator = makeValidator(false);
        assertValidatorMessage(validator, participant, "externalId", "cannot be blank");
    }
    @Test
    public void emptySynapseUserIdOnCreate() {
        StudyParticipant participant = withSynapseUserId(" ");
        
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "synapseUserId", "cannot be blank");
    }
    @Test
    public void emptySynapseUserIdOnUpdate() {
        StudyParticipant participant = withSynapseUserId(" ");
        
        validator = makeValidator(false);
        assertValidatorMessage(validator, participant, "synapseUserId", "cannot be blank");
    }
    @Test
    public void substudyAllowedIfCallerHasNoSubstudies() {
        // In other words, you can "taint" a user with substudies, putting them in a limited security role.
        StudyParticipant participant = withSubstudies("substudyA", "substudyB");
        
        when(substudyService.getSubstudy(app.getIdentifier(), "substudyA", false)).thenReturn(substudy);
        when(substudyService.getSubstudy(app.getIdentifier(), "substudyB", false)).thenReturn(substudy);
        
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void subsetOfsubstudiesOK() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyB", "substudyC")).build());
        try {
            // The user (in three substudies) can create a participant in only one of those substudies
            StudyParticipant participant = withSubstudies("substudyB");
            
            when(substudyService.getSubstudy(app.getIdentifier(), "substudyB", false)).thenReturn(substudy);
            
            validator = makeValidator(true);
            Validate.entityThrowingException(validator, participant);
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    @Test
    public void nonexistentSubstudyIds() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyC")).build());
        try {
            StudyParticipant participant = withSubstudies("substudyA");
            
            validator = makeValidator(true);
            assertValidatorMessage(validator, participant, "substudyIds[substudyA]", "is not a substudy");
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    @Test
    public void validateOrganization() {
        when(mockOrganizationDao.getOrganization(TEST_APP_ID, TEST_ORG_ID)).thenReturn(Optional.empty());
        
        StudyParticipant participant = withMemberOrganization(TEST_ORG_ID);
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "orgMembership", "is not a valid organization");
    }
    @Test
    public void validateOrgIsSameAsCallers() {
        when(mockOrganizationDao.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        StudyParticipant participant = withMemberOrganization(TEST_ORG_ID);
        validator = makeValidator(true);
        assertValidatorMessage(validator, participant, "orgMembership", "cannot be set by caller");
    }
    @Test
    public void validateOrgCanBeDifferentFromSuperadmin() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership("someOtherOrgId")
                .withCallerRoles(ImmutableSet.of(Roles.SUPERADMIN))
                .build());
        
        when(mockOrganizationDao.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        StudyParticipant participant = withMemberOrganization(TEST_ORG_ID);
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void organizationOK() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        when(mockOrganizationDao.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));
        
        StudyParticipant participant = withMemberOrganization(TEST_ORG_ID);
        validator = makeValidator(true);
        Validate.entityThrowingException(validator, participant);
    }
    
    private StudyParticipantValidator makeValidator(boolean isNew) {
        return new StudyParticipantValidator(externalIdService, substudyService, mockOrganizationDao, app, isNew);
    }

    private StudyParticipant withSubstudies(String... substudyIds) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withSubstudyIds(ImmutableSet.copyOf(substudyIds)).build();
    }
    private StudyParticipant withMemberOrganization(String orgId) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withOrgMembership(orgId).build();
    }
    private StudyParticipant withPhone(String phone, String phoneRegion) {
        return new StudyParticipant.Builder().withPhone(new Phone(phone, phoneRegion)).build();
    }
    
    private StudyParticipant withPassword(String password) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword(password).build();
    }
    
    private StudyParticipant withEmail(String email) {
        return new StudyParticipant.Builder().withEmail(email).withPassword("aAz1%_aAz1%").build();
    }
    
    private StudyParticipant withSynapseUserId(String synapseUserId) {
        return new StudyParticipant.Builder().withSynapseUserId(synapseUserId).build();
    }
    
    private StudyParticipant withEmailAndId(String email) {
        return new StudyParticipant.Builder().withId("id").withEmail(email).withPassword("aAz1%_aAz1%").build();
    }
    
    private StudyParticipant withExternalId(String externalId) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withExternalId(externalId).build();
    }
    
    private StudyParticipant withExternalIdAndId(String externalId) {
        return new StudyParticipant.Builder().withId("id").withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withExternalId(externalId).build();
    }
    
    private StudyParticipant withDataGroup(String dataGroup) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withDataGroups(Sets.newHashSet(dataGroup)).build();
    }
}
