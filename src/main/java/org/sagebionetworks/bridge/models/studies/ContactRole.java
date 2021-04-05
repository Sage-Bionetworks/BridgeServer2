package org.sagebionetworks.bridge.models.studies;

public enum ContactRole {
    /** 
     * Either the IRB that approved the research, or in secondary jurisdictions,
     * an “assenting” IRB that reviewed the initial approval and accepted it for
     * their own jurisdiction.
     */
    IRB,
    /** 
     * Principal investigator of the study. Conceivably there can be one of 
     * these for every jurisdiction, but this is rare.
     */
    PRINCIPAL_INVESTIGATOR,
    /** 
     * Position could be co-investigator, medical monitor, repository guardian, 
     * etc. Nevertheless, this person is not presented as a point of contact 
     * (use the SUPPORT role for that). 
     */ 
    INVESTIGATOR,
    /** 
     * The institution funding the study and/or providing IRB oversight. Often 
     * but not always the institution the Principal Investigator is affiliated 
     * with. 
     */
    SPONSOR,
    /** 
     * Position could be study coordinator or study contact, or just study-
     * related contact for an institutional contact.
     */
    SUPPORT;
}
