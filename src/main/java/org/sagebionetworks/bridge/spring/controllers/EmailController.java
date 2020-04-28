package org.sagebionetworks.bridge.spring.controllers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.App;

@CrossOrigin
@RestController
public class EmailController extends BaseController {
    private static final Logger LOG = LoggerFactory.getLogger(EmailController.class);
    
    /**
     * An URL to which a POST can be sent to set the user's email notification preference to "off". Cannot turn email
     * notifications back on through this endpoint. This cannot be part of the public API, because MailChimp doesn't
     * deal with non-200 status codes. The token that is submitted is set in the configuration, and must match to allow
     * this call to succeed. Subject to change without warning or backwards compatibility. 
     */
    @RequestMapping(method = {GET, POST}, path="/v3/users/self/unsubscribeEmail", produces="text/plain")
    public String unsubscribeFromEmail() {
        // We catch and return 200s because MailChimp makes a validation call when configuring the web hook, and if it fails,
        // MailChimp won't persist the configuration. We could also detect the validation call because it has a different
        // User-Agent than the real callbacks, "MailChimp.com WebHook Validator" versus "MailChimp.com", and always return 
        // 200 for that validation call.
        try {
            // The servlet getParameter() method will extract the value from a query string or from 
            // POSTed form data, making this much simpler than in Play.
            String token = request().getParameter("token");
            if (token == null || !token.equals(bridgeConfig.getEmailUnsubscribeToken())) {
                throw new BridgeServiceException("No authentication token provided.", SC_UNAUTHORIZED);
            }
            // App has to be provided as an URL parameter
            String studyId = request().getParameter("study");
            if (studyId == null) {
                throw new BadRequestException("Study not found.");
            }
            App app = appService.getApp(studyId);
            
            // MailChimp submits email as data[email]
            String email = request().getParameter("data[email]");
            if (email == null) {
                email = request().getParameter("email");
            }
            if (email == null) {
                throw new BadRequestException("Email not found.");
            }
            
            // This should always return a healthCode under normal circumstances.
            AccountId accountId = AccountId.forEmail(app.getIdentifier(), email);
            String healthCode = accountService.getHealthCodeForAccount(accountId);
            if (healthCode == null) {
                throw new BadRequestException("Email not found.");
            }
            accountService.editAccount(app.getIdentifier(), healthCode, account -> account.setNotifyByEmail(false));
            
            return "You have been unsubscribed from future email.";
        } catch(Throwable throwable) {
            String errorMsg = "Unknown error";
            if (isNotBlank(throwable.getMessage())) {
                errorMsg = throwable.getMessage();
            }
            LOG.error("Error unsubscribing: " + errorMsg, throwable);
            return errorMsg;
        }
    }
}
