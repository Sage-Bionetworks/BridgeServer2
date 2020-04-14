package org.sagebionetworks.bridge.spring.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

@CrossOrigin
@RestController
public class UserDataDownloadController extends BaseController {
    static final StatusMessage ACCEPTED_MSG = new StatusMessage("Request submitted.");
    private UserDataDownloadService userDataDownloadService;

    /** Service handler for User Data Download requests. */
    @Autowired
    final void setUserDataDownloadService(UserDataDownloadService userDataDownloadService) {
        this.userDataDownloadService = userDataDownloadService;
    }

    /**
     * Play handler for requesting user data. User must be authenticated and consented. (Otherwise, they couldn't have
     * any data to download to begin with.)
     */
    @PostMapping({"/v3/users/self/emailData", "/v3/users/self/sendData"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage requestUserData() throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        String studyIdentifier = session.getAppId();
        
        // At least for now, if the user does not have a verified email address, do not allow this service.
        StudyParticipant participant = session.getParticipant();
        boolean verifiedEmail = (participant.getEmail() != null && Boolean.TRUE.equals(participant.getEmailVerified()));
        boolean verifiedPhone = (participant.getPhone() != null && Boolean.TRUE.equals(participant.getPhoneVerified()));
        if (!verifiedEmail && !verifiedPhone) {
            throw new BadRequestException("Cannot request user data, account has no verified email address or phone number.");
        }

        DateRange dateRange = parseJson(DateRange.class);
        userDataDownloadService.requestUserData(studyIdentifier, session.getParticipant().getId(), dateRange);
        return ACCEPTED_MSG;
    }
}
