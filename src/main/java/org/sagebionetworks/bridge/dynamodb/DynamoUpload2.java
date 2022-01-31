package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;

/**
 * This DynamoDB table stores metadata for Bridge uploads. This class also defines global secondary indices, which can
 * be used for more efficient queries.
 */
@DynamoDBTable(tableName = "Upload2")
public class DynamoUpload2 implements Upload {
    private long contentLength;
    private String contentMd5;
    private String contentType;
    private String duplicateUploadId;
    private Boolean encrypted;
    private String filename;
    private String healthCode;
    private ObjectNode metadata;
    private String recordId;
    private UploadStatus status;
    private String appId;
    private long requestedOn;
    private long completedOn;
    private UploadCompletionClient completedBy;
    private LocalDate uploadDate;
    private String uploadId;
    private final List<String> validationMessageList = new ArrayList<>();
    private Long version;
    private Boolean zipped;
    private String instanceGuid;
    private DateTime eventTimestamp;

    /** This empty constructor is needed by the DynamoDB mapper. */
    public DynamoUpload2() {}

    /** Construct a DDB Upload object from an upload request and healthcode. */
    public DynamoUpload2(UploadRequest uploadRequest, String healthCode) {
        contentLength = uploadRequest.getContentLength();
        contentType = uploadRequest.getContentType();
        contentMd5 = uploadRequest.getContentMd5();
        encrypted = uploadRequest.isEncrypted();
        filename = uploadRequest.getName();
        this.healthCode = healthCode;
        // Already validated by UploadValidator.
        metadata = (ObjectNode) uploadRequest.getMetadata();
        status = UploadStatus.REQUESTED;
        uploadId = BridgeUtils.generateGuid();
        zipped = uploadRequest.isZipped();
        instanceGuid = uploadRequest.getInstanceGuid();
        eventTimestamp = uploadRequest.getEventTimestamp();
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public boolean canBeValidated() {
        // The only status that can be validated is REQUESTED. Once validation happens, the status moves to
        // VALIDATION_IN_PROGRESS, and the user can no longer call uploadComplete() to kick off validation.
        return status == UploadStatus.REQUESTED;
    }

    /** Upload content length in bytes. */
    @Override
    public long getContentLength() {
        return contentLength;
    }

    /** @see #getContentLength */
    @Override
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    /** The base64-encoded, 128-bit MD5 digest of the object body. */
    @Override
    public String getContentMd5() {
        return contentMd5;
    }

    /** @see #getContentMd5 */
    @Override
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }

    /** MIME content type. */
    @Override
    public String getContentType() {
        return contentType;
    }

    /** @see #getContentType */
    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /** {@inheritDoc} */
    @Override
    public String getDuplicateUploadId() {
        return duplicateUploadId;
    }

    /** @see #getDuplicateUploadId */
    public void setDuplicateUploadId(String duplicateUploadId) {
        this.duplicateUploadId = duplicateUploadId;
    }

    @Override
    public boolean isEncrypted() {
        // Defaults to true;
        return encrypted != null ? encrypted : true;
    }

    @Override
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    /** {@inheritDoc} */
    @Override
    public String getFilename() {
        return filename;
    }

    /** @see #getFilename */
    @Override
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-requestedOn-index")
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
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public ObjectNode getMetadata() {
        return metadata;
    }

    /** {@inheritDoc} */
    @Override
    public void setMetadata(ObjectNode metadata) {
        this.metadata = metadata;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public String getObjectId() {
        // In DynamoUpload2, object ID and upload ID are the same.
        return uploadId;
    }

    /** {@inheritDoc} */
    @Override
    public String getRecordId() {
        return recordId;
    }

    /** @see #getRecordId */
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public UploadStatus getStatus() {
        return status;
    }

    /** @see #getStatus */
    @Override
    public void setStatus(UploadStatus status) {
        this.status = status;
    }
    
    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "studyId-requestedOn-index")
    @Override
    public String getAppId() {
        return appId;
    }
    
