package org.sagebionetworks.bridge.upload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadFileHelperFindValueTest {
    private static final String FIELD_NAME_FILE = "record.json";
    private static final String FIELD_NAME_JSON_KEY = "record.json.foo";
    private static final String UPLOAD_ID = "upload-id";

    private InMemoryFileHelper inMemoryFileHelper;
    private S3Helper mockS3Helper;
    private File tmpDir;
    private UploadFileHelper uploadFileHelper;
    private ArgumentCaptor<ObjectMetadata> metadataCaptor;

    @BeforeMethod
    public void before() throws Exception {
        // Spy file helper, so we can check to see how many times we read the disk later. Also make a dummy temp dir,
        // as an in-memory place we can put files into.
        inMemoryFileHelper = spy(new InMemoryFileHelper());
        tmpDir = inMemoryFileHelper.createTempDir();

        // Mock dependencies.
        DigestUtils mockMd5DigestUtils = mock(DigestUtils.class);
        when(mockMd5DigestUtils.digest(any(File.class))).thenReturn(TestConstants.MOCK_MD5);
        when(mockMd5DigestUtils.digest(any(byte[].class))).thenReturn(TestConstants.MOCK_MD5);

        mockS3Helper = mock(S3Helper.class);

        metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        
        // Create UploadFileHelper.
        uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setFileHelper(inMemoryFileHelper);
        uploadFileHelper.setMd5DigestUtils(mockMd5DigestUtils);
        uploadFileHelper.setS3Helper(mockS3Helper);
    }

    @Test
    public void attachmentFile() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "dummy content");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        String expectedAttachmentFilename = UPLOAD_ID + '-' + FIELD_NAME_FILE;
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(result.textValue(), expectedAttachmentFilename);

        // Verify uploaded file
        verify(mockS3Helper).writeFileToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(expectedAttachmentFilename),
                eq(recordJsonFile), metadataCaptor.capture());

        ObjectMetadata metadata = metadataCaptor.getValue();
        assertEquals(metadata.getUserMetaDataOf(UploadFileHelper.KEY_CUSTOM_CONTENT_MD5),
                TestConstants.MOCK_MD5_BASE64_ENCODED);
        assertEquals(metadata.getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test
    public void attachmentFileEmpty() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map. File should exist but have empty content.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void inlineFile() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "\"dummy content\"");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(result.textValue(), "dummy content");

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void inlineFileTooLarge() throws Exception {
        // Set file size limit to something very small, to hit our test for sure.
        uploadFileHelper.setInlineFileSizeLimit(10);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_FILE)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "\"This file content is definitely exceeds our file size limit.\"");
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueNoValueFound() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map. One file doesn't match the name, and the other file matches the name but doesn't have the key.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"bar\":\"bar-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueAttachment() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.ATTACHMENT_V2).build();

        // Make file map. For good measure, have one file that doesn't match.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"foo\":\"record-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        String expectedAttachmentFilename = UPLOAD_ID + '-' + FIELD_NAME_JSON_KEY;
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(result.textValue(), expectedAttachmentFilename);

        // Verify uploaded file
        verify(mockS3Helper).writeBytesToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(expectedAttachmentFilename),
                eq("\"record-value\"".getBytes(Charsets.UTF_8)), metadataCaptor.capture());

        ObjectMetadata metadata = metadataCaptor.getValue();
        assertEquals(TestConstants.MOCK_MD5_BASE64_ENCODED, metadata.getUserMetaDataOf(
                UploadFileHelper.KEY_CUSTOM_CONTENT_MD5));
        assertEquals(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION, metadata.getSSEAlgorithm());
    }

    @Test
    public void findValueInline() throws Exception {
        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map. For good measure, have one file that doesn't match.
        File fooJsonFile = makeFileWithContent("foo.json", "{\"foo\":\"foo-value\"}");
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, "{\"foo\":\"record-value\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put("foo.json", fooJsonFile)
                .put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(result.textValue(), "record-value");

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueInlineWarningLimit() throws Exception {
        // Set limits to something that's easier to test.
        uploadFileHelper.setParsedJsonWarningLimit(20);
        uploadFileHelper.setParsedJsonFileSizeLimit(40);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "{\"foo\":\"Long but not too long\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute - The file is too large. Skip.
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertEquals(result.textValue(), "Long but not too long");

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void findValueInlineFileSizeLimit() throws Exception {
        // Set limits to something that's easier to test.
        uploadFileHelper.setParsedJsonWarningLimit(20);
        uploadFileHelper.setParsedJsonFileSizeLimit(40);

        // Make field def.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(FIELD_NAME_JSON_KEY)
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE,
                "{\"foo\":\"This is the value, but the file is definitely too long to parse\"}");
        Map<String, File> fileMap = ImmutableMap.<String, File>builder().put(FIELD_NAME_FILE, recordJsonFile).build();

        // Execute - The file is long enough to warn, but not long enough to skip.
        JsonNode result = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fieldDef, new HashMap<>());
        assertNull(result);

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);
    }

    @Test
    public void multipleKeys() throws Exception {
        // Make field defs.
        UploadFieldDefinition fooFieldDef = new UploadFieldDefinition.Builder().withName("record.json.sanitize____foo")
                .withType(UploadFieldType.STRING).build();
        UploadFieldDefinition barFieldDef = new UploadFieldDefinition.Builder().withName("record.json.sanitize____bar")
                .withType(UploadFieldType.STRING).build();

        // Make file map.
        String recordJsonText = "{\n" +
                "   \"sanitize!@#$foo\":\"foo-value\",\n" +
                "   \"sanitize!@#$bar\":\"bar-value\"\n" +
                "}";
        File recordJsonFile = makeFileWithContent(FIELD_NAME_FILE, recordJsonText);
        Map<String, File> fileMap = ImmutableMap.of(FIELD_NAME_FILE, recordJsonFile);

        // Execute
        Map<String, Map<String, JsonNode>> cache = new HashMap<>();

        JsonNode fooResult = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, fooFieldDef, cache);
        assertEquals(fooResult.textValue(), "foo-value");

        JsonNode barResult = uploadFileHelper.findValueForField(UPLOAD_ID, fileMap, barFieldDef, cache);
        assertEquals(barResult.textValue(), "bar-value");

        // Verify no uploaded files
        verifyZeroInteractions(mockS3Helper);

        // Verify we only read the file once.
        verify(inMemoryFileHelper, times(1)).getInputStream(recordJsonFile);
    }

    private File makeFileWithContent(String name, String content) {
        File file = inMemoryFileHelper.newFile(tmpDir, name);
        inMemoryFileHelper.writeBytes(file, content.getBytes(Charsets.UTF_8));
        return file;
    }
}
