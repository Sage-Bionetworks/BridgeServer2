package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.Collections;
import java.util.List;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.HealthDataService;

public class UploadValidationTaskFactoryTest {
    private static final String HEALTH_CODE = "health-code";

    @Test
    public void test() {
        // test dao and handlers
        List<UploadValidationHandler> handlerList = Collections.emptyList();
        UploadDao dao = mock(UploadDao.class);
        FileHelper fileHelper = new FileHelper();
        HealthDataService healthDataService = new HealthDataService();

        // set up task factory
        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setFileHelper(fileHelper);
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(dao);
        taskFactory.setHealthDataService(healthDataService);

        // inputs
        App app = TestUtils.getValidStudy(UploadValidationTaskFactoryTest.class);
        Upload upload = Upload.create();
        upload.setHealthCode(HEALTH_CODE);

        // execute and validate
        UploadValidationTask task = taskFactory.newTask(app.getIdentifier(), upload);
        assertEquals(task.getContext().getHealthCode(), HEALTH_CODE);
        assertSame(task.getContext().getAppId(), app.getIdentifier());
        assertSame(task.getContext().getUpload(), upload);

        assertSame(task.getFileHelper(), fileHelper);
        assertSame(task.getHandlerList(), handlerList);
        assertSame(task.getUploadDao(), dao);
        assertSame(task.getHealthDataService(), healthDataService);
    }
}
