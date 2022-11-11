package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ENROLLMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_OTHER_ENROLLMENTS;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PREVIEW_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.getElement;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLMENT_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.validators.EnrollmentValidator.INSTANCE;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.alerts.Alert;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class EnrollmentService {
    
    static final String PREVIEW_USER_ERROR_MSG = "Preview accounts can only enroll in one study";
    
    private static class EnrollmentHolder {
        Enrollment enrollment;
    }
    
    @Autowired
    private AccountService accountService;
    @Autowired
    private EnrollmentDao enrollmentDao;
    @Autowired
    private AlertService alertService;
    @Autowired
    private StudyService studyService;
    
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
    
    public List<EnrollmentDetail> getEnrollmentsForUser(String appId, @Nullable String studyId, String userIdToken) {
        checkNotNull(appId);
        checkNotNull(userIdToken);
        
        // verify the study exists if it is passed in
        if (studyId != null) {
            studyService.getStudy(appId, studyId, true);    
        }
        // Developers accessing production accounts will be prevented by getAccount()
        AccountId accountId = BridgeUtils.parseAccountId(appId, userIdToken);
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        // Study-scoped users must have access to the study, roles like developer/researcher/admin are also OK
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, studyId, USER_ID, account.getId());
        
        // Global roles can see all enrollments, but study-scoped roles only see studies they are associated to 
        RequestContext context = RequestContext.get();
        Set<String> studyIds = context.isInRole(ImmutableSet.of(DEVELOPER, RESEARCHER, ADMIN)) ? 
                ImmutableSet.of() : context.getOrgSponsoredStudies();
        return enrollmentDao.getEnrollmentsForUser(appId, studyIds, account.getId());
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
            holder.enrollment = addEnrollment(acct, enrollment, false);
        });
        return holder.enrollment;
    }
    
    /**
     * For methods that are going to save the account, this method adds an enrollment correctly
     * to an account, but does not persist it or fire an enrollment event. 
     * 
     * @param account - the account to be altered (but not persisted)
     * @param newEnrollment - the enrollment to add to the account.
     * @param updateRequestContext - update the current request context to reflect enrollment in the 
     *      study. Some calls are triggered by the participant, and will go on to fire study-related 
     *      events that check for study access permission, requiring the context to be updated event
     *      before the session is updated and returned from the call.
     * @return - the enrollment object instance used to enroll the user (usually the enrollment passed to 
     *      this method with modifications).
     */
    public Enrollment addEnrollment(Account account, Enrollment newEnrollment, boolean updateRequestContext) {
        checkNotNull(account);
        checkNotNull(newEnrollment);
        
        if (account.getDataGroups().contains(PREVIEW_USER_GROUP)) {
            Set<String> studyIds = BridgeUtils.collectStudyIds(account);
            Set<String> targetId = ImmutableSet.of(newEnrollment.getStudyId());
            if (!studyIds.isEmpty() && !studyIds.equals(targetId)) {
                throw new BadRequestException(PREVIEW_USER_ERROR_MSG);
            }
        }
        Validate.entityThrowingException(INSTANCE, newEnrollment);
        
        Study study = studyService.getStudy(newEnrollment.getAppId(), newEnrollment.getStudyId(), true);
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, newEnrollment.getStudyId(), USER_ID, account.getId());
        
        if (updateRequestContext) {
            RequestContext context = RequestContext.get();
            RequestContext.set(context.toBuilder().withCallerEnrolledStudies(
                    addToSet(context.getCallerEnrolledStudies(), newEnrollment.getStudyId())).build());            
        }
        Enrollment existingEnrollment = getElement(account.getEnrollments(), 
                Enrollment::getStudyId, newEnrollment.getStudyId()).orElse(null);
        
        // If you enroll an account in a study that is in the design phase, that account
        // can no longer be treated as a production account.
        if (study.getPhase() == DESIGN) {
            account.setDataGroups(addToSet(account.getDataGroups(), TEST_USER_GROUP));
        }
        if (existingEnrollment != null) {
            editEnrollment(account, newEnrollment, existingEnrollment);
            return existingEnrollment;
        }
        editEnrollment(account, newEnrollment, newEnrollment);
        account.getEnrollments().add(newEnrollment);

        // trigger alert for new enrollment
        alertService.createAlert(Alert.newEnrollment(newEnrollment.getAppId(), newEnrollment.getStudyId(),
                new AccountRef(account, newEnrollment.getStudyId())));

        return newEnrollment;
    }
    
    private void editEnrollment(Account account, Enrollment newEnrollment, Enrollment existingEnrollment) {
        existingEnrollment.setWithdrawnOn(null);
        existingEnrollment.setWithdrawnBy(null);
        existingEnrollment.setWithdrawalNote(null);
        existingEnrollment.setNote(newEnrollment.getNote());
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

    public void updateEnrollment(Enrollment enrollment) {
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        AccountId accountId = AccountId.forId(enrollment.getAppId(), enrollment.getAccountId());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        CAN_EDIT_OTHER_ENROLLMENTS.checkAndThrow(STUDY_ID, enrollment.getStudyId(), USER_ID, enrollment.getAccountId());
        
        for (Enrollment accountEnrollment : account.getEnrollments()) {
            if (accountEnrollment.getStudyId().equals(enrollment.getStudyId())) {
                accountEnrollment.setNote(enrollment.getNote());
                accountEnrollment.setWithdrawalNote(enrollment.getWithdrawalNote());
                accountEnrollment.setConsentRequired(enrollment.isConsentRequired());
                accountService.updateAccount(account);
                return;
            }
        }
    }
}
