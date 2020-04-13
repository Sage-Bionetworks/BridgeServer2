package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifierDao.CONFIG_KEY_ADD_LIMIT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;

public class DynamoFPHSExternalIdentifierDaoTest extends Mockito {
    
    private static String EXT_ID_STRING1 = "anExternalId1";
    
    private static String EXT_ID_STRING2 = "anExternalId2";

    @InjectMocks
    DynamoFPHSExternalIdentifierDao dao;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedScanList<DynamoFPHSExternalIdentifier> mockScanList;
    
    @Captor
    ArgumentCaptor<DynamoFPHSExternalIdentifier> externalIdCaptor;
    
    @Captor
    ArgumentCaptor<List<DynamoFPHSExternalIdentifier>> idsCaptor;
    
    DynamoExternalIdentifier externalId;

    DynamoFPHSExternalIdentifier fphsExternalId1;

    DynamoFPHSExternalIdentifier fphsExternalId2;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        BridgeConfig bridgeConfig = mock(BridgeConfig.class);
        when(bridgeConfig.getInt(CONFIG_KEY_ADD_LIMIT)).thenReturn(5);
        dao.setConfig(bridgeConfig);
        
        // These have state and change; reset every test.
        externalId = new DynamoExternalIdentifier(TEST_APP_ID, EXT_ID_STRING1);
        fphsExternalId1 = new DynamoFPHSExternalIdentifier(EXT_ID_STRING1);
        fphsExternalId2 = new DynamoFPHSExternalIdentifier(EXT_ID_STRING2);
    }
    
    @Test
    public void verifyExternalIdSucceeds() {
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.verifyExternalId(externalId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void verifyExternalIdFails() {
        dao.verifyExternalId(externalId);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class, 
            expectedExceptionsMessageRegExp = "ExternalIdentifier already exists.")
    public void verifyAnAlreadyExistingExternalId() {
        fphsExternalId1.setRegistered(true);
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.verifyExternalId(externalId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "ExternalIdentifier not found.")
    public void registerAMissingExternalId() {
        dao.registerExternalId(externalId);
    }
    
    @Test
    public void registerExternalIdSucceeds() {
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.registerExternalId(externalId);
        
        verify(mockMapper).save(externalIdCaptor.capture());
        assertTrue(externalIdCaptor.getValue().isRegistered());
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void registerExternalIdFails() {
        fphsExternalId1.setRegistered(true);
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.registerExternalId(externalId);
    }
    
    @Test
    public void unregisterExternalId() {
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.unregisterExternalId(externalId);
        
        verify(mockMapper).save(externalIdCaptor.capture());
        assertFalse(externalIdCaptor.getValue().isRegistered());
    }
    
    @Test
    public void unregisterExternalIdDoesNothingQuietly() {
        dao.unregisterExternalId(externalId);
        verify(mockMapper, never()).save(any());
    }
    
    @Test
    public void getExternalIds() {
        List<DynamoFPHSExternalIdentifier> identifiers = ImmutableList.of(fphsExternalId1);
        when(mockScanList.stream()).thenReturn(identifiers.stream());
        when(mockMapper.scan(eq(DynamoFPHSExternalIdentifier.class), any())).thenReturn(mockScanList);
        
        List<FPHSExternalIdentifier> ids = dao.getExternalIds();
        assertEquals(ids, identifiers);
        assertEquals(ids.size(), 1);

        verify(mockMapper).scan(eq(DynamoFPHSExternalIdentifier.class), any(DynamoDBScanExpression.class));
    }
    
    @Test
    public void addExternalIds() {
        List<FPHSExternalIdentifier> identifiers = ImmutableList.of(fphsExternalId1, fphsExternalId2);
        dao.addExternalIds(identifiers);
        
        verify(mockMapper).batchSave(idsCaptor.capture());
        
        List<DynamoFPHSExternalIdentifier> ids = idsCaptor.getValue();
        assertEquals(ids.size(), 2);
        assertEquals(ids.get(0).getExternalId(), fphsExternalId1.getExternalId());
        assertEquals(ids.get(1).getExternalId(), fphsExternalId2.getExternalId());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void addExternalIdsExceedsAddLimit() {
        List<FPHSExternalIdentifier> identifiers = ImmutableList.of(fphsExternalId1, fphsExternalId2, fphsExternalId1,
                fphsExternalId2, fphsExternalId1, fphsExternalId2);
        dao.addExternalIds(identifiers);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void addExternalIdsEmptyDoesNothing() {
        dao.addExternalIds(ImmutableList.of());
        verify(mockMapper, never()).batchSave(any(List.class));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void addExternalIdsExistDoesNothing() {
        // All these identifiers are found while loading, so nothing should happen.
        List<FPHSExternalIdentifier> identifiers = ImmutableList.of(fphsExternalId1, fphsExternalId2);
        when(mockMapper.load(any())).thenReturn(fphsExternalId1);
        
        dao.addExternalIds(identifiers);
        verify(mockMapper, never()).batchSave(any(List.class));
    }
    
    @Test
    public void deleteExternalId() {
        dao.deleteExternalId(EXT_ID_STRING1);
        
        verify(mockMapper).delete(externalIdCaptor.capture());
        assertEquals(externalIdCaptor.getValue().getExternalId(), EXT_ID_STRING1);
    }
}
