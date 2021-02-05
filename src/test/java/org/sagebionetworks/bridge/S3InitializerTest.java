package org.sagebionetworks.bridge;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CORSRule.AllowedMethods;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

    @Mock
    BridgeConfig mockBridgeConfig;
    
    @Mock
    AmazonS3Client mockS3Client;
    
    @InjectMocks
    @Spy
    S3Initializer initializer;
    
    @Captor
    ArgumentCaptor<CreateBucketRequest> requestCaptor;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Captor
    ArgumentCaptor<BucketCrossOriginConfiguration> corsConfigCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void bucketExists() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of("bucket.prop", 
                S3Initializer.BucketType.SYNAPSE_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(true);
        
        initializer.initBuckets();
        
        verify(mockS3Client, never()).createBucket(any(CreateBucketRequest.class));
        verify(mockS3Client, never()).setBucketPolicy(any(), any());
    }
    
    @Test
    public void bucketCreatedForSynapseBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.SYNAPSE_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.SYNAPSE_ACCESSIBLE.policy, 
                ImmutableMap.of("bucketName", BUCKET_NAME));
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(eq(BUCKET_NAME), stringCaptor.capture());
        
        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
        assertEquals(resolvedPolicy, stringCaptor.getValue());
    }
    
    @Test
    public void bucketCreatedForPublicBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.PUBLIC_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        String resolvedPolicy = BridgeUtils.resolveTemplate(
                S3Initializer.BucketType.PUBLIC_ACCESSIBLE.policy, 
                ImmutableMap.of("bucketName", BUCKET_NAME));
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client).setBucketPolicy(eq(BUCKET_NAME), stringCaptor.capture());
        
        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
        assertEquals(resolvedPolicy, stringCaptor.getValue());
    }
    
    @Test
    public void bucketCreatedForInternalBucket() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.INTERNAL);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client, never()).setBucketPolicy(any(), any());
        
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
    public void createBucketWithCrsSupport() {
        Map<String,S3Initializer.BucketType> props = ImmutableMap.of(
                "bucket.prop", S3Initializer.BucketType.INTERNAL_UPLOAD_ACCESSIBLE);
        
        when(initializer.getBucketNames()).thenReturn(props);
        when(mockBridgeConfig.get("bucket.prop")).thenReturn(BUCKET_NAME);
        when(mockS3Client.doesBucketExistV2(BUCKET_NAME)).thenReturn(false);
        
        initializer.initBuckets();
        
        verify(mockS3Client).createBucket(requestCaptor.capture());
        verify(mockS3Client, never()).setBucketPolicy(any(), any());
        verify(mockS3Client).setBucketCrossOriginConfiguration(eq(BUCKET_NAME), corsConfigCaptor.capture());
        
        assertEquals(requestCaptor.getValue().getBucketName(), BUCKET_NAME);
        
        CORSRule rule = corsConfigCaptor.getValue().getRules().get(0);
        assertEquals(rule.getAllowedHeaders(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedOrigins(), ImmutableList.of("*"));
        assertEquals(rule.getAllowedMethods(), ImmutableList.of(AllowedMethods.PUT));
        assertEquals(rule.getMaxAgeSeconds(), 3000);
    }
}
