package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.HealthDataDoc;

import java.net.URL;

@DynamoDBTable(tableName = "HealthDataDoc")
public class DynamoHealthDataDoc implements HealthDataDoc {
    String title;
    String appId;
    String identifier;
    String doc; // TODO maybe replaced by docUrl
    Long version;
    URL docUrl;
    String createdBy;
    DateTime createdOn;
    String modifiedBy;
    DateTime modifiedOn;


    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @DynamoDBHashKey
    @Override
    public String getAppId() {
        return this.appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @DynamoDBRangeKey
    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public String getDoc() {
        return this.doc;
    }

    @Override
    public void setDoc(String doc) {
        this.doc = doc;
    }

    @Override
    public Long getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public URL getDocUrl() {
        return this.docUrl;
    }

    @Override
    public void setDocUrl(URL docUrl) {
        this.docUrl = docUrl;
    }

    @Override
    public String getCreatedBy() {
        return this.createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public DateTime getCreatedOn() {
        return this.createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public String getModifiedBy() {
        return this.modifiedBy;
    }

    @Override
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    @Override
    public DateTime getModifiedOn() {
        return this.modifiedOn;
    }

    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}
