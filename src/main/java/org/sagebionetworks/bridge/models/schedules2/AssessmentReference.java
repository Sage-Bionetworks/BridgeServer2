package org.sagebionetworks.bridge.models.schedules2;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Table;

import org.sagebionetworks.bridge.hibernate.ColorSchemeConverter;
import org.sagebionetworks.bridge.hibernate.LabelListConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;

/**
 * When a session is created it can include display metadata about the assessments, and this
 * information will be carried over to the Timeline. If it's not included, it won't be
 * in the timeline, so this is a responsibility of the schedule design API consumers. 
 * The only information that is required is the appId and GUID of the assessment (because
 * schedules can link to shared assessments as well as local assessments, the appId is
 * required and not implied).
 */
@Embeddable
@Table(name = "SessionAssessments")
@BridgeTypeName("AssessmentReference")
public class AssessmentReference {
    
    private String guid;
    private String appId;
    private String identifier;
    private String title;
    @Convert(converter = LabelListConverter.class)
    private List<Label> labels;
    private Integer minutesToComplete;
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;

    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public List<Label> getLabels() {
        if (labels == null) {
            labels = new ArrayList<>();
        }
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
    public Integer getMinutesToComplete() {
        return minutesToComplete;
    }
    public void setMinutesToComplete(Integer minutesToComplete) {
        this.minutesToComplete = minutesToComplete;
    }
    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }
}
