package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeUtils.resolveTemplate;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.amazonaws.services.s3.model.CORSRule;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.NotificationConfiguration;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.CORSRule.AllowedMethods;
import com.amazonaws.services.s3.model.S3Event;
import com.amazonaws.services.s3.model.TopicConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.services.AccountService;

/** Initialzes S3 buckets based on config. This requires SnsInitializer to have been run first. */
@Component
public class S3Initializer {
    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    static final String BUCKET_NOTIFICATION_CONFIG_NAME_VIRUS_SCAN = "Bridge-Virus-Scan";
    static final String CONFIG_KEY_SYNAPSE_AWS_ACCOUNT_ID = "synapse.aws.account.id";
    static final String CONFIG_KEY_VIRUS_SCAN_TRIGGER_TOPIC_ARN = "virus.scan.trigger.topic.arn";

    // Buckets can only have one policy. Make the default policy include blocking infected files, and add this snippet
    // to all other policies as well.
    private static final String DEFAULT_ACCESS_POLICY = "{"
            + "    \"Version\": \"2008-10-17\","
            + "    \"Statement\": ["
            + "        {"
            + "            \"Effect\": \"Deny\","
            + "            \"Principal\": \"*\","
            + "            \"Action\": \"s3:GetObject\","
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\","
            + "            \"Condition\": {"
            + "                \"StringEquals\": {"
            + "                    \"s3:ExistingObjectTag/av-status\": \"INFECTED\""
            + "                }"
            + "            }"
            + "        }"
            + "    ]"
            + "}";

    private static final String SYNAPSE_ACCESS_POLICY = "{"
            + "    \"Version\": \"2008-10-17\","
            + "    \"Statement\": ["
            + "        {"
            + "            \"Effect\": \"Allow\","
            + "            \"Principal\": {"
            + "                \"AWS\": \"arn:aws:iam::${synapseAwsAccountId}:root\""
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
            + "                \"AWS\": \"arn:aws:iam::${synapseAwsAccountId}:root\""
            + "            },"
            + "            \"Action\": ["
            + "                \"s3:GetObject*\","
            + "                \"s3:*MultipartUpload*\""
            + "            ],"
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\""
            + "        },"
            + "        {"
            + "            \"Effect\": \"Deny\","
            + "            \"Principal\": \"*\","
            + "            \"Action\": \"s3:GetObject\","
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\","
            + "            \"Condition\": {"
            + "                \"StringEquals\": {"
            + "                    \"s3:ExistingObjectTag/av-status\": \"INFECTED\""
            + "                }"
            + "            }"
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
            + "        },"
            + "        {"
            + "            \"Effect\": \"Deny\","
            + "            \"Principal\": \"*\","
            + "            \"Action\": \"s3:GetObject\","
            + "            \"Resource\": \"arn:aws:s3:::${bucketName}/*\","
            + "            \"Condition\": {"
            + "                \"StringEquals\": {"
            + "                    \"s3:ExistingObjectTag/av-status\": \"INFECTED\""
            + "                }"
            + "            }"
            + "        }"
            + "    ]"
            + "}";
    
    private static final BucketCrossOriginConfiguration ALLOW_PUT = new BucketCrossOriginConfiguration()
            .withRules(new CORSRule()
                    .withAllowedHeaders(ImmutableList.of("*"))
                    .withAllowedMethods(ImmutableList.of(AllowedMethods.PUT))
                    .withAllowedOrigins(ImmutableList.of("*"))
                    .withMaxAgeSeconds(3000));
    
    public enum BucketType {
        INTERNAL(DEFAULT_ACCESS_POLICY, null),
        INTERNAL_UPLOAD_ACCESSIBLE(DEFAULT_ACCESS_POLICY, ALLOW_PUT),
        SYNAPSE_ACCESSIBLE(SYNAPSE_ACCESS_POLICY, null),
        PUBLIC_ACCESSIBLE(PUBLIC_ACCESS_POLICY, ALLOW_PUT);
        
        String policy;
        BucketCrossOriginConfiguration corsConfig;
        BucketType(String policy, BucketCrossOriginConfiguration corsConfig) {
            this.policy = policy;
            this.corsConfig = corsConfig;
        }
    }

    private BridgeConfig bridgeConfig;
    
