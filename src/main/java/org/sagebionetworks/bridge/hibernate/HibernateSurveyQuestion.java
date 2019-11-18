package org.sagebionetworks.bridge.hibernate;

import org.sagebionetworks.bridge.dynamodb.JsonNodeMarshaller;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.SurveyElementSQL;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionSQL;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public  class HibernateSurveyQuestion extends HibernateSurveyElement implements SurveyQuestionSQL {
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
    
    private String prompt;
    private String promptDetail;
    private boolean fireEvent;
    private UIHint hint;
    private Constraints constraints;

    public HibernateSurveyQuestion() {
        setType("SurveyQuestion");
    }
    
    public HibernateSurveyQuestion(SurveyElementSQL entry) {
        setType(entry.getType());
        setIdentifier(entry.getIdentifier());
        setGuid(entry.getGuid());
        setData(entry.getData());
        setBeforeRules(entry.getBeforeRules());
        setAfterRules(entry.getAfterRules());
    }
    
    @Override
    @DynamoDBIgnore
    public String getPrompt() {
        return prompt;
    }
    
    @Override
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    @Override
    @DynamoDBIgnore
    public String getPromptDetail() {
        return promptDetail;
    }
    
    @Override
    public void setPromptDetail(String promptDetail) {
        this.promptDetail = promptDetail;
    }

    @Override
    @DynamoDBIgnore
    public boolean getFireEvent() {
        return fireEvent;
    }
    
    @Override
    public void setFireEvent(boolean fireEvent) {
        this.fireEvent = fireEvent;
    }
    
    @Override
    @DynamoDBIgnore
    public UIHint getUiHint() {
        return hint;
    }
    
    @Override
    public void setUiHint(UIHint hint) {
        this.hint = hint;
    }
    
    @Override
    @DynamoDBIgnore
    public Constraints getConstraints() {
        return constraints;
    }
    
    @Override
    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    @Override
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @JsonIgnore
    public JsonNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put(PROMPT_PROPERTY, prompt);
        data.put(PROMPT_DETAIL_PROPERTY, promptDetail);
        data.put(FIRE_EVENT_PROPERTY, Boolean.toString(fireEvent));
        data.put(UI_HINTS_PROPERTY, hint.name().toLowerCase());    
        data.set(CONSTRAINTS_PROPERTY, BridgeObjectMapper.get().valueToTree(constraints));
        return data;
    }

    @Override
    public void setData(JsonNode data) {
        this.prompt = JsonUtils.asText(data, PROMPT_PROPERTY);
        this.promptDetail = JsonUtils.asText(data, PROMPT_DETAIL_PROPERTY);
        this.fireEvent = JsonUtils.asBoolean(data, FIRE_EVENT_PROPERTY);
        this.hint = JsonUtils.asEntity(data, UI_HINTS_PROPERTY, UIHint.class);
        this.constraints = JsonUtils.asConstraints(data, CONSTRAINTS_PROPERTY);
    }

    @Override
    public String toString() {
        return String.format("DynamoSurveyQuestion [guid=%s, identifier=%s, type=%s, order=%s, beforeRules=%s, afterRules=%s, hint=%s, prompt=%s, promptDetail=%s, fireEvent=%s, constraints=%s]", 
            getGuid(), getIdentifier(), getType(), getOrder(), getBeforeRules(), getAfterRules(), hint, prompt, promptDetail, fireEvent, constraints);
    }
}