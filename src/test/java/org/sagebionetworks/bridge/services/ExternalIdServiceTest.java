package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Optional;
import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import com.google.common.collect.ImmutableSet;

public class ExternalIdServiceTest {

    private static final String ID = "AAA";
    private static final String SUBSTUDY_ID = "substudyId";
    private static final Set<String> SUBSTUDIES = ImmutableSet.of(SUBSTUDY_ID);
    private static final String HEALTH_CODE = "healthCode";
    
    private Study study;
    private ExternalIdentifier extId;
    
    @Mock
    private ExternalIdDao externalIdDao;
    
    @Mock
    private SubstudyService substudyService;
    
    private ExternalIdService externalIdService;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY).build());
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        extId = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        extId.setSubstudyId(SUBSTUDY_ID);
        externalIdService = new ExternalIdService();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setSubstudyService(substudyService);
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void getExternalId() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(extId));
        
        Optional<ExternalIdentifier> retrieved = externalIdService.getExternalId(TestConstants.TEST_STUDY, ID);
        assertEquals(retrieved.get(), extId);
    }
    
    @Test
    public void getExternalIdNoExtIdReturnsEmptyOptional() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.empty());
        
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(TestConstants.TEST_STUDY, ID);
        assertFalse(optionalId.isPresent());
    }

    @Test
    public void getExternalIdNullExtIdReturnsEmptyOptional() {
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(TestConstants.TEST_STUDY, null);
        assertFalse(optionalId.isPresent());
    }
    
    @Test
    public void getExternalIds() {
        externalIdService.getExternalIds("offsetKey", 10, "idFilter", true);
        
        verify(externalIdDao).getExternalIds(TestConstants.TEST_STUDY, "offsetKey", 10, "idFilter", true);
    }
    
    @Test
    public void getExternalIdsDefaultsPageSize() {
        externalIdService.getExternalIds(null, null, null, null);
        
        verify(externalIdDao).getExternalIds(TestConstants.TEST_STUDY, null, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getExternalIdsUnderMinPageSizeThrows() {
        externalIdService.getExternalIds(null, 0, null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getExternalIdsOverMaxPageSizeThrows() {
        externalIdService.getExternalIds(null, 10000, null, null);
    }
    
    @Test
    public void createExternalId() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.empty());
        
        externalIdService.createExternalId(extId, false);
        
        verify(externalIdDao).createExternalId(extId);
    }
    
    @Test
    public void createExternalIdEnforcesStudyId() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.empty());
        
        ExternalIdentifier newExtId = ExternalIdentifier.create(new StudyIdentifierImpl("some-dumb-id"), ID);
        newExtId.setSubstudyId(SUBSTUDY_ID);
        externalIdService.createExternalId(newExtId, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(extId);        
    }
    
    @Test
    public void createExternalIdSetsSubstudyIdIfMissingAndUnambiguous() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.empty());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());
        
        ExternalIdentifier newExtId = ExternalIdentifier.create(TestConstants.TEST_STUDY,
                extId.getIdentifier());
        externalIdService.createExternalId(newExtId, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(extId);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createExternalIdDoesNotSetSubstudyIdAmbiguous() {
        extId.setSubstudyId(null); // not set by caller
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID, "anotherSubstudy")).build());
        
        externalIdService.createExternalId(extId, false);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createExternalIdValidates() {
        externalIdService.createExternalId(ExternalIdentifier.create(new StudyIdentifierImpl("nonsense"), "nonsense"), false);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createExternalIdAlreadyExistsThrows() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(extId));
        extId.setSubstudyId(SUBSTUDY_ID);
        
        externalIdService.createExternalId(extId, false);
    }
    
    @Test
    public void deleteExternalIdPermanently() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(extId));
        
        externalIdService.deleteExternalIdPermanently(study, extId);
        
        verify(externalIdDao).deleteExternalId(extId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyMissingThrows() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, extId.getIdentifier())).thenReturn(Optional.empty());
        
        externalIdService.deleteExternalIdPermanently(study, extId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyOutsideSubstudiesThrows() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());        
        extId.setSubstudyId("someOtherId");
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(Optional.of(extId));
        
        externalIdService.deleteExternalIdPermanently(study, extId);
    }
    
    @Test
    public void commitAssignExternalId() {
        ExternalIdentifier externalId = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        
        externalIdService.commitAssignExternalId(externalId);
        
        verify(externalIdDao).commitAssignExternalId(externalId);
    }

    @Test
    public void commitAssignExternalIdNullId() {
        externalIdService.commitAssignExternalId(null);
        
        verify(externalIdDao, never()).commitAssignExternalId(any());
    }

    @Test
    public void unassignExternalId() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, ID);
        
        verify(externalIdDao).unassignExternalId(account, ID);
    }

    @Test
    public void unassignExternalIdNullDoesNothing() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, null);
        
        verify(externalIdDao, never()).unassignExternalId(account, ID);
    }
}
