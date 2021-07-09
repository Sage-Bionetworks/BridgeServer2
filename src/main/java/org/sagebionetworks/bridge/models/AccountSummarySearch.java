package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.StringSearchPosition.INFIX;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonDeserialize(builder = AccountSummarySearch.Builder.class)
public final class AccountSummarySearch implements BridgeEntity {
    
    public static final AccountSummarySearch EMPTY_SEARCH = new AccountSummarySearch.Builder().build();
    private final int offsetBy;
    private final int pageSize;
    private final String emailFilter;
    private final String phoneFilter;
    private final Set<String> allOfGroups;
    private final Set<String> noneOfGroups; 
    private final String language;
    private final DateTime startTime;
    private final DateTime endTime;
    private final String orgMembership;
    private final Boolean adminOnly;
    private final String enrolledInStudyId;
    private final String externalIdFilter;
    private final AccountStatus status;
    private final EnrollmentFilter enrollment;
    private final String attributeKey;
    private final String attributeValueFilter;
    private final SearchTermPredicate predicate;
    private final StringSearchPosition stringSearchPosition;

    private AccountSummarySearch(AccountSummarySearch.Builder builder) {
        this.offsetBy = builder.offsetBy;
        this.pageSize = builder.pageSize;
        this.emailFilter = builder.emailFilter;
        this.phoneFilter = builder.phoneFilter;
        this.allOfGroups = builder.allOfGroups;
        this.noneOfGroups = builder.noneOfGroups;
        this.language = builder.language;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.orgMembership = builder.orgMembership;
        this.adminOnly = builder.adminOnly;
        this.enrolledInStudyId = builder.enrolledInStudyId;
        this.externalIdFilter = builder.externalIdFilter;
        this.status = builder.status;
        this.enrollment = builder.enrollment;
        this.attributeKey = builder.attributeKey;
        this.attributeValueFilter = builder.attributeValueFilter;
        this.predicate = builder.predicate;
        this.stringSearchPosition = builder.stringSearchPosition;
    }

