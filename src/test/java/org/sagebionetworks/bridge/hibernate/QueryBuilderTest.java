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
        
        assertEquals(builder.getQuery(), "AND (:inacctdatagroups1 IN elements(acct.dataGroups) AND "+
                ":inacctdatagroups2 IN elements(acct.dataGroups)) AND (:notinacctdatagroups1 NOT IN "+
                "elements(acct.dataGroups) AND :notinacctdatagroups2 NOT IN elements(acct.dataGroups))");
        assertEquals(builder.getParameters().get("inacctdatagroups1"), "A");
        assertEquals(builder.getParameters().get("inacctdatagroups2"), "B");
        assertEquals(builder.getParameters().get("notinacctdatagroups1"), "C");
        assertEquals(builder.getParameters().get("notinacctdatagroups2"), "D");
    }
}
