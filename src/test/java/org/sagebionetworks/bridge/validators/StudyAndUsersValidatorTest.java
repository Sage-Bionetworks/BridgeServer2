package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.testng.collections.Lists.newArrayList;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.StudyAndUsers;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;

public class StudyAndUsersValidatorTest extends Mockito {

    @Mock
    SynapseClient mockSynapseClient;
    
    StudyAndUsersValidator validator;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        validator = new StudyAndUsersValidator();
        validator.setSynapseClient(mockSynapseClient);
    }
    
    @Test
    public void validStudyAndUsers() throws Exception {
        // The synapse client as mocked, throws no exception that would make the IDs used
        // here invalid.
        
        List<String> adminIds = ImmutableList.of("abc", "def", "ghi");
        
        App app = App.create();
        app.setIdentifier("test-identifier");
        app.setName("Test Name");
        app.setSponsorName("Test Sponsor Name");
        
        StudyParticipant user1 = new StudyParticipant.Builder().withSynapseUserId("jkl")
                .withRoles(ImmutableSet.of(DEVELOPER)).build();
        StudyParticipant user2 = new StudyParticipant.Builder().withSynapseUserId("mno")
                .withRoles(ImmutableSet.of(RESEARCHER)).build();
        List<StudyParticipant> userIds = ImmutableList.of(user1, user2);
        
        StudyAndUsers model = new StudyAndUsers(adminIds, app, userIds);
        Validate.entityThrowingException(validator, model);
    }
    
    @Test
    public void adminIdsNull() {
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds", "are required");
    }
    
    @Test
    public void adminIdsEmpty() {
        StudyAndUsers model = new StudyAndUsers(ImmutableList.of(), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds", "are required");
    }

    @Test
    public void adminIdNull() {
        StudyAndUsers model = new StudyAndUsers(newArrayList((String)null), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds[0]", "cannot be blank or null");
    }
    
    @Test
    public void adminIdInvalid() throws SynapseException {
        when(mockSynapseClient.getUserProfile("userId")).thenThrow(new SynapseNotFoundException());
        
        StudyAndUsers model = new StudyAndUsers(newArrayList("userId"), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds[0]", "is invalid");
        
        verify(mockSynapseClient).getUserProfile("userId");
    }
    
    @Test
    public void usersNull() {
        StudyAndUsers model = new StudyAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "users", "are required");
    }
    
    @Test
    public void usersEmpty() {
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "users", "are required");
    }
    
    @Test
    public void userSynapseUserIdNull() {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of(participant));
        assertValidatorMessage(validator, model, "users[0].synapseUserId", "cannot be blank");
    }
    
    @Test
    public void userSynapseUserIdInvalid() throws SynapseException {
        when(mockSynapseClient.getUserProfile("userId")).thenThrow(new SynapseNotFoundException());
        
        StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId("userId").build();
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of(participant));
        assertValidatorMessage(validator, model, "users[0].synapseUserId", "is invalid");
        
        verify(mockSynapseClient).getUserProfile("userId");
    }
    
    @Test
    public void userRolesNull() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(null).build();
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of(participant));
        assertValidatorMessage(validator, model, "users[0].roles", "should have at least one role");
    }
    
    @Test
    public void userRolesEmpty() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of()).build();
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of(participant));
        assertValidatorMessage(validator, model, "users[0].roles", "should have at least one role");
    }
    
    @Test
    public void userRolesInvalid() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(ImmutableSet.of(WORKER, SUPERADMIN)).build();
        StudyAndUsers model = new StudyAndUsers(null, App.create(), ImmutableList.of(participant));
        assertValidatorMessage(validator, model, "users[0].roles", "can only have roles developer and/or researcher");
    }
    
    @Test
    public void studyNull() {
        StudyAndUsers model = new StudyAndUsers(null, null, null);
        assertValidatorMessage(validator, model, "study", "cannot be null");
    }
    
    @Test
    public void studyNameInvalidForSynapse() {
        App app = App.create();
        app.setName("  "); // blank is not okay
        StudyAndUsers model = new StudyAndUsers(null, app, null);
        assertValidatorMessage(validator, model, "study.name", "is an invalid Synapse project name");
    }
    
    @Test
    public void studySponsorNameRequired() { 
        StudyAndUsers model = new StudyAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "study.sponsorName", "is required");
    }
    
    @Test
    public void studyIdentifierRequired() { 
        StudyAndUsers model = new StudyAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "study.identifier", "is required");
    }
}
