package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Maps;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.StudyService;

public class EmailControllerTest extends Mockito {

    private static final String EMAIL = "email";
    private static final String DATA_BRACKET_EMAIL = "data[email]";
    private static final String HEALTH_CODE = "healthCode";
    private static final String UNSUBSCRIBE_TOKEN = "unsubscribeToken";
    private static final String TOKEN = "token";
    private static final String STUDY2 = "study";
    private static final String EMAIL_ADDRESS = "bridge-testing@sagebase.org";
    private static final AccountId ACCOUNT_ID = AccountId.forEmail(TEST_APP_ID, EMAIL_ADDRESS);

    @Mock
    StudyService mockStudyService;

    @Mock
    AccountService mockAccountService;
    
    @Mock
    Account mockAccount;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Spy
    @InjectMocks
    EmailController controller;

    Study study;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(mockConfig.getEmailUnsubscribeToken()).thenReturn(UNSUBSCRIBE_TOKEN);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        mockEditAccount(mockAccountService, mockAccount);
        
        study = Study.create();
        study.setIdentifier(TEST_APP_ID);
        
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);
    }

    private void mockContext(String... values) {
        Map<String, String[]> paramMap = Maps.newHashMap();
        for (int i = 0; i <= values.length - 2; i += 2) {
            paramMap.put(values[i], new String[] { values[i + 1] });
        }
        for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
            when(mockRequest.getParameter(param.getKey())).thenReturn(param.getValue()[0]);
        }
    }

    @Test
    public void test() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        controller.unsubscribeFromEmail();

        verify(mockAccount).setNotifyByEmail(false);
    }

    @Test
    public void testWithEmail() throws Exception {
        mockContext(EMAIL, EMAIL_ADDRESS, STUDY2, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        controller.unsubscribeFromEmail();

        verify(mockAccount).setNotifyByEmail(false);
    }
    
    @Test
    public void noStudyThrowsException() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, TOKEN, UNSUBSCRIBE_TOKEN);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "Study not found.");
    }

    @Test
    public void noEmailThrowsException() throws Exception {
        mockContext(STUDY2, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "Email not found.");
    }

    @Test
    public void noAccountThrowsException() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);
        doReturn(null).when(mockAccountService).getHealthCodeForAccount(ACCOUNT_ID);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "Email not found.");
    }

    @Test
    public void missingTokenThrowsException() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY2, TEST_APP_ID);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "No authentication token provided.");
    }
}
