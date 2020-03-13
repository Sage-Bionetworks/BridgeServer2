package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.services.TagService;

@CrossOrigin
@RestController
public class TagController extends BaseController {
    
    private TagService tagService;
    
    private ViewCache viewCache;
    
    @Autowired
    final void setTagService(TagService tagService) {
        this.tagService = tagService;
    }
    
    @Resource(name = "genericViewCache")
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    @GetMapping(path="/v1/tags", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getTags() throws Exception {
        String json = viewCache.getView(CacheKey.tagList(), () -> {
            return tagService.getTags();
        });
        return json;
    }
    
    @PostMapping("/v1/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage addTag() {
        getAuthenticatedSession(SUPERADMIN);
        
        Tag tag = parseJson(Tag.class);

        // This method is provided so Sage admins can seed the tags we wish to use
        tagService.addTag(tag.getValue());
        
        return new StatusMessage("Tag created if not already present.");
    }
    
    @DeleteMapping("/v1/tags/{tag}")
    public StatusMessage deleteTag(@PathVariable String tag) {
        getAuthenticatedSession(SUPERADMIN);
        
        // This method is provided so integration tests can clean up junk tags
        tagService.deleteTag(tag);
        
        return new StatusMessage("Tag deleted.");
    }
}
