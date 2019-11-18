package org.sagebionetworks.bridge.models.surveys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

import org.sagebionetworks.bridge.hibernate.HibernateSurvey;
import org.sagebionetworks.bridge.hibernate.HibernateSurveyElement;
import org.sagebionetworks.bridge.json.JsonNodeToSurveyElementConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

@JsonDeserialize(as=HibernateSurveyElement.class)
public interface SurveyElementSQL extends BridgeEntity {

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
    
    String getGuid();
    void setGuid(String guid);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    int getOrder();
    void setOrder(int order);
    
    String getType();
    void setType(String type);
    
    JsonNode getData();
    void setData(JsonNode data);
    
    JsonNode getBeforeRules();
    void setBeforeRules(JsonNode beforeRules);
    
    JsonNode getAfterRules();
    void setAfterRules(JsonNode afterRules);
}

