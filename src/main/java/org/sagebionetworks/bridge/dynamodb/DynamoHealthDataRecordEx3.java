package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;

@DynamoDBTable(tableName = "HealthDataRecordEx3")
public class DynamoHealthDataRecordEx3 implements HealthDataRecordEx3 {
    public static final String APPID_CREATEDON_INDEX = "appId-createdOn-index";
    public static final String APPSTUDYKEY_CREATEDON_INDEX = "appStudyKey-createdOn-index";
    public static final String HEALTHCODE_CREATEDON_INDEX = "healthCode-createdOn-index";

    private String id;
    private String appId;
    private String studyId;
    private String healthCode;
    private Integer participantVersion;
    private Long createdOn;
    private String clientInfo;
    private boolean exported;
    private Long exportedOn;
    private ExportedRecordInfo exportedRecord;
    private Map<String, ExportedRecordInfo> exportedStudyRecords;
    private Map<String, String> metadata;
    private SharingScope sharingScope;
    private Long version;
    private String downloadUrl;
    private long downloadExpiration;

    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @DynamoDBIndexHashKey(attributeName = "appId", globalSecondaryIndexName = APPID_CREATEDON_INDEX)
    @DynamoProjection(projectionType = ProjectionType.ALL, globalSecondaryIndexName = APPID_CREATEDON_INDEX)
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getStudyId() {
        return studyId;
    }

    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /**
     * We create a compound key to allow us to query by app and study. Note that some health data records do not belong
     * in a study. This is fine. They can still be accessed using the appId-createdOn-index.
     */
    @DynamoDBIndexHashKey(attributeName = "appStudyKey", globalSecondaryIndexName = APPSTUDYKEY_CREATEDON_INDEX)
    @DynamoProjection(projectionType = ProjectionType.ALL, globalSecondaryIndexName = APPSTUDYKEY_CREATEDON_INDEX)
    @JsonIgnore
    public String getAppStudyKey() {
        if (appId == null || studyId == null) {
            // Isn't part of a study. Return null.
            return null;
        }

        // App:Study
        return appId + ':' + studyId;
    }

    public void setAppStudyKey(String key) {
        // This should be in format App:Study. Ignore anything that doesn't fit this format.
        if (key != null) {
            String[] parts = key.split(":", 2);
            if (parts.length == 2) {
                appId = parts[0];
                studyId = parts[1];
            }
        }
    }

    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = HEALTHCODE_CREATEDON_INDEX)
    @DynamoProjection(projectionType = ProjectionType.ALL, globalSecondaryIndexName = HEALTHCODE_CREATEDON_INDEX)
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @Override
    public Integer getParticipantVersion() {
        return participantVersion;
    }

    @Override
    public void setParticipantVersion(Integer participantVersion) {
        this.participantVersion = participantVersion;
    }

    @DynamoDBIndexRangeKey(attributeName = "createdOn", globalSecondaryIndexNames = { APPID_CREATEDON_INDEX,
            APPSTUDYKEY_CREATEDON_INDEX, HEALTHCODE_CREATEDON_INDEX
    })
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getCreatedOn() {
        return createdOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(Long createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public String getClientInfo() {
        return clientInfo;
    }

    @Override
    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }

    @Override
    public boolean isExported() {
        return exported;
    }

    @Override
    public void setExported(boolean exported) {
        this.exported = exported;
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getExportedOn() {
        return exportedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setExportedOn(Long exportedOn) {
        this.exportedOn = exportedOn;
    }

    @DynamoDBTypeConverted(converter = ExportedRecordInfoMarshaller.class)
    @Override
    public ExportedRecordInfo getExportedRecord() {
        return exportedRecord;
    }

    @Override
    public void setExportedRecord(ExportedRecordInfo exportedRecord) {
        this.exportedRecord = exportedRecord;
    }

    @DynamoDBTypeConverted(converter = ExportedRecordInfoMapMarshaller.class)
    @Override
    public Map<String, ExportedRecordInfo> getExportedStudyRecords() {
        return exportedStudyRecords;
    }

    @Override
    public void setExportedStudyRecords(Map<String, ExportedRecordInfo> exportedStudyRecords) {
        // Dynamo DB doesn't support empty maps.
        this.exportedStudyRecords = exportedStudyRecords != null && !exportedStudyRecords.isEmpty() ?
                exportedStudyRecords : null;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(Map<String, String> metadata) {
        // Dynamo DB doesn't support empty maps.
        this.metadata = metadata != null && !metadata.isEmpty() ? metadata : null;
    }

    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public SharingScope getSharingScope() {
        return sharingScope;
    }

    @Override
    public void setSharingScope(SharingScope sharingScope) {
        this.sharingScope = sharingScope;
    }

    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    @DynamoDBIgnore
    public String getDownloadUrl() {
        return this.downloadUrl;
    }

    @Override
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getDownloadExpiration() {
        return this.downloadExpiration;
    }

    @Override
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setDownloadExpiration(long downloadExpiration) { this.downloadExpiration = downloadExpiration; }
}
