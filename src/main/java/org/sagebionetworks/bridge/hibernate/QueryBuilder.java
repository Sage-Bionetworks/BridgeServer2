package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.AND_JOINER;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.WITHDRAWN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

/**
 * A helper class to manage construction of HQL strings.
 */
class QueryBuilder {
    
    private final List<String> phrases = new ArrayList<>();
    private final Map<String,Object> params = new HashMap<>();
    private QueryBuilder whereClause;
    
    public QueryBuilder startWhere() {
        whereClause = new QueryBuilder();
        return whereClause;
    }
    
    void finishWhere() {
        if (whereClause != null) {
            phrases.add("WHERE " + AND_JOINER.join(whereClause.phrases));
            params.putAll(whereClause.getParameters());
            whereClause = null;
        }
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
    public void like(String phrase, String key, String value) {
        finishWhere();
        if (isNotBlank(value)) {
            phrases.add(phrase);
            params.put(key, "%"+value.toString()+"%");
        }
    }
    // HQL
    public void phone(String phoneFilter) {
        finishWhere();
        if (isNotBlank(phoneFilter)) {
            String phoneString = phoneFilter.replaceAll("\\D*", "");
            like("acct.phone.number LIKE :number", "number", phoneString);
        }
    }
    public void dataGroups(Set<String> dataGroups, String operator) {
        finishWhere();
        if (!BridgeUtils.isEmpty(dataGroups)) {
            int i = 0;
            List<String> clauses = new ArrayList<>();
            for (String oneDataGroup : dataGroups) {
                String varName = operator.replace(" ", "") + (++i);
                clauses.add(":"+varName+" "+operator+" elements(acct.dataGroups)");
                params.put(varName, oneDataGroup);
            }
            phrases.add("(" + AND_JOINER.join(clauses) + ")");
        }
    }
    public void adminOnly(Boolean isAdmin) {
        finishWhere();
        if (isAdmin != null) {
            if (TRUE.equals(isAdmin)) {
                phrases.add("size(acct.roles) > 0");
            } else {
                phrases.add("size(acct.roles) = 0");
            }
        }
    }
    public void orgMembership(String orgMembership) {
        finishWhere();
        if (orgMembership != null) {
            if ("<none>".equals(orgMembership.toLowerCase())) {
                phrases.add("acct.orgMembership IS NULL");
            } else {
                append("acct.orgMembership = :orgId", "orgId", orgMembership);
            }
        }
    }
    public void enrollment(EnrollmentFilter filter, boolean prefixed) {
        finishWhere();
        if (filter != null) {
            // We prefix this query for acounts, but for enrollments, it's a primary 
            // property, not a collection on the entity.
            if (filter == ENROLLED) {
                phrases.add((prefixed ? "enrollment." : "") + "withdrawnOn IS NULL");
            } else if (filter == WITHDRAWN) {
                phrases.add((prefixed ? "enrollment." : "") + "withdrawnOn IS NOT NULL");
            }
        }
    }
    // Native SQL, not HQL
    public void alternativeMatchedPairs(Map<String, DateTime> map, String varPrefix, String field1, String field2) {
        finishWhere();
        if (map != null && !map.isEmpty()) {
            phrases.add("AND (");
            int count = 0;
            for (Map.Entry<String, DateTime> entry : map.entrySet()) {
                String keyName = varPrefix + "Key" + count;
                String valName = varPrefix + "Val" + count;
                if (count++ > 0) {
                    phrases.add("OR");
                }
                String q = format("(%s = :%s AND %s = :%s)", field1, keyName, field2, valName);
                append(q, keyName, entry.getKey(), valName, entry.getValue().getMillis());
            }
            phrases.add(")");
        }
    }
    public String getQuery() {
        finishWhere();
        return BridgeUtils.SPACE_JOINER.join(phrases);
    }
    public Map<String,Object> getParameters() {
        return params;
    }
}
