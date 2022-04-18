package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesRequest;
import com.amazonaws.services.simpleemail.model.GetIdentityVerificationAttributesResult;
import com.amazonaws.services.simpleemail.model.IdentityVerificationAttributes;
import com.google.common.collect.Maps;

public class EmailVerificationServiceTest {

    private static final String EMAIL_ADDRESS = "foo@foo.com";
    
    private static final CacheKey EMAIL_ADDRESS_KEY = CacheKey.emailVerification(EMAIL_ADDRESS);

    @Mock
    private AmazonSimpleEmailService sesClient;
    @Mock
    private ExecutorService asyncExecutorService;
    @Mock
    private GetIdentityVerificationAttributesResult result;
    @Mock
    private IdentityVerificationAttributes attributes;
    @Mock
    private CacheProvider cacheProvider;
    @Spy
    private EmailVerificationService service;
    
    private ArgumentCaptor<GetIdentityVerificationAttributesRequest> getCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        service.setAmazonSimpleEmailServiceClient(sesClient);
        service.setAsyncExecutorService(asyncExecutorService);
        service.setCacheProvider(cacheProvider);
    }
    
    private void mockSession(String status) {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);
        
        Map<String,IdentityVerificationAttributes> map = Maps.newHashMap();
        map.put(EMAIL_ADDRESS, attributes);
        when(result.getVerificationAttributes()).thenReturn(map);
        when(attributes.getVerificationStatus()).thenReturn(status); // aka unverified
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);
    }
    
    @Test
    public void verifiedEmailTakesNoAction() {
        mockSession("Success");

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(status, EmailVerificationStatus.VERIFIED);
        verify(asyncExecutorService, never()).execute(any());
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(getCaptor.getValue().getIdentities().get(0), EMAIL_ADDRESS);

        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void ifUnverifiedAttemptsToResendVerification() {
        mockSession("Failure");

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(status, EmailVerificationStatus.PENDING);
        verifyAsyncHandler();
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(getCaptor.getValue().getIdentities().get(0), EMAIL_ADDRESS);

        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }
    
    @Test
    public void emailDoesntExistRequestVerification() {
        mockSession(null);

        EmailVerificationStatus status = service.verifyEmailAddress(EMAIL_ADDRESS);

        assertEquals(status, EmailVerificationStatus.PENDING);
        verifyAsyncHandler();
        verify(sesClient).getIdentityVerificationAttributes(getCaptor.capture());
        assertEquals(getCaptor.getValue().getIdentities().get(0), EMAIL_ADDRESS);
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }

    @Test
    public void getEmailStatus() {
        mockSession("Success");

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(status, EmailVerificationStatus.VERIFIED);
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void getEmailStatusAttributesNull() {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);

        when(result.getVerificationAttributes()).thenReturn(null);
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(status, EmailVerificationStatus.UNVERIFIED);
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());        
    }
    
    @Test
    public void getEmailStatusVerificationStatusNull() {
        getCaptor = ArgumentCaptor.forClass(GetIdentityVerificationAttributesRequest.class);

        Map<String, IdentityVerificationAttributes> map = Maps.newHashMap();
        map.put(EMAIL_ADDRESS, attributes);
        when(result.getVerificationAttributes()).thenReturn(map);
        when(attributes.getVerificationStatus()).thenReturn(null);
        when(sesClient.getIdentityVerificationAttributes(any())).thenReturn(result);

        EmailVerificationStatus status = service.getEmailStatus(EMAIL_ADDRESS);

        verify(sesClient).getIdentityVerificationAttributes(any());
        assertEquals(status, EmailVerificationStatus.UNVERIFIED);
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());        
    }

    @Test
    public void isVerifiedAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("VERIFIED");
        assertTrue(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isPendingAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("PENDING");
        assertFalse(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isUnverifiedAndCached() throws Exception {
        when(cacheProvider.getObject(EMAIL_ADDRESS_KEY, String.class)).thenReturn("UNVERIFIED");
        assertFalse(service.isVerified(EMAIL_ADDRESS));
    }
    
    @Test
    public void isVerifiedUncached() {
        doReturn(EmailVerificationStatus.VERIFIED).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertTrue(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("VERIFIED"), anyInt());
    }
    
    @Test
    public void isPendingUncached() {
        doReturn(EmailVerificationStatus.PENDING).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertFalse(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("PENDING"), anyInt());
    }
    
    @Test
    public void isUnverifiedUncached() {
        doReturn(EmailVerificationStatus.UNVERIFIED).when(service).getEmailStatus(EMAIL_ADDRESS);
        
        assertFalse(service.isVerified(EMAIL_ADDRESS));
        
        verify(cacheProvider).setObject(eq(EMAIL_ADDRESS_KEY), eq("UNVERIFIED"), anyInt());
    }

    private void verifyAsyncHandler() {
        ArgumentCaptor<EmailVerificationService.AsyncSnsTopicHandler> handlerCaptor = ArgumentCaptor.forClass(
                EmailVerificationService.AsyncSnsTopicHandler.class);
        verify(asyncExecutorService).execute(handlerCaptor.capture());
        assertEquals(handlerCaptor.getValue().getEmailAddress(), EMAIL_ADDRESS);
    }
}
