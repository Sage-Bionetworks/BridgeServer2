package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.PermissionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.permissions.EntityType;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class PermissionService {
    
    private PermissionDao permissionDao;
    
    private AccountService accountService;
    
    @Autowired
    final void setPermissionDao(PermissionDao permissionDao) {
        this.permissionDao = permissionDao;
    }
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    public PermissionDetail createPermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
        
        permission.setGuid(BridgeUtils.generateGuid());
        
        // TODO: validate permission
        
        Permission createdPermission = permissionDao.createPermission(appId, permission);
        
        return getPermissionDetail(appId, createdPermission);
    }
    
    public PermissionDetail updatePermission(String appId, Permission permission) {
        checkArgument(isNotBlank(appId));
        checkNotNull(permission);
    
        // TODO: validate permission
        
        Permission updatedPermission = permissionDao.updatePermission(appId, permission);
    
        return getPermissionDetail(appId, updatedPermission);
    }
    
    public Set<Permission> getPermissionsForUser(String appId, String userId) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
        
        return permissionDao.getPermissionsForUser(appId, userId);
    }
    
    public Set<PermissionDetail> getPermissionsForEntity(String appId, String entityType, String entityId) {
        checkArgument(isNotBlank(appId));
        checkNotNull(entityType);
        checkArgument(isNotBlank(entityId));
        
        Set<Permission> permissions = permissionDao.getPermissionsForEntity(appId, EntityType.valueOf(entityType), entityId);
        
        Set<PermissionDetail> permissionDetails = new HashSet<>();
        for (Permission permission : permissions) {
            permissionDetails.add(getPermissionDetail(appId, permission));
        }
        
        return permissionDetails;
    }
    
    public void deletePermission(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        permissionDao.deletePermission(appId, guid);
    }
    
    private PermissionDetail getPermissionDetail(String appId, Permission permission) {
        AccountId accountId = AccountId.forId(appId, permission.getUserId());
        Account userAccount = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        AccountRef userAccountRef = new AccountRef(userAccount);
        
        return new PermissionDetail(permission, userAccountRef);
    }
}
