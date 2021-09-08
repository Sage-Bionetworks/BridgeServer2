package org.sagebionetworks.bridge.models.appconfig;

public enum AppConfigEnumId {

    STUDY_DISEASES("bridge:study-diseases"),
    STUDY_DESIGN_TYPES("bridge:study-design-types");
    
    private String appConfigKey;
    
    private AppConfigEnumId(String appConfigKey) {
        this.appConfigKey = appConfigKey;
    }
    
    public String getAppConfigKey() {
        return appConfigKey;
    }
}
