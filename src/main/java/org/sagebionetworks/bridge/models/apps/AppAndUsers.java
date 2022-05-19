package org.sagebionetworks.bridge.models.apps;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.Account;

public final class AppAndUsers implements BridgeEntity {
    private final List<String> adminIds;
    private final App app;
    private final List<Account> users;

    public AppAndUsers(@JsonProperty("adminIds") List<String> adminIds,
            @JsonAlias("study") @JsonProperty("app") App app, 
            @JsonProperty("users") List<Account> users) {
        this.adminIds = adminIds;
        this.app = app;
        this.users = users;
    }

    public List<String> getAdminIds() {
        return adminIds;
    }

    public List<Account> getUsers() {
        return users;
    }

    public App getApp() {
        return app;
    }
}
