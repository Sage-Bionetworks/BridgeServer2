package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
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
        UserSession session = getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR, ADMIN);
        
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
        UserSession session = getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR, ADMIN);
        
        Enrollment enrollment = parseJson(Enrollment.class);
        enrollment.setAppId(session.getAppId());
        enrollment.setStudyId(studyId);
        
        return service.enroll(enrollment);
    }
    
    @DeleteMapping("/v5/studies/{studyId}/enrollments/{userId}")
    public Enrollment unenroll(@PathVariable String studyId, @PathVariable String userId,
            @RequestParam(required = false) String withdrawalNote) {
        UserSession session = getAuthenticatedSession(RESEARCHER, STUDY_COORDINATOR, ADMIN);
        
        Enrollment enrollment = Enrollment.create(session.getAppId(), studyId, userId);
        enrollment.setWithdrawalNote(withdrawalNote);
        
        return service.unenroll(enrollment);
    }
    
    @GetMapping("/v1/migration/participants/{userId}/enrollments")
    public List<EnrollmentMigration> getUserEnrollments(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account.getEnrollments().stream()
                .map(EnrollmentMigration::create).collect(toList());
    }
    
    @PostMapping("/v1/migration/participants/{userId}/enrollments")
    public StatusMessage updateUserEnrollments(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        List<EnrollmentMigration> migrations = parseJson(new TypeReference<List<EnrollmentMigration>>() {});
        
        AccountId accountId = BridgeUtils.parseAccountId(session.getAppId(), userId);
        Account acct = accountService.getAccount(accountId);
        if (acct == null) {
            throw new EntityNotFoundException(Account.class);
        }
        accountService.editAccount(session.getAppId(), acct.getHealthCode(), (account) -> {
            account.getEnrollments().clear();
            account.getEnrollments().addAll(migrations.stream().map(m -> m.asEnrollment()).collect(toSet()));
        });
        return new StatusMessage("Enrollments updated.");
    }
}
