package org.sagebionetworks.bridge.models.schedules2.adherence;

public enum AdherenceRecordType {
    /** 
     * Filter search results to return only assessment records. In addition to
     * limiting results for date-based searches, if you search by session GUIDs, 
     * you can ask to return the assessments for those sessions. 
     */
    ASSESSMENT,
    /**
     * Filter search results to only return session records. In addition to
     * limiting results for date-based searches, if you search by 
     * session GUIDs and do not wish to retrieve the accompanying assessment 
     * records, you can do so by limiting the returned records to session 
     * records.
     */
    SESSION;
}
