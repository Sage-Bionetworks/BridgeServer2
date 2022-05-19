package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.OR;
import static org.sagebionetworks.bridge.models.StringSearchPosition.EXACT;
import static org.sagebionetworks.bridge.models.StringSearchPosition.INFIX;
import static org.sagebionetworks.bridge.models.StringSearchPosition.POSTFIX;
import static org.sagebionetworks.bridge.models.StringSearchPosition.PREFIX;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

public class QueryBuilderTest {

    @Test
    public void append() {
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
    public void whereClause_dataGroups() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.dataGroups(ImmutableSet.of("A", "B"), "IN");
        where.dataGroups(ImmutableSet.of("C", "D"), "NOT IN");
        
        assertEquals(builder.getQuery(), "WHERE (:IN1 IN elements(acct.dataGroups) AND :IN2 IN " + 
                "elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND "+
                ":NOTIN2 NOT IN elements(acct.dataGroups))");
        assertEquals(builder.getParameters().get("IN1"), "A");
        assertEquals(builder.getParameters().get("IN2"), "B");
        assertEquals(builder.getParameters().get("NOTIN1"), "C");
        assertEquals(builder.getParameters().get("NOTIN2"), "D");
    }
    
    @Test
    public void whereClause_booleanField() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.appendBoolean("admin", null);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.appendBoolean("admin", true);
        assertEquals(builder.getQuery(), "WHERE admin = 1");

        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.appendBoolean("admin", false);
        assertEquals(builder.getQuery(), "WHERE admin = 0");
    }
    
    @Test
    public void whereClause_orgMembershipRequired() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.orgMembershipRequired(null);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.orgMembershipRequired("<NONE>");
        assertEquals(builder.getQuery(), "WHERE acct.orgMembership IS NULL");

        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.orgMembershipRequired("foo");
        assertEquals(builder.getQuery(), "WHERE acct.orgMembership = :orgId");
        assertEquals(builder.getParameters().get("orgId"), "foo");
    }
    
    @Test
    public void whereClause_enrollment() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.ENROLLED, false);
        assertEquals(builder.getQuery(), "WHERE withdrawnOn IS NULL");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.WITHDRAWN, false);
        assertEquals(builder.getQuery(), "WHERE withdrawnOn IS NOT NULL");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.ALL, false);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(null, false);
        assertEquals(builder.getQuery(), "");

        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.ENROLLED, true);
        assertEquals(builder.getQuery(), "WHERE enrollment.withdrawnOn IS NULL");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.WITHDRAWN, true);
        assertEquals(builder.getQuery(), "WHERE enrollment.withdrawnOn IS NOT NULL");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(EnrollmentFilter.ALL, true);
        assertEquals(builder.getQuery(), "");
        
        builder = new QueryBuilder();
        where = builder.startWhere(AND);
        where.enrollment(null, true);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_alternativeMatchedPairs() {
        Map<String, DateTime> map = ImmutableMap.of("event_1", CREATED_ON, "event_2", MODIFIED_ON);
        
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.alternativeMatchedPairs(map, "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        
        assertEquals(builder.getQuery(), "WHERE ( (tm.sessionStartEventId = :eKey0 AND " +
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
    public void whereClause_alternativeMatchedPairsIsNull() { 
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.alternativeMatchedPairs(null, "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        assertEquals(builder.getQuery(), "");
    }

    @Test
    public void whereClause_alternativeMatchedPairsIsEmpty() { 
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.alternativeMatchedPairs(ImmutableMap.of(), "e", "tm.sessionStartEventId", "ar.eventTimestamp");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_append() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT * FROM TABLE");
        
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("foo1 = :bar1", "bar1", "baz1");
        where.append("foo2 = :bar2", "bar2", "baz2");
        builder.append("ORDER BY name");
        
        assertEquals(builder.getQuery(), "SELECT * FROM TABLE WHERE foo1 = :bar1 AND foo2 = :bar2 ORDER BY name");
        assertEquals(builder.getParameters().get("bar1"), "baz1");
        assertEquals(builder.getParameters().get("bar2"), "baz2");
    }
    
    @Test
    public void whereClause_appendNoValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("a = :a", "a", null);
    }
    
    @Test
    public void whereClause_orPredicate() { 
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(OR);
        where.appendRequired("appId = :appId", "appId", "appIdValue");
        where.appendRequired("studyId = :studyId", "studyId", "studyIdValue");
        where.adherenceRecordType(SESSION);
        where.dataGroups(USER_DATA_GROUPS, "IN");
        
        assertEquals(builder.getQuery(), "WHERE appId = :appId AND studyId = "
                + ":studyId AND (tm.assessmentGuid IS NULL OR (:IN1 IN elements("
                + "acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)))");
    }
    
    @Test
    public void whereClause_andPredicate() { 
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.appendRequired("appId = :appId", "appId", "appIdValue");
        where.appendRequired("studyId = :studyId", "studyId", "studyIdValue");
        where.adherenceRecordType(SESSION);
        where.dataGroups(USER_DATA_GROUPS, "IN");
        
        assertEquals(builder.getQuery(), "WHERE appId = :appId AND studyId = "
                + ":studyId AND tm.assessmentGuid IS NULL AND (:IN1 IN elements("
                + "acct.dataGroups) AND :IN2 IN elements(acct.dataGroups))");
    }
    
    @Test
    public void append_phraseOnly() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("from Table");
        
        assertEquals(builder.getQuery(), "from Table");
    }
    
    @Test
    public void append_missingValue1of1() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void append_missingValue1of2() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", null, "key2", "value2");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void append_missingValue2of2() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void append_missingValue1of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", null, "key2", "value2", "key3", "value3");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void append_missingValue2of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", null, "key3", "value3");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void append_missingValue3of3() {
        QueryBuilder builder = new QueryBuilder();
        builder.append("phrase", "key1", "value1", "key2", "value2", "key3", null);
        assertEquals(builder.getQuery(), "");
    }

    @Test
    public void whereClause_likeWithPrefix() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(PREFIX, "phrase", "key", "value");
        
        assertEquals(builder.getQuery(), "WHERE phrase");
        assertEquals(builder.getParameters().get("key"), "value%");
    }

    @Test
    public void whereClause_likeWithInfix() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(INFIX, "phrase", "key", "value");
        
        assertEquals(builder.getQuery(), "WHERE phrase");
        assertEquals(builder.getParameters().get("key"), "%value%");
    }
    
    @Test
    public void whereClause_likeWithPostfix() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(POSTFIX, "phrase", "key", "value");
        
        assertEquals(builder.getQuery(), "WHERE phrase");
        assertEquals(builder.getParameters().get("key"), "%value");
    }
    
    @Test
    public void whereClause_likeWithExact() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(EXACT, "phrase", "key", "value");
        
        assertEquals(builder.getQuery(), "WHERE phrase");
        assertEquals(builder.getParameters().get("key"), "value");
    }
    
    @Test
    public void whereClause_likeWithNullValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(INFIX, "phrase", "key", null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_likeWithBlankValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.like(INFIX, "phrase", "key", "");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_phone() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.phone(INFIX, "(971) 248-6796");
        assertEquals(builder.getQuery(), "WHERE acct.phone.number LIKE :number");
        assertEquals(builder.getParameters().get("number"), "%9712486796%");
    }
    
    @Test
    public void whereClause_phoneWithNullValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.phone(INFIX, null);
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_dataGroupsWithNullValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.phone(INFIX, "---");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_dataGroupsWithEmptyValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.dataGroups(null, "IN");
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_appendWithMissingValue() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.appendRequired("from Table", "foo", null);
        
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void startWhere_withPredicate() { 
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(OR);
        where.append("a = :a", "a", "a");
        where.append("b = :b", "b", "b");
        
        assertEquals(builder.getQuery(), "WHERE a = :a OR b = :b");
    }
    
    @Test
    public void whereClause_adherenceRecordTypeIsNull() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.adherenceRecordType(null);
        
        assertEquals(builder.getQuery(), "");
    }
    
    @Test
    public void whereClause_adherenceRecordTypeIsSession() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.adherenceRecordType(SESSION);
        
        assertEquals(builder.getQuery(), "WHERE tm.assessmentGuid IS NULL");
    }

    @Test
    public void whereClause_adherenceRecordTypeIsAssessment() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.adherenceRecordType(ASSESSMENT);
        
        assertEquals(builder.getQuery(), "WHERE tm.assessmentGuid IS NOT NULL");
    }
    
    @Test
    public void whereClause_appendPhraseOnly() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("a IS NOT NULL");
        
        assertEquals(builder.getQuery(), "WHERE a IS NOT NULL");
    }
    
    @Test
    public void whereClause_appendMissingValue1of1() {
        QueryBuilder builder = new QueryBuilder();
        WhereClauseBuilder where = builder.startWhere(AND);
        where.append("a = :a", "a", null);
        
        assertEquals(builder.getQuery(), "");
    }
}
