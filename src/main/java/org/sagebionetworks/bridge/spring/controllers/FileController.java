package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import org.joda.time.DateTime;
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

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.services.FileService;

@CrossOrigin
@RestController
public class FileController extends BaseController {
    
    static final StatusMessage DELETE_MSG = new StatusMessage("File metadata and revisions deleted.");
    static final StatusMessage UPLOAD_FINISHED_MSG = new StatusMessage("File revision upload completed.");

    private FileService fileService;
    
    @Autowired
    final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }
    
    @GetMapping("/v3/files")
    public ResourceList<FileMetadata> getFiles(@RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize, 
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAdministrativeSession();
        
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean includeDeletedBool = Boolean.valueOf(includeDeleted);
        
        return fileService.getFiles(session.getAppId(), offsetInt, pageSizeInt, includeDeletedBool);
    }
    
    @PostMapping("/v3/files")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidVersionHolder createFile() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        FileMetadata file = parseJson(FileMetadata.class);
        FileMetadata updated = fileService.createFile(session.getAppId(), file);
        
        return new GuidVersionHolder(updated.getGuid(), Long.valueOf(updated.getVersion()));
    }
    
    @GetMapping("/v3/files/{guid}")
    public FileMetadata getFile(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        return fileService.getFile(session.getAppId(), guid);
    }
    
    @PostMapping("/v3/files/{guid}")
    public GuidVersionHolder updateFile(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        FileMetadata file = parseJson(FileMetadata.class);
        file.setGuid(guid);
        FileMetadata updated = fileService.updateFile(session.getAppId(), file);
        
        return new GuidVersionHolder(updated.getGuid(), Long.valueOf(updated.getVersion()));
    }
    
    @DeleteMapping("/v3/files/{guid}")
    public StatusMessage deleteFile(@PathVariable String guid,
            @RequestParam(defaultValue = "false") String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            fileService.deleteFilePermanently(session.getAppId(), guid);
        } else {
            fileService.deleteFile(session.getAppId(), guid);
        }
        return DELETE_MSG;
    }
    
    @GetMapping("/v3/files/{guid}/revisions")
    public PagedResourceList<FileRevision> getFileRevisions(@PathVariable String guid,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        int offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return fileService.getFileRevisions(session.getAppId(), guid, offsetInt, pageSizeInt);
    }
    
    @PostMapping("/v3/files/{guid}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public FileRevision createFileRevision(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        // The only information that can really be submitted is a description
        FileRevision revision = parseJson(FileRevision.class);
        revision.setFileGuid(guid);
        
        return fileService.createFileRevision(session.getAppId(), revision);
    }
    
    @GetMapping("/v3/files/{guid}/revisions/{createdOn}")
    public FileRevision getFileRevision(@PathVariable String guid, @PathVariable("createdOn") String createdOnStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        DateTime createdOn = DateTime.parse(createdOnStr);
        
        // Verify access to this file stream.
        fileService.getFile(session.getAppId(), guid);
        
        return fileService.getFileRevision(guid, createdOn)
            .orElseThrow(() -> new EntityNotFoundException(FileRevision.class));
    }
    
    @PostMapping("/v3/files/{guid}/revisions/{createdOn}")
    public StatusMessage finishFileRevision(@PathVariable String guid, @PathVariable("createdOn") String createdOnStr) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        DateTime createdOn = DateTime.parse(createdOnStr);
        
        fileService.finishFileRevision(session.getAppId(), guid, createdOn);
        return UPLOAD_FINISHED_MSG;
    }
}
