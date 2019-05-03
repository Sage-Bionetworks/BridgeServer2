package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_API_STATUS_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.COOKIE;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.spring.filters.MetricsFilter;
import org.sagebionetworks.bridge.spring.util.HttpUtil;

/**
 * Pass-through controller. Takes in HTTP requests that aren't caught by any other controller and forwards them to
 * BridgePF.
 */
@RestController
public class PassthroughController {
    private static final Logger LOG = LoggerFactory.getLogger(PassthroughController.class);

    static final String BAD_REQUEST_EXCEPTION = "BadRequestException";
    static final String CONFIG_KEY_BRIDGE_PF_HOST = "bridge.pf.host";
    static final String NOT_IMPLEMENTED_EXCEPTION = "NotImplementedException";

    private String bridgePfHost;

    /** Bridge config. */
    @Autowired
    public void setConfig(Config config) {
        bridgePfHost = config.get(CONFIG_KEY_BRIDGE_PF_HOST);
    }

    /** Passthrough handler. */
    @RequestMapping
    @CrossOrigin(origins = "*", allowCredentials = "true", allowedHeaders = "*", methods = { RequestMethod.GET,
            RequestMethod.POST, RequestMethod.DELETE, RequestMethod.HEAD })
    public ResponseEntity<String> handleDefault(HttpServletRequest request, @RequestBody(required = false) String body)
            throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        // URL. This includes query parameters. Spring provides them to use as a string, so we just append them to the
        // url like a string.
        String url = request.getRequestURI();
        String ipAddress = request.getRemoteAddr();
        LOG.info("Received request " + request.getMethod() + " " + url + " from IP address " + ipAddress);

        String fullUrl = bridgePfHost + url;
        if (request.getQueryString() != null) {
            fullUrl += "?" + request.getQueryString();
        }

        // Method. Note that Apache HTTP has different function calls for each HTTP method, so we need this switch.
        Request pfRequest;
        switch (request.getMethod()) {
            case "GET":
                pfRequest = Request.Get(fullUrl);
                break;
            case "POST":
                pfRequest = Request.Post(fullUrl);
                break;
            case "DELETE":
                pfRequest = Request.Delete(fullUrl);
                break;
            case "HEAD":
                pfRequest = Request.Head(fullUrl);
                break;
            default:
                String errorMessage = "Method " + request.getMethod() + " not supported";
                LOG.warn(errorMessage);
                return HttpUtil.convertErrorToJsonResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_EXCEPTION,
                        errorMessage);
        }

        // Only carry over the headers that Bridge server directly examines. Let Spring do the Cross-Origin headers.
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        String userAgent = request.getHeader(USER_AGENT);
        String acceptLanguage = request.getHeader(ACCEPT_LANGUAGE);
        String sessionToken = request.getHeader(SESSION_TOKEN_HEADER);
        // This will only work with the first cookie header. We set only one cookie on the bridge server, so this 
        // should be sufficient for a testing feature.
        String cookie = request.getHeader(COOKIE);

        // although we called getRemoteAddr() above, in the original header logic, we then look for this 
        // forwarding header, so retrieve the value here. If it didn't exist, ipAddress would not be 
        // overridden.
        ipAddress = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        
        if (requestId != null) {
            pfRequest.addHeader(X_REQUEST_ID_HEADER, requestId);    
        }
        if (userAgent != null) {
            pfRequest.addHeader(USER_AGENT, userAgent);    
        }
        if (acceptLanguage != null) {
            pfRequest.addHeader(ACCEPT_LANGUAGE, acceptLanguage);    
        }
        if (ipAddress != null) {
            pfRequest.addHeader(X_FORWARDED_FOR_HEADER, ipAddress);            
        }
        if (sessionToken != null) {
            pfRequest.addHeader(SESSION_TOKEN_HEADER, sessionToken);
        }
        if (cookie != null) {
            pfRequest.addHeader(COOKIE, cookie);
        }

        LOG.info("Sending request " + request.getMethod() + " " + url + " w/ requestId=" + requestId);

        // Request body. We take in the raw body as a string and pass it along to BridgePF.
        if (body != null) {
            pfRequest.bodyString(body, ContentType.parse(request.getContentType()));
        }

        // Execute.
        HttpResponse pfResponse = pfRequest.execute().returnResponse();

        // Response status.
        int statusCode = pfResponse.getStatusLine().getStatusCode();
        HttpStatus springStatus = HttpStatus.resolve(statusCode);
        if (springStatus == null) {
            String errMsg = "Unrecognized status code " + statusCode;
            LOG.error(errMsg);
            return HttpUtil.convertErrorToJsonResponse(HttpStatus.NOT_IMPLEMENTED, NOT_IMPLEMENTED_EXCEPTION, errMsg);
        }

        // Response headers. We only pass back Bridge specific headers so there's no interference with Spring filters.
        HttpHeaders springHeaders = new HttpHeaders();
        springHeaders.add(MetricsFilter.X_PASSTHROUGH, "BridgePF");
        copyHeader(springHeaders, pfResponse, SESSION_TOKEN_HEADER);
        copyHeader(springHeaders, pfResponse, BRIDGE_API_STATUS_HEADER);
        copyHeader(springHeaders, pfResponse, CONTENT_LENGTH);
        copyHeader(springHeaders, pfResponse, CONTENT_TYPE);
        copyHeader(springHeaders, pfResponse, SET_COOKIE);
        
        // Response body. HEAD and DELETE do not return a response body, check first.
        String responseBody = null;
        if (pfResponse.getEntity() != null) {
            responseBody = EntityUtils.toString(pfResponse.getEntity(), Charsets.UTF_8);    
        }
        LOG.info("Request " + requestId + ", status=" + statusCode + ", took " +
                stopwatch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return new ResponseEntity<>(responseBody, springHeaders, springStatus);
    }
    
    private void copyHeader(HttpHeaders springHeaders, HttpResponse pfResponse, String headerName) {
        Header header = pfResponse.getFirstHeader(headerName);
        if (header != null) {
            springHeaders.add(headerName, header.getValue());
        }
    }

}
