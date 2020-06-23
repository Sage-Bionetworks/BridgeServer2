package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtilsTest {
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void checkOrgMembershipSucceedsForSuperadmin() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipSucceedsForMatchingOrgId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnMismatch() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnNullOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipAndThrowSucceeds() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipAndThrowFails() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }
}
