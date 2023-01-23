package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@Entity
@Table(name = "Alerts")
@BridgeTypeName("Alert")
public class Alert implements BridgeEntity {
    @Id
    private String id;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @JsonIgnore
    private String studyId;
    @JsonIgnore
    private String appId;
    @JsonIgnore
    private String userId;
    @Transient
    private AccountRef participant;
    @Enumerated(EnumType.STRING)
    private AlertCategory category;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode data;

    public enum AlertCategory {
        NEW_ENROLLMENT,
        TIMELINE_ACCESSED,
        LOW_ADHERENCE,
        UPCOMING_STUDY_BURST,
        STUDY_BURST_CHANGE
    }

    public Alert() {
    }

    public Alert(String id, DateTime createdOn, String studyId, String appId, String userId, AlertCategory category,
            JsonNode data) {
        this.id = id;
        this.createdOn = createdOn;
        this.studyId = studyId;
        this.appId = appId;
        this.userId = userId;
        this.category = category;
        this.data = data;
    }

    public static Alert newEnrollment(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, AlertCategory.NEW_ENROLLMENT,
                BridgeObjectMapper.get().nullNode());
    }

    public static Alert timelineAccessed(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, AlertCategory.TIMELINE_ACCESSED,
                BridgeObjectMapper.get().nullNode());
    }

    public static Alert lowAdherence(String studyId, String appId, String userId, double adherenceThreshold) {
        return new Alert(null, null, studyId, appId, userId, AlertCategory.LOW_ADHERENCE,
                BridgeObjectMapper.get().valueToTree(new LowAdherenceAlertData(adherenceThreshold)));
    }

    public static Alert studyBurstChange(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, AlertCategory.STUDY_BURST_CHANGE,
                BridgeObjectMapper.get().nullNode());
    }

    public static class LowAdherenceAlertData {
        double adherenceThreshold;

        public LowAdherenceAlertData(double adherenceThreshold) {
            this.adherenceThreshold = adherenceThreshold;
        }

        public double getAdherenceThreshold() {
            return adherenceThreshold;
        }

        public void setAdherenceThreshold(double adherenceThreshold) {
            this.adherenceThreshold = adherenceThreshold;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public AccountRef getParticipant() {
        return participant;
    }

    public void setParticipant(AccountRef participant) {
        this.participant = participant;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public void setCategory(AlertCategory category) {
        this.category = category;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Alert [id=" + id + ", createdOn=" + createdOn + ", studyId=" + studyId + ", appId=" + appId
                + ", userId=" + userId + ", category=" + category + ", data=" + data + "]";
    }
}
