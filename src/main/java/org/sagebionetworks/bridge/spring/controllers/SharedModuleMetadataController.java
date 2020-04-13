package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.services.SharedModuleMetadataService;

@CrossOrigin
@RestController
public class SharedModuleMetadataController extends BaseController {
    static final StatusMessage DELETED_MSG = new StatusMessage("Metadata has been deleted.");
    private SharedModuleMetadataService metadataService;

    /** Shared Module Metadata Service, configured by Spring. */
    @Autowired
    final void setMetadataService(SharedModuleMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /** Creates the specified metadata object. */
    @PostMapping("/v3/sharedmodules/metadata")
    @ResponseStatus(HttpStatus.CREATED)
    public SharedModuleMetadata createMetadata() {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(SharedModuleMetadata.class);
        return metadataService.createMetadata(metadata);
    }

    /** Deletes all metadata for module versions with the given ID. */
    @DeleteMapping("/v3/sharedmodules/metadata/{id}/versions")
    public StatusMessage deleteMetadataByIdAllVersions(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = verifySharedDeveloperOrAdminAccess();
        
        if (physical && session.isInRole(Roles.ADMIN)) {
            metadataService.deleteMetadataByIdAllVersionsPermanently(id);    
        } else {
            metadataService.deleteMetadataByIdAllVersions(id);
        }
        return DELETED_MSG;
    }

    /** Deletes metadata for the specified module ID and version. */
    @DeleteMapping("/v3/sharedmodules/metadata/{id}/versions/{version}")
    public StatusMessage deleteMetadataByIdAndVersion(@PathVariable String id, @PathVariable int version,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = verifySharedDeveloperOrAdminAccess();
        
        if (physical && session.isInRole(Roles.ADMIN)) {
            metadataService.deleteMetadataByIdAndVersionPermanently(id, version);    
        } else {
            metadataService.deleteMetadataByIdAndVersion(id, version);
        }
        return DELETED_MSG;
    }

    /** Gets metadata for the specified version of the specified module. */
    @GetMapping("/v3/sharedmodules/metadata/{id}/versions/{version}")
    public SharedModuleMetadata getMetadataByIdAndVersion(@PathVariable String id, @PathVariable int version) {
        return metadataService.getMetadataByIdAndVersion(id, version);
    }

    /** Gets metadata for the latest version of the specified module. */
    @GetMapping("/v3/sharedmodules/metadata/{id}")
    public SharedModuleMetadata getMetadataByIdLatestVersion(@PathVariable String id) {
        return metadataService.getMetadataByIdLatestVersion(id);
    }

    private String createQuery(String name, String notes, Map<String,Object> parameters) {
        List<String> clauses = new ArrayList<>();
        if (StringUtils.isNotBlank(name)) {
            clauses.add("name like :name");
            parameters.put("name", "%"+name+"%");
        }
        if (StringUtils.isNotBlank(notes)) {
            clauses.add("notes like :notes");
            parameters.put("notes", "%"+notes+"%");
        }
        return Joiner.on(" or ").join(clauses);
    }
    
    /**
     * Queries module metadata using the set of given parameters. See
     * {@link SharedModuleMetadataService#queryAllMetadata} for details. This method does not
     * require authentication.
     */
    @GetMapping("/v3/sharedmodules/metadata")
    public ResourceList<SharedModuleMetadata> queryAllMetadata(
            @RequestParam(name = "mostrecent", defaultValue = "true") boolean mostRecent,
            @RequestParam(defaultValue = "false") boolean published, @RequestParam(required = false) String name,
            @RequestParam(required = false) String notes, @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        // Parse inputs
        Set<String> tagSet = parseTags(tags);

        Map<String,Object> parameters = Maps.newHashMap();
        String where = createQuery(name, notes, parameters);
        
        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryAllMetadata(mostRecent, published, where,
                parameters, tagSet, includeDeleted);
        return new ResourceList<>(metadataList);
    }

    /** Similar to queryAllMetadata, except this only queries on module versions of the specified ID. */
    @GetMapping("/v3/sharedmodules/metadata/{id}/versions")
    public ResourceList<SharedModuleMetadata> queryMetadataById(@PathVariable String id,
            @RequestParam(name = "mostrecent", defaultValue = "false") boolean mostRecent,
            @RequestParam(defaultValue = "false") boolean published, @RequestParam(required = false) String name,
            @RequestParam(required = false) String notes, @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        // Parse inputs
        Set<String> tagSet = parseTags(tags);

        Map<String,Object> parameters = Maps.newHashMap();
        String where = createQuery(name, notes, parameters);
        
        // Call service
        List<SharedModuleMetadata> metadataList = metadataService.queryMetadataById(id, mostRecent, published, where,
                parameters, tagSet, includeDeleted);
        return new ResourceList<>(metadataList);
    }

    // Helper method to parse tags from URL query params. Package-scoped for unit tests.
    static Set<String> parseTags(String tagsString) {
        // Parse set of tags from a comma-delimited list.
        Set<String> tagSet = new HashSet<>();
        if (StringUtils.isNotBlank(tagsString)) {
            String[] tagsSplit = tagsString.split(",");
            Collections.addAll(tagSet, tagsSplit);
        }
        return tagSet;
    }

    /** Updates metadata for the specified module version. */
    @PostMapping("/v3/sharedmodules/metadata/{id}/versions/{version}")
    public SharedModuleMetadata updateMetadata(@PathVariable String id, @PathVariable int version) {
        verifySharedDeveloperAccess();
        SharedModuleMetadata metadata = parseJson(SharedModuleMetadata.class);
        return metadataService.updateMetadata(id, version, metadata);
    }

    // Helper method to verify caller permissions for write operations. You need to be a developer in the "shared"
    // study (the study for the Shared Module Library).
    private UserSession verifySharedDeveloperAccess() {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);
        String studyId = session.getStudyIdentifier();
        if (!SHARED_APP_ID.equals(studyId)) {
            throw new UnauthorizedException();
        }
        return session;
    }
    
    private UserSession verifySharedDeveloperOrAdminAccess() {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER, Roles.ADMIN);
        String studyId = session.getStudyIdentifier();
        if (!SHARED_APP_ID.equals(studyId)) {
            throw new UnauthorizedException();
        }
        return session;
    }
}
