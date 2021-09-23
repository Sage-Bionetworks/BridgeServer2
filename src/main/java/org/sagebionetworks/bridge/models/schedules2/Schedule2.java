package org.sagebionetworks.bridge.models.schedules2;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.joda.time.DateTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

@Entity
@Table(name = "Schedules")
@BridgeTypeName("Schedule")
public class Schedule2 implements BridgeEntity {

    @JsonIgnore
    private String appId;
    private String ownerId;
    private String name;
    @Id
    private String guid;
    @Convert(converter = PeriodToStringConverter.class)
    private Period duration;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode clientData;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    private boolean published;
    private boolean deleted;
    // orphanRemoval = true just does not work, and I'm not sure why. We need to treat
    // sessions as entities so we can have embedded collections in them (embeddables
    // can't embed further embeddable collections). Deletion code in the DAO handles
    // cleanup of removed sessions. 
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "position")
    private List<Session> sessions;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    @OrderColumn(name="position")
    @CollectionTable(name="ScheduleStudyBursts", joinColumns= {
            @JoinColumn(name="scheduleGuid")
    })
    private List<StudyBurst> studyBursts;
    
    @Version
    private long version;
    
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public Period getDuration() {
        return duration;
    }
    public void setDuration(Period duration) {
        this.duration = duration;
    }
    public JsonNode getClientData() {
        return clientData;
    }
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    public boolean isPublished() {
        return published;
    }
    public void setPublished(boolean published) {
        this.published = published;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public List<StudyBurst> getStudyBursts() {
        if (studyBursts == null) {
            studyBursts = new ArrayList<>();
        }
        return studyBursts;
    }
    public void setStudyBursts(List<StudyBurst> studyBursts) {
        this.studyBursts = studyBursts;
    }    
    public List<Session> getSessions() {
        if (sessions == null) {
            sessions = new ArrayList<>();
        }
        return sessions;
    }
    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    @JsonIgnore
    public Map<String, ActivityEventUpdateType> getStudyBurstsUpdateMap() {
        return getStudyBursts().stream()
                .filter(b -> b != null && b.getIdentifier() != null && b.getUpdateType() != null)
                .collect(toMap(StudyBurst::getIdentifier, StudyBurst::getUpdateType));
    }
}
