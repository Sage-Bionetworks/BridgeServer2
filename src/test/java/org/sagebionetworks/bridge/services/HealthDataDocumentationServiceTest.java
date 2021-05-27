package org.sagebionetworks.bridge.services;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.dao.HealthDataDocumentationDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

public class HealthDataDocumentationServiceTest {

    @Mock
    private HealthDataDocumentationDao mockDao;

    @InjectMocks
    private HealthDataDocumentationService service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateDoc() {

    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrUpdate_NullDoc() {

    }

    private static HealthDataDocumentation makeValidDoc() {
        HealthDataDocumentation doc = HealthDataDocumentation.create();
        doc.setParentId(TEST_APP_ID);
        doc.setIdentifier(IDENTIFIER);
        return doc;
    }
}