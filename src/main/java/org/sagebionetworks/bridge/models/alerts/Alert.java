package org.sagebionetworks.bridge.models.alerts;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
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
    private String category;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode data;

    public Alert(String id, DateTime createdOn, String studyId, String appId, AccountRef participant, String category,
            JsonNode data) {
        this.id = id;
        this.createdOn = createdOn;
        this.studyId = studyId;
        this.appId = appId;
        this.participant = participant;
        this.category = category;
        this.data = data;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
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
