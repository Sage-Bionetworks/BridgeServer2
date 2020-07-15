package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.ImmutableList;

public class StudyServiceTest {
    private static final PagedResourceList<Study> STUDIES = new PagedResourceList<>(ImmutableList.of(Study.create(), Study.create()), 5);
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);
    
    @Mock
    private StudyDao studyDao;
    
    @Captor
    private ArgumentCaptor<Study> studyCaptor;
    
    private StudyService service;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        service = new StudyService();
        service.setStudyDao(studyDao);
    }
    
    @Test
    public void getStudy() {
        Study study = Study.create();
        when(studyDao.getStudy(TEST_APP_ID, "id")).thenReturn(study);
        
        Study returnedValue = service.getStudy(TEST_APP_ID, "id", true);
        assertEquals(returnedValue, study);
        
        verify(studyDao).getStudy(TEST_APP_ID, "id");
    }
    
    @Test
    public void getStudyIds() {
        Study studyA = Study.create();
        studyA.setId("studyA");
        
        Study studyB = Study.create();
        studyB.setId("studyB");
        PagedResourceList<Study> studies = new PagedResourceList<>(ImmutableList.of(studyA, studyB), 10); 
        
        when(studyDao.getStudies(TEST_APP_ID, 0, API_MAXIMUM_PAGE_SIZE, false)).thenReturn(studies);
        
        Set<String> studyIds = service.getStudyIds(TEST_APP_ID);
        assertEquals(studyIds, USER_STUDY_IDS);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getStudyNotFoundThrowingException() {
        service.getStudy(TEST_APP_ID, "id", true);
    }
    
    @Test
    public void getStudyNotFoundNotThrowingException() {
        Study study = service.getStudy(TEST_APP_ID, "id", false);
        assertNull(study);
    }
    
    @Test
    public void getStudiesIncludeDeleted() {
        when(studyDao.getStudies(TEST_APP_ID, 0, 50, true)).thenReturn(STUDIES);
        
        PagedResourceList<Study> returnedValue = service.getStudies(TEST_APP_ID, 0, 50, true);
        assertEquals(returnedValue, STUDIES);
        
        verify(studyDao).getStudies(TEST_APP_ID, 0, 50, true);
    }
    
    @Test
    public void getStudiesExcludeDeleted() {
        when(studyDao.getStudies(TEST_APP_ID, 0, 10, false)).thenReturn(STUDIES);
        
        PagedResourceList<Study> returnedValue = service.getStudies(TEST_APP_ID, 0, 10, false);
        assertEquals(returnedValue, STUDIES);
        
        verify(studyDao).getStudies(TEST_APP_ID, 0, 10, false);
    }
    
    @Test
    public void createStudy() {
        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        study.setAppId("junk");
        study.setVersion(10L);
        study.setDeleted(true);
        DateTime timestamp = DateTime.now().minusHours(2);
        study.setCreatedOn(timestamp);
        study.setModifiedOn(timestamp);

        when(studyDao.createStudy(any())).thenReturn(VERSION_HOLDER);
        
        VersionHolder returnedValue = service.createStudy(TEST_APP_ID, study);
        assertEquals(returnedValue, VERSION_HOLDER);
        
        verify(studyDao).createStudy(studyCaptor.capture());
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");
        assertEquals(persisted.getAppId(), TEST_APP_ID);
        assertNull(persisted.getVersion());
        assertFalse(persisted.isDeleted());
        assertNotEquals(persisted.getCreatedOn(), timestamp);
        assertNotEquals(persisted.getModifiedOn(), timestamp);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createStudyInvalidStudy() {
        service.createStudy(TEST_APP_ID, Study.create());
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createStudyAlreadyExists() {
        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        
        when(studyDao.getStudy(TEST_APP_ID, "oneId")).thenReturn(study);
        
        service.createStudy(TEST_APP_ID, study);
    }

    @Test
    public void updateStudy() {
        Study existing = Study.create();
        existing.setId("oneId");
        existing.setName("oldName");
        existing.setCreatedOn(DateTime.now());
        when(studyDao.getStudy(TEST_APP_ID, "oneId")).thenReturn(existing);
        when(studyDao.updateStudy(any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setAppId("wrongAppId");
        study.setId("oneId");
        study.setName("newName");
        
        VersionHolder versionHolder = service.updateStudy(TEST_APP_ID, study);
        assertEquals(versionHolder, VERSION_HOLDER);
        
        verify(studyDao).updateStudy(studyCaptor.capture());
        
        Study returnedValue = studyCaptor.getValue();
        assertEquals(returnedValue.getAppId(), TEST_APP_ID);
        assertEquals(returnedValue.getId(), "oneId");
        assertEquals(returnedValue.getName(), "newName");
        assertNotNull(returnedValue.getCreatedOn());
        assertNotNull(returnedValue.getModifiedOn());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateStudyInvalidStudy() {
        service.updateStudy(TEST_APP_ID, Study.create());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateStudyEntityNotFound() {
        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        study.setDeleted(true);

        service.updateStudy(TEST_APP_ID, study);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateStudyEntityDeleted() {
        Study existing = Study.create();
        existing.setDeleted(true);
        when(studyDao.getStudy(TEST_APP_ID, "oneId")).thenReturn(existing);

        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        study.setDeleted(true);
        
        service.updateStudy(TEST_APP_ID, study);
    }

    @Test
    public void deleteStudy() {
        when(studyDao.getStudy(TEST_APP_ID, "id")).thenReturn(Study.create());
        
        service.deleteStudy(TEST_APP_ID, "id");
        
        verify(studyDao).updateStudy(studyCaptor.capture());
        Study persisted = studyCaptor.getValue();
        assertTrue(persisted.isDeleted());
        assertNotNull(persisted.getModifiedOn());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudyNotFound() {
        service.deleteStudy(TEST_APP_ID, "id");
    }
    
    @Test
    public void deleteStudyPermanently() {
        when(studyDao.getStudy(TEST_APP_ID, "id")).thenReturn(Study.create());
        
        service.deleteStudyPermanently(TEST_APP_ID, "id");
        
        verify(studyDao).deleteStudyPermanently(TEST_APP_ID, "id");
    }    

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteStudyPermanentlyNotFound() {
        service.deleteStudyPermanently(TEST_APP_ID, "id");
    }    
}
