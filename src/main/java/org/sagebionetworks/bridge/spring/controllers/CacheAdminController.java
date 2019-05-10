package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.services.CacheAdminService;

@CrossOrigin
@RestController
@RequestMapping("/v3/cache")
public class CacheAdminController extends BaseController {
    
    private CacheAdminService cacheAdminService;

    @Autowired
    final void setCacheAdminService(CacheAdminService cacheService) {
        this.cacheAdminService = cacheService;
    }
    
    @GetMapping
    public Set<String> listItems() throws Exception {
        getAuthenticatedSession(ADMIN);
        
        return cacheAdminService.listItems();
    }
    
    @DeleteMapping("{cacheKey}")
    public StatusMessage removeItem(@PathVariable String cacheKey) throws Exception {
        getAuthenticatedSession(ADMIN);
        
        cacheAdminService.removeItem(cacheKey);
        
        return new StatusMessage("Item removed from cache.");
    }
}
