package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;
import org.sagebionetworks.bridge.models.studies.EnrollmentMigration;
import org.sagebionetworks.bridge.services.EnrollmentService;

@CrossOrigin
@RestController
public class EnrollmentController extends BaseController {

    private EnrollmentService service;
    
    @Autowired
    final void setEnrollmentService(EnrollmentService service) {
        this.service = service;
    }
    
    @GetMapping("/v5/studies/{studyId}/enrollments")
    public PagedResourceList<EnrollmentDetail> getEnrollmentsForStudy(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String enrollmentFilter,
            @RequestParam(required = false) String includeTesters) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        EnrollmentFilter filter = BridgeUtils.getEnumOrDefault(enrollmentFilter, EnrollmentFilter.class, null);
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean includeTestersBool = Boolean.valueOf(includeTesters);

        return service.getEnrollmentsForStudy(session.getAppId(), studyId, filter, includeTestersBool, offsetByInt,
                pageSizeInt);
    }
    
    @PostMapping("/v5/studies/{studyId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment enroll(@PathVariable String studyId) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        Enrollment enrollment = parseJson(Enrollment.class);
        enrollment.setAppId(session.getAppId());
        enrollment.setStudyId(studyId);
        
        return service.enroll(enrollment);
    }
    
    @DeleteMapping("/v5/studies/{studyId}/enrollments/{userId}")
    public Enrollment unenroll(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(required = false) String withdrawalNote) {
        UserSession session = getAdministrativeSession();
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);
        
        Enrollment enrollment = Enrollment.create(session.getAppId(), studyId, userId);
        enrollment.setWithdrawalNote(withdrawalNote);
        
        return service.unenroll(enrollment);
    }
    
    @PostMapping("/v3/participants/{userId}/enrollments")
    public StatusMessage updateUserEnrollments(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        List<EnrollmentMigration> migrations = parseJson(new TypeReference<List<EnrollmentMigration>>() {});
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        accountService.editAccount(accountId, (acct) -> {
            acct.getEnrollments().clear();
            acct.getEnrollments().addAll(migrations.stream().map(m -> m.asEnrollment()).collect(toSet()));
        });
        return new StatusMessage("Enrollments updated.");
    }    
}
