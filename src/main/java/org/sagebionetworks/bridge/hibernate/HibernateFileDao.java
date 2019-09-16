package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.FileDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.File;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

@Component
public class HibernateFileDao implements FileDao {
    private static final String SELECT_FILE = "SELECT file ";
    private static final String SELECT_COUNT = "SELECT count(guid) ";
    private static final String GET_ALL = "FROM File as file " + 
            "WHERE studyId = :studyId ORDER BY createdOn DESC";
    private static final String GET_ACTIVE = "FROM File as file " + 
            "WHERE studyId = :studyId AND deleted = 0 ORDER BY createdOn DESC";
    private static final String DELETE_STUDY = "DELETE FROM File WHERE studyId = :studyId";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public Optional<File> getFile(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        Map<String, Object> params = ImmutableMap.of("studyId", studyId.getIdentifier(), "guid", guid);

        List<File> files = hibernateHelper.queryGet(SELECT_FILE, params, null, null, File.class);
        return (files.isEmpty()) ? Optional.empty() : Optional.of(files.get(0));
    }

    @Override
    public PagedResourceList<File> getFiles(StudyIdentifier studyId, Integer offset, Integer limit,
            boolean includeDeleted) {
        checkNotNull(studyId);
        
        String countQuery = SELECT_COUNT + ((!includeDeleted) ? GET_ACTIVE : GET_ALL);
        String getQuery = SELECT_FILE + ((!includeDeleted) ? GET_ACTIVE : GET_ALL);        
        
        Map<String,Object> params = ImmutableMap.of("studyId", studyId.getIdentifier());
        int count = hibernateHelper.queryCount(countQuery, params);
        
        List<File> files = hibernateHelper.queryGet(getQuery, params, offset, limit, File.class);
        
        return new PagedResourceList<>(files, count)
                .withRequestParam("offset", offset)
                .withRequestParam("limit", limit)
                .withRequestParam("includeDeleted", includeDeleted);
    }

    @Override
    public File createFile(File file) {
        checkNotNull(file);
        
        hibernateHelper.create(file, null);
        return file;
    }

    @Override
    public File updateFile(File file) {
        checkNotNull(file);
        
        hibernateHelper.update(file, null);
        return file;
    }

    @Override
    public void deleteFilePermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        File file = hibernateHelper.getById(File.class, guid);
        if (file == null || !file.getStudyId().equals(studyId.getIdentifier())) {
            return;
        }
        hibernateHelper.deleteById(File.class, guid);
    }
    
    @Override
    public void deleteAllStudyFiles(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        hibernateHelper.query(DELETE_STUDY, ImmutableMap.of("studyId", studyId.getIdentifier()));   
    }
}
