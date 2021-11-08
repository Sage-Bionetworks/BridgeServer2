package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;

import java.math.BigInteger;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * When events are submitted through the API, they are submitted partly as a compound string along
 * with some separate JSON fields. The compound string may or may not be valid so it it parsed 
 * and all the data is returned as a Builder for a study activity event. In our system code, these 
 * values can be supplied directly through the builder.
 */
@Entity
@Table(name = "StudyActivityEvents")
@IdClass(StudyActivityEventId.class)
@BridgeTypeName("ActivityEvent")
public class StudyActivityEvent implements HasTimestamp, BridgeEntity {
    
    /**
     * This mirrors the create method below, so it is easier to keep the two synchronized.
     * This method is only used to test the results of a SQL query. 
     */
    public static Object[] recordify(StudyActivityEvent event) {
        Object[] array = new Object[12];
        array[0] = event.getAppId();
        array[1] = event.getUserId();
        array[2] = event.getStudyId();
        array[3] = event.getEventId();
        if (event.getTimestamp() != null) {
            array[4] = BigInteger.valueOf(event.getTimestamp().getMillis());    
        }
        array[5] = event.getAnswerValue(); 
        array[6] = event.getClientTimeZone();
        if (event.getCreatedOn() != null) {
            array[7] = BigInteger.valueOf(event.getCreatedOn().getMillis());    
        }
        array[8] = event.getStudyBurstId();
        array[9] = event.getOriginEventId();
        if (event.getPeriodFromOrigin() != null) {
            array[10] = event.getPeriodFromOrigin().toString();    
        }
        // Not in table, this is retrieved from the query itself
        array[11] = BigInteger.valueOf(event.getRecordCount());
        return array;
    }
    
    /**
     * The field requiring this unusual constructions is the subselect of total records
     * for a given eventID..this is no harder than making a @ResultSetMapping to get 
     * the total subselect, so I went this route.
     */
    public static StudyActivityEvent create(Object[] record) {
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder();
        builder.withAppId(toString(record[0]));
        builder.withUserId(toString(record[1]));
        builder.withStudyId(toString(record[2]));
        builder.withEventId(toString(record[3]));
        builder.withTimestamp(toDateTime(record[4]));
        builder.withAnswerValue(toString(record[5]));
        builder.withClientTimeZone(toString(record[6]));
        builder.withCreatedOn(toDateTime(record[7]));
        builder.withStudyBurstId(toString(record[8]));
        builder.withOriginEventId(toString(record[9]));
        builder.withPeriodFromOrigin(toPeriod(record[10]));
        if (record.length > 11) {
            builder.withRecordCount(toInt(record[11]));    
        }
        return builder.build();
    }
    private static int toInt(Object obj) {
        return (obj == null) ? -1 : ((BigInteger)obj).intValue();
    }
    private static String toString(Object obj) {
        return (obj == null) ? null : (String)obj;
    }
    private static DateTime toDateTime(Object obj) {
        return (obj == null) ? null : new DateTime( ((BigInteger)obj).longValue() );
    }
    private static Period toPeriod(Object obj) {
        return (obj == null) ? null : Period.parse((String)obj);
    }

    @JsonIgnore
    private String appId; 
    @JsonIgnore
    @Id
    private String userId;
    @JsonIgnore
    @Id
    private String studyId;
    @Id
    private String eventId;
    @Id
    @Column(name = "eventTimestamp")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime timestamp;
    private String answerValue;
    private String clientTimeZone;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    /** If this is a study burst event, the origin event that triggered it. */
    private String originEventId;
    /** If this is a study burst event, the studyBurstId separate from the eventId. */
    private String studyBurstId;
    /** The period from the origin timestamp when this event was scheduled to occur, at the 
     * time the study burst fired (note that the burst may be updated after the setting of
     * this event; in this case, updates will adjust the periodFromOrigin as well). 
     */
    @Convert(converter = PeriodToStringConverter.class)
    private Period periodFromOrigin;
    @Transient
    private int recordCount;
    @Transient
    private ActivityEventUpdateType updateType;
    
