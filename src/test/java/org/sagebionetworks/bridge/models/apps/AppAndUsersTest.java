package org.sagebionetworks.bridge.models.apps;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public class AppAndUsersTest {
    private static final String TEST_APP_NAME = "test=app-name";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";

    @Test
    public void deserializeWithStudyProperty() throws Exception {
        deserializeCorrectly("study");
    }
    
    @Test
    public void deserializeWithAppProperty() throws Exception {
        deserializeCorrectly("app");
    }
    
    private void deserializeCorrectly(String appFieldName) throws Exception {
        // mock
        String json = "{" +
                "  \"adminIds\": [\"3346407\", \"3348228\"]," +
                "  \"" + appFieldName + "\": {" +
                "    \"identifier\": \""+TEST_APP_ID+"\"," +
                "    \"supportEmail\": \"test+user@email.com\"," +
                "    \"name\": \"test=app-name\"," +
                "    \"active\": \"true\"" +
                "  }," +
                "  \"users\": [" +
                "    {" +
                "      \"firstName\": \"test_user_first_name\"," +
                "      \"lastName\": \"test_user_last_name\"," +
                "      \"email\": \"test+user@email.com\"," +
                "      \"password\": \"test_user_password\"," +
                "      \"roles\": [\"developer\",\"researcher\"]" +
                "    }," +
                "    {" +
                "      \"firstName\": \"test_user_first_name\"," +
                "      \"lastName\": \"test_user_last_name\"," +
                "      \"email\": \"test+user+2@email.com\"," +
                "      \"password\": \"test_user_password\"," +
                "      \"roles\": [\"researcher\"]" +
                "    }" +
                "  ]" +
                "}";

        App app = new DynamoApp();
        app.setActive(true);
        app.setIdentifier(TEST_APP_ID);
        app.setName(TEST_APP_NAME);
        app.setSupportEmail(TEST_USER_EMAIL);

        // make it ordered
        LinkedHashSet<Roles> user1Roles = new LinkedHashSet<>();
        user1Roles.add(Roles.RESEARCHER);
        user1Roles.add(Roles.DEVELOPER);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.copyOf(user1Roles))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        List<String> adminIds = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

        AppAndUsers retAppAndUsers = BridgeObjectMapper.get().readValue(json, AppAndUsers.class);
        List<String> retAdminIds = retAppAndUsers.getAdminIds();
        App retApp = retAppAndUsers.getApp();
        List<StudyParticipant> userList = retAppAndUsers.getUsers();

        // verify
        assertEquals(retAdminIds, adminIds);
        assertEquals(retApp, app);
        assertEquals(userList, mockUsers);
    }
}
