package org.sagebionetworks.bridge.models.schedules2.adherence;

public enum AdherenceRecordType {
    /** Return assessments, either selected assessment records or the 
     * assessments in selected session instances.
     */
    ASSESSMENT,
    /**
     * Return sessions, either selected session instance records or the 
     * sessions of selected assessment instances.
     */
    SESSION,
    /**
     * Return sessions and assessments of the selected instance records (all 
     * instance sessions and instance sessions for selected assessment instances, 
     * and all instance assessments and assessment instances of selected session
     * instances.
     */
    BOTH;
}
