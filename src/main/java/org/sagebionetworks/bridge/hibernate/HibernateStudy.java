package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.models.studies.StudyPhase.LEGACY;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Version;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyId;
import org.sagebionetworks.bridge.models.studies.StudyPhase;

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
    @Enumerated(EnumType.STRING)
    private StudyPhase phase = LEGACY;
    private String details;
    @Convert(converter = LocalDateToStringConverter.class)
    private LocalDate irbApprovedOn;
    @Convert(converter = LocalDateToStringConverter.class)
    private LocalDate irbApprovedUntil;
    private String studyLogoUrl;
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;
    private String institutionId;
    private String irbProtocolId;
    private String scheduleGuid;
    private String disease;
    private String studyDesignType;
    @Version
    private Long version;
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn(name="pos") // can’t use 'position' in this case
    @CollectionTable(name="StudyContacts", joinColumns= {
            @JoinColumn(name="appId"), @JoinColumn(name="studyId")
    })
    private List<Contact> contacts;
    
    /**
     * For full construction of object by Hibernate.
     */
    public HibernateStudy() {}
    
    /**
     * For partial construction of object by Hibernate, excluding expensive fields like clientData.
     */
    public HibernateStudy(String name, String identifier, String appId, 
            DateTime createdOn, DateTime modifiedOn, boolean deleted, Long version) {
        this.name = name;
        this.identifier = identifier;
        this.appId = appId;
        this.createdOn = createdOn;
        this.modifiedOn = modifiedOn;
        this.deleted = deleted;
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
    public List<Contact> getContacts() {
        if (contacts == null) {
            contacts = new ArrayList<>();
        }
        return contacts;
    }

    @Override
    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
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
    public StudyPhase getPhase() {
        return phase;
    }

    @Override
    public void setPhase(StudyPhase phase) {
        this.phase = phase;
    }
    
    @Override
    public LocalDate getIrbApprovedOn() {
        return irbApprovedOn;
    }

    @Override
    public void setIrbApprovedOn(LocalDate irbApprovedOn) {
        this.irbApprovedOn = irbApprovedOn;
    }

    @Override
    public LocalDate getIrbApprovedUntil() {
        return irbApprovedUntil;
    }

    @Override
    public void setIrbApprovedUntil(LocalDate irbApprovedUntil) {
        this.irbApprovedUntil = irbApprovedUntil;
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
    public String getInstitutionId() {
        return institutionId;
    }

    @Override
    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
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

    @Override
    public String getDisease() {
        return disease;
    }

    @Override
    public void setDisease(String disease) {
        this.disease = disease;
    }

    @Override
    public String getStudyDesignType() {
        return studyDesignType;
    }

    @Override
    public void setStudyDesignType(String studyDesignType) {
        this.studyDesignType = studyDesignType;
    }
}
