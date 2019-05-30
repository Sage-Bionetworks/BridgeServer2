package org.sagebionetworks.bridge.spring.controllers;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.EndpointNotFoundException;

/** Catch-all controller for any URL not defined by existing controllers. */
@CrossOrigin
@RestController
public class DefaultController {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

    /** Default catch-all handler. Logs the method and URL, then throws a 404. */
    @RequestMapping
    public void handle(HttpServletRequest request) {
        LOG.info("Unrecognized endpoint " + request.getMethod() + " " + request.getRequestURI());
        throw new EndpointNotFoundException();
    }
}
