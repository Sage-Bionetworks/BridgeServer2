package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeUtils.resolveTemplate;

import java.util.Map;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.CORSRule.AllowedMethods;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.services.AccountService;

@Component
public class S3Initializer {
    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private static final String SYNAPSE_ACCESS_POLICY = "{"
            + "    \"Version\": \"2008-10-17\","
            + "    \"Statement\": ["
            + "        {"
            + "            \"Effect\": \"Allow\","
            + "            \"Principal\": {"
            + "                \"AWS\": \"arn:aws:iam::325565585839:root\""
            + "            },"
            + "            \"Action\": ["
            + "                \"s3:ListBucket*\","
            + "                \"s3:GetBucketLocation\""
            + "            ],"
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}\""
            + "        },"
            + "        {"
            + "            \"Effect\": \"Allow\","
            + "            \"Principal\": {"
            + "                \"AWS\": \"arn:aws:iam::325565585839:root\""
            + "            },"
            + "            \"Action\": ["
            + "                \"s3:GetObject*\","
            + "                \"s3:*MultipartUpload*\""
            + "            ],"
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\""
            + "        }"
            + "    ]"
            + "}";
    
    private static final String PUBLIC_ACCESS_POLICY = "{"
            + "    \"Version\": \"2012-10-17\","
            + "    \"Id\": \"Policy20160803001\","
            + "    \"Statement\": ["
            + "        {"
            + "            \"Sid\": \"Stmt20160803001\","
            + "            \"Effect\": \"Allow\","
            + "            \"Principal\": \"*\","
            + "            \"Action\": \"s3:GetObject\","
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\""
            + "        }"
            + "    ]"
            + "}";
    
    private static final BucketCrossOriginConfiguration ALLOW_PUT = new BucketCrossOriginConfiguration()
            .withRules(new CORSRule()
                    .withAllowedHeaders(ImmutableList.of("*"))
                    .withAllowedMethods(ImmutableList.of(AllowedMethods.PUT))
                    .withAllowedOrigins(ImmutableList.of("*"))
                    .withMaxAgeSeconds(3000));
    
    private static enum BucketType {
        INTERNAL(null, null),
        INTERNAL_UPLOAD_ACCESSIBLE(null, ALLOW_PUT),
        SYNAPSE_ACCESSIBLE(SYNAPSE_ACCESS_POLICY, null),
        PUBLIC_ACCESSIBLE(PUBLIC_ACCESS_POLICY, null);
        
        String policy;
        BucketCrossOriginConfiguration corsConfig;
        private BucketType(String policy, BucketCrossOriginConfiguration corsConfig) {
            this.policy = policy;
            this.corsConfig = corsConfig;
        }
    }

    private BridgeConfig bridgeConfig;
    
    private AmazonS3Client s3Client;
    
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }
    
    @Autowired
    public final void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    private Map<String, BucketType> S3_BUCKET_PROP_NAMES = new ImmutableMap.Builder<String, BucketType>()
            .put("attachment.bucket", BucketType.SYNAPSE_ACCESSIBLE)
            .put("upload.bucket", BucketType.INTERNAL_UPLOAD_ACCESSIBLE)
            .put("upload.cms.cert.bucket", BucketType.INTERNAL)
            .put("upload.cms.priv.bucket", BucketType.INTERNAL)
            .put("consents.bucket", BucketType.INTERNAL)
            .put("usersigned.consents.bucket", BucketType.INTERNAL)
            .put("participant-file.bucket", BucketType.INTERNAL)
            .put("docs.bucket", BucketType.PUBLIC_ACCESSIBLE)
            .build();
    
    // Accessor so we can mock this list of buckets
    Map<String, BucketType> getBucketNames() {
        return S3_BUCKET_PROP_NAMES;
    }
    
    public void initBuckets() {
        for (Map.Entry<String, BucketType> entry : getBucketNames().entrySet()) {
            String propName = entry.getKey();
            BucketType type = entry.getValue();
            String bucketName = bridgeConfig.get(propName);
            
            if (StringUtils.isBlank(bucketName)) {
                throw new RuntimeException("Value is missing for bucket property: " + propName);
            }
            if (!s3Client.doesBucketExistV2(bucketName)) {
                LOG.info("Creating bucket " + bucketName + " with policy to be " + type);
                
                s3Client.createBucket(new CreateBucketRequest(bucketName, Region.US_Standard));
                
                if (type.policy != null) {
                    String policy = resolveTemplate(type.policy, ImmutableMap.of("bucketName", bucketName));
                    s3Client.setBucketPolicy(bucketName, policy);
                }
                if (type.corsConfig != null) {
                    s3Client.setBucketCrossOriginConfiguration(bucketName, type.corsConfig);
                }
            }
        }
    }
}
