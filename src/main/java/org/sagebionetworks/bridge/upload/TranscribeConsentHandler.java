package org.sagebionetworks.bridge.upload;

import static org.sagebionetworks.bridge.BridgeUtils.mapSubstudyMemberships;

import javax.annotation.Nonnull;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class TranscribeConsentHandler implements UploadValidationHandler {
    private AccountDao accountDao;
    private ParticipantService participantService;

    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    public final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        HealthDataRecord record = context.getHealthDataRecord();

        AccountId accountId = AccountId.forHealthCode(context.getStudy().getIdentifier(), context.getHealthCode());
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            // write user info to health data record
            record.setUserSharingScope(account.getSharingScope());
            record.setUserExternalId(account.getExternalId());
            record.setUserDataGroups(account.getDataGroups());
            record.setUserSubstudyMemberships( mapSubstudyMemberships(account) );

            // Calculate dayInStudy.
            // Snap to a calendar date in the local time zone. For example, a participant has an
            // activities_retrieved with calendar date 2019-07-24. Therefore, 2019-07-24 would be day 1, 2019-07-25
            // would be day 2, etc.
            DateTime studyStartTime = participantService.getStudyStartTime(accountId);
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
