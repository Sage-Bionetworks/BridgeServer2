package org.sagebionetworks.bridge;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Sets;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

public class DefaultStudyBootstrapperTest {

    private StudyService studyService;

    private DefaultStudyBootstrapper defaultStudyBootstrapper;

    @BeforeMethod
    public void before() {
        studyService = mock(StudyService.class);

        when(studyService.getStudy(any(StudyIdentifier.class))).thenThrow(EntityNotFoundException.class);
        defaultStudyBootstrapper = new DefaultStudyBootstrapper(studyService,
                mock(AnnotationBasedTableCreator.class),
                mock(DynamoInitializer.class)
        );
    }

    @Test
    public void createsDefaultStudyWhenMissing() {
        defaultStudyBootstrapper.initializeDatabase();

        ArgumentCaptor<Study> argument = ArgumentCaptor.forClass(Study.class);
        verify(studyService, times(2)).createStudy(argument.capture());

        List<Study> createdStudyList = argument.getAllValues();

        // Validate api study.
        Study study = createdStudyList.get(0);
        assertEquals(study.getName(), "Test Study");
        assertEquals(study.getIdentifier(), BridgeConstants.API_STUDY_ID_STRING);
        assertEquals(study.getSponsorName(), "Sage Bionetworks");
        assertEquals(study.getShortName(), "TestStudy");
        assertEquals(study.getMinAgeOfConsent(), 18);
        assertEquals(study.getConsentNotificationEmail(), "bridge-testing+consent@sagebase.org");
        assertEquals(study.getTechnicalEmail(), "bridge-testing+technical@sagebase.org");
        assertEquals(study.getSupportEmail(), "support@sagebridge.org");
        assertEquals(study.getUserProfileAttributes(), Sets.newHashSet("can_be_recontacted"));
        assertEquals(study.getPasswordPolicy(), new PasswordPolicy(2, false, false, false, false));
        assertTrue(study.isEmailVerificationEnabled());

        // Validate shared study. No need to test every attribute. Just validate the important attributes.
        Study sharedStudy = createdStudyList.get(1);
        assertEquals(sharedStudy.getName(), "Shared Module Library");
        assertEquals(sharedStudy.getIdentifier(), BridgeConstants.SHARED_STUDY_ID_STRING);

        // So it doesn't get out of sync, validate the study. However, default templates are set 
        // by the service. so those two errors are expected.
        try {
            Validate.entityThrowingException(new StudyValidator(), study);    
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().keySet().size(), 2);
            assertEquals(e.getErrors().get("verifyEmailTemplate").size(), 1);
            assertEquals(e.getErrors().get("resetPasswordTemplate").size(), 1);
        }
        
    }
}
