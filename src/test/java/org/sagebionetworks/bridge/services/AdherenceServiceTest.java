package org.sagebionetworks.bridge.services;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;

import org.sagebionetworks.bridge.dao.AdherenceRecordDao;

public class AdherenceServiceTest extends Mockito {

    @Mock
    AdherenceRecordDao dao;
    
    @Mock
    AppService appService;

    @InjectMocks
    AdherenceService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
}
