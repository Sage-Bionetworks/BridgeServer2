package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public class AssessmentInfo {

    private String guid;
    private String appId;
    private String identifier;
    private String title;
    private String label;
    private Integer minutesToComplete;
    private ColorScheme colorScheme;

    public static AssessmentInfo create(AssessmentReference ref) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = BridgeUtils.selectByLang(ref.getLabels(), 
                languages, new Label("", ref.getTitle()));
        
        AssessmentInfo info = new AssessmentInfo();
        info.guid = ref.getGuid();
        info.appId = ref.getAppId();
        info.identifier = ref.getIdentifier();
        info.title = ref.getTitle();
        info.label = label.getValue();
        return info;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(appId, colorScheme, guid, identifier, label, minutesToComplete, title);
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
                Objects.equals(label, other.label) &&
                Objects.equals(minutesToComplete, other.minutesToComplete) &&
                Objects.equals(title, other.title);
    }
}
