package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.AppService;

public class StrictValidationHandlerGetValidationStrictnessTest {
    private StrictValidationHandler handler;
    private AppService mockAppService;

    @BeforeMethod
    public void setup() {
        mockAppService = mock(AppService.class);
        handler = new StrictValidationHandler();
        handler.setAppService(mockAppService);
    }

    @Test
    public void enumStrict() {
        // mock app
        App app = App.create();
        app.setUploadValidationStrictness(UploadValidationStrictness.STRICT);
        app.setStrictUploadValidationEnabled(false);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForApp(TEST_APP_ID);
        assertEquals(retVal, UploadValidationStrictness.STRICT);
    }

    @Test
    public void enumReport() {
        // mock app
        App app = App.create();
        app.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        app.setStrictUploadValidationEnabled(false);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForApp(TEST_APP_ID);
        assertEquals(retVal, UploadValidationStrictness.REPORT);
    }

    @Test
    public void enumWarn() {
        // mock app
        App app = App.create();
        app.setUploadValidationStrictness(UploadValidationStrictness.WARNING);
        app.setStrictUploadValidationEnabled(true);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForApp(TEST_APP_ID);
        assertEquals(retVal, UploadValidationStrictness.WARNING);
    }

    @Test
    public void booleanTrue() {
        // mock app
        App app = App.create();
        app.setUploadValidationStrictness(null);
        app.setStrictUploadValidationEnabled(true);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForApp(TEST_APP_ID);
        assertEquals(retVal, UploadValidationStrictness.STRICT);
    }

    @Test
    public void booleanFalse() {
        // mock app
        App app = App.create();
        app.setUploadValidationStrictness(null);
        app.setStrictUploadValidationEnabled(false);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        // execute and validate
        UploadValidationStrictness retVal = handler.getUploadValidationStrictnessForApp(TEST_APP_ID);
        assertEquals(retVal, UploadValidationStrictness.WARNING);
    }
}
