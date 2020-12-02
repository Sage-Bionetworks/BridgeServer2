package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;

@Component
public class HibernateFileMetadataDao implements FileMetadataDao {
    static final String SELECT_COUNT = "SELECT count(guid) "; 
    static final String DELETE = "DELETE ";
    static final String FROM_FILE = "FROM FileMetadata WHERE appId = :appId";
    static final String WO_DELETED = " AND deleted = 0";
    static final String WITH_GUID = " AND guid = :guid";
    static final String ORDER_BY = " ORDER BY name";    
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public Optional<FileMetadata> getFile(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        Map<String, Object> params = ImmutableMap.of("appId", appId, "guid", guid);

        List<FileMetadata> files = hibernateHelper.queryGet(FROM_FILE+WITH_GUID, params, null, null, FileMetadata.class);
        return (files.isEmpty()) ? Optional.empty() : Optional.of(files.get(0));
    }

    @Override
    public PagedResourceList<FileMetadata> getFiles(String appId, int offset, int limit, boolean includeDeleted) {
        checkNotNull(appId);
        
        String countQuery = SELECT_COUNT+FROM_FILE + (!includeDeleted ? WO_DELETED : "");
        String getQuery = FROM_FILE + (!includeDeleted ? WO_DELETED : "") + ORDER_BY;

        Map<String,Object> params = ImmutableMap.of("appId", appId);
        int count = hibernateHelper.queryCount(countQuery, params);
        
        List<FileMetadata> files = hibernateHelper.queryGet(getQuery, params, offset, limit, FileMetadata.class);
        
        return new PagedResourceList<>(files, count)
                .withRequestParam("offsetBy", offset)
                .withRequestParam("pageSize", limit)
                .withRequestParam("includeDeleted", includeDeleted);
    }

    @Override
    public FileMetadata createFile(FileMetadata file) {
        checkNotNull(file);
        
        hibernateHelper.create(file);
        return file;
    }

    @Override
    public FileMetadata updateFile(FileMetadata file) {
        checkNotNull(file);
        
        hibernateHelper.update(file);
        return file;
    }

    @Override
    public void deleteFilePermanently(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        FileMetadata file = hibernateHelper.getById(FileMetadata.class, guid);
        if (file == null || !file.getAppId().equals(appId)) {
            return;
        }
        hibernateHelper.deleteById(FileMetadata.class, guid);
    }
    
    @Override
    public void deleteAllAppFiles(String appId) {
        checkNotNull(appId);
        
        hibernateHelper.query(DELETE+FROM_FILE, ImmutableMap.of("appId", appId));   
    }
}
