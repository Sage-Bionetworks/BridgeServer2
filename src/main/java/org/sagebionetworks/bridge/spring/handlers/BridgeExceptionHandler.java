package org.sagebionetworks.bridge.spring.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.sagebionetworks.bridge.spring.util.HttpUtil;

/** Exception handler to convert exceptions into JSON instead of a generic HTML error page. */
@ControllerAdvice
public class BridgeExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) throws JsonProcessingException {
        // Spring by default doesn't log the exception if it's caught by a handler, so we need to log it ourselves.
        LOG.error(ex.getMessage(), ex);

        return HttpUtil.convertErrorToJsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getClass().getSimpleName(),
                ex.getMessage());
    }
}
