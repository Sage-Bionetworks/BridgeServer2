package org.sagebionetworks.bridge.models.surveys;

public abstract class TimeBasedConstraints extends Constraints {
    
    protected boolean allowFuture;
    // This has to be true by default in order for past surveys to continue to be valid and work
    protected boolean allowPast = true;
    
    public boolean getAllowFuture() {
        return allowFuture;
    }
    public void setAllowFuture(boolean allowFuture) {
        this.allowFuture = allowFuture;
    }
    public boolean getAllowPast() {
        return allowPast;
    }
    public void setAllowPast(boolean allowPast) {
        this.allowPast = allowPast;
    }
}
