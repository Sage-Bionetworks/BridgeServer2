package org.sagebionetworks.bridge.models.schedules2.adherence;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "AdherenceRecords")
@IdClass(AdherenceRecordId.class)
public class AdherenceRecord implements BridgeEntity {
    
    @Id
    private String userId;
    @Id
    private String studyId;
    @Id
    private String instanceGuid;
    @Id
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime startedOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime finishedOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime eventTimestamp;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode clientData;
    
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    public void setInstanceGuid(String instanceGuid) {
        this.instanceGuid = instanceGuid;
    }
    public DateTime getStartedOn() {
        return startedOn;
    }
    public void setStartedOn(DateTime startedOn) {
        this.startedOn = startedOn;
    }
    public DateTime getFinishedOn() {
        return finishedOn;
    }
    public void setFinishedOn(DateTime finishedOn) {
        this.finishedOn = finishedOn;
    }
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }
    public void setEventTimestamp(DateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
    /**
     * Arbitrary JSON information stored by the client (limited to ~65k).
     */
    public JsonNode getClientData() {
        return clientData;
    }
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
}
