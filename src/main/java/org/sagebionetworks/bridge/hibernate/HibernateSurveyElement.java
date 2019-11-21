package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

@Entity
@Table(name = "SurveyElements")
public class HibernateSurveyElement implements SurveyElement {
    @Id
    private String guid;
    private String surveyGuid;
    private long createdOn;
    private String identifier;
    private String type;
    private int order;
    
    @Column(columnDefinition = "mediumtext", name = "data", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode data;
    
    @Column(columnDefinition = "mediumtext", name = "beforeRules", nullable = true)
    @Convert(converter = SurveyRuleListConverter.class)
    private List<SurveyRule> beforeRules;
    
    @Column(columnDefinition = "mediumtext", name = "afterRules", nullable = true)
    @Convert(converter = SurveyRuleListConverter.class)
    private List<SurveyRule> afterRules;
    
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
    
    public Long getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(Long createdOn) {
        this.createdOn = createdOn;
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
    
    public List<SurveyRule> getBeforeRules() {
        return (this.beforeRules == null) ? null : ImmutableList.copyOf(this.beforeRules);
    }
    public void setBeforeRules(List<SurveyRule> beforeRules) {
        this.beforeRules = beforeRules;
    }
    
    public List<SurveyRule> getAfterRules() {
        return (this.afterRules == null) ? null : ImmutableList.copyOf(this.afterRules);
    }
    public void setAfterRules(List<SurveyRule> afterRules) {
        this.afterRules = afterRules;
    }
    
    public String getSurveyCompoundKey() {
        return surveyGuid + ":" + Long.toString(createdOn);
    }
    
    public void setSurveyCompoundKey(String surveyCompoundKey) {
        if (surveyCompoundKey.contains(":")) {
            setSurveyKeyComponents(surveyCompoundKey.split(":")[0], Long.valueOf(surveyCompoundKey.split(":")[1]));
        }
    }
    
    public void setSurveyKeyComponents(String surveyGuid, long createdOn) {
        this.surveyGuid = surveyGuid;
        this.createdOn = createdOn;
    }
}
