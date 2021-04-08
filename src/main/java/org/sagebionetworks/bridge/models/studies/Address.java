package org.sagebionetworks.bridge.models.studies;

import javax.persistence.Embeddable;

/**
 * This is suitable for displaying many addresses to end users (even something 
 * unusual like “50 miles (80 km) West of Socorro, New Mexico, USA”), but that’s 
 * it (and that’s our use case).
 * 
 * Here is an example of a more difficult address and how it might be stored 
 * (from the Phillipinnes). Note that you need to know what you’re displaying
 * to put these fields in the right order:
 * 
 * 647 National Road (placeName)<br>
 * 16 Sunlight Building (street)<br>
 * Barangay Muzon, Taytay, Rizal (division, city, and state)<br>
 * Taytay CPO-PO Box# 1920 + Rizal (mailRouting)<br>
 * Philippines (country)
 */
@Embeddable
public class Address {
    
    private String placeName;
    private String street;
    private String mailRouting;
    private String city;
    private String division;
    private String postalCode;
    private String country;
    
    /** The name of a building, or sometimes the name of the organization at the 
     * address. */
    public String getPlaceName() {
        return placeName;
    }
    public void setPlaceName(String placeName) {
        this.placeName = placeName; 
    }
    /** A street (usually a number and a name, but anything that defines the 
     * locality). */
    public String getStreet() {
        return street;
    }
    public void setStreet(String street) {
        this.street = street;
    }
    /** Mail routing information such as a unit, P.O. box, mail stop, or 
     * other directions on where to deliver mail at the locality. */
    public String getMailRouting() {
        return mailRouting;
    }
    public void setMailRouting(String mailRouting) {
        this.mailRouting = mailRouting;
    }
    /** City. */
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    /** State, province, prefecture, etc. */
    public String getDivision() {
        return division;
    }
    public void setDivision(String division) {
        this.division = division;
    }
    /** Zip code or other postal code. */
    public String getPostalCode() {
        return postalCode;
    }
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    /** Country. */
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
}
