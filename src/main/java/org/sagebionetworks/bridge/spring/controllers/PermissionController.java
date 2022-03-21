package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.models.permissions.PermissionDetail;
import org.sagebionetworks.bridge.services.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin
@RestController
public class PermissionController extends BaseController{
    
    private PermissionService permissionService;
    
    @Autowired
    final void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }
    
    @GetMapping("v1/permissions/{userId}")
    public List<PermissionDetail> getPermissionsForUser(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        return permissionService.getPermissionsForUser(appId, userId);
    }
    
    @GetMapping("v1/permissions/{entityType}/{entityId}")
    public List<PermissionDetail> getPermissionsForEntity(
            @PathVariable String entityType, @PathVariable String entityId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        return permissionService.getPermissionsForEntity(appId, entityType, entityId);
    }
    
    @PostMapping("v1/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionDetail createPermission() {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
    
        Permission permission = parseJson(Permission.class);
        permission.setGuid(null);
        permission.setAppId(appId);
        
        return permissionService.createPermission(appId, permission);
    }
    
    @PostMapping("v1/permissions/{guid}")
    public PermissionDetail updatePermission(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        Permission permission = parseJson(Permission.class);
        permission.setAppId(appId);
        permission.setGuid(guid);
        
        return permissionService.updatePermission(appId, permission);
    }
    
    @DeleteMapping("v1/permissions/{guid}")
    public StatusMessage deletePermission(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        permissionService.deletePermission(appId, guid);
        
        return new StatusMessage("Permission deleted.");
    }
}
