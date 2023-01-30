package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.AuthUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.AlertFilter;
import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.sagebionetworks.bridge.services.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class AlertController extends BaseController {
    private static final StatusMessage DELETE_ALERTS_MESSAGE = new StatusMessage("Alerts successfully deleted");

    private AlertService alertService;

    @Autowired
    public final void setAlertService(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Fetches all alerts for a study.
     * 
     * @param studyId  The studyId to fetch the alerts from.
     * @param offsetBy The offset at which the list of alerts should begin.
     * @param pageSize The maximum number of entries in the returned list of alerts.
     * @return The fetched list of alerts.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if the caller is not a researcher or study
     *                                   coordinator.
     * @throws BadRequestException       if offsetBy or pageSize is invalid.
     */
    @PostMapping("/v5/studies/{studyId}/alerts")
    public PagedResourceList<Alert> getAlerts(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize)
            throws NotAuthenticatedException, UnauthorizedException, BadRequestException {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(AuthEvaluatorField.STUDY_ID, studyId);

        AlertFilter alertFilter = parseJson(AlertFilter.class);
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        return alertService.getAlerts(session.getAppId(), studyId, offsetInt, pageSizeInt, alertFilter);
    }

    /**
     * Deletes alerts given a list of their ids.
     * 
     * This uses the POST method because DELETE cannot have a body.
     * 
     * @param studyId The studyId to delete the alerts from.
     * @return A status message indicating the alerts were deleted.
     * @throws NotAuthenticatedException if the caller is not authenticated.
     * @throws UnauthorizedException     if the caller is not a researcher or study
     *                                   coordinator.
     * @throws EntityNotFoundException   if the alerts to delete do not exist or are
     *                                   not from this study.
     */
    @PostMapping("/v5/studies/{studyId}/alerts/delete")
    public StatusMessage deleteAlerts(@PathVariable String studyId)
            throws NotAuthenticatedException, UnauthorizedException, EntityNotFoundException {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.STUDY_COORDINATOR);
        AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(AuthEvaluatorField.STUDY_ID, studyId);

        AlertIdCollection alertsToDelete = parseJson(AlertIdCollection.class);
        alertService.deleteAlerts(session.getAppId(), studyId, alertsToDelete);
        return DELETE_ALERTS_MESSAGE;
    }
}
