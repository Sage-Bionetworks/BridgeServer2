package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Version;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyContact;
import org.sagebionetworks.bridge.models.studies.StudyId;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@Entity
@Table(name = "Substudies")
@IdClass(StudyId.class)
@BridgeTypeName("Study")
public class HibernateStudy implements Study {
    @Id
    @Column(name = "id")
    private String identifier;
    @Id
    private String appId;
    private String name;
    private boolean deleted;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    @Column(columnDefinition = "mediumtext", name = "clientData", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode clientData;
    private String createdBy;
    private String modifiedBy;
    private String details;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime launchedOn;
    private String launchedBy;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime closeoutOn;
    private String closeoutBy;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime irbApprovedOn;
    private String studyLogoUrl;
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;
    private String externalProtocolId;
    private String irbProtocolId;
    private String scheduleGuid;
    @Version
    private Long version;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name="pos") // can’t use 'position' in this case
    @CollectionTable(name="StudyContacts", joinColumns= {
            @JoinColumn(name="appId"), @JoinColumn(name="studyId")
    })
    private List<StudyContact> contacts;
    
    /**
     * For full construction of object by Hibernate.
     */
    public HibernateStudy() {}
    
    /**
     * For partial construction of object by Hibernate, excluding expensive fields like clientData.
     */
    public HibernateStudy(String name, String identifier, String appId, boolean deleted, DateTime createdOn,
            DateTime modifiedOn, Long version) {
        this.name = name;
        this.identifier = identifier;
        this.appId = appId;
        this.deleted = deleted;
        this.createdOn = createdOn;
        this.modifiedOn = modifiedOn;
        this.version = version;
    }
    
    @Override
    @JsonAlias("id")
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @JsonIgnore
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }
    
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    @Override
    public JsonNode getClientData() {
        return clientData;
    }
    
    @Override
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    
    @Override
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public DateTime getModifiedOn() {
        return modifiedOn;
    }

    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @Override
    public List<StudyContact> getContacts() {
        if (contacts == null) {
            contacts = new ArrayList<>();
        }
        return contacts;
    }

    @Override
    public void setContacts(List<StudyContact> contacts) {
        this.contacts = contacts;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String getModifiedBy() {
        return modifiedBy;
    }

    @Override
    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    @Override
    public String getDetails() {
        return details;
    }

    @Override
    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public DateTime getLaunchedOn() {
        return launchedOn;
    }

    @Override
    public void setLaunchedOn(DateTime launchedOn) {
        this.launchedOn = launchedOn;
    }

    @Override
    public String getLaunchedBy() {
        return launchedBy;
    }

    @Override
    public void setLaunchedBy(String launchedBy) {
        this.launchedBy = launchedBy;
    }

    @Override
    public DateTime getCloseoutOn() {
        return closeoutOn;
    }

    @Override
    public void setCloseoutOn(DateTime closeoutOn) {
        this.closeoutOn = closeoutOn;
    }

    @Override
    public String getCloseoutBy() {
        return closeoutBy;
    }

    @Override
    public void setCloseoutBy(String closeoutBy) {
        this.closeoutBy = closeoutBy;
    }

    @Override
    public DateTime getIrbApprovedOn() {
        return irbApprovedOn;
    }

    @Override
    public void setIrbApprovedOn(DateTime irbApprovedOn) {
        this.irbApprovedOn = irbApprovedOn;
    }

    @Override
    public String getStudyLogoUrl() {
        return studyLogoUrl;
    }

    @Override
    public void setStudyLogoUrl(String studyLogoUrl) {
        this.studyLogoUrl = studyLogoUrl;
    }

    @Override
    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    @Override
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    @Override
    public String getExternalProtocolId() {
        return externalProtocolId;
    }

    @Override
    public void setExternalProtocolId(String externalProtocolId) {
        this.externalProtocolId = externalProtocolId;
    }

    @Override
    public String getIrbProtocolId() {
        return irbProtocolId;
    }

    @Override
    public void setIrbProtocolId(String irbProtocolId) {
        this.irbProtocolId = irbProtocolId;
    }

    @Override
    public String getScheduleGuid() {
        return scheduleGuid;
    }

    @Override
    public void setScheduleGuid(String scheduleGuid) {
        this.scheduleGuid = scheduleGuid;
    }
}
