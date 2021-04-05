package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;

@Embeddable
public final class Address {

    private String placeName;
    private String street;
    private String mailRouting;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    public String getPlaceName() {
        return placeName;
    }
    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }
    public String getStreet() {
        return street;
    }
    public void setStreet(String street) {
        this.street = street;
    }
    public String getMailRouting() {
        return mailRouting;
    }
    public void setMailRouting(String mailRouting) {
        this.mailRouting = mailRouting;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }
    public String getPostalCode() {
        return postalCode;
    }
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }    
}
