package org.sagebionetworks.bridge.models.studies;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

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
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "firstName", column = @Column(name = "accountFirstName")),
            @AttributeOverride(name = "lastName", column = @Column(name = "accountLastName")),
            @AttributeOverride(name = "email", column = @Column(name = "accountEmail")),
            @AttributeOverride(name = "phone.number", column = @Column(name = "accountPhone")),
            @AttributeOverride(name = "phone.regionCode", column = @Column(name = "accountPhoneRegion")),
            @AttributeOverride(name = "synapseUserId", column = @Column(name = "accountSynapseUserId")),
            @AttributeOverride(name = "orgMembership", column = @Column(name = "accountOrgMembership")),
            @AttributeOverride(name = "identifier", column = @Column(name = "accountIdentifier")),
            @AttributeOverride(name = "externalId", column = @Column(name = "accountExternalId"))
    })
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

    public Alert(String id, DateTime createdOn, String studyId, String appId, AccountRef participant,
            AlertCategory category, JsonNode data) {
        this.id = id;
        this.createdOn = createdOn;
        this.studyId = studyId;
        this.appId = appId;
        this.participant = participant;
        this.category = category;
        this.data = data;
    }

    public static Alert newEnrollment(String studyId, String appId, AccountRef participant) {
        return new Alert(null, null, studyId, appId, participant, AlertCategory.NEW_ENROLLMENT,
                BridgeObjectMapper.get().nullNode());
    }

    public static Alert timelineAccessed(String studyId, String appId, AccountRef participant) {
        return new Alert(null, null, studyId, appId, participant, AlertCategory.TIMELINE_ACCESSED,
                BridgeObjectMapper.get().nullNode());
    }

    public static Alert lowAdherence(String studyId, String appId, AccountRef participant, double adherenceThreshold) {
        return new Alert(null, null, studyId, appId, participant, AlertCategory.LOW_ADHERENCE,
                BridgeObjectMapper.get().valueToTree(new LowAdherenceAlertData(adherenceThreshold)));
    }

    public static Alert studyBurstChange(String studyId, String appId, AccountRef participant) {
        return new Alert(null, null, studyId, appId, participant, AlertCategory.STUDY_BURST_CHANGE,
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
                + participant == null ? ""
                        : ", participant=" + participant.getIdentifier() + ", category=" + category + ", data=" + data
                                + "]";
    }
}
