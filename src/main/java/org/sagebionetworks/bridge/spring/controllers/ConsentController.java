package org.sagebionetworks.bridge.spring.controllers;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.Map;

import javax.servlet.http.Cookie;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;

@CrossOrigin
@RestController
public class ConsentController extends BaseController {

    private ConsentService consentService;

    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Deprecated
    @GetMapping(path = { "/v3/consents/signature", "/api/v2/consent", "/api/v1/consent" }, produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getConsentSignature() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        return getConsentSignatureV2(session.getStudyIdentifier());
    }

    @Deprecated
    @PostMapping("/api/v1/consent")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode giveV1() throws Exception {
        UserSession session = getAuthenticatedSession();
        return giveConsentForVersion(1, SubpopulationGuid.create(session.getStudyIdentifier()));
    }

    @Deprecated
    @PostMapping({"/v3/consents/signature", "/api/v2/consent"})
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode giveV2() throws Exception {
        UserSession session = getAuthenticatedSession();
        return giveConsentForVersion(2, SubpopulationGuid.create(session.getStudyIdentifier()));
    }

    @Deprecated
    @PostMapping({"/v3/consents/signature/email", "/api/v1/consent/email"})
    public StatusMessage emailCopy() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        return resendConsentAgreement(session.getStudyIdentifier());
    }

    @Deprecated
    @PostMapping("/api/v1/consent/dataSharing/suspend")
    public JsonNode suspendDataSharing() throws Exception {
        return changeSharingScope(SharingScope.NO_SHARING, 
                "Data sharing with the study researchers has been suspended.");
    }

    @Deprecated
    @PostMapping("/api/v1/consent/dataSharing/resume")
    public JsonNode resumeDataSharing() throws Exception {
        return changeSharingScope(SharingScope.SPONSORS_AND_PARTNERS,
                "Data sharing with the study researchers has been resumed.");
    }
    
    @PostMapping({"/v3/users/self/dataSharing", "/api/v2/consent/dataSharing"})
    public JsonNode changeSharingScope() throws Exception {
        SharingOption sharing = SharingOption.fromJson(parseJson(JsonNode.class), 2);
        return changeSharingScope(sharing.getSharingScope(), "Data sharing has been changed.");
    }
    
    @Deprecated
    @PostMapping("/v3/consents/signature/withdraw")
    public JsonNode withdrawConsent() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return withdrawConsentV2(study.getIdentifier());
    }

    // V2: consent to a specific subpopulation
    
    @GetMapping(path="/v3/subpopulations/{guid}/consents/signature", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getConsentSignatureV2(@PathVariable String guid) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());

        ConsentSignature sig = consentService.getConsentSignature(study, SubpopulationGuid.create(guid), session.getId());
        return ConsentSignature.SIGNATURE_WRITER.writeValueAsString(sig);
    }
    
    @PostMapping("/v3/subpopulations/{guid}/consents/signature")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode giveV3(@PathVariable String guid) throws Exception {
        return giveConsentForVersion(2, SubpopulationGuid.create(guid));
    }
    
    @PostMapping("/v3/subpopulations/{guid}/consents/signature/withdraw")
    public JsonNode withdrawConsentV2(@PathVariable String guid) throws Exception {
        UserSession session = getAuthenticatedSession();
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        long withdrewOn = DateTime.now().getMillis();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);

        CriteriaContext context = getCriteriaContext(session);
        consentService.withdrawConsent(study, subpopGuid, session.getParticipant(), context, withdrawal, withdrewOn);
        
        // We must do a full refresh of the session because consents can set data groups and substudies.
        UserSession updatedSession = authenticationService.getSession(study, context);
        sessionUpdateService.updateSession(session, updatedSession);

        return UserSessionInfo.toJSON(updatedSession);
    }
    
    @PostMapping("/v3/consents/withdraw")
    public StatusMessage withdrawFromStudy() {
        UserSession session = getAuthenticatedSession();
        Withdrawal withdrawal = parseJson(Withdrawal.class);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        long withdrewOn = DateTime.now().getMillis();
        
        consentService.withdrawFromStudy(study, session.getParticipant(), withdrawal, withdrewOn);
        
        authenticationService.signOut(session);
        
        Cookie cookie = makeSessionCookie("", 0);
        response().addCookie(cookie);
        return new StatusMessage("Signed out.");
    }
    
    @PostMapping({ "/v3/subpopulations/{guid}/consents/signature/email",
            "/v3/subpopulations/{guid}/consents/signature/resend" })
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatusMessage resendConsentAgreement(@PathVariable String guid) {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());

        consentService.resendConsentAgreement(study, SubpopulationGuid.create(guid), session.getParticipant());
        return new StatusMessage("Signed consent agreement resent.");
    }
    
    private JsonNode changeSharingScope(SharingScope sharingScope, String message) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        accountService.editAccount(session.getStudyIdentifier(), session.getHealthCode(),
                account -> account.setSharingScope(sharingScope));

        sessionUpdateService.updateSharingScope(session, sharingScope);
        
        return UserSessionInfo.toJSON(session);
    }
    
    private JsonNode giveConsentForVersion(int version, SubpopulationGuid subpopGuid) throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = parseJson(JsonNode.class);
        ConsentSignature consentSignature = ConsentSignature.fromJSON(node);
        SharingOption sharing = SharingOption.fromJson(node, version);
        
        // On client update, clients have sent consent signatures even before the session reflects the need
        // for the new consent. Update the criteria context before consent, using the latest User-Agent
        // header, so the server is synchronized with the client's state.
        CriteriaContext context = getCriteriaContext(session);
        Map<SubpopulationGuid,ConsentStatus> consentStatuses = consentService.getConsentStatuses(context);
        
        // If provided subpopulation is not in the statuses, it either doesn't exist or doesn't apply to 
        // this user, and we return a 404
        ConsentStatus status = consentStatuses.get(subpopGuid);
        if (status == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        
        consentService.consentToResearch(study, subpopGuid, session.getParticipant(), consentSignature,
                sharing.getSharingScope(), true);
        
        // We must do a full refresh of the session because consents can set data groups and substudies.
        CriteriaContext updatedContext = getCriteriaContext(session);
        UserSession updatedSession = authenticationService.getSession(study, updatedContext);
        sessionUpdateService.updateSession(session, updatedSession);
        
        return UserSessionInfo.toJSON(updatedSession);
    }
}
