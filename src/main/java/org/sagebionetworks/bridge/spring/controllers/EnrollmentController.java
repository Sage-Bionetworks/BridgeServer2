package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;
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
    public PagedResourceList<Enrollment> getEnrollmentsForStudy(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String enrollmentFilter) {
        UserSession session = getAuthenticatedSession(RESEARCHER, ADMIN);
        
        EnrollmentFilter filter = BridgeUtils.getEnumOrDefault(enrollmentFilter, EnrollmentFilter.class, null);
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return service.getEnrollmentsForStudy(session.getAppId(), studyId, filter, offsetByInt, pageSizeInt);        
    }
    
    @PostMapping("/v5/studies/{studyId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public Enrollment enroll(@PathVariable String studyId) {
        UserSession session = getAuthenticatedSession(RESEARCHER, ADMIN);
        
        Enrollment enrollment = parseJson(Enrollment.class);
        enrollment.setAppId(session.getAppId());
        enrollment.setStudyId(studyId);
        
        return service.enroll(enrollment);
    }
    
    @PostMapping("/v5/studies/{studyId}/enrollments/{userId}")
    public Enrollment unenroll(@PathVariable String studyId, @PathVariable String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER, ADMIN);
        
        Enrollment enrollment = parseJson(Enrollment.class);
        enrollment.setAppId(session.getAppId());
        enrollment.setStudyId(studyId);
        enrollment.setAccountId(userId);
        
        return service.unenroll(enrollment);
    }
}
