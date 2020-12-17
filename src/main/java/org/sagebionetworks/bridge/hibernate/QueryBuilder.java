package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.WITHDRAWN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

import com.google.common.base.Joiner;

/**
 * A helper class to manage construction of HQL strings.
 */
class QueryBuilder {
    
    private final List<String> phrases = new ArrayList<>();
    private final Map<String,Object> params = new HashMap<>();
    
    public void append(String phrase) {
        phrases.add(phrase);
    }
    public void append(String phrase, String key, Object value) {
        phrases.add(phrase);
        params.put(key, value);
    }
    public void append(String phrase, String key1, Object value1, String key2, Object value2) {
        phrases.add(phrase);
        params.put(key1, value1);
        params.put(key2, value2);
    }
    public void dataGroups(Set<String> dataGroups, String operator) {
        if (!BridgeUtils.isEmpty(dataGroups)) {
            int i = 0;
            List<String> clauses = new ArrayList<>();
            for (String oneDataGroup : dataGroups) {
                String varName = operator.replace(" ", "") + (++i);
                clauses.add(":"+varName+" "+operator+" elements(acct.dataGroups)");
                params.put(varName, oneDataGroup);
            }
            phrases.add("AND (" + Joiner.on(" AND ").join(clauses) + ")");
        }
    }
    public void adminOnly(Boolean isAdmin) {
        if (isAdmin != null) {
            if (TRUE.equals(isAdmin)) {
                phrases.add("AND size(acct.roles) > 0");
            } else {
                phrases.add("AND size(acct.roles) = 0");
            }
        }
    }
    public void orgMembership(String orgMembership) {
        if (orgMembership != null) {
            if ("<none>".equals(orgMembership.toLowerCase())) {
                phrases.add("AND acct.orgMembership IS NULL");
            } else {
                append("AND acct.orgMembership = :orgId", "orgId", orgMembership);
            }
        }
    }
    public void enrollment(EnrollmentFilter filter) {
        if (filter != null) {
            if (filter == ENROLLED) {
                phrases.add("AND withdrawnOn IS NULL");
            } else if (filter == WITHDRAWN) {
                phrases.add("AND withdrawnOn IS NOT NULL");
            }
        }
    }
    
    public String getQuery() {
        return BridgeUtils.SPACE_JOINER.join(phrases);
    }
    public Map<String,Object> getParameters() {
        return params;
    }
}
