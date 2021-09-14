package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ENROLLMENTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLMENT_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.EnrollmentValidator.INSTANCE;

import java.util.List;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class EnrollmentService {
    
    private static class EnrollmentHolder {
        Enrollment enrollment;
    }
    
    private AccountService accountService;
    
    private EnrollmentDao enrollmentDao;
    
    private StudyService studyService;
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Autowired
    final void setEnrollmentDao(EnrollmentDao enrollmentDao) {
        this.enrollmentDao = enrollmentDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    protected DateTime getEnrollmentDateTime() {
        return DateTime.now();
    }

    protected DateTime getWithdrawalDateTime() {
        return DateTime.now();
    }
    
    /**
     * Get enrollments in a study. This API will be expanded to retrieve and sort the data for 
     * common reporting requirements (e.g. how many people have withdrawn from the study).
     */
    public PagedResourceList<EnrollmentDetail> getEnrollmentsForStudy(String appId, String studyId, 
            EnrollmentFilter filter, boolean includeTesters, Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        studyService.getStudy(appId, studyId, true);
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, studyId);

        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return enrollmentDao.getEnrollmentsForStudy(appId, studyId, filter, includeTesters, offsetBy, pageSize)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(ENROLLMENT_FILTER, filter);
    }
    
    public List<EnrollmentDetail> getEnrollmentsForUser(String appId, String studyId, String userIdToken) {
        checkNotNull(appId);
        checkNotNull(userIdToken);
        
        studyService.getStudy(appId, studyId, true);
        
        // We want all enrollments, even withdrawn enrollments, so don't filter here.
        AccountId accountId = BridgeUtils.parseAccountId(appId, userIdToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, studyId, USER_ID, account.getId());

        return enrollmentDao.getEnrollmentsForUser(appId, account.getId());
    }
    
    public Enrollment enroll(Enrollment enrollment) {
        checkNotNull(enrollment);

        // verify this has appId and accountId
        Validate.entityThrowingException(INSTANCE, enrollment);

        studyService.getStudy(enrollment.getAppId(), enrollment.getStudyId(), true);

        // Verify that the caller has access to this study
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, enrollment.getStudyId(), USER_ID,
                enrollment.getAccountId());

        // Because this is an enrollment, we don't want to check the caller's access to the 
        // account based on study, because the account has not been put in a study accessible
        // to the caller. The check would fail for researchers.
        final EnrollmentHolder holder = new EnrollmentHolder();
        AccountId accountId = AccountId.forId(enrollment.getAppId(), enrollment.getAccountId());
        accountService.editAccount(accountId, (acct) -> {
            holder.enrollment = addEnrollment(acct, enrollment);
        });
        return holder.enrollment;
    }
    
    /**
     * For methods that are going to save the account, this method adds an enrollment correctly
     * to an account, but does not persist it or fire an enrollment event. It will also tag
     * the user as a test user if the enrollment is for a study in the design phase. Once 
     * tagged as a test account, an account is always a test account.
     */
    public Enrollment addEnrollment(Account account, Enrollment newEnrollment) {
        checkNotNull(account);
        checkNotNull(newEnrollment);
        
        Validate.entityThrowingException(INSTANCE, newEnrollment);
        
        studyService.getStudy(newEnrollment.getAppId(), newEnrollment.getStudyId(), true);
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, newEnrollment.getStudyId(), USER_ID, account.getId());

        for (Enrollment existingEnrollment : account.getEnrollments()) {
            if (existingEnrollment.getStudyId().equals(newEnrollment.getStudyId())) {
                updateEnrollment(account, newEnrollment, existingEnrollment);
                return existingEnrollment;
            }
        }
        updateEnrollment(account, newEnrollment, newEnrollment);
        account.getEnrollments().add(newEnrollment);
        return newEnrollment;
    }
    
    private void updateEnrollment(Account account, Enrollment newEnrollment, Enrollment existingEnrollment) {
        existingEnrollment.setWithdrawnOn(null);
        existingEnrollment.setWithdrawnBy(null);
        existingEnrollment.setWithdrawalNote(null);
        existingEnrollment.setConsentRequired(newEnrollment.isConsentRequired());
        // We might want eventually to allow this to be nullified, but right now with two 
        // systems for enrolling the user, ParticipantService and ConsentService can easily
        // stomp on one another. Eventually StudyParticipant will be able to accept an Enrollment
        // record as part of account creation, which fixes this problem.
        if (newEnrollment.getExternalId() != null) {
            existingEnrollment.setExternalId(newEnrollment.getExternalId());                    
        }
        if (newEnrollment.getEnrolledOn() != null) {
            existingEnrollment.setEnrolledOn(newEnrollment.getEnrolledOn());    
        } else {
            existingEnrollment.setEnrolledOn(getEnrollmentDateTime());
        }
        String callerUserId = RequestContext.get().getCallerUserId();
        if (!account.getId().equals(callerUserId)) {
            existingEnrollment.setEnrolledBy(callerUserId);
        }
    }
    
    public Enrollment unenroll(Enrollment enrollment) {
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        studyService.getStudy(enrollment.getAppId(), enrollment.getStudyId(), true);
        
        final EnrollmentHolder holder = new EnrollmentHolder();
        AccountId accountId = AccountId.forId(enrollment.getAppId(), enrollment.getAccountId());
        accountService.editAccount(accountId, (acct) -> {
            holder.enrollment = unenroll(acct, enrollment);
        });
        return holder.enrollment;
    }

    /**
     * For methods that are going to save the account, this method withdraws the account but does not persist it.
     */
    public Enrollment unenroll(Account account, Enrollment enrollment) {
        checkNotNull(account);
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        studyService.getStudy(enrollment.getAppId(), enrollment.getStudyId(), true);
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, enrollment.getStudyId(), USER_ID, account.getId());
        
        // If supplied, this value should be the same timestamp as the withdrewOn
        // value in the signature. Otherwise just set it here. 
        if (enrollment.getWithdrawnOn() == null) {
            enrollment.setWithdrawnOn(getWithdrawalDateTime());
        }
        String callerUserId = RequestContext.get().getCallerUserId();
        String withdrawnBy = (!account.getId().equals(callerUserId)) ? callerUserId : null;
        enrollment.setWithdrawnBy(withdrawnBy);
        
        for (Enrollment existingEnrollment : account.getEnrollments()) {
            if (existingEnrollment.getStudyId().equals(enrollment.getStudyId())) {
                existingEnrollment.setWithdrawnOn(enrollment.getWithdrawnOn());
                existingEnrollment.setWithdrawnBy(enrollment.getWithdrawnBy());
                existingEnrollment.setWithdrawalNote(enrollment.getWithdrawalNote());
                return existingEnrollment;
            }
        }
        throw new EntityNotFoundException(Enrollment.class);
    }
}
