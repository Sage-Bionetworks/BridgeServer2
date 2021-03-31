package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public final class AssessmentInfo {

    private final String guid;
    private final String appId;
    private final String identifier;
    private final Integer revision;
    private final String label;
    private final Integer minutesToComplete;
    private final ColorScheme colorScheme;

    public static AssessmentInfo create(AssessmentReference ref) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = BridgeUtils.selectByLang(ref.getLabels(), 
                languages, new Label("", ref.getTitle()));
        
        return new AssessmentInfo(ref.getGuid(), ref.getAppId(), ref.getIdentifier(), ref.getRevision(),
                label.getValue(), ref.getMinutesToComplete(), ref.getColorScheme());
    }
    
    private AssessmentInfo(String guid, String appId, String identifier, Integer revision, String label,
            Integer minutesToComplete, ColorScheme colorScheme) {
        this.guid = guid;
        this.appId = appId;
        this.identifier = identifier;
        this.revision = revision;
        this.label = label;
        this.colorScheme = colorScheme;
        this.minutesToComplete = minutesToComplete;
    }
    
    public String getKey() {
        return String.valueOf(this.hashCode());
    }
    
    public String getGuid() {
        return guid;
    }

    public String getAppId() {
        return appId;
    }

    public String getIdentifier() {
        return identifier;
    }
    
    public Integer getRevision() {
        return revision;
    }

    public String getLabel() {
        return label;
    }

    public Integer getMinutesToComplete() {
        return minutesToComplete;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(appId, colorScheme, guid, identifier, revision, label, minutesToComplete);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AssessmentInfo other = (AssessmentInfo) obj;
        return Objects.equals(appId, other.appId) &&
                Objects.equals(colorScheme, other.colorScheme) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(identifier, other.identifier) &&
                Objects.equals(revision, other.revision) &&
                Objects.equals(label, other.label) &&
                Objects.equals(minutesToComplete, other.minutesToComplete);
    }
}
