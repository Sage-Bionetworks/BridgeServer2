package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.DELETE;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.FROM_FILE;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.ORDER_BY;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.SELECT_COUNT;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.WITH_GUID;
import static org.sagebionetworks.bridge.hibernate.HibernateFileMetadataDao.WO_DELETED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

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
import org.sagebionetworks.bridge.models.files.FileMetadata;

public class HibernateFileMetadataDaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHibernateHelper;
    
    @InjectMocks
    HibernateFileMetadataDao dao;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getFile() {
        FileMetadata metadata = new FileMetadata();
        when(mockHibernateHelper.queryGet(eq(FROM_FILE + WITH_GUID), paramsCaptor.capture(), isNull(), isNull(),
                eq(FileMetadata.class))).thenReturn(ImmutableList.of(metadata));
        
        Optional<FileMetadata> returned = dao.getFile(TEST_APP_ID, GUID);
        assertSame(returned.get(), metadata);
        
        Map<String,Object> map = paramsCaptor.getValue();
        assertEquals(map.get("appId"), TEST_APP_ID);
        assertEquals(map.get("guid"), GUID);
    }

    @Test
    public void getFileMissing() {
        when(mockHibernateHelper.queryGet(eq(FROM_FILE + WITH_GUID), paramsCaptor.capture(), isNull(), isNull(),
                eq(FileMetadata.class))).thenReturn(ImmutableList.of());
        
        Optional<FileMetadata> returned = dao.getFile(TEST_APP_ID, GUID);
        assertFalse(returned.isPresent());
    }
    
    @Test
    public void getFilesExcludeDeleted() {
        when(mockHibernateHelper.queryCount(eq(SELECT_COUNT + FROM_FILE + WO_DELETED), paramsCaptor.capture())).thenReturn(10);
        when(mockHibernateHelper.queryGet(eq(FROM_FILE + WO_DELETED + ORDER_BY), paramsCaptor.capture(), eq(5), eq(25),
                eq(FileMetadata.class))).thenReturn(ImmutableList.of(new FileMetadata(), new FileMetadata()));

        PagedResourceList<FileMetadata> returned = dao.getFiles(TEST_APP_ID, 5, 25, false);
        assertEquals(returned.getItems().size(), 2);
        assertEquals(returned.getTotal(), Integer.valueOf(10));
        assertEquals(returned.getRequestParams().get("offsetBy"), 5);
        assertEquals(returned.getRequestParams().get("pageSize"), 25);
        assertFalse((Boolean)returned.getRequestParams().get("includeDeleted"));
        
        verify(mockHibernateHelper).queryCount(eq(SELECT_COUNT + FROM_FILE + WO_DELETED), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(eq(FROM_FILE + WO_DELETED + ORDER_BY), paramsCaptor.capture(), eq(5), eq(25),
                eq(FileMetadata.class));
        
        Map<String,Object> map = paramsCaptor.getValue();
        assertEquals(map.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getFilesIncludeDeleted() {
        when(mockHibernateHelper.queryCount(eq(SELECT_COUNT + FROM_FILE), paramsCaptor.capture())).thenReturn(10);
        when(mockHibernateHelper.queryGet(eq(FROM_FILE + ORDER_BY), paramsCaptor.capture(), eq(5), eq(25),
                eq(FileMetadata.class))).thenReturn(ImmutableList.of(new FileMetadata(), new FileMetadata()));

        PagedResourceList<FileMetadata> returned = dao.getFiles(TEST_APP_ID, 5, 25, true);
        assertEquals(returned.getItems().size(), 2);
        assertEquals(returned.getTotal(), Integer.valueOf(10));
        assertTrue((Boolean)returned.getRequestParams().get("includeDeleted"));
        
        verify(mockHibernateHelper).queryCount(eq(SELECT_COUNT + FROM_FILE), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(eq(FROM_FILE + ORDER_BY), paramsCaptor.capture(), eq(5), eq(25),
                eq(FileMetadata.class));
        
        Map<String,Object> map = paramsCaptor.getValue();
        assertEquals(map.get("appId"), TEST_APP_ID);
    }
    
    @Test
    public void createFile() {
        FileMetadata metadata = new FileMetadata();
        
        FileMetadata returned = dao.createFile(metadata);
        assertSame(returned, metadata);
        
        verify(mockHibernateHelper).create(metadata, null);
    }

    @Test
    public void updateFile() {
        FileMetadata metadata = new FileMetadata();
        
        FileMetadata returned = dao.updateFile(metadata);
        assertSame(returned, metadata);
        
        verify(mockHibernateHelper).update(metadata, null);
    }

    @Test
    public void deleteFilePermanently() {
        FileMetadata metadata = new FileMetadata();
        metadata.setAppId(TEST_APP_ID);
        when(mockHibernateHelper.getById(FileMetadata.class, GUID)).thenReturn(metadata);
        
        dao.deleteFilePermanently(TEST_APP_ID, GUID);
        
        verify(mockHibernateHelper).getById(FileMetadata.class, GUID);
        verify(mockHibernateHelper).deleteById(FileMetadata.class, GUID);
    }
    
    @Test
    public void deleteFilePermanentlyNoFile() {
        dao.deleteFilePermanently(TEST_APP_ID, GUID);
        
        verify(mockHibernateHelper).getById(FileMetadata.class, GUID);
        verify(mockHibernateHelper, never()).deleteById(FileMetadata.class, GUID);
    }
    
    @Test
    public void deleteFilePermanentlyWrongStudy() {
        FileMetadata metadata = new FileMetadata();
        metadata.setAppId("some-other-study");
        when(mockHibernateHelper.getById(FileMetadata.class, GUID)).thenReturn(metadata);
        
        dao.deleteFilePermanently(TEST_APP_ID, GUID);
        
        verify(mockHibernateHelper).getById(FileMetadata.class, GUID);
        verify(mockHibernateHelper, never()).deleteById(FileMetadata.class, GUID);
    }
    
    @Test
    public void deleteAllStudyFiles() {
        dao.deleteAllStudyFiles(TEST_APP_ID);
        
        verify(mockHibernateHelper).query(eq(DELETE + FROM_FILE), paramsCaptor.capture());
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
    }
}
