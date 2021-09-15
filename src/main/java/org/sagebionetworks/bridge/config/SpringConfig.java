package org.sagebionetworks.bridge.config;

import static com.amazonaws.regions.Regions.US_EAST_1;
import static org.hibernate.event.spi.EventType.DELETE;
import static org.hibernate.event.spi.EventType.MERGE;
import static org.hibernate.event.spi.EventType.SAVE_UPDATE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;
import javax.servlet.Filter;
import javax.sql.DataSource;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mchange.v2.c3p0.DriverManagerDataSource;

import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataDocumentation;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfigElement;
import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthCode;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecordEx3;
import org.sagebionetworks.bridge.dynamodb.DynamoIndexHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoMasterSchedulerConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoMasterSchedulerStatus;
import org.sagebionetworks.bridge.dynamodb.DynamoNamingHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoNotificationRegistration;
import org.sagebionetworks.bridge.dynamodb.DynamoNotificationTopic;
import org.sagebionetworks.bridge.dynamodb.DynamoOAuthAccessGrant;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.dynamodb.DynamoReportIndex;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoSmsMessage;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.dynamodb.DynamoTopicSubscription;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDedupe;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.DynamoUtils;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.heartbeat.HeartbeatLogger;
import org.sagebionetworks.bridge.hibernate.AccountPersistenceExceptionConverter;
import org.sagebionetworks.bridge.hibernate.HibernateAccount;
import org.sagebionetworks.bridge.hibernate.HibernateAccountSecret;
import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;
import org.sagebionetworks.bridge.hibernate.HibernateHelper;
import org.sagebionetworks.bridge.hibernate.MySQLHibernatePersistenceExceptionConverter;
import org.sagebionetworks.bridge.hibernate.HibernateSharedModuleMetadata;
import org.sagebionetworks.bridge.hibernate.HibernateStudy;
import org.sagebionetworks.bridge.hibernate.HibernateTemplate;
import org.sagebionetworks.bridge.hibernate.HibernateTemplateRevision;
import org.sagebionetworks.bridge.hibernate.OrganizationPersistenceExceptionConverter;
import org.sagebionetworks.bridge.hibernate.SponsorPersistenceExceptionConverter;
import org.sagebionetworks.bridge.hibernate.TagEventListener;
import org.sagebionetworks.bridge.hibernate.BasicPersistenceExceptionConverter;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResource;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.schedules2.Notification;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.spring.filters.MetricsFilter;
import org.sagebionetworks.bridge.spring.filters.RequestFilter;
import org.sagebionetworks.bridge.spring.filters.StaticHeadersFilter;
import org.sagebionetworks.bridge.synapse.SynapseHelper;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.InitRecordHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadFormatHandler;
import org.sagebionetworks.bridge.upload.UploadRawZipHandler;
import org.sagebionetworks.bridge.upload.UploadValidationHandler;
import org.sagebionetworks.client.SynapseAdminClientImpl;
import org.sagebionetworks.client.SynapseClient;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@ComponentScan("org.sagebionetworks.bridge")
@Configuration
public class SpringConfig {
    
    @Bean
    public HeartbeatLogger heartbeatLogger() {
        HeartbeatLogger heartbeatLogger = new HeartbeatLogger();
        heartbeatLogger.setIntervalMinutes(bridgeConfig().getInt("heartbeat.interval.minutes"));
        return heartbeatLogger;
    }
    
    // Filters. The filters themselves are registered with @Component, but must also be mapped here with a
    // FilterRegistrationBean to a specific URL. 
    
    private <T extends Filter> FilterRegistrationBean<T> filterRegistration(T filter) {
        FilterRegistrationBean<T> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("*");
        return registrationBean;
    }

    @Bean
    @Order(1)
    public FilterRegistrationBean<RequestFilter> requestFilterRegistration(RequestFilter filter) {
        return filterRegistration(filter);
    }

    @Bean
    @Order(2)
    public FilterRegistrationBean<StaticHeadersFilter> staticHeadersFilterRegistration(StaticHeadersFilter filter) {
        return filterRegistration(filter);
    }

    @Bean
    @Order(3)
    public FilterRegistrationBean<MetricsFilter> metricsFilterRegistration(MetricsFilter filter) {
        return filterRegistration(filter);
    }

