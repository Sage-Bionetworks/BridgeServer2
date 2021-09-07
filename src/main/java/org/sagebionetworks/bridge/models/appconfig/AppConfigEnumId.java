package org.sagebionetworks.bridge.models.appconfig;

public enum AppConfigEnumId {
    DISEASES("bridge:diseases"),
    DESIGN_TYPES("bridge:study-design-types");
    
    private String appConfigKey;
    
    private AppConfigEnumId(String appConfigKey) {
        this.appConfigKey = appConfigKey;
    }
    
    public String getAppConfigKey() {
        return appConfigKey;
    }
}
