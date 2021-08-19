package org.sagebionetworks.bridge.models.worker;

/** Wrapper for all worker requests. */
public class WorkerRequest {
    private String service;
    private Object body;

    /**
     * Service ID of the specific worker we're requesting. Worker service IDs are documented at
     * https://github.com/Sage-Bionetworks/BridgeWorkerPlatform/wiki#workers
     */
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    /** Request body. This depends on the specific worker being requested. See for example {@link Exporter3Request}. */
    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
