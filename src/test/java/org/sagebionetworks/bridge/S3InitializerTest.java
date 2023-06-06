package org.sagebionetworks.bridge;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CORSRule.AllowedMethods;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.S3Event;
import com.amazonaws.services.s3.model.TopicConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class S3InitializerTest extends Mockito {
    private static final String BUCKET_NAME = "oneBucketName";
    private static final String SYNAPSE_AWS_ACCOUNT_ID = "1234567890";
    private static final String VIRUS_SCAN_TRIGGER_TOPIC_ARN = "arn:aws:sns:us-east-1:111111111111:virus-scan-trigger-topic";

    @Mock
    BridgeConfig mockBridgeConfig;
    
    @Mock
    AmazonS3 mockS3Client;
    
    @InjectMocks
    @Spy
    S3Initializer initializer;
    
    @Captor
    ArgumentCaptor<CreateBucketRequest> requestCaptor;
    
    @Captor
    ArgumentCaptor<BucketCrossOriginConfiguration> corsConfigCaptor;
    
    @Captor
    ArgumentCaptor<BucketWebsiteConfiguration> websiteConfigCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        // Mock config.
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockBridgeConfig.get(S3Initializer.CONFIG_KEY_SYNAPSE_AWS_ACCOUNT_ID)).thenReturn(SYNAPSE_AWS_ACCOUNT_ID);
        when(mockBridgeConfig.get(S3Initializer.CONFIG_KEY_VIRUS_SCAN_TRIGGER_TOPIC_ARN)).thenReturn(
                VIRUS_SCAN_TRIGGER_TOPIC_ARN);

        // Default spy operations so that we don't have to specify these everywhere.
        doReturn(ImmutableSet.of()).when(initializer).getBucketsVirusScanEnabled();
        doReturn(ImmutableMap.of()).when(initializer).getBucketNames();
    }
    
    @Test
    public void bucketExists() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of("bucket.prop", 
                S3Initializer.BucketType.SYNAPSE_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);
        
        initializer.initBuckets();
        
        verify(mockS3Client, never()).createBucket(any(CreateBucketRequest.class));
        verify(mockS3Client, never()).setBucketPolicy(any(), any());
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);
    }
    
    @Test
    public void bucketCreatedForSynapseBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.SYNAPSE_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.SYNAPSE_ACCESSIBLE.policy, 
                ImmutableMap.of("bucketName", BUCKET_NAME,
                        "synapseAwsAccountId", SYNAPSE_AWS_ACCOUNT_ID));
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(BUCKET_NAME, resolvedPolicy);
        verify(mockS3Client, never()).setBucketCrossOriginConfiguration(any(), any());
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);

        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
    }
    
    @Test
    public void bucketCreatedForPublicBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.PUBLIC_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.PUBLIC_ACCESSIBLE.policy, 
                ImmutableMap.of("bucketName", BUCKET_NAME));
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(BUCKET_NAME, resolvedPolicy);
        verify(mockS3Client).setBucketWebsiteConfiguration(eq(BUCKET_NAME), websiteConfigCaptor.capture());
        verify(mockS3Client).setBucketCrossOriginConfiguration(eq(BUCKET_NAME), corsConfigCaptor.capture());
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);

        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
        
        // Not a lot to check here, but
        BucketWebsiteConfiguration config = websiteConfigCaptor.getValue();
        assertEquals(config.getIndexDocumentSuffix(), "index.html");
        
        CORSRule rule = corsConfigCaptor.getValue().getRules().get(0);
        assertEquals(rule.getAllowedHeaders(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedOrigins(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedMethods(), ImmutableList.of(AllowedMethods.PUT));
        assertEquals(rule.getMaxAgeSeconds(), 3000);
    }
    
    @Test
    public void bucketCreatedForInternalBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.INTERNAL);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();

        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.INTERNAL.policy,
                ImmutableMap.of("bucketName", BUCKET_NAME));

        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(BUCKET_NAME, resolvedPolicy);
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);

        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Value is missing for bucket property: bucket.prop")
    public void missingBucketConfigThrowsException() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of("bucket.prop", 
                S3Initializer.BucketType.SYNAPSE_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(null);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);
        
        initializer.initBuckets();
    }
    
    @Test
    public void createBucketWithCORSSupport() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.INTERNAL_UPLOAD_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();

        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.INTERNAL_UPLOAD_ACCESSIBLE.policy,
                ImmutableMap.of("bucketName", BUCKET_NAME));

        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(BUCKET_NAME, resolvedPolicy);
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verify(mockS3Client).setBucketCrossOriginConfiguration(eq(BUCKET_NAME), corsConfigCaptor.capture());
        verifyNoMoreInteractions(mockS3Client);

        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
        assertCorsConfig(corsConfigCaptor.getValue());
    }

    private static void assertCorsConfig(BucketCrossOriginConfiguration corsConfig) {
        CORSRule rule = corsConfig.getRules().get(0);
        assertEquals(rule.getAllowedHeaders(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedOrigins(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedMethods(), ImmutableList.of(AllowedMethods.PUT));
        assertEquals(rule.getMaxAgeSeconds(), 3000);
    }

    @Test
    public void virusScanConfig_noBucketConfigObject() {
        // Spy initializer.
        doReturn(ImmutableMap.of("bucket.prop", S3Initializer.BucketType.INTERNAL)).when(initializer)
                .getBucketNames();
        doReturn(ImmutableSet.of("bucket.prop")).when(initializer).getBucketsVirusScanEnabled();

        // Mock S3.
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);
        when(mockS3Client.getBucketNotificationConfiguration(BUCKET_NAME)).thenReturn(null);

        // Execute and verify.
        initializer.initBuckets();

        verifyGetBucketNotificationConfiguration();
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verify(mockS3Client).getBucketNotificationConfiguration(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);
    }

    @Test
    public void virusScanConfig_noBucketConfigMap() {
        // Spy initializer.
        doReturn(ImmutableMap.of("bucket.prop", S3Initializer.BucketType.INTERNAL)).when(initializer)
                .getBucketNames();
        doReturn(ImmutableSet.of("bucket.prop")).when(initializer).getBucketsVirusScanEnabled();

        // Mock S3.
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);

        // BucketNotificationConfiguration constructor creates an empty map. Explicitly set it to null.
        BucketNotificationConfiguration originalBucketConfig = new BucketNotificationConfiguration();
        originalBucketConfig.setConfigurations(null);
        when(mockS3Client.getBucketNotificationConfiguration(BUCKET_NAME)).thenReturn(originalBucketConfig);

        // Execute and verify.
        initializer.initBuckets();

        verifyGetBucketNotificationConfiguration();
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verify(mockS3Client).getBucketNotificationConfiguration(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);
    }

    @Test
    public void virusScanConfig_noVirusScanConfig() {
        // Spy initializer.
        doReturn(ImmutableMap.of("bucket.prop", S3Initializer.BucketType.INTERNAL)).when(initializer)
                .getBucketNames();
        doReturn(ImmutableSet.of("bucket.prop")).when(initializer).getBucketsVirusScanEnabled();

        // Mock S3.
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);

        when(mockS3Client.getBucketNotificationConfiguration(BUCKET_NAME)).thenReturn(
                new BucketNotificationConfiguration());

        // Execute and verify.
        initializer.initBuckets();

        verifyGetBucketNotificationConfiguration();
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verify(mockS3Client).getBucketNotificationConfiguration(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);
    }

    @Test
    public void virusScanConfig_alreadyExists() {
        // Spy initializer.
        doReturn(ImmutableMap.of("bucket.prop", S3Initializer.BucketType.INTERNAL)).when(initializer)
                .getBucketNames();
        doReturn(ImmutableSet.of("bucket.prop")).when(initializer).getBucketsVirusScanEnabled();

        // Mock S3.
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);

        // Add a dummy config. Doesn't matter what it contains.
        BucketNotificationConfiguration originalBucketConfig = new BucketNotificationConfiguration();
        originalBucketConfig.addConfiguration(S3Initializer.BUCKET_NOTIFICATION_CONFIG_NAME_VIRUS_SCAN,
                new TopicConfiguration());
        when(mockS3Client.getBucketNotificationConfiguration(BUCKET_NAME)).thenReturn(originalBucketConfig);

        // Execute and verify.
        initializer.initBuckets();

        verify(mockS3Client, never()).setBucketNotificationConfiguration(any(), any());
        verify(mockS3Client).doesBucketExistV2(BUCKET_NAME);
        verify(mockS3Client).getBucketNotificationConfiguration(BUCKET_NAME);
        verifyNoMoreInteractions(mockS3Client);
    }

    private void verifyGetBucketNotificationConfiguration() {
        ArgumentCaptor<BucketNotificationConfiguration> bucketConfigCaptor = ArgumentCaptor.forClass(
                BucketNotificationConfiguration.class);
        verify(mockS3Client).setBucketNotificationConfiguration(eq(BUCKET_NAME), bucketConfigCaptor.capture());

        BucketNotificationConfiguration bucketConfig = bucketConfigCaptor.getValue();
        TopicConfiguration notificationConfig = (TopicConfiguration) bucketConfig.getConfigurationByName(
                S3Initializer.BUCKET_NOTIFICATION_CONFIG_NAME_VIRUS_SCAN);
        assertEquals(notificationConfig.getTopicARN(), VIRUS_SCAN_TRIGGER_TOPIC_ARN);

        Set<String> eventSet = notificationConfig.getEvents();
        assertEquals(eventSet.size(), 1);
        assertTrue(eventSet.contains(S3Event.ObjectCreated.toString()));
    }
}
