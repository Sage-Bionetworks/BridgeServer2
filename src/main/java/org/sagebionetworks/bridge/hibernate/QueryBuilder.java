package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.AND_JOINER;
import static org.sagebionetworks.bridge.BridgeUtils.OR_JOINER;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.StringSearchPosition.INFIX;
import static org.sagebionetworks.bridge.models.StringSearchPosition.POSTFIX;
import static org.sagebionetworks.bridge.models.StringSearchPosition.PREFIX;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.WITHDRAWN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.StringSearchPosition;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

/**
 * A helper class to manage construction of HQL strings.
 */
class QueryBuilder {
    
    private final List<String> phrases = new ArrayList<>();
    private final Map<String,Object> params = new HashMap<>();
    private WhereClauseBuilder whereClause;
    
    public WhereClauseBuilder startWhere(SearchTermPredicate predicate) {
        whereClause = new WhereClauseBuilder(predicate);
        return whereClause;
    }
    
    void finishWhere() {
        if (whereClause != null) {
            phrases.add(whereClause.getWhereClause());
            params.putAll(whereClause.getParameters());
        }
        whereClause = null;
    }

    public void append(String phrase) {
        finishWhere();
        phrases.add(phrase);
    }
    public void append(String phrase, String key, Object value) {
        finishWhere();
        if (value != null) {
            phrases.add(phrase);
            params.put(key, value);
        }
    }
    public void append(String phrase, String key1, Object value1, String key2, Object value2) {
        finishWhere();
        if (value1 != null && value2 != null) {
            phrases.add(phrase);
            params.put(key1, value1);
            params.put(key2, value2);
        }
    }
    public void append(String phrase, String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        finishWhere();
        if (value1 != null && value2 != null && value3 != null) {
            phrases.add(phrase);
            params.put(key1, value1);
            params.put(key2, value2);
            params.put(key3, value3);
        }
    }
    public String getQuery() {
        finishWhere();
        return BridgeUtils.SPACE_JOINER.join(phrases);
    }
    public Map<String,Object> getParameters() {
        return params;
    }
    
    static final class WhereClauseBuilder {
        private final SearchTermPredicate predicate;
        private final List<String> required = new ArrayList<>();
        private final List<String> predicated = new ArrayList<>();
        private final Map<String,Object> whereParams = new HashMap<>();
        