    public StudyActivityEvent() {} // for hibernate
    
    public StudyActivityEvent(StudyActivityEvent.Builder builder) {
        this.appId = builder.appId;
        this.userId = builder.userId;
        this.studyId = builder.studyId;
        this.clientTimeZone = builder.clientTimeZone;
        this.timestamp = builder.timestamp;
        this.answerValue = builder.answerValue;
        this.createdOn = builder.createdOn;
        this.updateType = builder.updateType;
        this.originEventId = builder.originEventId; 
        this.studyBurstId = builder.studyBurstId;
        this.periodFromOrigin = builder.periodFromOrigin;
        this.recordCount = builder.recordCount;
        this.eventId = builder.eventId;
    }
    
    public String getAppId() {
        return appId;
    }
    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getEventId() {
        return eventId;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }
    public String getAnswerValue() {
        return answerValue;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    // the service needs to set this, not the builder 
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public String getOriginEventId() { 
        return originEventId;
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public Period getPeriodFromOrigin() {
        return periodFromOrigin;
    }
    public int getRecordCount() {
        return recordCount;
    }
    public ActivityEventUpdateType getUpdateType() {
        return updateType;
    }
    
    public static final class Builder {
        private String appId;
        private String userId;
        private String studyId;
        private String clientTimeZone;
        private DateTime createdOn;
        private ActivityEventObjectType objectType;
        private String objectId;
        private ActivityEventType eventType;
        private ActivityEventUpdateType updateType;
        private String answerValue;
        private DateTime timestamp;
        private String originEventId;
        private String studyBurstId;
        private Period periodFromOrigin;
        private int recordCount;
        private String eventId;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withClientTimeZone(String clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }
        public Builder withObjectType(ActivityEventObjectType objectType) {
            this.objectType = objectType;
            return this;
        }
        public Builder withObjectId(String objectId) {
            this.objectId = objectId;
            return this;
        }
        public Builder withAnswerValue(String answerValue) {
            this.answerValue = answerValue;
            return this;
        }
        public Builder withTimestamp(DateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        public Builder withOriginEventId(String originEventId) { 
            this.originEventId = originEventId;
            return this;
        }
        public Builder withStudyBurstId(String studyBurstId) {
            this.studyBurstId = studyBurstId;
            return this;
        }
        public Builder withPeriodFromOrigin(Period periodFromOrigin) {
            this.periodFromOrigin = periodFromOrigin;
            return this;
        }
        public Builder withUpdateType(ActivityEventUpdateType updateType) {
            this.updateType = updateType;
            return this;
        }
        public Builder withEventType(ActivityEventType eventType) {
            this.eventType = eventType;
            return this;
        }
        public Builder withRecordCount(int recordCount) {
            this.recordCount = recordCount;
            return this;
        }
        /**
         * Events are stored with the compound event ID string. However, we do not want to construct
         * instances with this string alone, since it will not be validated. Nor can we hide this 
         * because our code is not organized in packages correctly. This method should be called by 
         * code that is constructing events from the database, or from a couple of exceptional 
         * places in our code where we are “faking” the existence of database events. Normal events 
         * should be parsed for correctness or constructed with object type, event type, and object ID.
         */
        public Builder withEventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public StudyActivityEvent build() {
            // We’re constructing the event with a known (already validated) event ID
            if (eventId != null) {
                if (updateType == null) {
                    updateType = IMMUTABLE;
                }
            } 
            // We’re constructing the event ID from its constituent parts
            else if (objectType != null) {
                if (updateType == null) {
                    updateType = objectType.getUpdateType();
                }
                eventId = objectType.getEventId(objectId, eventType, answerValue);
            }
            return new StudyActivityEvent(this);
        }        
    }
}