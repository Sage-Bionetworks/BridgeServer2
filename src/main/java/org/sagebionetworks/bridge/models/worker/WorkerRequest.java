package org.sagebionetworks.bridge.models.worker;

// todo doc
public class WorkerRequest {
    private String service;
    private Object body;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