        private WhereClauseBuilder(SearchTermPredicate predicate) {
            this.predicate = predicate;
        }
        public void appendRequired(String phrase) { 
            required.add(phrase);
        }
        public void appendRequired(String phrase, String key, Object value) { 
            if (value != null) {
                required.add(phrase);
                whereParams.put(key, value);
            }
        }
        public void adminOnlyRequired(Boolean isAdmin) {
            if (isAdmin != null) {
                if (TRUE.equals(isAdmin)) {
                    appendRequired("size(acct.roles) > 0");
                } else {
                    appendRequired("size(acct.roles) = 0");
                }
            }
        }
        public void orgMembershipRequired(String orgMembership) {
            if (orgMembership != null) {
                if ("<none>".equals(orgMembership.toLowerCase())) {
                    appendRequired("acct.orgMembership IS NULL");
                } else {
                    appendRequired("acct.orgMembership = :orgId", "orgId", orgMembership);
                }
            }
        }
        public void append(String phrase) {
            predicated.add(phrase);
        }
        public void append(String phrase, String key, Object value) {
            if (value != null) {
                predicated.add(phrase);
                whereParams.put(key, value);
            }
        }
        public void like(StringSearchPosition pos, String phrase, String key, String value) {
            if (isNotBlank(value)) {
                predicated.add(phrase);
                String searchString = 
                        ((pos == POSTFIX || pos == INFIX) ? "%" : "") +
                        value.toString() +
                        ((pos == PREFIX || pos == INFIX) ? "%" : "");
                whereParams.put(key, searchString);
            }
        }
        public void labels(List<String> labelFilter) {
            List<String> phrases = new ArrayList<>();
            for (int i=0; i < labelFilter.size(); i++) {
                phrases.add("label LIKE :labelFilter"+i);
                whereParams.put("labelFilter"+i,  "%:" + labelFilter.get(i) + ":%");
            }
            predicated.add("(" + Joiner.on(" OR ").join(phrases) + ")");
        }
        // HQL
        public void phone(StringSearchPosition pos, String phoneFilter) {
            if (isNotBlank(phoneFilter)) {
                String phoneString = phoneFilter.replaceAll("\\D*", "");
                like(pos, "acct.phone.number LIKE :number", "number", phoneString);
            }
        }
        public void dataGroups(Set<String> dataGroups, String operator) {
            if (!BridgeUtils.isEmpty(dataGroups)) {
                int i = 0;
                List<String> clauses = new ArrayList<>();
                for (String oneDataGroup : dataGroups) {
                    String varName = operator.replace(" ", "") + (++i);
                    clauses.add(":"+varName+" "+operator+" elements(acct.dataGroups)");
                    whereParams.put(varName, oneDataGroup);
                }
                predicated.add("(" + AND_JOINER.join(clauses) + ")");
            }
        }
        public void enrollment(EnrollmentFilter filter, boolean prefixed) {
            if (filter != null) {
                // We prefix this query for acounts, but for enrollments, it's a primary 
                // property, not a collection on the entity.
                if (filter == ENROLLED) {
                    predicated.add((prefixed ? "enrollment." : "") + "withdrawnOn IS NULL");
                } else if (filter == WITHDRAWN) {
                    predicated.add((prefixed ? "enrollment." : "") + "withdrawnOn IS NOT NULL");
                }
            }
        }
        // Native SQL, not HQL
        public void alternativeMatchedPairs(Map<String, DateTime> map, String varPrefix, String field1, String field2) {
            if (map != null && !map.isEmpty()) {
                List<String> clauses = new ArrayList<>();
                int count = 0;
                for (Map.Entry<String, DateTime> entry : map.entrySet()) {
                    String keyName = varPrefix + "Key" + count;
                    String valName = varPrefix + "Val" + count++;
                    String q = format("(%s = :%s AND %s = :%s)", field1, keyName, field2, valName);
                    clauses.add(q);
                    whereParams.put(keyName, entry.getKey());
                    whereParams.put(valName, entry.getValue().getMillis());
                }
                predicated.add("( "+OR_JOINER.join(clauses)+" )");
            }
        }
        public void adherenceRecordType(AdherenceRecordType type) {
            if (type != null) {
                if (type == SESSION) {
                    predicated.add("tm.assessmentGuid IS NULL");
                } else {
                    predicated.add("tm.assessmentGuid IS NOT NULL");
                }
            }
            
        }
        
        public String getWhereClause() {
            Joiner predJoiner = (predicate == AND) ? AND_JOINER : OR_JOINER;
            
            // There's always a where clause, or else this would be problematic.
            StringBuilder sb = new StringBuilder();
            if (!required.isEmpty() || !predicated.isEmpty()) {
                sb.append("WHERE ");
            }
            // This is a special case to simplify the syntax. If we didn't do this
            // it would still be valid, but overly nested, and would require rewriting
            // many tests.
            if (!required.isEmpty() && !predicated.isEmpty() && predicate == AND) {
                sb.append(AND_JOINER.join(Iterables.concat(required, predicated)));
            } else if (!required.isEmpty()) { 
                sb.append(AND_JOINER.join(required));
                if (!predicated.isEmpty()) {
                    sb.append(" AND (" + predJoiner.join(predicated) + ")");
                }
            } else if (!predicated.isEmpty()) {
                sb.append(predJoiner.join(predicated));
            }
            return sb.toString();
        }
        public Map<String,Object> getParameters() {
            return whereParams;
        }
    }
}