    /** @see #getAppId */
    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    // This is currently returned from the API and needs to be maintained until 
    // downstream users are updated.
    @DynamoDBIgnore
    public String getStudyId() {
        return appId;
    }
    
    /** {@inheritDoc} */
    @DynamoDBIndexRangeKey(attributeName = "requestedOn", globalSecondaryIndexNames ={"healthCode-requestedOn-index", "studyId-requestedOn-index"})
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @JsonInclude(Include.NON_DEFAULT)
    @Override
    public long getRequestedOn() {
        return requestedOn;
    }
    
    /** @see #getRequestedOn */
    @Override
    public void setRequestedOn(long requestedOn) {
        this.requestedOn = requestedOn;
    }
    
    /** {@inheritDoc} */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @JsonInclude(Include.NON_DEFAULT)
    @Override
    public long getCompletedOn() {
        return completedOn;
    }
    
    /** @see #getCompletedOn */
    @Override
    public void setCompletedOn(long completedOn) {
        this.completedOn = completedOn;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public UploadCompletionClient getCompletedBy() {
        return completedBy;
    }
    
    /** @see #getCompletedBy */
    @Override
    public void setCompletedBy(UploadCompletionClient completedBy) {
        this.completedBy = completedBy;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = LocalDateMarshaller.class)
    @Override
    public LocalDate getUploadDate() {
        return uploadDate;
    }

    /** @see #getUploadDate */
    public void setUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
    }

    /**
     * Unique ID for the upload. This is used both as a DynamoDB key and as an S3 filename. We generate a guid for the
     * S3 filename to avoid hotspots in S3 from poorly distributed filenames.
     */
    @DynamoDBHashKey
    @Override
    public String getUploadId() {
        return uploadId;
    }

    /** @see #getUploadId */
    @Override
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * Returns an immutable copy of the validation message list. Changes to the underlying message list will not be
     * reflected in this copy. Note that the validation message list will be non-null and will not contain null
     * messages.
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    @Override
    public @Nonnull List<String> getValidationMessageList() {
        return ImmutableList.copyOf(validationMessageList);
    }

    /**
     * <p>
     * Sets the validation message list. Internally, this clears all messages from the internal validation message
     * list, then copies all elements from the given validation message list. Note that the validation message list
     * must be non-null and must not contain null messages.
     * </p>
     * <p>
     * The DynamoDB mapper needs this method, and it needs it to take a List of String.
     * </p>
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    public void setValidationMessageList(@Nonnull List<String> validationMessageList) {
        this.validationMessageList.clear();
        appendValidationMessages(validationMessageList);
    }

    /**
     * Appends validation messages to the validation message list. Note that the validation message list must be
     * non-null and must not contain null messages.
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    public void appendValidationMessages(@Nonnull Collection<? extends String> validationMessageList) {
        this.validationMessageList.addAll(validationMessageList);
    }

    /** DynamoDB version, used for optimistic locking */
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean isZipped() {
        // Defaults to true.
        return zipped != null ? zipped : true;
    }

    @Override
    public void setZipped(boolean zipped) {
        this.zipped = zipped;
    }

    /**
     * If this upload is based on an assessment specified by the v2 scheduler, this optional field should
     * be the instance GUID of the assessment or session in the participant’s Timeline. This will be used 
     * to associate the upload with scheduling context information. 
     */
    @Override
    public String getInstanceGuid() {
        return instanceGuid;
    }

    @Override
    /** @see #getInstanceGuid */
    public void setInstanceGuid(String instanceGuid) {
        this.instanceGuid = instanceGuid;
    }

    /**
     * If this upload is based on an assessment specified by the v2 scheduler, this optional field should
     * be the timestamp of the event that triggered this performance of the assessment or session in the 
     * participant’s Timeline. This will be used to associate the upload with scheduling context information. 
     */
    @Override
    @JsonSerialize(using = DateTimeSerializer.class)
    @DynamoDBTypeConverted(converter = DateTimeToLongMarshaller.class)
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }

    @Override
    @JsonDeserialize(using = DateTimeDeserializer.class)
    /** @see #getEventTimestamp */
    public void setEventTimestamp(DateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
}
