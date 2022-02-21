package org.sagebionetworks.bridge.models.schedules2.timelines;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

@JsonDeserialize(builder = ScheduledAssessment.Builder.class)
public class ScheduledAssessment {

    private final String refKey;
    private final String instanceGuid;
    // This is carried over in order to make it faster and easer to construct
    // the TimelineMetadata object during construction of the Timeline. It is
    // not part of the JSON serialization of the ScheduledAssessment.
    private final AssessmentReference reference;
    private final SessionCompletionState state;
    private final DateTime finishedOn;
    private final String clientTimeZone;

    private ScheduledAssessment(ScheduledAssessment.Builder builder) {
        this.refKey = builder.refKey;
        this.instanceGuid = builder.instanceGuid;
        this.reference = builder.reference;
        this.state = builder.state;
        this.finishedOn = builder.finishedOn;
        this.clientTimeZone = builder.clientTimeZone;
    }

    public String getRefKey() {
        return refKey;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    @JsonIgnore
    public AssessmentReference getReference() {
        return reference;
    }
    public SessionCompletionState getState() {
        return state;
    }
    @JsonSerialize(using = DateTimeSerializer.class) // preserve time zone offset
    public DateTime getFinishedOn() {
        return finishedOn;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }

    public static class Builder {
        private String refKey;
        private String instanceGuid;
        private AssessmentReference reference;
        private SessionCompletionState state;
        private DateTime finishedOn;
        private String clientTimeZone;
        
        public Builder withRefKey(String refKey) {
            this.refKey = refKey;
            return this;
        }
        public Builder withInstanceGuid(String instanceGuid) {
            this.instanceGuid = instanceGuid;
            return this;
        }
        public Builder withReference(AssessmentReference reference) {
            this.reference = reference;
            return this;
        }
        public Builder withState(SessionCompletionState state) {
            this.state = state;
            return this;
        }
        public Builder withFinishedOn(DateTime finishedOn) {
            this.finishedOn = finishedOn;
            return this;
        }
        public Builder withClientTimeZone(String clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }
        public ScheduledAssessment build() {
            return new ScheduledAssessment(this);
        }
    }
}
