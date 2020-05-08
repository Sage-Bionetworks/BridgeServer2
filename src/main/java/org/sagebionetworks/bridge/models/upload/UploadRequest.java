package org.sagebionetworks.bridge.models.upload;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = UploadRequest.Builder.class)
public class UploadRequest implements BridgeEntity {
    private final String name;
    private final long contentLength;
    private final String contentMd5;
    private final String contentType;
    private final boolean encrypted;
    private final boolean zipped;

    private UploadRequest(String name, long contentLength, String contentMd5, String contentType, boolean encrypted,
            boolean zipped) {
        this.name = name;
        this.contentLength = contentLength;
        this.contentMd5 = contentMd5;
        this.contentType = contentType;
        this.encrypted = encrypted;
        this.zipped = zipped;
    }

    public String getName() {
        return name;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentMd5() {
        return contentMd5;
    }

    public String getContentType() {
        return contentType;
    }

    /** True if the upload is encrypted. False if it is not encrypted. If not specified, defaults to true. */
    public boolean isEncrypted() {
        return encrypted;
    }

    /** True if the upload is zipped. False if it is a single file. If not specified, defaults to true. */
    public boolean isZipped() {
        return zipped;
    }

    public static class Builder {
        private String name;
        private Long contentLength;
        private String contentMd5;
        private String contentType;
        private Boolean encrypted;
        private Boolean zipped;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withContentLength(Long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public Builder withContentMd5(String contentMd5) {
            this.contentMd5 = contentMd5;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withEncrypted(Boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        public Builder withZipped(Boolean zipped) {
            this.zipped = zipped;
            return this;
        }

        public UploadRequest build() {
            // contentLength defaults to 0.
            long actualContentLength = contentLength != null ? contentLength : 0;

            // encrypted and zipped default to true.
            boolean actualEncrypted = encrypted != null ? encrypted : true;
            boolean actualZipped = zipped != null ? zipped : true;

            return new UploadRequest(name, actualContentLength, contentMd5, contentType, actualEncrypted,
                    actualZipped);
        }
    }
}
