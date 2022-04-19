package org.sagebionetworks.bridge.s3;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.common.base.Charsets;

import org.testng.annotations.Test;

public class S3HelperTest {
    // Test strategy is that given a mock input stream from a mock S3 object, the S3Helper can still turn that
    // input stream into a byte array or a string.

    @Test
    public void readAsBytes() throws Exception {
        String bucket = "bucket-with-bytes";
        String key = "key-with-bytes";
        String content = "this is the answer in bytes";

        S3Helper testS3Helper = setupWithMockS3(bucket, key, content);
        byte[] retValBytes = testS3Helper.readS3FileAsBytes(bucket, key);
        assertEquals(new String(retValBytes, Charsets.UTF_8), content);
    }

    @Test
    public void readAsString() throws Exception {
        String bucket = "test-bucket";
        String key = "test-key";
        String content = "this is the answer";

        S3Helper testS3Helper = setupWithMockS3(bucket, key, content);
        String retVal = testS3Helper.readS3FileAsString(bucket, key);
        assertEquals(retVal, content);
    }

    @Test
    public void readAsLines() throws Exception {
        String bucket = "bucket-with-lines";
        String key = "key-with-lines";
        String content = "foo\nbar\nbaz";

        S3Helper testS3Helper = setupWithMockS3(bucket, key, content);
        List<String> lineList = testS3Helper.readS3FileAsLines(bucket, key);
        assertEquals(lineList.size(), 3);
        assertEquals(lineList.get(0), "foo");
        assertEquals(lineList.get(1), "bar");
        assertEquals(lineList.get(2), "baz");
    }

    private static S3Helper setupWithMockS3(String bucket, String key, String content) {
        // mock S3 stream
        byte[] contentBytes = content.getBytes(Charsets.UTF_8);
        InputStream contentStream = new ByteArrayInputStream(contentBytes);

        // not sure this is safe, but this is the easiest way to mock an S3 stream
        S3ObjectInputStream mockS3Stream = new S3ObjectInputStream(contentStream, null, false);

        // mock S3 object
        S3Object mockS3Object = new S3Object();
        mockS3Object.setObjectContent(mockS3Stream);

        // mock S3 client
        AmazonS3 mockS3Client = mock(AmazonS3.class);
        when(mockS3Client.getObject(bucket, key)).thenReturn(mockS3Object);

        // set up test S3 helper
        S3Helper testS3Helper = new S3Helper();
        testS3Helper.setS3Client(mockS3Client);
        return testS3Helper;
    }
}
