package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DynamoHealthDataDocumentationDaoTest {

    @Mock
    private DynamoDBMapper mockMapper;

    @InjectMocks
    @Spy
    private DynamoHealthDataDocumentationDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateDocumentation_NewDoc() {

    }

    @Test
    public void createOrUpdateDocumentation_UpdateDoc() {

    }

    @Test
    public void deleteDocumentationForParentId() {

    }

    @Test
    public void deleteDocumentationForParentID_NoDoc() {

    }

    @Test
    public void getDocumentationForIdentifier() {

    }

    @Test
    public void getDocumentationForIdentifier_NoDoc() {

    }

    @Test
    public void getDocumentationForParentId() {

    }

    @Test
    public void getDocumentationForParentId_Paginated() {

    }

    @Test
    public void getDocumentationForParentId_NoDoc() {

    }

    @Test
    public void getDocumentationForParentId_InvalidOffsetKey() {

    }
}