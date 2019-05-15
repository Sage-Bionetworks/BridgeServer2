package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.SmsService;

@CrossOrigin
@RestController
public class SmsController extends BaseController {
    private ParticipantService participantService;
    private SmsService smsService;

    /** Participant service, used to get a phone number for an account. */
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /** SMS service. */
    @Autowired
    final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    /** Returns the most recent message sent to the phone number of the given user. Used by integration tests. */
    @GetMapping("/v3/participants/{userId}/sms/recent")
    public SmsMessage getMostRecentMessage(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Get phone number for participant.
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        if (participant.getPhone() == null) {
            throw new BadRequestException("participant has no phone number");
        }

        // Get SMS message for phone number.
        return smsService.getMostRecentMessage(participant.getPhone().getNumber());
    }
}
