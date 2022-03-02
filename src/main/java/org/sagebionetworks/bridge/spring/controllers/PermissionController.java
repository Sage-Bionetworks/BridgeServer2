package org.sagebionetworks.bridge.spring.controllers;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.permissions.Permission;
import org.sagebionetworks.bridge.services.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.sagebionetworks.bridge.Roles.ADMIN;

@CrossOrigin
@RestController
public class PermissionController extends BaseController{
    
    private PermissionService permissionService;
    
    @Autowired
    final void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }
    
    @GetMapping("v1/permissions/{userId}")
    public Set<Permission> getPermissionsForUser(@PathVariable String userId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        return permissionService.getPermissionsForUser(appId, userId);
    }
    
    @GetMapping("v1/permissions/{permissionType}/{objectId}")
    public Set<Permission> getPermissionsForObject(
            @PathVariable String permissionType, @PathVariable String objectId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        return permissionService.getPermissionsForObject(appId, permissionType, objectId);
    }
    
    @PostMapping("v1/permissions")
    public StatusMessage createPermission() {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
    
        Permission permission = parseJson(Permission.class);
        permission.setAppId(appId);
        
        permissionService.createPermission(appId, permission);
        
        return new StatusMessage("Permission created.");
    }
    
    @PostMapping("v1/permissions/{guid}")
    public StatusMessage updatePermission(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        Permission permission = parseJson(Permission.class);
        permission.setAppId(appId);
        
        permissionService.updatePermission(appId, permission);
        
        return new StatusMessage("Permission updated.");
    }
    
    @DeleteMapping("v1/permissions/{guid}")
    public StatusMessage deletePermission(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        String appId = session.getAppId();
        
        permissionService.deletePermission(appId, guid);
        
        return new StatusMessage("Permission deleted.");
    }
}
