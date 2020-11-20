package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.files.FileRevisionId;

public class HibernateFileRevisionDaoTest extends Mockito {

    @Mock
    HibernateHelper mockHelper;
    
    @InjectMocks
    HibernateFileRevisionDao dao;
    
    @Captor
    ArgumentCaptor<FileRevisionId> revisionIdCaptor;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getFileRevisions() {
        List<FileRevision> list = ImmutableList.of(new FileRevision());
        when(mockHelper.queryGet(any(), any(), any(), any(), eq(FileRevision.class))).thenReturn(list);
        when(mockHelper.queryCount(any(), any())).thenReturn(100);
        
        PagedResourceList<FileRevision> results = dao.getFileRevisions(GUID, 10, 25);
        assertEquals(results.getItems().size(), 1);
        assertEquals(results.getRequestParams().get("offsetBy"), 10);
        assertEquals(results.getRequestParams().get("pageSize"), 25);
        assertEquals(results.getTotal(), new Integer(100));
        
        verify(mockHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(fileGuid) FROM FileRevision WHERE fileGuid = :fileGuid");
        assertEquals(paramsCaptor.getAllValues().get(0).get("fileGuid"), GUID);
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(25), eq(FileRevision.class));
        assertEquals(queryCaptor.getAllValues().get(1), "FROM FileRevision WHERE fileGuid = :fileGuid ORDER BY createdOn DESC");
        assertEquals(paramsCaptor.getAllValues().get(1).get("fileGuid"), GUID);
    }
    
    @Test
    public void getFileRevision() {
        FileRevision found = new FileRevision();
        when(mockHelper.getById(eq(FileRevision.class), any())).thenReturn(found);
        
        Optional<FileRevision> opt = dao.getFileRevision(GUID, TIMESTAMP);
        assertSame(opt.get(), found);
        
        verify(mockHelper).getById(eq(FileRevision.class), revisionIdCaptor.capture());
        FileRevisionId id = revisionIdCaptor.getValue();
        assertEquals(id.getFileGuid(), GUID);
        assertEquals(id.getCreatedOn(), TIMESTAMP);
    }

    @Test
    public void getFileRevisionNotFound() {
        Optional<FileRevision> opt = dao.getFileRevision(GUID, TIMESTAMP);
        assertFalse(opt.isPresent());
    }
    
    @Test
    public void createFileRevision() {
        FileRevision revision = new FileRevision();
                
        dao.createFileRevision(revision);
        
        verify(mockHelper).create(revision);
    }
    
    @Test
    public void updateFileRevision() {
        FileRevision revision = new FileRevision();
        
        dao.updateFileRevision(revision);
        
        verify(mockHelper).update(revision);
    }
    
    @Test
    public void deleteFileRevision() {
        FileRevision revision = new FileRevision();
        revision.setFileGuid(GUID);
        revision.setCreatedOn(TIMESTAMP);
        
        dao.deleteFileRevision(revision);
        
        verify(mockHelper).deleteById(eq(FileRevision.class), revisionIdCaptor.capture());
        
        FileRevisionId revId = revisionIdCaptor.getValue();
        assertEquals(revId.getFileGuid(), GUID);
        assertEquals(revId.getCreatedOn(), TIMESTAMP);
    }
}
