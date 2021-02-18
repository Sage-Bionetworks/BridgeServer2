package org.sagebionetworks.bridge.upload;

import static org.sagebionetworks.bridge.BridgeUtils.collectExternalIds;
import static org.sagebionetworks.bridge.BridgeUtils.mapStudyMemberships;

import java.util.Set;

import javax.annotation.Nonnull;

import com.google.common.collect.Iterables;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private AccountService accountService;
    private ParticipantService participantService;

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        HealthDataRecord record = context.getHealthDataRecord();

        AccountId accountId = AccountId.forHealthCode(context.getAppId(), context.getHealthCode());
        Account account = accountService.getAccountNoFilter(accountId).orElse(null);
        if (account != null) {
            
            Set<String> externalIds = collectExternalIds(account);
            String externalId = Iterables.getFirst(externalIds, null);
            
            // write user info to health data record
            record.setUserSharingScope(account.getSharingScope());
            record.setUserExternalId(externalId);
            record.setUserDataGroups(account.getDataGroups());
            record.setUserStudyMemberships( mapStudyMemberships(account) );

            // Calculate dayInStudy.
            // Snap to a calendar date in the local time zone. For example, a participant has an
            // activities_retrieved with calendar date 2019-07-24. Therefore, 2019-07-24 would be day 1, 2019-07-25
            // would be day 2, etc.
            // This must be a self call.
            RequestContext.set(RequestContext.get().toBuilder().withCallerUserId(account.getId()).build());
            DateTime studyStartTime = participantService.getStudyStartTime(account);
            LocalDate studyStartDate = studyStartTime.withZone(BridgeConstants.LOCAL_TIME_ZONE).toLocalDate();
            LocalDate todayLocalDate = DateUtils.getCurrentCalendarDateInLocalTime();

            // "dayInStudy" is 1-indexed. The first day of the study is day 1. Therefore, add 1.
            int dayInStudy = Days.daysBetween(studyStartDate, todayLocalDate).getDays() + 1;
            record.setDayInStudy(dayInStudy);
        } else {
            // default sharing to NO_SHARING
            record.setUserSharingScope(SharingScope.NO_SHARING);
        }
    }
}
