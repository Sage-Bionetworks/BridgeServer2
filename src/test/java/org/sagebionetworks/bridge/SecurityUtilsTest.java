package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.APP_ID;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.util.BridgeCollectors;

public class SecurityUtilsTest {
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void ownershipOwnerIdIsBlank() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        SecurityUtils.checkOwnership(APP_ID, null);
    }
    
    @Test
    public void ownershipGlobalUser() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        SecurityUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test
    public void ownershipScopedUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(OWNER_ID)).build());
        SecurityUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void ownershipScopedUserOrgIdIsMissing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("notValidOwner")).build());
        SecurityUtils.checkOwnership(APP_ID, OWNER_ID);
    }
    
    @Test
    public void substudyIdsVisibleToCallerFilters() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);

        Set<String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();

        assertEquals(visibles, ImmutableSet.of("substudyA", "substudyB"));
    }

    @Test
    public void substudyIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);

        Set<String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();

        assertEquals(visibles, ImmutableSet.of("substudyA", "substudyB", "substudyC"));
    }

    @Test
    public void substudyIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        Set<String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getSubstudyIdsVisibleToCaller();

        assertEquals(visibles, ImmutableSet.of());
    }

    @Test
    public void substudyIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        Set<String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(null).getSubstudyIdsVisibleToCaller();

        assertEquals(visibles, ImmutableSet.of());
    }

    @Test
    public void externalIdsVisibleToCaller() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);

        Map<String, String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();

        assertEquals(visibles, ImmutableMap.of("substudyA", "extA", "substudyB", "extB"));
    }

    @Test
    public void externalIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);

        Map<String, String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();

        assertEquals(visibles, ImmutableMap.of("substudyA", "extA", "substudyB", "extB", "substudyC", "extC"));
    }

    @Test
    public void externalIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        Map<String, String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getExternalIdsVisibleToCaller();

        assertEquals(visibles, ImmutableMap.of());
    }

    @Test
    public void externalIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        Map<String, String> visibles = SecurityUtils.substudyAssociationsVisibleToCaller(null)
                .getExternalIdsVisibleToCaller();

        assertEquals(visibles, ImmutableMap.of());
    }

    @Test
    public void filterForSubstudyAccountRemovesUnsharedSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(substudies).build());

        Account account = SecurityUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(account.getAccountSubstudies().size(), 1);
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getSubstudyId(), "substudyA");

        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyAccountReturnsAllUnsharedSubstudyIdsForNonSubstudyCaller() {
        Account account = SecurityUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(account.getAccountSubstudies().size(), 2);
    }

    @Test
    public void filterForSubstudyAccountNullReturnsNull() {
        assertNull(SecurityUtils.filterForSubstudy((Account) null));
    }

    @Test
    public void filterForSubstudyAccountNoContextReturnsNormalAccount() {
        assertNotNull(SecurityUtils.filterForSubstudy(getAccountWithSubstudy()));
    }

    @Test
    public void filterForSubstudyAccountNoContextReturnsSubstudyAccount() {
        assertNotNull(SecurityUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyAccountWithSubstudiesHidesNormalAccount() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(SecurityUtils.filterForSubstudy(getAccountWithSubstudy()));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyAccountWithMatchingSubstudiesReturnsSubstudyAccount() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(SecurityUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyAccountWithMismatchedSubstudiesHidesSubstudyAccount() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("notSubstudyA")).build());
        assertNull(SecurityUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyExtIdNullReturnsNull() {
        assertNull(SecurityUtils.filterForSubstudy((ExternalIdentifier) null));
    }

    @Test
    public void filterForSubstudyExtIdNoContextReturnsExtId() {
        assertNotNull(SecurityUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyExtIdWithSubstudiesHidesNormalExtId() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(SecurityUtils.filterForSubstudy(getExternalIdentifierWithSubstudy(null)));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyExtIdWithMatchingSubstudiesReturnsExtId() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(SecurityUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyExtIdWithMismatchedSubstudiesHidesExtId() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(SecurityUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyB")));
    }

    @Test
    public void isInRoleMethodsAreNullSafe() {
        assertFalse(SecurityUtils.isInRole(null, (Roles) null));
        assertFalse(SecurityUtils.isInRole(null, (Set<Roles>) null));
    }

    @Test
    public void isInRoleForSuperadminMatchesEverything() {
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), DEVELOPER));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), RESEARCHER));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ADMIN));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), WORKER));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(ADMIN)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(WORKER)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }

    @Test
    public void isInRole() {
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), DEVELOPER));
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), RESEARCHER));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ADMIN));
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), WORKER));
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER)));
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(ADMIN)));
        assertFalse(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(WORKER)));
        assertTrue(SecurityUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }

    private Account getAccountWithSubstudy(String... substudyIds) {
        Account account = Account.create();
        Set<AccountSubstudy> accountSubstudies = Arrays.asList(substudyIds).stream().map((id) -> {
            return AccountSubstudy.create("studyId", id, "accountId");
        }).collect(BridgeCollectors.toImmutableSet());
        account.setAccountSubstudies(accountSubstudies);
        return account;
    }

    private ExternalIdentifier getExternalIdentifierWithSubstudy(String substudyId) {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_STUDY, "identifier");
        id.setSubstudyId(substudyId);
        return id;
    }
}
