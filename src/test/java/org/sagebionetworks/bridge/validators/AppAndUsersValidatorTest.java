package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
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
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.AppAndUsers;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;

public class AppAndUsersValidatorTest extends Mockito {

    @Mock
    SynapseClient mockSynapseClient;
    
    AppAndUsersValidator validator;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        validator = new AppAndUsersValidator(mockSynapseClient);
    }
    
    @Test
    public void validAppAndUsers() throws Exception {
        // The synapse client as mocked, throws no exception that would make the IDs used
        // here invalid.
        
        List<String> adminIds = ImmutableList.of("abc", "def", "ghi");
        
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setName("Test Name");
        app.setSponsorName("Test Sponsor Name");
        
        Account user1 = Account.create();
        user1.setSynapseUserId("jkl");
        user1.setRoles(ImmutableSet.of(DEVELOPER));
        
        Account user2 = Account.create();
        user2.setSynapseUserId("mno");
        user2.setRoles(ImmutableSet.of(RESEARCHER));
        List<Account> userIds = ImmutableList.of(user1, user2);
        
        AppAndUsers model = new AppAndUsers(adminIds, app, userIds);
        Validate.entityThrowingException(validator, model);
    }
    
    @Test
    public void adminIdsNull() {
        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds", "are required");
    }
    
    @Test
    public void adminIdsEmpty() {
        AppAndUsers model = new AppAndUsers(ImmutableList.of(), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds", "are required");
    }

    @Test
    public void adminIdNull() {
        AppAndUsers model = new AppAndUsers(newArrayList((String)null), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds[0]", "cannot be blank or null");
    }
    
    @Test
    public void adminIdInvalid() throws SynapseException {
        when(mockSynapseClient.getUserProfile("userId")).thenThrow(new SynapseNotFoundException());
        
        AppAndUsers model = new AppAndUsers(newArrayList("userId"), App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "adminIds[0]", "is invalid");
        
        verify(mockSynapseClient).getUserProfile("userId");
    }
    
    @Test
    public void usersNull() {
        AppAndUsers model = new AppAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "users", "are required");
    }
    
    @Test
    public void usersEmpty() {
        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of());
        assertValidatorMessage(validator, model, "users", "are required");
    }
    
    @Test
    public void userSynapseUserIdNull() {
        Account account = Account.create();
        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of(account));
        assertValidatorMessage(validator, model, "users[0].synapseUserId", "cannot be blank");
    }
    
    @Test
    public void userSynapseUserIdInvalid() throws SynapseException {
        when(mockSynapseClient.getUserProfile("userId")).thenThrow(new SynapseNotFoundException());
        
        Account account = Account.create();
        account.setSynapseUserId("userId");
        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of(account));
        assertValidatorMessage(validator, model, "users[0].synapseUserId", "is invalid");
        
        verify(mockSynapseClient).getUserProfile("userId");
    }
    
    @Test
    public void userRolesNull() {
        Account account = Account.create();
        account.setRoles(null);

        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of(account));
        assertValidatorMessage(validator, model, "users[0].roles", "should have at least one role");
    }
    
    @Test
    public void userRolesEmpty() {
        Account account = Account.create();
        account.setRoles(ImmutableSet.of());
        
        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of(account));
        assertValidatorMessage(validator, model, "users[0].roles", "should have at least one role");
    }
    
    @Test
    public void userRolesInvalid() {
        Account account = Account.create();
        account.setRoles(ImmutableSet.of(WORKER, SUPERADMIN));

        AppAndUsers model = new AppAndUsers(null, App.create(), ImmutableList.of(account));
        assertValidatorMessage(validator, model, "users[0].roles", "can only have roles developer and/or researcher");
    }
    
    @Test
    public void appNull() {
        AppAndUsers model = new AppAndUsers(null, null, null);
        assertValidatorMessage(validator, model, "app", "cannot be null");
    }
    
    @Test
    public void appNameInvalidForSynapse() {
        App app = App.create();
        app.setName("  "); // blank is not okay
        AppAndUsers model = new AppAndUsers(null, app, null);
        assertValidatorMessage(validator, model, "app.name", "is an invalid Synapse project name");
    }
    
    @Test
    public void appSponsorNameRequired() { 
        AppAndUsers model = new AppAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "app.sponsorName", "is required");
    }
    
    @Test
    public void appIdentifierRequired() { 
        AppAndUsers model = new AppAndUsers(null, App.create(), null);
        assertValidatorMessage(validator, model, "app.identifier", "is required");
    }
}
