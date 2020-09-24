package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthUtils.checkSelfAdminOrSponsorAndThrow;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLMENT_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.EnrollmentValidator.INSTANCE;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
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
    
    private AccountService accountService;
    
    private SponsorService sponsorService;
    
    private EnrollmentDao enrollmentDao;
    
    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Autowired
    public final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }
    
    @Autowired
    public final void setEnrollmentDao(EnrollmentDao enrollmentDao) {
        this.enrollmentDao = enrollmentDao;
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
            EnrollmentFilter filter, Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        checkSelfAdminOrSponsorAndThrow(sponsorService, studyId, null);

        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return enrollmentDao.getEnrollmentsForStudy(appId, studyId, filter, offsetBy, pageSize)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(ENROLLMENT_FILTER, filter);
    }
    
    /**
     * For methods that are going to save the account, this method handles enrollment but does not persist it.
     */
    public Enrollment enroll(Account account, Enrollment enrollment) {
        checkNotNull(account);
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        checkSelfAdminOrSponsorAndThrow(sponsorService, enrollment.getStudyId(), enrollment.getAccountId());

        for (Enrollment existingEnrollment : account.getEnrollments()) {
            // appId and accountId are always going to match, given the way these 
            // records are loaded. We only need to look for the studyId. 
            if (existingEnrollment.getStudyId().equals(enrollment.getStudyId())) {
                if (existingEnrollment.getWithdrawnOn() != null) {
                    account.getEnrollments().remove(existingEnrollment);
                    break;
                } else {
                    throw new EntityAlreadyExistsException(Enrollment.class,
                        ImmutableMap.of("accountId", account.getId(), "studyId", enrollment.getStudyId()));
                }
            }
        }
        enrollment.setWithdrawnOn(null);
        enrollment.setWithdrawnBy(null);
        enrollment.setWithdrawalNote(null);
        enrollment.setExternalId(enrollment.getExternalId());
        if (enrollment.getEnrolledOn() == null) {
            enrollment.setEnrolledOn(getEnrollmentDateTime());
        }
        String callerUserId = RequestContext.get().getCallerUserId();
        if (!account.getId().equals(callerUserId)) {
            enrollment.setEnrolledBy(callerUserId);
        }

        account.getEnrollments().add(enrollment);
        return enrollment;
    }
    
    public Enrollment enroll(Enrollment enrollment) {
        checkNotNull(enrollment);
        
        // verify this has appId and accountId
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        AccountId accountId = AccountId.forId(enrollment.getAppId(), enrollment.getAccountId());
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        enroll(account, enrollment);
        accountService.updateAccount(account, null);
        return enrollment;
    }
    
    /**
     * For methods that are going to save the account, this method withdraws the account but does not persist it.
     */
    public Enrollment unenroll(Account account, Enrollment enrollment) {
        checkNotNull(account);
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        checkSelfAdminOrSponsorAndThrow(sponsorService, enrollment.getStudyId(), enrollment.getAccountId());
        
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
                if (existingEnrollment.getWithdrawnOn() != null) {
                    throw new EntityAlreadyExistsException(Enrollment.class, 
                            "Participant is already withdrawn from study.", 
                            ImmutableMap.of("studyId", enrollment.getStudyId()));
                } else {
                    existingEnrollment.setWithdrawnOn(enrollment.getWithdrawnOn());
                    existingEnrollment.setWithdrawnBy(enrollment.getWithdrawnBy());
                    existingEnrollment.setWithdrawalNote(enrollment.getWithdrawalNote());
                    return existingEnrollment;
                }
            }
        }
        throw new EntityNotFoundException(Enrollment.class);
    }
    
    public Enrollment unenroll(Enrollment enrollment) {
        checkNotNull(enrollment);
        
        Validate.entityThrowingException(INSTANCE, enrollment);
        
        AccountId accountId = AccountId.forId(enrollment.getAppId(), enrollment.getAccountId());
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        enrollment = unenroll(account, enrollment);
        accountService.updateAccount(account, null);
        return enrollment;
    }
}
