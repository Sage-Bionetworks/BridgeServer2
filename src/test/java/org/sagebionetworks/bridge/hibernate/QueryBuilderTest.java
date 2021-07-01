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
        builder.append("phrase one=:one", "one", "valueForOne");
        builder.append("phrase two=:two three=:three", "two", "valueForTwo", "three", "valueForThree");
        builder.append("phrase four=:four five=:five six=:six", "four", "valueForFour",
                "five", "valueForFive", "six", "valueForSix");
        assertEquals(builder.getQuery(), "phrase phrase one=:one phrase two=:two three=:three phrase four=:four five=:five six=:six");
        assertEquals(builder.getParameters().get("one"), "valueForOne");
        assertEquals(builder.getParameters().get("two"), "valueForTwo");
        assertEquals(builder.getParameters().get("three"), "valueForThree");
        assertEquals(builder.getParameters().get("four"), "valueForFour");
        assertEquals(builder.getParameters().get("five"), "valueForFive");
        assertEquals(builder.getParameters().get("six"), "valueForSix");
    }
    
    @Test
    public void testDataGroups() {
        QueryBuilder builder = new QueryBuilder();
        builder.dataGroups(ImmutableSet.of("A", "B"), "IN");
        builder.dataGroups(ImmutableSet.of("C", "D"), "NOT IN");
        
        assertEquals(builder.getQuery(), "(:IN1 IN elements(acct.dataGroups) AND :IN2 IN " + 
                "elements(acct.dataGroups)) (:NOTIN1 NOT IN elements(acct.dataGroups) AND "+
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
        assertEquals(builder.getQuery(), "size(acct.roles) > 0");

        builder = new QueryBuilder();
        builder.adminOnly(false);
        assertEquals(builder.getQuery(), "size(acct.roles) = 0");
    }
    
    @Test
    public void testOrgMembership() {
        QueryBuilder builder = new QueryBuilder();
        builder.orgMembership(null);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.orgMembership("<NONE>");
        assertEquals(builder.getQuery(), "acct.orgMembership IS NULL");

        builder = new QueryBuilder();
        builder.orgMembership("foo");
        assertEquals(builder.getQuery(), "acct.orgMembership = :orgId");
        assertEquals(builder.getParameters().get("orgId"), "foo");
    }
    
    @Test
    public void enrollment() {
        QueryBuilder builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.ENROLLED, false);
        assertEquals(builder.getQuery(), "withdrawnOn IS NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.WITHDRAWN, false);
        assertEquals(builder.getQuery(), "withdrawnOn IS NOT NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.ALL, false);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.enrollment(null, false);
        assertEquals(builder.getQuery(), "");

        builder.enrollment(EnrollmentFilter.ENROLLED, true);
        assertEquals(builder.getQuery(), "enrollment.withdrawnOn IS NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.WITHDRAWN, true);
        assertEquals(builder.getQuery(), "enrollment.withdrawnOn IS NOT NULL");
        
        builder = new QueryBuilder();
        builder.enrollment(EnrollmentFilter.ALL, true);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        builder.enrollment(null, true);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void alternativeMatchedPairs() {
        Map<String, DateTime> map = ImmutableMap.of("event_1", CREATED_ON, "event_2", MODIFIED_ON);
        
        QueryBuilder builder = new QueryBuilder();
        builder.alternativeMatchedPairs(map, "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        
        assertEquals(builder.getQuery(), "AND ( (tm.sessionStartEventId = :eKey0 AND " +
                "ar.eventTimestamp = :eVal0) OR (tm.sessionStartEventId = :eKey1 AND " +
                "ar.eventTimestamp = :eVal1) )");
        assertEquals((Long)builder.getParameters().get("eVal0"),
                Long.valueOf(CREATED_ON.getMillis()));
        assertEquals((Long)builder.getParameters().get("eVal1"),
                Long.valueOf(MODIFIED_ON.getMillis()));
        assertEquals(builder.getParameters().get("eKey0"), "event_1");
        assertEquals(builder.getParameters().get("eKey1"), "event_2");
    }
    
    @Test
    public void alternativeMatchedPairs_nullSkipped() { 
        QueryBuilder builder = new QueryBuilder();
        builder.alternativeMatchedPairs(null, 
                "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        assertEquals(builder.getQuery(), "");
    }

    @Test
    public void alternativeMatchedPairs_emptySkipped() { 
        QueryBuilder builder = new QueryBuilder();
        builder.alternativeMatchedPairs(ImmutableMap.of(), 
                "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT * FROM TABLE");
        
        QueryBuilder where = builder.startWhere();
        where.append("foo1 = :bar1", "bar1", "baz1");
        where.append("foo2 = :bar2", "bar2", "baz2");
        builder.append("ORDER BY name");
        
        assertEquals(builder.getQuery(), "SELECT * FROM TABLE WHERE foo1 = :bar1 AND foo2 = :bar2 ORDER BY name");
        assertEquals(builder.getParameters().get("bar1"), "baz1");
        assertEquals(builder.getParameters().get("bar2"), "baz2");
    }
    
    @Test
    public void appendSkipsMissingValue1of1() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void appendSkipsMissingValue1of2() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", null, "key2", "value2");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void appendSkipsMissingValue2of2() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void appendSkipsMissingValue1of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", null, "key2", "value2", "key3", "value3");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void appendSkipsMissingValue2of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", null, "key3", "value3");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void appendSkipsMissingValue3of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", "value2", "key3", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void testLike() {
        QueryBuilder builder = new QueryBuilder();
        builder.like("phrase", "key", "value");
        
        assertEquals(builder.getQuery(), "phrase");
        assertEquals(builder.getParameters().get("key"), "%value%");
    }
    
    @Test
    public void likeSkipsNullValue() {
        QueryBuilder builder = new QueryBuilder();
        builder.like("phrase", "key", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void likeSkipsBlankValue() {
        QueryBuilder builder = new QueryBuilder();
        builder.like("phrase", "key", "");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void testPhone() {
        QueryBuilder builder = new QueryBuilder();
        builder.phone("(971) 248-6796");
        assertEquals(builder.getQuery(), "acct.phone.number LIKE :number");
        assertEquals(builder.getParameters().get("number"), "%9712486796%");
    }
    
    @Test
    public void phoneSkipsNullValue() {
        QueryBuilder builder = new QueryBuilder();
        builder.phone(null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void dataGroupsSkipsNullValue() {
        QueryBuilder builder = new QueryBuilder();
        builder.phone("---");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void dataGroupsSkipsEmptyValue() {
        QueryBuilder builder = new QueryBuilder();
        builder.dataGroups(null, "IN");
        assertEquals(builder.getQuery(), "");
        
    }
}
