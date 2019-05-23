package org.sagebionetworks.bridge.spring.handlers;

import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputExceededException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NoStackTraceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.spring.util.HttpUtil;

/** Exception handler to convert exceptions into JSON instead of a generic HTML error page. */
@ControllerAdvice
public class BridgeExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeExceptionHandler.class);
    
    // We serialize exceptions to JSON, but do not want any of the root properties of Throwable 
    // to be exposed, so these are removed;
    public static final Set<String> UNEXPOSED_FIELD_NAMES = ImmutableSet.of("stackTrace", "localizedMessage",
            "suppressed", "cause", "errorType", "errorMessage", "retryable", "requestId", "serviceName", "httpHeaders",
            "errorCode", "rawResponse", "rawResponseContent");

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(HttpServletRequest request, Exception ex) throws JsonProcessingException {
        logException(request, ex);
        return getResult(ex);
    }

    private void logException(HttpServletRequest request, Throwable throwable) {
        final String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        final String msg = "request: " + requestId + " " + throwable.getMessage();
        if (throwable.getClass().isAnnotationPresent(NoStackTraceException.class)) {
            LOG.info(msg);
            return;
        }
        LOG.error(msg, throwable);
    }

    private ResponseEntity<String> getResult(Throwable throwable) throws JsonProcessingException {
        // Consent exceptions return a session payload (you are signed in),
        // but a 412 error status code.
        if (throwable instanceof ConsentRequiredException) {
            ConsentRequiredException cre = (ConsentRequiredException)throwable;
            
            JsonNode info = UserSessionInfo.toJSON(cre.getUserSession());
            return ResponseEntity.status(cre.getStatusCode())
                    .header(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.CONTENT_TYPE_JSON)
                    .body(info.toString());
        }
        ObjectNode node = BridgeObjectMapper.get().valueToTree(throwable);
        final int status = getStatusCode(throwable);
        final String message = getMessage(throwable, status);
        final String type = getType(throwable, node);
        
        node.put("message", message);
        node.put("statusCode", status);
        node.put("type", type);
        node.remove(UNEXPOSED_FIELD_NAMES);
        
        return ResponseEntity.status(status)
                .header(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.CONTENT_TYPE_JSON)
                .body(node.toString());
    }
    
    private String getType(final Throwable throwable, final ObjectNode node) {
        String type = node.get("type").asText();
        if (throwable instanceof ProvisionedThroughputExceededException) {
            // DDB Throughput exception is a 400 from Amazon's side. But from our side, we should report this as a 500
            // for monitoring purposes and to inform our callers properly.
            type = "BridgeServiceException";
        } else if (throwable instanceof AmazonServiceException) {
            AmazonServiceException ase = (AmazonServiceException)throwable;
            if (ase.getStatusCode() >= 400 && ase.getStatusCode() < 500) {
                type = "BadRequestException";
            }
        }
        return type;
    }

    private int getStatusCode(final Throwable throwable) {
        int status = 500;
        if (throwable instanceof BridgeServiceException) {
            status = ((BridgeServiceException)throwable).getStatusCode();
        } else if (throwable instanceof ProvisionedThroughputExceededException) {
            // Similarly, DDB Throughput exception should be a 500.
            status = 500;
        } else if (throwable instanceof AmazonServiceException) {
            status = ((AmazonServiceException)throwable).getStatusCode();
        }
        return status;
    }

    private String getMessage(final Throwable throwable, final int status) {
        String message = throwable.getMessage();
        if (throwable instanceof AmazonServiceException) {
            // This is a more appropriately formatted error message for end users than
            // amazonServiceException.getMessage()
            message = ((AmazonServiceException)throwable).getErrorMessage();
        }
        if (StringUtils.isBlank(message)) {
            message = Integer.toString(status);
        }
        return message;
    }    
}
