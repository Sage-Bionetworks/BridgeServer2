package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Basic list of items, not paged, as calculated based on the parameters that were 
 * sent to the server and are included in the <code>ResourceList</code>. As a step 
  * toward eliminating the deprecated properties on this class, it now takes a flag
  * that causes these fields to be null and thus, they will be excluded from JSON 
  * payloads. New APIs can set this flag to true.
 */
public class ResourceList<T> {
    
    public static final String ADHERENCE_MAX = "adherenceMax";
    public static final String ADHERENCE_MIN = "adherenceMin";
    public static final String ADHERENCE_RECORD_TYPE = "adherenceRecordType";
    public static final String ADMIN_ONLY = "adminOnly";
    public static final String ALL_OF_GROUPS = "allOfGroups";
    public static final String ASSESSMENT_IDS = "assessmentIds";
    public static final String ASSIGNMENT_FILTER = "assignmentFilter";
    public static final String ATTRIBUTE_KEY = "attributeKey";
    public static final String ATTRIBUTE_VALUE_FILTER = "attributeValueFilter";
    public static final String CATEGORIES = "categories"; // should be a set or list
    public static final String CURRENT_TIMESTAMPS_ONLY = "currentTimestampsOnly";
    public static final String EMAIL_FILTER = "emailFilter";
    public static final String END_DATE = "endDate";
    public static final String END_TIME = "endTime";
    public static final String ENROLLED_IN_STUDY_ID = "enrolledInStudyId";
    public static final String ENROLLMENT = "enrollment";
    public static final String ENROLLMENT_FILTER = "enrollmentFilter";
    public static final String EVENT_TIMESTAMPS = "eventTimestamps";
    public static final String EXTERNAL_ID_FILTER = "externalIdFilter";
    public static final String GUID = "guid";
    public static final String ID_FILTER = "idFilter";
    public static final String IDENTIFIER = "identifier";
    public static final String INCLUDE_DELETED = "includeDeleted";
    public static final String INCLUDE_REPEATS = "includeRepeats";
    public static final String INSTANCE_GUIDS = "instanceGuids";
    public static final String IN_USE = "inUse";
    public static final String LABEL_FILTERS = "labelFilters";
    public static final String LANGUAGE = "language";
    public static final String MAX_REVISION = "maxRevision";
    public static final String MIN_REVISION = "minRevision";
    public static final String NEXT_PAGE_OFFSET_KEY = "nextPageOffsetKey";
    public static final String NONE_OF_GROUPS = "noneOfGroups";
    public static final String OFFSET_BY = "offsetBy";
    public static final String OFFSET_KEY = "offsetKey";
    public static final String ORG_MEMBERSHIP = "orgMembership";
    public static final String PAGE_SIZE = "pageSize";
    public static final String PHONE_FILTER = "phoneFilter";
    public static final String PREDICATE = "predicate";
    public static final String PROGRESSION_FILTERS = "progressionFilters";
    public static final String REPORT_TYPE = "reportType";
    public static final String SCHEDULED_ON_END = "scheduledOnEnd";
    public static final String SCHEDULED_ON_START = "scheduledOnStart";
    public static final String SESSION_GUIDS = "sessionGuids";
    public static final String SORT_ORDER = "sortOrder";
    public static final String START_DATE = "startDate";
    public static final String START_TIME = "startTime";
    public static final String STATUS = "status";
    public static final String STRING_SEARCH_POSITION = "stringSearchPosition";
    public static final String STUDY_ID = "studyId";
    public static final String TAGS = "tags";
    public static final String TEMPLATE_TYPE = "templateType";
    public static final String TEST_FILTER = "testFilter";
    public static final String TIME_WINDOW_GUIDS = "timeWindowGuids";
    public static final String TOTAL = "total";
    public static final String TYPE = "type";
    public static final String REQUEST_PARAMS = "RequestParams";
    
    protected static final String ITEMS = "items";
    
    private final List<T> items;
    private final Map<String,Object> requestParams = new HashMap<>();
    protected final boolean suppressDeprecated;

    /**
     * 
     * @param items
     * @param suppressDeprecated
     *      set to true to suppress deprecated fields in JSON serialization of the class.
     */
    public ResourceList(List<T> items, boolean suppressDeprecated) {
        checkNotNull(items);
        this.items = items;
        this.requestParams.put(TYPE, REQUEST_PARAMS);
        this.suppressDeprecated = suppressDeprecated;
    }

    @JsonCreator
    public ResourceList(@JsonProperty(ITEMS) List<T> items) {
        this(items, false);
    }

    public List<T> getItems() {
        return items;
    }
    public Map<String, Object> getRequestParams() {
        return ImmutableMap.copyOf(requestParams);
    }
    public ResourceList<T> withRequestParam(String key, Object value) {
        if (!ResourceList.TYPE.equals(key) && isNotBlank(key) && value != null) {
            if (value instanceof DateTime) {
                // For DateTime, forcing toString() here rather than using Jackson's serialization mechanism, 
                // ensures the string is in the timezone supplied by the user.
                requestParams.put(key, value.toString());    
            } else {
                requestParams.put(key, value);    
            }
        }
        return this;
    }
    @Deprecated
    public Integer getTotal() {
        return (suppressDeprecated || items.isEmpty()) ? null : items.size();
    }
    protected DateTime getDateTime(String fieldName) {
        String value = (String)requestParams.get(fieldName);
        return (value == null) ? null : DateTime.parse(value);
    }
    protected LocalDate getLocalDate(String fieldName) {
        Object object = requestParams.get(fieldName);
        if (object instanceof String) {
            return LocalDate.parse((String)object);
        }
        return (LocalDate)object;
    }
}