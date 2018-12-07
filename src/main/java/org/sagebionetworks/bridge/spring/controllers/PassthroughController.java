package org.sagebionetworks.bridge.spring.controllers;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;
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
    static final String HEADER_IP_ADDRESS = "X-Forwarded-For";
    static final String HEADER_REQUEST_ID = "X-Request-Id";
    static final String NOT_IMPLEMENTED_EXCEPTION = "NotImplementedException";

    private String bridgePfHost;

    /** Bridge config. */
    @Autowired
    public void setConfig(Config config) {
        bridgePfHost = config.get(CONFIG_KEY_BRIDGE_PF_HOST);
    }

    /** Passthrough handler. */
    @RequestMapping
    @CrossOrigin(origins="*", methods = {RequestMethod.GET, RequestMethod.POST, 
        RequestMethod.DELETE}, allowCredentials="true", allowedHeaders= {
        "Accept", "Content-Type", "User-Agent", "Bridge-Session", "Origin"})
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
            default:
                String errorMessage = "Method " + request.getMethod() + " not supported";
                LOG.warn(errorMessage);
                return HttpUtil.convertErrorToJsonResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_EXCEPTION,
                        errorMessage);
        }

        // Headers.
        Enumeration<String> headerNameEnum = request.getHeaderNames();
        boolean hasIpAddress = false;
        String requestId = null;
        while (headerNameEnum.hasMoreElements()) {
            String headerName = headerNameEnum.nextElement();
            if (headerName.equalsIgnoreCase("content-length")) {
                // Request.body() automatically sets this header for us. We can't set it here, or else we'll get a
                // "header already present" exception.
                continue;
            }

            String headerValue = request.getHeader(headerName);
            pfRequest.addHeader(headerName, headerValue);

            if (headerName.equalsIgnoreCase(HEADER_IP_ADDRESS)) {
                hasIpAddress = true;
            }

            if (headerName.equalsIgnoreCase(HEADER_REQUEST_ID)) {
                requestId = headerValue;
            }
        }

        // Add IP Address header, if it doesn't exist.
        if (!hasIpAddress) {
            pfRequest.addHeader(HEADER_IP_ADDRESS, ipAddress);
        }

        // Add request ID header, if it doesn't exist.
        if (requestId == null) {
            requestId = randomGuid();
            pfRequest.addHeader(HEADER_REQUEST_ID, requestId);
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

        // Response headers.
        HttpHeaders springHeaders = new HttpHeaders();
        for (Header header : pfResponse.getAllHeaders()) {
            springHeaders.add(header.getName(), header.getValue());
        }

        // Response body.
        String responseBody = EntityUtils.toString(pfResponse.getEntity(), Charsets.UTF_8);

        LOG.info("Request " + requestId + ", status=" + statusCode + ", took " +
                stopwatch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return new ResponseEntity<>(responseBody, springHeaders, springStatus);
    }

    // Package-scoped to allow unit tests to spy.
    String randomGuid() {
        return UUID.randomUUID().toString();
    }
}
