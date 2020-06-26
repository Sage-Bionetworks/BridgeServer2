package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

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
}
