package org.sagebionetworks.bridge.hibernate;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.models.substudies.SubstudyId;

import com.google.common.collect.ImmutableList;

public class HibernateSubstudyDaoTest {
    private static final List<HibernateSubstudy> SUBSTUDIES = ImmutableList.of(new HibernateSubstudy(),
            new HibernateSubstudy());
    
    @Mock
    private HibernateHelper hibernateHelper;
    
    private HibernateSubstudyDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;

    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<SubstudyId> substudyIdCaptor;
    
    @Captor
    ArgumentCaptor<HibernateSubstudy> substudyCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new HibernateSubstudyDao();
        dao.setHibernateHelper(hibernateHelper);
    }
    
    @Test
    public void getSubstudiesIncludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateSubstudy.class)))
                .thenReturn(SUBSTUDIES);
        
        List<Substudy> list = dao.getSubstudies(TEST_STUDY_IDENTIFIER, true);
        assertEquals(list.size(), 2);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateSubstudy.class));
        
        assertEquals(queryCaptor.getValue(), "from HibernateSubstudy as substudy where studyId=:studyId");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("studyId"), TEST_STUDY_IDENTIFIER);
    }

    @Test
    public void getSubstudiesExcludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateSubstudy.class)))
            .thenReturn(SUBSTUDIES);

        List<Substudy> list = dao.getSubstudies(TEST_STUDY_IDENTIFIER, false);
        assertEquals(list.size(), 2);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateSubstudy.class));
        
        assertEquals(queryCaptor.getValue(),
                "from HibernateSubstudy as substudy where studyId=:studyId and deleted != 1");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("studyId"), TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void getSubstudy() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        when(hibernateHelper.getById(eq(HibernateSubstudy.class), any())).thenReturn(substudy);
        
        Substudy returnedValue = dao.getSubstudy(TEST_STUDY_IDENTIFIER, "id");
        assertEquals(returnedValue, substudy);
        
        verify(hibernateHelper).getById(eq(HibernateSubstudy.class), substudyIdCaptor.capture());
        
        SubstudyId substudyId = substudyIdCaptor.getValue();
        assertEquals(substudyId.getId(), "id");
        assertEquals(substudyId.getStudyId(), TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void createSubstudy() {
        Substudy substudy = Substudy.create();
        substudy.setVersion(2L);
        
        VersionHolder holder = dao.createSubstudy(substudy);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).create(substudyCaptor.capture(), eq(null));
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
    }

    @Test
    public void updateSubstudy() {
        Substudy substudy = Substudy.create();
        substudy.setVersion(2L);
        
        VersionHolder holder = dao.updateSubstudy(substudy);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).update(substudyCaptor.capture(), eq(null));
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
    }

    @Test
    public void deleteSubstudyPermanently() {
        dao.deleteSubstudyPermanently(TEST_STUDY_IDENTIFIER, "oneId");
        
        verify(hibernateHelper).deleteById(eq(HibernateSubstudy.class), substudyIdCaptor.capture());
        SubstudyId substudyId = substudyIdCaptor.getValue();
        assertEquals(substudyId.getId(), "oneId");
        assertEquals(substudyId.getStudyId(), TEST_STUDY_IDENTIFIER);
    }    

    @Test(expectedExceptions = PersistenceException.class)
    public void deleteSubstudyPermanentlyNotFound() {
        doThrow(new PersistenceException()).when(hibernateHelper).deleteById(eq(HibernateSubstudy.class), any());
            
        dao.deleteSubstudyPermanently(TEST_STUDY_IDENTIFIER, "oneId");
    }    
}