    private AmazonS3 s3Client;
    
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }
    
    @Autowired
    public final void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    private static final Set<String> S3_BUCKETS_VIRUS_SCAN_ENABLED = ImmutableSet.of("docs.bucket",
            "attachment.bucket", "consents.bucket", "health.data.bucket.raw", "participant-file.bucket",
            "upload.bucket", "usersigned.consents.bucket");

    // Accessor so we can mock this in unit tests.
    Set<String> getBucketsVirusScanEnabled() {
        return S3_BUCKETS_VIRUS_SCAN_ENABLED;
    }

    private static final Map<String, BucketType> S3_BUCKET_PROP_NAMES = new ImmutableMap.Builder<String, BucketType>()
            .put("attachment.bucket", BucketType.SYNAPSE_ACCESSIBLE)
            .put("health.data.bucket.raw", BucketType.SYNAPSE_ACCESSIBLE)
            .put("upload.bucket", BucketType.INTERNAL_UPLOAD_ACCESSIBLE)
            .put("upload.cms.cert.bucket", BucketType.INTERNAL)
            .put("upload.cms.priv.bucket", BucketType.INTERNAL)
            .put("consents.bucket", BucketType.INTERNAL)
            .put("usersigned.consents.bucket", BucketType.INTERNAL)
            .put("participant-file.bucket", BucketType.INTERNAL)
            .put("docs.bucket", BucketType.PUBLIC_ACCESSIBLE)
            .put("participantroster.bucket", BucketType.INTERNAL)
            .build();
    
    // Accessor so we can mock this list of buckets
    Map<String, BucketType> getBucketNames() {
        return S3_BUCKET_PROP_NAMES;
    }
    
    public void initBuckets() {
        Set<String> bucketsVirusScanEnabled = getBucketsVirusScanEnabled();
        String synapseAwsAccountId = bridgeConfig.get(CONFIG_KEY_SYNAPSE_AWS_ACCOUNT_ID);
        String virusScanTriggerTopicArn = bridgeConfig.get(CONFIG_KEY_VIRUS_SCAN_TRIGGER_TOPIC_ARN);
        TopicConfiguration virusScanNotificationConfig = new TopicConfiguration(virusScanTriggerTopicArn,
                EnumSet.of(S3Event.ObjectCreated));

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

                Map<String, String> varMap = ImmutableMap.<String, String>builder()
                        .put("bucketName", bucketName)
                        .put("synapseAwsAccountId", synapseAwsAccountId)
                        .build();
                String policy = resolveTemplate(type.policy, varMap);
                s3Client.setBucketPolicy(bucketName, policy);

                // For public buckets to serve for retrieving documents via HTTP, they
                // must also be configured as web hosting buckets. index file is required, 
                // but does not need to exist (and does not exist) 
                if (type.policy == PUBLIC_ACCESS_POLICY) {
                    BucketWebsiteConfiguration config = new BucketWebsiteConfiguration();
                    config.setIndexDocumentSuffix("index.html"); 
                    s3Client.setBucketWebsiteConfiguration(bucketName, config);
                }
                if (type.corsConfig != null) {
                    s3Client.setBucketCrossOriginConfiguration(bucketName, type.corsConfig);
                }
            }

            // Add SNS notification for virus scan, if needed.
            if (bucketsVirusScanEnabled.contains(propName)) {
                // Get old bucket configuration.
                BucketNotificationConfiguration bucketConfig = s3Client.getBucketNotificationConfiguration(bucketName);
                boolean updated = false;

                // Bootstrap bucket config, if needed.
                if (bucketConfig == null) {
                    bucketConfig = new BucketNotificationConfiguration();
                    updated = true;
                }
                if (bucketConfig.getConfigurations() == null) {
                    // This should never happen, but just in case...
                    bucketConfig.setConfigurations(new HashMap<>());
                    updated = true;
                }

                // Add virus scan config, if needed.
                NotificationConfiguration notificationConfig = bucketConfig.getConfigurationByName(
                        BUCKET_NOTIFICATION_CONFIG_NAME_VIRUS_SCAN);
                if (notificationConfig == null) {
                    bucketConfig.addConfiguration(BUCKET_NOTIFICATION_CONFIG_NAME_VIRUS_SCAN,
                            virusScanNotificationConfig);
                    updated = true;
                }

                // Update to S3, if needed.
                if (updated) {
                    LOG.info("Creating antivirus SNS notification for bucket " + bucketName);
                    s3Client.setBucketNotificationConfiguration(bucketName, bucketConfig);
                }
            }
        }
    }
}
