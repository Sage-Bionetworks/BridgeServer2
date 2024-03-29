package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.reports.ReportIndex;

import java.util.HashSet;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

@DynamoDBTable(tableName = "ReportIndex")
public class DynamoReportIndex implements ReportIndex {

    private String key;
    private String identifier;
    private Set<String> studyIds;
    private boolean isPublic;
    
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }
    
    @DynamoDBRangeKey
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;

    }
    
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    @JsonAlias("substudyIds")
    @DynamoDBAttribute(attributeName = "substudyIds")
    public Set<String> getStudyIds() {
        if (this.studyIds == null) {
            this.studyIds = new HashSet<>();
        }
        return this.studyIds;
    }
    
    @Override
    public void setStudyIds(Set<String> studyIds) {
        this.studyIds = studyIds;
    }
    
    @DynamoDBAttribute
    @Override
    public boolean isPublic() {
        return isPublic;
    }
    
    @Override
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
