package org.sagebionetworks.bridge.spring.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Test controller to test error handling. Throws an exception. */
@CrossOrigin
@RestController
public class ErrorController {
    @RequestMapping(path = "/error", method = RequestMethod.GET)
    public void handle() {
        throw new UnsupportedOperationException("This API is not supported.");
    }
}
