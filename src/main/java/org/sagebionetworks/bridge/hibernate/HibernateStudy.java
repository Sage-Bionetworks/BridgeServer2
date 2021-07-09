package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.IrbDecisionType;
import org.sagebionetworks.bridge.models.studies.SignInType;
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
    private StudyPhase phase;
    private String details;
    private String irbName;
    @Convert(converter = LocalDateToStringConverter.class)
    private LocalDate irbDecisionOn;
    @Convert(converter = LocalDateToStringConverter.class)
    private LocalDate irbExpiresOn;
    @Enumerated(EnumType.STRING)
    private IrbDecisionType irbDecisionType;
    private String irbProtocolId;
    private String irbProtocolName;
    private String studyLogoUrl;
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;
    private String institutionId;
    private String scheduleGuid;
    @JsonIgnore
    private String logoGuid;
    private String keywords;
    @Version
    private Long version;
    
    // the subselect annotations below reduce the number of SQL queries that
    // hibernate makes to retrieve the full study object. Tables are being used
    // for collections that we might use in future queries; JSON for collections
    // that we'll never reference apart from the study object.
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    @OrderColumn(name="pos") // can’t use 'position' in this case
    @CollectionTable(name="StudyContacts", joinColumns= {
            @JoinColumn(name="appId"), @JoinColumn(name="studyId")
    })
    private List<Contact> contacts;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    @CollectionTable(name="StudyDiseases", 
        joinColumns = {@JoinColumn(name="appId"), @JoinColumn(name="studyId")})
    @Column(name="disease")
    private Set<String> diseases;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    @CollectionTable(name="StudyDesignTypes", 
        joinColumns = {@JoinColumn(name="appId"), @JoinColumn(name="studyId")})
    @Column(name = "designType")
    private Set<String> studyDesignTypes;

    @Convert(converter = SignInTypeListConverter.class)
    private List<SignInType> signInTypes;
    
    /**
     * For full construction of object by Hibernate.
     */
    public HibernateStudy() {}
    
    /**
     * For partial construction of object by Hibernate, excluding expensive fields like clientData.
     */
    public HibernateStudy(String name, String identifier, String details, StudyPhase phase,
            boolean deleted, DateTime createdOn, DateTime modifiedOn, String studyLogoUrl,
            ColorScheme colorScheme, List<SignInType> signInTypes) {
        this.name = name;
        this.identifier = identifier;
        this.details = details;
        this.phase = phase;
        this.deleted = deleted;
        this.createdOn = createdOn;
        this.modifiedOn = modifiedOn;
        this.studyLogoUrl = studyLogoUrl;
        this.colorScheme = colorScheme;
        this.signInTypes = signInTypes;
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
    public String getIrbName() {
        return irbName;
    }
    
    @Override
    public void setIrbName(String irbName) {
        this.irbName = irbName;
    }
    
    @Override
    public LocalDate getIrbDecisionOn() {
        return irbDecisionOn;
    }
    
    @Override
    public void setIrbDecisionOn(LocalDate irbDecisionOn) {
        this.irbDecisionOn = irbDecisionOn;
    }
    
    @Override
    public LocalDate getIrbExpiresOn() {
        return irbExpiresOn;
    }
    
    @Override
    public void setIrbExpiresOn(LocalDate irbExpiresOn) {
        this.irbExpiresOn = irbExpiresOn;
    }
    
    @Override
    public IrbDecisionType getIrbDecisionType() {
        return irbDecisionType;
    }
    
    @Override
    public void setIrbDecisionType(IrbDecisionType irbDecisionType) {
        this.irbDecisionType = irbDecisionType;
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
    public String getIrbProtocolName() {
        return irbProtocolName;
    }
    
    @Override
    public void setIrbProtocolName(String irbProtocolName) { 
        this.irbProtocolName = irbProtocolName;
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
    public String getKeywords() { 
        return keywords;
    }
    
    @Override
    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }
    
    @Override
    public Set<String> getDiseases() {
        if (diseases == null) {
            diseases = new HashSet<>();
        }
        return diseases;
    }

    @Override
    public void setDiseases(Set<String> diseases) {
        this.diseases = diseases;
    }

    @Override
    public Set<String> getStudyDesignTypes() {
        if (studyDesignTypes == null) {
            studyDesignTypes = new HashSet<>();
        }
        return studyDesignTypes;
    }

    @Override
    public void setStudyDesignTypes(Set<String> studyDesignTypes) {
        this.studyDesignTypes = studyDesignTypes;
    }
    
    @Override
    public List<SignInType> getSignInTypes() {
        if (signInTypes == null) {
            signInTypes = new ArrayList<>();
        }
        return signInTypes;
    }
    
    @Override
    public void setSignInTypes(List<SignInType> signInTypes) {
        this.signInTypes = signInTypes;
    }

    @Override
    public String getLogoGuid() {
        return logoGuid;
    }

    @Override
    public void setLogoGuid(String logoGuid) {
        this.logoGuid = logoGuid;
    }
}
