package org.sagebionetworks.bridge.models.schedules2;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

/**
 * When an originEventId is published for a participant, the study bursts in 
 * a participant’s schedule generate a sequence of calculated/synthetic events
 * in the format: <code>study_burst:[identifier]:[occurrence #]</code>.
 *      
 * When the study burst fires, all synthetic events will be calculated and 
 * added according to the mutablity rules of the study burst itself (so for 
 * example, the events might be overwritten, or they might not be mutable, 
 * depending on the study burst configuration).
 *      
 * When an update is submitted for one of the synthetic events, the update type
 * of the study burst also determines the update behavior.
 * 
 * When a study burst is associated with a schedule’s session, all of the 
 * synthetic events will be included in the Timeline that is generated for a 
 * participant. To the mobile client, these work like any other event streams in 
 * the scheduling system (there are just a lot more of them). 
 */
@Embeddable
public final class StudyBurst {

    private String identifier;
    private String originEventId;
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "intervalPeriod")
    private Period interval;
    private Integer occurrences;
    @Enumerated(EnumType.STRING)
    private ActivityEventUpdateType updateType;
    
    public StudyBurst() {
    }

    public StudyBurst(String identifier, ActivityEventUpdateType updateType) {
        this.identifier = identifier;
        this.updateType = updateType;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public String getOriginEventId() {
        return originEventId;
    }
    public void setOriginEventId(String originEventId) {
        this.originEventId = originEventId;
    }
    public Integer getOccurrences() {
        return occurrences;
    }
    public void setOccurrences(Integer occurrences) {
        this.occurrences = occurrences;
    }
    public Period getInterval() {
        return interval;
    }
    public void setInterval(Period interval) {
        this.interval = interval;
    }    
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    public void setUpdateType(ActivityEventUpdateType updateType) {
        this.updateType = updateType;
    }
    @Override
    public int hashCode() {
        return Objects.hash(identifier, interval, occurrences, originEventId, updateType);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StudyBurst other = (StudyBurst) obj;
        return Objects.equals(identifier, other.identifier) 
                && Objects.equals(interval, other.interval)
                && Objects.equals(occurrences, other.occurrences) 
                && Objects.equals(originEventId, other.originEventId)
                && updateType == other.updateType;
    }
    
}