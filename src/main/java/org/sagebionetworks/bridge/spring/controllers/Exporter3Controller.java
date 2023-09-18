package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDIES;
import static org.sagebionetworks.bridge.AuthUtils.CAN_UPDATE_STUDIES;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.springframework.http.HttpStatus.ACCEPTED;

import java.io.IOException;

import org.sagebionetworks.client.exceptions.SynapseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionResult;
import org.sagebionetworks.bridge.services.Exporter3Service;

/** Controller for Exporter 3.0. */
@CrossOrigin
@RestController
public class Exporter3Controller extends BaseController {
    static final StatusMessage SEND_NOTIFICATION_MSG = new StatusMessage("Notifications have been sent.");

    private Exporter3Service exporter3Service;

    @Autowired
    public final void setExporter3Service(Exporter3Service exporter3Service) {
        this.exporter3Service = exporter3Service;
    }

    /** Initializes configs and Synapse resources for Exporter 3.0. */
    @PostMapping(path = "/v1/apps/self/exporter3")
    @ResponseStatus(HttpStatus.CREATED)
    public Exporter3Configuration initExporter3() throws BridgeSynapseException, IOException, SynapseException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        return exporter3Service.initExporter3(session.getAppId());
    }

    /** Subscribe to be notified when a study is initialized for Exporter 3.0 in caller's app. */
    @PostMapping(path = "/v1/apps/self/exporter3/notifications/study/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ExporterSubscriptionResult subscribeToCreateStudyNotifications() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        ExporterSubscriptionRequest subscriptionRequest = parseJson(ExporterSubscriptionRequest.class);
        ExporterSubscriptionResult subscriptionResult = exporter3Service.subscribeToCreateStudyNotifications(
                session.getAppId(), subscriptionRequest);
        return subscriptionResult;
    }

    /** Subscribe to be notified when health data is exported to any Synapse projects in the app. */
    @PostMapping(path = "/v1/apps/self/exporter3/notifications/export/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ExporterSubscriptionResult subscribeToExportNotificationsForApp() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        ExporterSubscriptionRequest subscriptionRequest = parseJson(ExporterSubscriptionRequest.class);
        ExporterSubscriptionResult subscriptionResult = exporter3Service.subscribeToExportNotificationsForApp(
                session.getAppId(), subscriptionRequest);
        return subscriptionResult;
    }

    /**
     * This is called by the Worker when a health data record is exported to Synapse. This sends notifications to all
     * subscribers for both the app-wide Synapse project and all study-specific Synapse projects that the record was
     * exported to.
     */
    @PostMapping(path = "/v1/exporter3/notifications/export")
    @ResponseStatus(code = ACCEPTED)
    public StatusMessage sendExportNotifications() {
        getAuthenticatedSession(WORKER);
        ExportToAppNotification notification = parseJson(ExportToAppNotification.class);
        exporter3Service.sendExportNotifications(notification);
        return SEND_NOTIFICATION_MSG;
    }

    /** Initializes configs and Synapse resources for Exporter 3.0 for a study. */
    @PostMapping("/v5/studies/{studyId}/exporter3")
    @ResponseStatus(HttpStatus.CREATED)
    public Exporter3Configuration initExporter3ForStudy(@PathVariable String studyId) throws BridgeSynapseException, IOException,
            SynapseException {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, studyId);
        return exporter3Service.initExporter3ForStudy(session.getAppId(), studyId);
    }

    @PostMapping("/v5/studies/{studyId}/timeline/export")
    @ResponseStatus(HttpStatus.CREATED)
    public Exporter3Configuration exportTimelineForStudy(@PathVariable String studyId)
            throws BridgeSynapseException, SynapseException, IOException {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);
        CAN_UPDATE_STUDIES.checkAndThrow(STUDY_ID, studyId);
        return exporter3Service.exportTimelineForStudy(session.getAppId(), studyId);
    }

    /** Subscribe to be notified when health data is exported to the study-specific Synapse project. */
    @PostMapping(path = "/v5/studies/{studyId}/exporter3/notifications/export/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ExporterSubscriptionResult subscribeToExportNotificationsForStudy(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, DEVELOPER);
        CAN_READ_STUDIES.checkAndThrow(STUDY_ID, studyId);

        ExporterSubscriptionRequest subscriptionRequest = parseJson(ExporterSubscriptionRequest.class);
        ExporterSubscriptionResult subscriptionResult = exporter3Service.subscribeToExportNotificationsForStudy(
                session.getAppId(), studyId, subscriptionRequest);
        return subscriptionResult;
    }
}