    // This will replace Spring Boot's default configuration using Jackson2ObjectMapperBuilder.
    // See: https://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference
    //      /html/howto.html#howto-customize-the-jackson-objectmapper
    @Bean(name = "bridgeObjectMapper")
    @Primary
    public ObjectMapper bridgeObjectMapper() {
        return BridgeObjectMapper.get();
    }

    @Bean(name = "bridgeConfig")
    public BridgeConfig bridgeConfig() {
        return BridgeConfigFactory.getConfig();
    }

    @Bean(name = "annotationBasedTableCreator")
    public AnnotationBasedTableCreator annotationBasedTableCreator(DynamoNamingHelper dynamoNamingHelper) {
        return new AnnotationBasedTableCreator(dynamoNamingHelper);
    }

    @Bean(name = "awsCredentials")
    public BasicAWSCredentials awsCredentials() {
        BridgeConfig bridgeConfig = bridgeConfig();
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key"),
                bridgeConfig.getProperty("aws.secret.key"));
    }

    @Bean(name = "dynamoDbClient")
    @Resource(name = "awsCredentials")
    public AmazonDynamoDBClient dynamoDbClient() {
        int maxRetries = bridgeConfig().getPropertyAsInt("ddb.max.retries");
        ClientConfiguration awsClientConfig = PredefinedClientConfigurations.dynamoDefault()
                .withMaxErrorRetry(maxRetries);
        return new AmazonDynamoDBClient(awsCredentials(), awsClientConfig);
    }
    
    @Bean(name = "snsClient")
    @Resource(name = "awsCredentials")
    public AmazonSNSClient snsClient() {
        return new AmazonSNSClient(awsCredentials());
    }

    @Bean(name = "dataPipelineClient")
    @Resource(name = "awsCredentials")
    public DataPipelineClient dataPipelineClient(BasicAWSCredentials awsCredentials) {
        return new DataPipelineClient(awsCredentials);
    }

    @Bean(name = "s3Client")
    @Resource(name = "awsCredentials")
    public AmazonS3Client s3Client(BasicAWSCredentials awsCredentials) {
        // Setting region is necessary to prevent bug BRIDGE-2910. Don't remove.
        return new AmazonS3Client(awsCredentials).withRegion(US_EAST_1);
    }

    // This client needs to be configured to handle S3 file paths differently, so we can use bucket
    // names with periods in them (and we need these in turn so they can be fronted with CloudFront).
    @Bean(name = "fileUploadS3Client")
    @Resource(name = "awsCredentials")
    public AmazonS3 fileUploadS3Client(BasicAWSCredentials awsCredentials) {
        return AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true).withRegion(US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();
    }
    
    @Bean(name ="uploadTokenServiceClient")
    @Resource(name = "awsCredentials")
    public AWSSecurityTokenServiceClient uploadTokenServiceClient(BasicAWSCredentials awsCredentials) {
        return new AWSSecurityTokenServiceClient(awsCredentials);
    }

    @Bean(name = "md5DigestUtils")
    public DigestUtils md5DigestUtils() {
        return new DigestUtils(DigestUtils.getMd5Digest());
    }

    @Bean(name = "s3Helper")
    @Resource(name = "s3Client")
    public S3Helper s3Helper(AmazonS3Client s3Client) {
        S3Helper s3Helper = new S3Helper();
        s3Helper.setS3Client(s3Client);
        return s3Helper;
    }

    @Bean(name = "sesClient")
    @Resource(name="awsCredentials")
    public AmazonSimpleEmailServiceClient sesClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSimpleEmailServiceClient(awsCredentials);
    }

    @Bean(name = "sqsClient")
    @Resource(name = "awsCredentials")
    public AmazonSQSClient sqsClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSQSClient(awsCredentials);
    }

    @Bean(name = "asyncExecutorService")
    @Resource(name = "bridgeConfig")
    public ExecutorService asyncExecutorService(BridgeConfig bridgeConfig) {
        return Executors.newFixedThreadPool(bridgeConfig.getPropertyAsInt("async.worker.thread.count"));
    }

    @Bean(name = "supportEmail")
    @Resource(name = "bridgeConfig")
    public String supportEmail(BridgeConfig bridgeConfig) {
        return bridgeConfig.getProperty("support.email");
    }

    @Bean(name = "cmsEncryptorCache")
    @Autowired
    public LoadingCache<String, CmsEncryptor> cmsEncryptorCache(S3Helper s3Helper) {
        BridgeConfig bridgeConfig = bridgeConfig();

        CmsEncryptorCacheLoader cacheLoader = new CmsEncryptorCacheLoader();
        cacheLoader.setCertBucket(bridgeConfig.getProperty("upload.cms.cert.bucket"));
        cacheLoader.setPrivateKeyBucket(bridgeConfig.getProperty("upload.cms.priv.bucket"));
        cacheLoader.setS3Helper(s3Helper);

        return CacheBuilder.newBuilder().build(cacheLoader);
    }

    @Bean(name = "dynamoUtils")
    @Autowired
    public DynamoUtils dynamoUtils(DynamoNamingHelper dynamoNamingHelper, AmazonDynamoDB dynamoDB) {
        return new DynamoUtils(dynamoNamingHelper, dynamoDB);
    }

    @Bean(name = "dynamoNamingHelper")
    @Autowired
    public DynamoNamingHelper dynamoNamingHelper(BridgeConfig bridgeConfig) {
        return new DynamoNamingHelper(bridgeConfig);
    }

    @Bean(name = "healthCodeDdbMapper")
    @Autowired
    public DynamoDBMapper healthCodeDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthCode.class);
    }
    
    @Bean(name = "compoundActivityDefinitionDdbMapper")
    @Autowired
    public DynamoDBMapper compoundActivityDefinitionDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoCompoundActivityDefinition.class);
    }

    @Bean(name = "smsMessageDdbMapper")
    @Autowired
    public DynamoDBMapper smsMessageDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSmsMessage.class);
    }

    @Bean(name = "reportDataMapper")
    @Autowired
    public DynamoDBMapper reportDataMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoReportData.class);
    }

    @Bean(name = "participantDataMapper")
    @Autowired
    public DynamoDBMapper participantDataMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper((DynamoParticipantData.class));
    }
    
    @Bean(name = "reportIndexMapper")
    @Autowired
    public DynamoDBMapper reportIndexMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoReportIndex.class);
    }
    
    @Bean(name = "healthDataDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthDataRecord.class);
    }

    @Bean(name = "healthDataEx3DdbMapper")
    @Autowired
    public DynamoDBMapper healthDataEx3DdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthDataRecordEx3.class);
    }

    @Bean(name = "healthDataDocumentationDbMapper")
    @Autowired
    public DynamoDBMapper healthDataDocumentationDbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoHealthDataDocumentation.class);
    }

    @Bean(name = "activityEventDdbMapper")
    @Autowired
    public DynamoDBMapper activityEventDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoActivityEvent.class);
    }

    @Bean(name = "studyConsentDdbMapper")
    @Autowired
    public DynamoDBMapper studyConsentDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoStudyConsent1.class);
    }

    @Bean(name = "subpopulationDdbMapper")
    @Autowired
    public DynamoDBMapper subpopulationDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSubpopulation.class);
    }
    
    @Bean(name = "appConfigDdbMapper")
    @Autowired
    public DynamoDBMapper appConfigDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoAppConfig.class);
    }
    
    @Bean(name = "appConfigElementDdbMapper")
    @Autowired
    public DynamoDBMapper appConfigElementDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoAppConfigElement.class);
    }
    
    @Bean(name = "surveyMapper")
    @Autowired
    public DynamoDBMapper surveyDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSurvey.class);
    }

    @Bean(name = "surveyElementMapper")
    @Autowired
    public DynamoDBMapper surveyElementDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSurveyElement.class);
    }

    @Bean(name = "criteriaMapper")
    @Autowired
    public DynamoDBMapper criteriaMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoCriteria.class);
    }
    
    @Bean(name = "schedulePlanMapper")
    @Autowired
    public DynamoDBMapper schedulePlanMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoSchedulePlan.class);
    }
    
    @Bean(name = "masterSchedulerConfigMapper")
    @Autowired
    public DynamoDBMapper masterSchedulerConfigMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoMasterSchedulerConfig.class);
    }
    
    @Bean(name = "masterSchedulerStatusMapper")
    @Autowired
    public DynamoDBMapper masterSchedulerStatusMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoMasterSchedulerStatus.class);
    }
    
    @Bean(name = "notificationRegistrationMapper")
    @Autowired
    public DynamoDBMapper notificationRegistrationMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoNotificationRegistration.class);
    }
    
    @Bean(name = "notificationTopicMapper")
    @Autowired
    public DynamoDBMapper notificationTopicMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoNotificationTopic.class);
    }
    
    @Bean(name = "topicSubscriptionMapper")
    @Autowired
    public DynamoDBMapper topicSubscriptionMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoTopicSubscription.class);
    }
    
    @Bean(name = "oauthAccessGrantMapper")
    @Autowired
    public DynamoDBMapper oauthAccessGrantMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoOAuthAccessGrant.class);
    }
    
    @Bean(name = "uploadHealthCodeRequestedOnIndex")
    @Autowired
    public DynamoIndexHelper uploadHealthCodeRequestedOnIndex(AmazonDynamoDBClient dynamoDBClient, DynamoUtils dynamoUtils,
            DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoUpload2.class, "healthCode-requestedOn-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }
    
    @Bean(name = "healthCodeActivityGuidIndex")
    @Autowired
    public DynamoIndexHelper healthCodeActivityGuidIndex(AmazonDynamoDBClient dynamoDBClient, DynamoUtils dynamoUtils,
            DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoScheduledActivity.class, "healthCodeActivityGuid-scheduledOnUTC-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }
    
    @Bean(name = "uploadStudyIdRequestedOnIndex")
    @Autowired
    public DynamoIndexHelper uploadStudyIdRequestedOnIndex(AmazonDynamoDBClient dynamoDBClient, DynamoUtils dynamoUtils,
            DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoUpload2.class, "studyId-requestedOn-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "healthDataHealthCodeCreatedOnIndex")
    @Autowired
    public DynamoIndexHelper healthDataHealthCodeCreatedOnIndex(AmazonDynamoDBClient dynamoDBClient,
                                                       DynamoUtils dynamoUtils,
                                                       DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "healthCode-createdOn-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "healthDataUploadDateIndex")
    @Autowired
    public DynamoIndexHelper healthDataUploadDateIndexDynamoUtils(AmazonDynamoDBClient dynamoDBClient,
                                                                  DynamoUtils dynamoUtils,
                                                                  DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "uploadDate-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }
    
    @Bean(name = "activitySchedulePlanGuidIndex")
    @Autowired
    public DynamoIndexHelper activitySchedulePlanGuidIndex(AmazonDynamoDBClient dynamoDBClient,
                                                           DynamoUtils dynamoUtils,
                                                           DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper
                .create(DynamoScheduledActivity.class, "schedulePlanGuid-index", dynamoDBClient, dynamoNamingHelper, dynamoUtils);
    }
    
    @Bean(name = "healthCodeReferentGuidIndex")
    @Autowired
    public DynamoIndexHelper healthCodeReferentGuidIndex(AmazonDynamoDBClient dynamoDBClient, DynamoUtils dynamoUtils,
            DynamoNamingHelper dynamoNamingHelper) {
        return DynamoIndexHelper.create(DynamoScheduledActivity.class, "healthCode-referentGuid-index", dynamoDBClient,
                dynamoNamingHelper, dynamoUtils);
    }

    @Bean(name = "uploadDdbMapper")
    @Autowired
    public DynamoDBMapper uploadDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUpload2.class);
    }

    @Bean(name = "uploadDedupeDdbMapper")
    public DynamoDBMapper uploadDedupeDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUploadDedupe.class);
    }
    
    @Bean(name = "fphsExternalIdDdbMapper")
    @Autowired
    public DynamoDBMapper fphsExternalIdDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoFPHSExternalIdentifier.class);
    }
    
    @Bean(name = "externalIdDdbMapper")
    @Autowired
    public DynamoDBMapper externalIdDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoExternalIdentifier.class);
    }

    @Bean(name = "participantFileDdbMapper")
    @Autowired
    public DynamoDBMapper participantFileDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoParticipantFile.class);
    }
    
    @Bean(name = "uploadValidationHandlerList")
    @Autowired
    public List<UploadValidationHandler> uploadValidationHandlerList(S3DownloadHandler s3DownloadHandler,
            DecryptHandler decryptHandler, UnzipHandler unzipHandler,
            InitRecordHandler initRecordHandler, UploadFormatHandler uploadFormatHandler,
            StrictValidationHandler strictValidationHandler, TranscribeConsentHandler transcribeConsentHandler,
            UploadRawZipHandler uploadRawZipHandler, UploadArtifactsHandler uploadArtifactsHandler) {
        return ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler,
                initRecordHandler, uploadFormatHandler, strictValidationHandler, transcribeConsentHandler,
                uploadRawZipHandler, uploadArtifactsHandler);
    }

    @Bean(name = "uploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoUploadSchema.class);
    }

    @Bean(name = "activityDdbMapper")
    @Autowired
    public DynamoDBMapper activityDdbMapper(DynamoUtils dynamoUtils) {
        return dynamoUtils.getMapper(DynamoScheduledActivity.class);
    }

    @Bean
    public FileHelper fileHelper() {
        return new FileHelper();
    }
    
    private String databaseURL() {
        BridgeConfig config = bridgeConfig();
        
        String url = config.get("hibernate.connection.url");
        // Append SSL props to URL
        boolean useSsl = Boolean.valueOf(config.get("hibernate.connection.useSSL"));
        url += "?rewriteBatchedStatements=true&serverTimezone=UTC&requireSSL="+useSsl+"&useSSL="+useSsl+"&verifyServerCertificate="+useSsl;
        
        return url;
    }

    @Bean
    @Autowired
    public SessionFactory hibernateSessionFactory(TagEventListener listener) {
        ClassLoader classLoader = getClass().getClassLoader();

        // Need to set env vars to find the truststore so we can validate Amazon's RDS SSL certificate. Note that
        // because this truststore only contains public certs (CA certs and Amazon's RDS certs), we can include the
        // truststore in our source repo and set the password to something public.
        //
        // For more information, see
        // https://stackoverflow.com/questions/32156046/using-java-to-establish-a-secure-connection-to-mysql-amazon-rds-ssl-tls
        // https://stackoverflow.com/questions/27536380/how-to-connect-to-a-remote-mysql-database-via-ssl-using-play-framework/27536391
        Path trustStorePath = null;
        try {
            trustStorePath = Paths.get(classLoader.getResource("truststore.jks").toURI());
        } catch (URISyntaxException ex/*IOException ex*/) {
            throw new RuntimeException("Error loading truststore from classpath: " + ex.getMessage(), ex);
        }
        System.setProperty("javax.net.ssl.trustStore", trustStorePath.toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "public");

        // Hibernate configs
        Properties props = new Properties();
        props.put("hibernate.connection.characterEncoding", "UTF-8");
        props.put("hibernate.connection.CharSet", "UTF-8");
        props.put("hibernate.connection.useUnicode", true);
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        // c3p0 connection pool properties
        props.put("hibernate.c3p0.min_size", 5);
        props.put("hibernate.c3p0.max_size", 20);
        props.put("hibernate.c3p0.timeout", 300);
        props.put("hibernate.c3p0.idle_test_period", 300);

        // Connection properties come from Bridge configs
        BridgeConfig config = bridgeConfig();
        props.put("hibernate.connection.password", config.get("hibernate.connection.password"));
        props.put("hibernate.connection.username", config.get("hibernate.connection.username"));
        props.put("hibernate.connection.url", databaseURL());

        StandardServiceRegistry reg = new StandardServiceRegistryBuilder().applySettings(props).build();
        
        // For whatever reason, we need to list each Hibernate-enabled class individually.
        MetadataSources metadataSources = new MetadataSources(reg);
        metadataSources.addAnnotatedClass(HibernateAccount.class);
        metadataSources.addAnnotatedClass(HibernateStudy.class);
        metadataSources.addAnnotatedClass(HibernateEnrollment.class);
        metadataSources.addAnnotatedClass(HibernateSharedModuleMetadata.class);
        metadataSources.addAnnotatedClass(HibernateAccountSecret.class);
        metadataSources.addAnnotatedClass(HibernateTemplate.class);
        metadataSources.addAnnotatedClass(HibernateTemplateRevision.class);
        metadataSources.addAnnotatedClass(RequestInfo.class);
        metadataSources.addAnnotatedClass(FileMetadata.class);
        metadataSources.addAnnotatedClass(FileRevision.class);
        metadataSources.addAnnotatedClass(HibernateAssessment.class);
        metadataSources.addAnnotatedClass(HibernateAssessmentResource.class);
        metadataSources.addAnnotatedClass(HibernateAssessmentConfig.class);
        metadataSources.addAnnotatedClass(HibernateOrganization.class);
        metadataSources.addAnnotatedClass(Schedule2.class);
        metadataSources.addAnnotatedClass(Session.class);
        metadataSources.addAnnotatedClass(Tag.class);
        metadataSources.addAnnotatedClass(TimelineMetadata.class);
        metadataSources.addAnnotatedClass(AdherenceRecord.class);
        metadataSources.addAnnotatedClass(StudyActivityEvent.class);
        metadataSources.addAnnotatedClass(Notification.class);
        
        SessionFactory factory = metadataSources.buildMetadata().buildSessionFactory();
        
        // I could not find a more elegant way to register this listener that was picked up by Hibernate
        ServiceRegistryImplementor serviceImpl = ((SessionFactoryImplementor)factory).getServiceRegistry();
        EventListenerRegistry eventRegistry = serviceImpl.getService(EventListenerRegistry.class);
        eventRegistry.appendListeners(SAVE_UPDATE, listener);
        eventRegistry.appendListeners(DELETE, listener);
        eventRegistry.appendListeners(MERGE, listener);
        
        return factory;
    }
    
    @Bean
    @Profile("noinit")
    public DataSource primaryDataSource() {
        BridgeConfig config = bridgeConfig();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClass("com.mysql.jdbc.Driver");
        dataSource.setJdbcUrl(databaseURL());
        dataSource.setUser(config.get("hibernate.connection.username"));
        dataSource.setPassword(config.get("hibernate.connection.password"));
        return dataSource;
    }
    
    @Bean
    @Profile("default")
    @LiquibaseDataSource
    public DataSource dataSource() {
        BridgeConfig config = bridgeConfig();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClass("com.mysql.jdbc.Driver");
        dataSource.setJdbcUrl(databaseURL());
        dataSource.setUser(config.get("hibernate.connection.username"));
        dataSource.setPassword(config.get("hibernate.connection.password"));
        return dataSource;
    }
    
    // For cases where we have no special exception handling, the basicHibernateHelper
    // is sufficient.
    @Bean(name = "basicHibernateHelper")
    @Autowired
    public HibernateHelper basicHibernateHelper(SessionFactory sessionFactory,
            BasicPersistenceExceptionConverter converter) {
        return new HibernateHelper(sessionFactory, converter);
    }
    
    @Bean(name = "accountHibernateHelper")
    @Autowired
    public HibernateHelper accountHibernateHelper(SessionFactory sessionFactory,
            AccountPersistenceExceptionConverter converter) {
        return new HibernateHelper(sessionFactory, converter);
    }
    
    @Bean(name = "sponsorHibernateHelper")
    @Autowired
    public HibernateHelper sponsorHibernateHelper(SessionFactory sessionFactory,
            SponsorPersistenceExceptionConverter converter) {
        return new HibernateHelper(sessionFactory, converter);
    }
    
    @Bean(name = "organizationHibernateHelper")
    @Autowired
    public HibernateHelper organizationHibernateHelper(SessionFactory sessionFactory,
            OrganizationPersistenceExceptionConverter converter) {
        return new HibernateHelper(sessionFactory, converter);
    }
    
    @Bean(name = "mysqlHibernateHelper")
    @Autowired
    public HibernateHelper schedule2HibernateHelper(SessionFactory sessionFactory,
            MySQLHibernatePersistenceExceptionConverter converter) {
        return new HibernateHelper(sessionFactory, converter);
    }
    
    @Bean(name = "sessionExpireInSeconds")
    public int getSessionExpireInSeconds() {
        return BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
    }

    @Bean(name="bridgePFSynapseClient")
    public SynapseClient synapseClient() {
        SynapseClient synapseClient = new SynapseAdminClientImpl();
        synapseClient.setUsername(bridgeConfig().get("synapse.user"));
        synapseClient.setApiKey(bridgeConfig().get("synapse.api.key"));
        return synapseClient;
    }

    @Bean(name="exporterSynapseClient")
    public SynapseClient exporterSynapseClient() {
        SynapseClient synapseClient = new SynapseAdminClientImpl();
        synapseClient.setUsername(bridgeConfig().get("exporter.synapse.user"));
        synapseClient.setApiKey(bridgeConfig().get("exporter.synapse.api.key"));
        return synapseClient;
    }

    @Bean(name="exporterSynapseHelper")
    public SynapseHelper exporterSynapseHelper() {
        SynapseHelper synapseHelper = new SynapseHelper();
        synapseHelper.setSynapseClient(exporterSynapseClient());
        return synapseHelper;
    }

    @Bean(name = "genericViewCache")
    @Autowired
    public ViewCache genericViewCache(CacheProvider cacheProvider) {
        ViewCache cache = new ViewCache();
        cache.setCacheProvider(cacheProvider);
        cache.setObjectMapper(BridgeObjectMapper.get());
        cache.setCachePeriod(BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        return cache;
    }
    
    @Bean(name = "appLinkViewCache")
    @Autowired
    public ViewCache appLinkViewCache(CacheProvider cacheProvider) {
        ViewCache cache = new ViewCache();
        cache.setCacheProvider(cacheProvider);
        cache.setObjectMapper(new ObjectMapper());
        cache.setCachePeriod(BridgeConstants.APP_LINKS_EXPIRE_IN_SECONDS);
        return cache;
    }
    
    // From BridgeProductionSpringConfig in BridgePF

    @Bean(name = "jedisOps")
    public JedisOps jedisOps() throws URISyntaxException {
        return new JedisOps(jedisPool());
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool() throws URISyntaxException {
        return createJedisPool("elasticache.url");
    }

    private JedisPool createJedisPool(@SuppressWarnings("SameParameterValue") String redisServerProperty)
            throws URISyntaxException {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(bridgeConfig().getPropertyAsInt("redis.max.total"));
        poolConfig.setMinIdle(bridgeConfig().getPropertyAsInt("redis.min.idle"));
        poolConfig.setMaxIdle(bridgeConfig().getPropertyAsInt("redis.max.idle"));
        poolConfig.setTestOnCreate(true);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        final String url = bridgeConfig().get(redisServerProperty);
        final JedisPool jedisPool = constructJedisPool(url, poolConfig);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(jedisPool::destroy));

        return jedisPool;
    }
    
    private JedisPool constructJedisPool(final String url, final JedisPoolConfig poolConfig)
            throws URISyntaxException {

        URI redisURI = new URI(url);
        String password = BridgeUtils.extractPasswordFromURI(redisURI);
        
        if (password != null) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    bridgeConfig().getPropertyAsInt("redis.timeout"), password);
        }
        return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                bridgeConfig().getPropertyAsInt("redis.timeout"));
    }   
    
    @Bean(name = "defaultSchemaRevisionMap")
    public Map<String,Map<String,Integer>> defaultSchemaRevisionMap() {
        return new ImmutableMap.Builder<String,Map<String,Integer>>()
                .put("api", ImmutableMap.of(
                        "schema-rev-test", 2))
                .put("asthma", ImmutableMap.of(
                        "Air Quality Report", 4, 
                        "NonIdentifiableDemographicsTask", 2))
                .put("breastcancer", ImmutableMap.of(
                        "Journal", 3, 
                        "My Journal", 3, 
                        "NonIdentifiableDemographicsTask", 2))
                .put("cardiovascular", ImmutableMap.of(
                        "6-Minute Walk Test", 3,  
                        "2-APHHeartAge-7259AC18-D711-47A6-ADBD-6CFCECDED1DF", 2,
                        "NonIdentifiableDemographicsTask", 2))
                .put("diabetes", ImmutableMap.of(
                        "NonIdentifiableDemographicsTask", 2, 
                        "glucoseLogEntryStep", 2))
                .put("parkinson", ImmutableMap.of(
                        "NonIdentifiableDemographicsTask", 2, 
                        "Tapping Activity", 2, 
                        "Voice Activity", 3, 
                        "Walking Activity", 5))
                .build();
    }
    
}
