package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.StudyService;

public class StrictValidationHandlerGetValidationStrictnessTest {
    private StrictValidationHandler handler;
    private StudyService mockStudyService;

    @BeforeMethod
    public void setup() {
        mockStudyService = mock(StudyService.class);
        handler = new StrictValidationHandler();
        handler.setStudyService(mockStudyService);
    }

    @Test
    public void enumStrict() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.STRICT);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(retVal, UploadValidationStrictness.STRICT);
    }

    @Test
    public void enumReport() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(retVal, UploadValidationStrictness.REPORT);
    }

    @Test
    public void enumWarn() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(UploadValidationStrictness.WARNING);
        study.setStrictUploadValidationEnabled(true);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(retVal, UploadValidationStrictness.WARNING);
    }

    @Test
    public void booleanTrue() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(null);
        study.setStrictUploadValidationEnabled(true);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(retVal, UploadValidationStrictness.STRICT);
    }

    @Test
    public void booleanFalse() {
        // mock study
        Study study = Study.create();
        study.setUploadValidationStrictness(null);
        study.setStrictUploadValidationEnabled(false);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForStudy(TEST_STUDY_IDENTIFIER);
        assertEquals(retVal, UploadValidationStrictness.WARNING);
    }
}
