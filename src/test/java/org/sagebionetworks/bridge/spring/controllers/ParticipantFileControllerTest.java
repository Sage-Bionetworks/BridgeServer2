package org.sagebionetworks.bridge.spring.controllers;

import com.google.common.collect.ImmutableList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.sagebionetworks.bridge.services.ParticipantFileService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.sagebionetworks.bridge.spring.controllers.ParticipantFileController.DELETE_MSG;

public class ParticipantFileControllerTest {

    @Mock
    ParticipantFileService mockFileService;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @Spy
    @InjectMocks
    ParticipantFileController controller;

    UserSession session;
    ParticipantFile persisted;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        StudyParticipant participant = new StudyParticipant.Builder().withId("test_user").build();
        session.setParticipant(participant);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();

        persisted = ParticipantFile.create();
        persisted.setFileId("file_id");
        persisted.setUserId("test_user");
        persisted.setCreatedOn(TestConstants.TIMESTAMP);
        persisted.setAppId("api");
        persisted.setMimeType("dummy-type");
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ParticipantFileController.class);
        assertGet(ParticipantFileController.class, "getParticipantFile");
        assertGet(ParticipantFileController.class, "getParticipantFiles");
        assertPost(ParticipantFileController.class, "createParticipantFile");
        assertDelete(ParticipantFileController.class, "deleteParticipantFile");
    }

    @Test
    public void getParticipantFiles() {
        ParticipantFile file = ParticipantFile.create();
        file.setUserId("test_user");
        file.setFileId("file_id");
        ForwardCursorPagedResourceList<ParticipantFile> page = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(file), null);
        when(mockFileService.getParticipantFiles(eq("test_user"), isNull(), anyInt())).thenReturn(page);

        ForwardCursorPagedResourceList<ParticipantFile> result = controller.getParticipantFiles(null, "5");
        assertSame(page, result);

        verify(mockFileService).getParticipantFiles(eq("test_user"), isNull(), eq(5));
    }

    @Test
    public void getParticipantFile() {
        when(mockFileService.getParticipantFile(eq("test_user"), eq("file_id"))).thenReturn(persisted);
        ParticipantFile result = controller.getParticipantFile("file_id");
        assertSame(result, persisted);
        verify(mockFileService).getParticipantFile("test_user", "file_id");
    }

    @Test
    public void createParticipantFile() throws Exception {
        ParticipantFile newFile = ParticipantFile.create();
        newFile.setUserId("test_user");
        newFile.setFileId("file_id");
        newFile.setAppId("api");
        newFile.setMimeType("dummy-type");
        mockRequestBody(mockRequest, newFile);
        when(mockFileService.createParticipantFile(any())).thenReturn(persisted);
        ParticipantFile result = controller.createParticipantFile("file_id");

        assertEquals(result.getFileId(), "file_id");
        assertEquals(result.getUserId(), "test_user");
        assertEquals(result.getAppId(), "api");
        assertEquals(result.getMimeType(), "dummy-type");
        assertEquals(result.getCreatedOn(), TestConstants.TIMESTAMP);
    }

    @Test
    public void deleteParticipantFile() {
        StatusMessage message = controller.deleteParticipantFile("file_id");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        verify(mockFileService).deleteParticipantFile(eq("test_user"), eq("file_id"));
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantFileNoSuchFile() {
        doThrow(EntityNotFoundException.class).when(mockFileService).deleteParticipantFile(any(), any());
        StatusMessage message = controller.deleteParticipantFile("file_id");
    }
}
