package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementSQL;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.fasterxml.jackson.databind.JsonNode;

//@Embeddable
@Entity
@Table(name = "SurveyElements")
public class HibernateSurveyElement implements SurveyElementSQL {
    String CONSTRAINTS_PROPERTY = "constraints";
    String FIRE_EVENT_PROPERTY = "fireEvent";
    String GUID_PROPERTY = "guid";
    String IDENTIFIER_PROPERTY = "identifier";
    String IMAGE_PROPERTY = "image";
    String PROMPT_DETAIL_PROPERTY = "promptDetail";
    String PROMPT_PROPERTY = "prompt";
    String BEFORE_RULES_PROPERTY = "beforeRules";
    String AFTER_RULES_PROPERTY = "afterRules";
    String TITLE_PROPERTY = "title";
    String TYPE_PROPERTY = "type";
    String UI_HINTS_PROPERTY = "uiHint";
    
    @Id
    private String guid;    
    private String surveyGuid;
    private String identifier;
    private String type;
    private int order;
    
    @Column(columnDefinition = "mediumtext", name = "data", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode data;
    
    @Column(columnDefinition = "mediumtext", name = "beforeRules", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode beforeRules;
    
    @Column(columnDefinition = "mediumtext", name = "afterRules", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode afterRules;
    
    /**
     * No args constructor, required and used by Hibernate for full object initialization.
     */
    public HibernateSurveyElement() {}
    
    public String getSurveyGuid() {
        return surveyGuid;
    }
    public void setSurveyGuid(String surveyGuid) {
        this.surveyGuid = surveyGuid;
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
    
    public int getOrder() {
        return order;
    }
    public void setOrder(int order) {
        this.order = order;
    }
    
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    
    public JsonNode getData() {
        return data;
    }
    public void setData(JsonNode data) {
        this.data = data;
    }
    
    public JsonNode getBeforeRules() {
        return beforeRules;
    }
    public void setBeforeRules(JsonNode beforeRules) {
        this.beforeRules = beforeRules;
    }
    
    public JsonNode getAfterRules() {
        return afterRules;
    }
    public void setAfterRules(JsonNode afterRules) {
        this.afterRules = afterRules;
    }
}
