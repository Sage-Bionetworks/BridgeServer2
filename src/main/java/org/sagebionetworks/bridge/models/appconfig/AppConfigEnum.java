package org.sagebionetworks.bridge.models.appconfig;

import static java.util.stream.Collectors.toList;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class AppConfigEnum {

    private boolean validate;
    private List<AppConfigEnumEntry> entries;
    
    public boolean shouldValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    /**
     * The server only cares about the canonical value that can be submitted, regardless 
     * of the labels for that value (which are provided for UIs retrieving and displaying 
     * the enumerations), so only the value is found and returned. 
     */
    public List<String> getEntryValues() {
        if (entries != null && !entries.isEmpty()) {
            List<String> canonicalValues = entries.stream()
                    .map(AppConfigEnumEntry::getValue)
                    .filter(el -> el != null)
                    .collect(toList());
            return canonicalValues;
        }
        return ImmutableList.of();
    }

    public void setEntries(List<AppConfigEnumEntry> entries) {
        this.entries = entries;
    }
}
