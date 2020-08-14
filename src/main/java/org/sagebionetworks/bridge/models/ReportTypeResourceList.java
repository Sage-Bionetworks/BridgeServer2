package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportTypeResourceList<T> extends ResourceList<T> {
    
    public ReportTypeResourceList(List<T> items, boolean suppressDeprecated) {
        super(items, suppressDeprecated);
    }
    @JsonCreator
    public ReportTypeResourceList(@JsonProperty(ITEMS) List<T> items) {
        super(items, false);
    }
    @Deprecated
    public ReportType getReportType() {
        return (suppressDeprecated) ? null : (ReportType)getRequestParams().get(REPORT_TYPE);
    }
    public ReportTypeResourceList<T> withRequestParam(String key, Object value) {
        super.withRequestParam(key, value);
        return this;
    }
    
}
