package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.annotation.JsonFilter;

import java.util.Map;
import java.util.Set;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.LocalDateToStringSerializer;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.LocalDateDeserializer;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataRecord}. */
@DynamoDBTable(tableName = "HealthDataRecord3")
@JsonFilter("filter")
public class DynamoHealthDataRecord implements HealthDataRecord {
    private String appVersion;
    private Long createdOn;
    private String createdOnTimeZone;
    private JsonNode data;
    private Integer dayInStudy;
    private String healthCode;
    private String id;
    private JsonNode metadata;
    private String phoneInfo;
    private String rawDataAttachmentId;
    private String schemaId;
    private Integer schemaRevision;
    private String appId;
    private LocalDate uploadDate;
    private String uploadId;
    private Long uploadedOn;
    private JsonNode userMetadata;
    private SharingScope userSharingScope;
    private String userExternalId;
    private Set<String> userDataGroups;
    private Map<String, String> userStudyMemberships;
    private Map<String, Integer> dayInEachStudy;
    private String validationErrors;
    private Long version;
    private ExporterStatus synapseExporterStatus;

    /** {@inheritDoc} */
    @Override
    public String getAppVersion() {
        return appVersion;
    }

    /** {@inheritDoc} */
    @Override
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexRangeKey(attributeName = "createdOn", globalSecondaryIndexName = "healthCode-createdOn-index")
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(Long createdOn) {
        this.createdOn = createdOn;
    }

    /** {@inheritDoc} */
    @Override
    public String getCreatedOnTimeZone() {
        return createdOnTimeZone;
    }

    /** @see #getCreatedOnTimeZone */
    @Override
    public void setCreatedOnTimeZone(String createdOnTimeZone) {
        this.createdOnTimeZone = createdOnTimeZone;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getData() {
        return data;
    }

    /** @see #getData */
    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getDayInStudy() {
        return dayInStudy;
    }

    /** @see #getDayInStudy */
    @Override
    public void setDayInStudy(Integer dayInStudy) {
        this.dayInStudy = dayInStudy;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-createdOn-index")
    @DynamoProjection(projectionType = ProjectionType.ALL, globalSecondaryIndexName = "healthCode-createdOn-index")
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    /** @see #getId */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getMetadata() {
        return metadata;
    }

    /** @see #getMetadata */
    @Override
    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    /** {@inheritDoc} */
    @Override
    public String getPhoneInfo() {
        return phoneInfo;
    }

    /** {@inheritDoc} */
    @Override
    public void setPhoneInfo(String phoneInfo) {
        this.phoneInfo = phoneInfo;
    }

    /** {@inheritDoc} */
    @Override
    public String getRawDataAttachmentId() {
        return rawDataAttachmentId;
    }

    /** {@inheritDoc} */
    @Override
    public void setRawDataAttachmentId(String rawDataAttachmentId) {
        this.rawDataAttachmentId = rawDataAttachmentId;
    }

    /** {@inheritDoc} */
    @Override
    public String getSchemaId() {
        return schemaId;
    }

    /** @see #getSchemaId */
    @Override
    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getSchemaRevision() {
        return schemaRevision;
    }

    /** @see #getSchemaRevision */
    @Override
    public void setSchemaRevision(Integer schemaRevision) {
        this.schemaRevision = schemaRevision;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "study-uploadedOn-index")
    @DynamoDBAttribute(attributeName = "studyId")
    @Override
    public String getAppId() {
        return appId;
    }

    /** @see #getAppId */
    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    // This value is exposed in the API and needs to be maintained through migration
    @DynamoDBIgnore
    public String getStudyId() {
        return appId;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "uploadDate", globalSecondaryIndexName = "uploadDate-index")
    @DynamoDBTypeConverted(converter = LocalDateMarshaller.class)
    @JsonSerialize(using = LocalDateToStringSerializer.class)
    @Override
    public LocalDate getUploadDate() {
        return uploadDate;
    }

    /** @see #getUploadDate */
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @Override
    public void setUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
    }

    /** {@inheritDoc} */
    @Override
    public String getUploadId() {
        return uploadId;
    }

    /** @see #getUploadId */
    @Override
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexRangeKey(attributeName = "uploadedOn", globalSecondaryIndexName = "study-uploadedOn-index")
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getUploadedOn() {
        return uploadedOn;
    }

    /** @see #getUploadedOn */
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setUploadedOn(Long uploadedOn) {
        this.uploadedOn = uploadedOn;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getUserMetadata() {
        return userMetadata;
    }

    /** {@inheritDoc} */
    @Override
    public void setUserMetadata(JsonNode userMetadata) {
        this.userMetadata = userMetadata;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public SharingScope getUserSharingScope() {
        return userSharingScope;
    }

    /** @see #getUserSharingScope */
    @Override
    public void setUserSharingScope(SharingScope userSharingScope) {
        this.userSharingScope = userSharingScope;
    }
    
    /** {@inheritDoc} */
    @Override
    public String getUserExternalId() {
        return userExternalId;
    }

    /** @see #getUserExternalId */
    @Override
    public void setUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    /** @see #getUserDataGroups() */
    @Override
    public void setUserDataGroups(Set<String> userDataGroups) {
        // DDB doesn't support empty sets, use null reference for empty set. This is also enforced by the builder.
        this.userDataGroups = (userDataGroups != null && !userDataGroups.isEmpty()) ? userDataGroups : null;
    }
    
    /** {@inheritDoc} */
    @Override
    @DynamoDBAttribute(attributeName = "userSubstudyMemberships")
    public Map<String, String> getUserStudyMemberships() {
        return userStudyMemberships;
    }
    
    /** @see #getUserStudyMemberships() */
    @Override
    public void setUserStudyMemberships(Map<String, String> userStudyMemberships) {
        this.userStudyMemberships = (userStudyMemberships != null && !userStudyMemberships.isEmpty())
                ? userStudyMemberships : null;
    }
    
    /** {@inheritDoc} */
    @Override
    @DynamoDBAttribute
    public Map<String, Integer> getDayInEachStudy() {
        return dayInEachStudy;
    }
    
    /** @see #getDayInEachStudy() */
    @Override
    public void setDayInEachStudy(Map<String, Integer> dayInEachStudy) {
        this.dayInEachStudy = dayInEachStudy;
    }

    /** {@inheritDoc} */
    @Override
    public String getValidationErrors() {
        return validationErrors;
    }

    /** {@inheritDoc} */
    @Override
    public void setValidationErrors(String validationErrors) {
        this.validationErrors = validationErrors;
    }

    /** {@inheritDoc} */
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public ExporterStatus getSynapseExporterStatus() {
        return synapseExporterStatus;
    }

    /** @see #getSynapseExporterStatus */
    @Override
    public void setSynapseExporterStatus(ExporterStatus synapseExporterStatus) {
        this.synapseExporterStatus = synapseExporterStatus;
    }
}
