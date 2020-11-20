package org.sagebionetworks.bridge.spring.controllers;

public class MembershipControllerTest {
/*
    
    @Test
    public void getMembers() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(ImmutableList.of(), 0);
        when(mockService.getMembers(eq(TEST_APP_ID), eq(IDENTIFIER), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withEmailFilter("email").build();
        mockRequestBody(mockRequest, search);
        
        PagedResourceList<AccountSummary> retValue = controller.getMembers(IDENTIFIER);
        assertSame(retValue, page);
        
        verify(mockService).getMembers(eq(TEST_APP_ID), eq(IDENTIFIER), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getEmailFilter(), "email");
    }
    
    @Test
    public void addMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.addMember(IDENTIFIER, USER_ID);
        
        verify(mockService).addMember(eq(TEST_APP_ID), eq(IDENTIFIER), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(USER_ID, accountId.getId());
    }

    @Test
    public void removeMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.removeMember(IDENTIFIER, USER_ID);
        
        verify(mockService).removeMember(eq(TEST_APP_ID), eq(IDENTIFIER), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(USER_ID, accountId.getId());
    }
    
    @Test
    public void getUnassignedAdmins() throws Exception {
        doReturn(session).when(controller).getAdministrativeSession();
        
        AccountSummarySearch initial = new AccountSummarySearch.Builder()
            .withOrgMembership("something-to-be-overridden")
            .withEmailFilter("sagebase.org").build();
        mockRequestBody(mockRequest, initial);
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        PagedResourceList<AccountSummary> results = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockParticipantService.getPagedAccountSummaries(eq(app), any())).thenReturn(results);
        
        PagedResourceList<AccountSummary> retValue = controller.getUnassignedAdmins();
        assertSame(retValue, results);
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());
        assertEquals("sagebase.org", searchCaptor.getValue().getEmailFilter());
        assertEquals("<none>", searchCaptor.getValue().getOrgMembership());
        assertTrue(searchCaptor.getValue().isAdminOnly());
    } */
}
