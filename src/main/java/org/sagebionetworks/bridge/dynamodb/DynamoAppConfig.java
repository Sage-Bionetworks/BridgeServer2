package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition.SchemaReferenceListMarshaller;
import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition.SurveyReferenceListMarshaller;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.assessments.AssessmentReference;
import org.sagebionetworks.bridge.models.files.FileReference;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "AppConfig")
@BridgeTypeName("AppConfig")
public class DynamoAppConfig implements AppConfig {
    public static class ConfigReferenceListMarshaller extends ListMarshaller<ConfigReference> {
        private static final TypeReference<List<ConfigReference>> CONFIG_REF_LIST_TYPE =
                new TypeReference<List<ConfigReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<ConfigReference>> getTypeReference() {
            return CONFIG_REF_LIST_TYPE;
        }
    }
    
    public static class FileReferenceListMarshaller extends ListMarshaller<FileReference> {
        private static final TypeReference<List<FileReference>> FILE_REF_LIST_TYPE =
                new TypeReference<List<FileReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<FileReference>> getTypeReference() {
            return FILE_REF_LIST_TYPE;
        }
    }
    
    public static class AssessmentReferenceListMarshaller extends ListMarshaller<AssessmentReference> {
        private static final TypeReference<List<AssessmentReference>> ASSESSMENT_REF_LIST_TYPE =
                new TypeReference<List<AssessmentReference>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<AssessmentReference>> getTypeReference() {
            return ASSESSMENT_REF_LIST_TYPE;
        }
    }

    private String appId;
    private String label;
    private String guid;
    private Criteria criteria;
    private long createdOn;
    private long modifiedOn;
    private JsonNode clientData;
    private List<SurveyReference> surveyReferences;
    private List<SchemaReference> schemaReferences;
    private List<ConfigReference> configReferences;
    private List<FileReference> fileReferences;
    private List<AssessmentReference> assessmentReferences;
    boolean configIncluded;
    private Map<String,JsonNode> configElements;
    private Long version;
    private boolean deleted;
    
    @JsonIgnore
    @DynamoDBHashKey(attributeName = "studyId")
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    @Override
    public String getLabel() {
        return label;
    }
    
    @Override
    public void setLabel(String label) {
        this.label = label;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getCreatedOn() {
        return createdOn;
    }
    
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }
    
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @DynamoDBRangeKey
    @Override
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    @DynamoDBIgnore
    @Override
    public Criteria getCriteria() {
        return criteria;
    }

    @Override
    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @Override
    public JsonNode getClientData() {
        return clientData;
    }

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    
    @DynamoDBTypeConverted(converter=SurveyReferenceListMarshaller.class)
    @Override
    public List<SurveyReference> getSurveyReferences() {
        if (surveyReferences == null) {
            surveyReferences = new ArrayList<>();
        }
        return surveyReferences;
    }

    @DynamoDBTypeConverted(converter=SurveyReferenceListMarshaller.class)
    @Override
    public void setSurveyReferences(List<SurveyReference> references) {
        this.surveyReferences = references; 
    }

    @DynamoDBTypeConverted(converter=SchemaReferenceListMarshaller.class)
    @Override
    public List<SchemaReference> getSchemaReferences() {
        if (schemaReferences == null) {
            schemaReferences = new ArrayList<>();
        }
        return schemaReferences;
    }

    @DynamoDBTypeConverted(converter=SchemaReferenceListMarshaller.class)
    @Override
    public void setSchemaReferences(List<SchemaReference> references) {
        this.schemaReferences = references;
    }
    
    @DynamoDBTypeConverted(converter=FileReferenceListMarshaller.class)
    @Override
    public void setFileReferences(List<FileReference> references) {
        this.fileReferences = references;
    }
    
    @DynamoDBTypeConverted(converter=FileReferenceListMarshaller.class)
    @Override
    public List<FileReference> getFileReferences() {
        if (fileReferences == null) {
            fileReferences = new ArrayList<>();
        }
        return fileReferences;
    }
    
    @DynamoDBTypeConverted(converter=AssessmentReferenceListMarshaller.class)
    @Override
    public void setAssessmentReferences(List<AssessmentReference> references) {
        this.assessmentReferences = references;
    }
    
    @DynamoDBTypeConverted(converter=FileReferenceListMarshaller.class)
    @Override
    public List<AssessmentReference> getAssessmentReferences() {
        if (assessmentReferences == null) {
            assessmentReferences = new ArrayList<>();
        }
        return assessmentReferences;
    }
    
    @DynamoDBTypeConverted(converter=ConfigReferenceListMarshaller.class)
    @Override
    public List<ConfigReference> getConfigReferences() {
        if (configReferences == null) {
            configReferences = new ArrayList<>();
        }
        return configReferences;
    }
    
    @DynamoDBTypeConverted(converter=ConfigReferenceListMarshaller.class)
    @Override
    public void setConfigReferences(List<ConfigReference> references) {
        this.configReferences = references;
    }
    
    @DynamoDBIgnore
    @Override
    public Map<String,JsonNode> getConfigElements() {
        if (configElements == null) {
            configElements = new HashMap<>();
        }
        return configElements;
    }
    
    @Override
    public void setConfigElements(Map<String,JsonNode> configElements) {
        this.configElements = configElements;
    };
        
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientData, createdOn, criteria, guid, label, modifiedOn, schemaReferences, appId,
                surveyReferences, configReferences, fileReferences, assessmentReferences, version, deleted);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoAppConfig other = (DynamoAppConfig) obj;
        return Objects.equals(clientData, other.clientData) && Objects.equals(createdOn, other.createdOn)
                && Objects.equals(criteria, other.criteria) && Objects.equals(guid, other.guid)
                && Objects.equals(label, other.label) && Objects.equals(modifiedOn, other.modifiedOn)
                && Objects.equals(getSchemaReferences(), other.getSchemaReferences())
                && Objects.equals(getSurveyReferences(), other.getSurveyReferences()) 
                && Objects.equals(getConfigReferences(), other.getConfigReferences())
                && Objects.equals(getFileReferences(), other.getFileReferences())
                && Objects.equals(getAssessmentReferences(), other.getAssessmentReferences())
                && Objects.equals(appId, other.appId) && Objects.equals(version, other.version)
                && Objects.equals(deleted, other.deleted);
    }

    @Override
    public String toString() {
        return "DynamoAppConfig [appId=" + appId + ", label=" + label + ", guid=" + guid + ", criteria=" + criteria
                + ", createdOn=" + createdOn + ", modifiedOn=" + modifiedOn + ", clientData=" + clientData
                + ", surveyReferences=" + getSurveyReferences() + ", schemaReferences=" + getSchemaReferences()
                + ", configReferences=" + getConfigReferences() + ", fileReferences=" + getFileReferences()
                + ", assessmentReferences=" + getAssessmentReferences() + ", version=" + version + ", deleted=" 
                + deleted + "]";
    }
}
