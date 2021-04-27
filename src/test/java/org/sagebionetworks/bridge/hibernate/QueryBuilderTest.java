package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

public class QueryBuilderTest {

    @Test
    public void testSimpleParams() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase");
        builder.append("phrase two=:two", "two", "valueForTwo");
        builder.append("phrase three=:three four=:four", "three", "valueForThree", "four", "valueForFour");

        assertEquals(builder.getQuery(), "phrase phrase two=:two phrase three=:three four=:four");
        assertEquals(builder.getParameters().get("two"), "valueForTwo");
        assertEquals(builder.getParameters().get("three"), "valueForThree");
        assertEquals(builder.getParameters().get("four"), "valueForFour");
    }
    
    @Test
    public void testDataGroups() {
        QueryBuilder builder = new QueryBuilder();
        builder.dataGroups(ImmutableSet.of("A", "B"), "IN");
        builder.dataGroups(ImmutableSet.of("C", "D"), "NOT IN");
        
        assertEquals(builder.getQuery(), "AND (:IN1 IN elements(acct.dataGroups) AND :IN2 IN " + 
                "elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND "+
                ":NOTIN2 NOT IN elements(acct.dataGroups))");
        assertEquals(builder.getParameters().get("IN1"), "A");
        assertEquals(builder.getParameters().get("IN2"), "B");
        assertEquals(builder.getParameters().get("NOTIN1"), "C");
        assertEquals(builder.getParameters().get("NOTIN2"), "D");
    }
    
    @Test
    public void testAdmin() {
        QueryBuilder builder = new QueryBuilder();
        builder.adminOnly(null);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.adminOnly(true);
        assertEquals(builder.getQuery(), "AND size(acct.roles) > 0");

        builder = new QueryBuilder();
        builder.adminOnly(false);
        assertEquals(builder.getQuery(), "AND size(acct.roles) = 0");
    }
    
    @Test
    public void testOrgMembership() {
        QueryBuilder builder = new QueryBuilder();
        builder.orgMembership(null);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.orgMembership("<NONE>");
        assertEquals(builder.getQuery(), "AND acct.orgMembership IS NULL");

        builder = new QueryBuilder();
        builder.orgMembership("foo");
        assertEquals(builder.getQuery(), "AND acct.orgMembership = :orgId");
        assertEquals(builder.getParameters().get("orgId"), "foo");
    }
    
    @Test
    public void enrollment() {
        QueryBuilder builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.ENROLLED);
        assertEquals(builder.getQuery(), "AND withdrawnOn IS NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.WITHDRAWN);
        assertEquals(builder.getQuery(), "AND withdrawnOn IS NOT NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.ALL);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.enrollment(null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void eventTimestamps() {
        Map<String, DateTime> map = ImmutableMap.of("event_1", CREATED_ON, "event_2", MODIFIED_ON);
        
        QueryBuilder builder = new QueryBuilder();
        builder.eventTimestamps(map);
        
        assertEquals("AND ( (tm.sessionStartEventId = :evt0 AND ar.eventTimestamp = :ts0) OR (tm.sessionStartEventId = :evt1 AND ar.eventTimestamp = :ts1) )", 
                builder.getQuery());
        assertEquals(Long.valueOf(CREATED_ON.getMillis()), 
                (Long)builder.getParameters().get("ts0"));
        assertEquals(Long.valueOf(MODIFIED_ON.getMillis()), 
                (Long)builder.getParameters().get("ts1"));
        assertEquals("event_1", builder.getParameters().get("evt0"));
        assertEquals("event_2", builder.getParameters().get("evt1"));
    }
}
