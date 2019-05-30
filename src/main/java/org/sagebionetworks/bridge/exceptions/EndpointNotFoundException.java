package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

/** 404 exception thrown in response to an unrecognized endpoint. */
@NoStackTraceException
@SuppressWarnings("serial")
public class EndpointNotFoundException extends BridgeServiceException {
    public EndpointNotFoundException() {
        super("Endpoint not found.", HttpStatus.SC_NOT_FOUND);
    }
}