    public int getOffsetBy() {
        return offsetBy;
    }
    public int getPageSize() {
        return pageSize;
    }
    public String getEmailFilter() {
        return emailFilter;
    }
    public String getPhoneFilter() {
        return phoneFilter;
    }
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    public String getLanguage() {
        return language;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getStartTime() {
        return startTime;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getEndTime() {
        return endTime;
    }
    /**
     * If no organizational ID is supplied, all accounts will be returned. If an ID is 
     * supplied, only accounts assigned to that organization are returned. If the 
     * keyword value "<none>" is supplied, only accounts are returned that are NOT 
     * assigned to any organization. adminOnly=true and orgMembership=<none> will return
     * admin accounts that still need to be assigned to an organization (a useful 
     * query for user interfaces). 
     */
    public String getOrgMembership() {
        return orgMembership;
    }
    /**
     * Administrative accounts are accounts with any roles that allow them to operate 
     * against our administrative APIs (not participant-facing, and not requiring consent). 
     * When null, the search returns all accounts. When true, returns accounts with at 
     * least one assigned role. When false, returns accounts with no administrative roles.
     * StudyId will be treated as null when adminOnly == true.  
     */
    public Boolean isAdminOnly() {
        return adminOnly;
    }
    
    /**
     * Accounts returned should be enrolled in this study (not administrative accounts with 
     * access to the study). If the caller does not have access to this study, the search
     * results should be empty. This flag implies that adminOnly == false. 
     */
    public String getEnrolledInStudyId() {
        return enrolledInStudyId;
    }
    public String getExternalIdFilter() {
        return externalIdFilter;
    }
    public AccountStatus getStatus() {
        return status;
    }
    public EnrollmentFilter getEnrollment() {
        return enrollment;
    }
    public String getAttributeKey() {
        return attributeKey;
    }
    public String getAttributeValueFilter() {
        return attributeValueFilter;
    }
    public SearchTermPredicate getPredicate() {
        return predicate;
    }
    public StringSearchPosition getStringSearchPosition() {
        return stringSearchPosition;
    }
    public AccountSummarySearch.Builder toBuilder() {
        return new AccountSummarySearch.Builder()
            .withOffsetBy(offsetBy)
            .withPageSize(pageSize)
            .withEmailFilter(emailFilter)
            .withPhoneFilter(phoneFilter)
            .withAllOfGroups(allOfGroups)
            .withNoneOfGroups(noneOfGroups)
            .withLanguage(language)
            .withStartTime(startTime)
            .withEndTime(endTime)
            .withOrgMembership(orgMembership)
            .withAdminOnly(adminOnly)
            .withEnrolledInStudyId(enrolledInStudyId)
            .withExternalIdFilter(externalIdFilter)
            .withStatus(status)
            .withEnrollment(enrollment)
            .withAttributeKey(attributeKey)
            .withAttributeValueFilter(attributeValueFilter)
            .withPredicate(predicate)
            .withStringSearchPosition(stringSearchPosition);
    }

    @Override
    public int hashCode() {
        // When serialized, Joda DateTime objects can change their Chronology and become unequal, even when representing
        // the same time instant in the same time zone. (For example, ISOChronology[America/Los_Angeles]
        // versus ISOChronology[-07:00] if that's the offset at the time of serialization). Using the ISO String
        // representation of the DateTime gives us equality across serialization.
        return Objects.hash(allOfGroups, emailFilter, nullsafeDateString(endTime), language, noneOfGroups, offsetBy,
                pageSize, phoneFilter, nullsafeDateString(startTime), orgMembership, adminOnly, enrolledInStudyId,
                externalIdFilter, status, enrollment, attributeKey, attributeValueFilter, predicate,
                stringSearchPosition);
    }

    @Override
    public boolean equals(Object obj) {
        // When serialized, Joda DateTime objects can change their Chronology and become unequal, even when representing
        // the same time instant in the same time zone. (For example, ISOChronology[America/Los_Angeles]
        // versus ISOChronology[-07:00] if that's the offset at the time of serialization). Using the ISO String
        // representation of the DateTime gives us equality across serialization.
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSummarySearch other = (AccountSummarySearch) obj;
        return Objects.equals(allOfGroups, other.allOfGroups) && Objects.equals(emailFilter, other.emailFilter)
                && Objects.equals(nullsafeDateString(endTime),  nullsafeDateString(other.endTime))
                && Objects.equals(language, other.language) && Objects.equals(noneOfGroups, other.noneOfGroups)
                && Objects.equals(offsetBy, other.offsetBy) && Objects.equals(pageSize, other.pageSize)
                && Objects.equals(phoneFilter, other.phoneFilter)
                && Objects.equals(nullsafeDateString(startTime), nullsafeDateString(other.startTime))
                && Objects.equals(orgMembership, other.orgMembership)
                && Objects.equals(adminOnly, other.adminOnly)
                && Objects.equals(enrolledInStudyId, other.enrolledInStudyId)
                && Objects.equals(externalIdFilter, other.externalIdFilter)
                && Objects.equals(status, other.status)
                && Objects.equals(enrollment, other.enrollment)
                && Objects.equals(attributeKey, other.attributeKey)
                && Objects.equals(attributeValueFilter, other.attributeValueFilter)
                && Objects.equals(predicate, other.predicate)
                && Objects.equals(stringSearchPosition, other.stringSearchPosition);
    }
    
    private String nullsafeDateString(DateTime dateTime) {
        return (dateTime == null) ? null : dateTime.toString();
    }

    @Override
    public String toString() {
        return "AccountSummarySearch [offsetBy=" + offsetBy + ", pageSize=" + pageSize + ", emailFilter=" + emailFilter
                + ", phoneFilter=" + phoneFilter + ", allOfGroups=" + allOfGroups + ", noneOfGroups=" + noneOfGroups
                + ", language=" + language + ", startTime=" + startTime + ", endTime=" + endTime + ", orgMembership="
                + orgMembership + ", adminOnly=" + adminOnly + ", enrolledInStudyId=" + enrolledInStudyId
                + ", externalIdFilter=" + externalIdFilter + ", status=" + status + ", enrollment=" + enrollment
                + ", attributeKey=" + attributeKey + ", attributeValueFilter=" + attributeValueFilter + ", predicate="
                + predicate + ", stringSearchPosition=" + stringSearchPosition + "]";
    }

    public static class Builder {
        private Integer offsetBy;
        private Integer pageSize;
        private String emailFilter;
        private String phoneFilter;
        private Set<String> allOfGroups = new HashSet<>();
        private Set<String> noneOfGroups = new HashSet<>();
        private String language;
        private DateTime startTime;
        private DateTime endTime;
        private String orgMembership;
        private Boolean adminOnly;
        private String enrolledInStudyId;
        private String externalIdFilter;
        private AccountStatus status;
        private EnrollmentFilter enrollment;
        private String attributeKey;
        private String attributeValueFilter;
        private SearchTermPredicate predicate;
        private StringSearchPosition stringSearchPosition;
        
        public Builder withOffsetBy(Integer offsetBy) {
            this.offsetBy = offsetBy;
            return this;
        }
        public Builder withPageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }
        public Builder withEmailFilter(String emailFilter) {
            this.emailFilter = emailFilter;
            return this;
        }
        public Builder withPhoneFilter(String phoneFilter) {
            this.phoneFilter = phoneFilter;
            return this;
        }
        public Builder withAllOfGroups(Set<String> allOfGroups) {
            if (allOfGroups != null) {
                this.allOfGroups = allOfGroups;    
            }
            return this;
        }
        public Builder withNoneOfGroups(Set<String> noneOfGroups) {
            if (noneOfGroups != null) {
                this.noneOfGroups = noneOfGroups;    
            }
            return this;
        }
        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withStartTime(DateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        @JsonDeserialize(using = DateTimeDeserializer.class)
        public Builder withEndTime(DateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        public Builder withOrgMembership(String orgId) {
            this.orgMembership = orgId;
            return this;
        }
        public Builder withAdminOnly(Boolean adminOnly) {
            this.adminOnly = adminOnly;
            return this;
        }
        public Builder withEnrolledInStudyId(String enrolledInStudyId) {
            this.enrolledInStudyId = enrolledInStudyId;
            return this;
        }
        public Builder withExternalIdFilter(String externalIdFilter) {
            this.externalIdFilter = externalIdFilter;
            return this;
        }
        public Builder withStatus(AccountStatus status) {
            this.status = status;
            return this;
        }
        public Builder withEnrollment(EnrollmentFilter enrollment) {
            this.enrollment = enrollment;
            return this;
        }
        public Builder withAttributeKey(String attributeKey) {
            this.attributeKey = attributeKey;
            return this;
        }
        public Builder withAttributeValueFilter(String attributeValueFilter) {
            this.attributeValueFilter = attributeValueFilter;
            return this;
        }
        public Builder withPredicate(SearchTermPredicate predicate) {
            this.predicate = predicate;
            return this;
        }
        public Builder withStringSearchPosition(StringSearchPosition stringSearchPosition) {
            this.stringSearchPosition = stringSearchPosition;
            return this;
        }
        public AccountSummarySearch build() {
            if (offsetBy == null) {
                offsetBy = 0;
            }
            if (pageSize == null) {
                pageSize = API_DEFAULT_PAGE_SIZE;
            }
            if (predicate == null) {
                predicate = AND;
            }
            if (stringSearchPosition == null) {
                stringSearchPosition = INFIX;
            }
            return new AccountSummarySearch(this);
        }
    }
}
