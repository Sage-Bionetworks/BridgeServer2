package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.FileRevisionDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.files.FileRevisionId;

@Component
public class HibernateFileRevisionDao implements FileRevisionDao {
    static final String SELECT_COUNT = "SELECT count(fileGuid) "; 
    static final String FROM_REVISION = "FROM FileRevision WHERE fileGuid = :fileGuid";
    static final String ORDER_BY = " ORDER BY createdOn DESC";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<FileRevision> getFileRevisions(String guid, int offset, int pageSize) {
        checkNotNull(guid);
        
        String countQuery = SELECT_COUNT + FROM_REVISION;
        String getQuery = FROM_REVISION + ORDER_BY;

        Map<String,Object> params = ImmutableMap.of("fileGuid", guid);
        int count = hibernateHelper.queryCount(countQuery, params);
        
        List<FileRevision> revisions = hibernateHelper.queryGet(getQuery, params, offset, pageSize, FileRevision.class);
        
        return new PagedResourceList<>(revisions, count)
                .withRequestParam("offsetBy", offset)
                .withRequestParam("pageSize", pageSize);
    }
    
    @Override
    public Optional<FileRevision> getFileRevision(String guid, DateTime createdOn) {
        checkNotNull(guid);
        checkNotNull(createdOn);
        
        FileRevisionId revisionId = new FileRevisionId(guid, createdOn);
        
        FileRevision revision = hibernateHelper.getById(FileRevision.class, revisionId);
        if (revision == null) {
            return Optional.empty();
        }
        return Optional.of(revision);
    }

    @Override
    public void createFileRevision(FileRevision revision) {
        checkNotNull(revision);
        
        hibernateHelper.create(revision, null);
    }

    @Override
    public void updateFileRevision(FileRevision revision) {
        checkNotNull(revision);
        
        hibernateHelper.update(revision, null);
    }
    
    @Override
    public void deleteFileRevision(FileRevision revision) {
        checkNotNull(revision);
        
        FileRevisionId revisionId = new FileRevisionId(revision.getFileGuid(), revision.getCreatedOn());
        hibernateHelper.deleteById(FileRevision.class, revisionId);
    }
}