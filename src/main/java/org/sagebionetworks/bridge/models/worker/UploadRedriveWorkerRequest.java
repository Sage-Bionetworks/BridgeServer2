package org.sagebionetworks.bridge.models.worker;

/** Worker request to redrive uploads . */
public class UploadRedriveWorkerRequest {
    private String s3Bucket;
    private String s3Key;
    private String redriveTypeStr;

    public String getS3Bucket() {
        return s3Bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getRedriveTypeStr() {
        return redriveTypeStr;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public void setRedriveTypeStr(String redriveTypeStr) {
        this.redriveTypeStr = redriveTypeStr;
    }
}
