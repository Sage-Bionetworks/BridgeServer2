package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.Phone;

/**
 * All the values in this object should be considered display values for display
 * to an end user. A contact does not have to have an Account in the Bridge 
 * system.
 */
@Embeddable
@BridgeTypeName("Contact")
public class StudyContact {
    
    /**
     * The name of the person or organization, e.g. “Dr. Tim Powers, Ph.D.” or 
     * “Sage Bionetworks”.
     */
    private String name;
    /**
     * The purpose of the contact, for example the sponsoring institution, the 
     * principal investigator, or a study support contact. (There are specific
     * contacts every study must present to participants.)
     */
    @Enumerated(EnumType.STRING)
    private ContactRole role;
    /**
     * The position of an individual relative to their institutional affiliation
     * (not their role in the study, please use the role attribute for this).
     * For example, “Associate Professor of Psychiatry and Bioengineering”
     */
    private String position;
    /**
     * The organization an individual is affiliated with, e.g. "UC San 
     * Francisco”. This may or may not be the same as the institution sponsoring
     * the study. 
     */
    private String affiliation;
    /**
     * The full mailing address, if required
     */
    @Embedded
    private Address address;
    /**
     * Email address of an individual or organization.
     */
    private String email;
    /**
     * Phone number of an individual or organization. 
     */
    @Embedded
    private Phone phone;
    /**
     * The regulatory jurisdiction of this entry. When multiple jurisdictions
     * are involved in a study, there may be multiple lead scientists, IRBs, 
     * and study coordinators involved from each jurisdiction. It may be useful 
     * to show a participant the people and organizations involved in their 
     * specific jurisdiction, or the primary jurisdiction and their specific 
     * jurisdiction. If appropriate, records for a specific jurisdiction might
     * therefore be localized.
     */
    private String jurisdiction;

    public ContactRole getRole() {
        return role;
    }
    public void setRole(ContactRole role) {
        this.role = role;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPosition() {
        return position;
    }
    public void setPosition(String position) {
        this.position = position;
    }
    public String getAffiliation() {
        return affiliation;
    }
    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }
    public Address getAddress() {
        return address;
    }
    public void setAddress(Address address) {
        this.address = address;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public Phone getPhone() {
        return phone;
    }
    public void setPhone(Phone phone) {
        this.phone = phone;
    }
    public String getJurisdiction() {
        return jurisdiction;
    }
    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }
}
