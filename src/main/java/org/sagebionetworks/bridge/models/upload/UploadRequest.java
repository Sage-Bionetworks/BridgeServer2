package org.sagebionetworks.bridge.models.upload;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonDeserialize(builder = UploadRequest.Builder.class)
public class UploadRequest implements BridgeEntity {
    private final String name;
    private final long contentLength;
    private final String contentMd5;
    private final String contentType;
    private final boolean encrypted;
    private final ObjectNode metadata;
    private final boolean zipped;
    private final String instanceGuid;
    private final DateTime eventTimestamp;

    private UploadRequest(String name, long contentLength, String contentMd5, String contentType, boolean encrypted,
            ObjectNode metadata, boolean zipped, String instanceGuid, DateTime eventTimestamp) {
        this.name = name;
        this.contentLength = contentLength;
        this.contentMd5 = contentMd5;
        this.contentType = contentType;
        this.encrypted = encrypted;
        this.metadata = metadata;
        this.zipped = zipped;
        this.instanceGuid = instanceGuid;
        this.eventTimestamp = eventTimestamp;
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

    /**
     * Metadata fields for this upload, as submitted by the app. This corresponds with the
     * uploadMetadataFieldDefinitions configured in the app.
     */
    public ObjectNode getMetadata() {
        return metadata;
    }

    /** True if the upload is zipped. False if it is a single file. If not specified, defaults to true. */
    public boolean isZipped() {
        return zipped;
    }
    
    public String getInstanceGuid() {
        return instanceGuid;
    }
    
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public static class Builder {
        private String name;
        private Long contentLength;
        private String contentMd5;
        private String contentType;
        private Boolean encrypted;
        private ObjectNode metadata;
        private Boolean zipped;
        private String instanceGuid;
        private DateTime eventTimestamp;

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

        public Builder withMetadata(ObjectNode metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder withZipped(Boolean zipped) {
            this.zipped = zipped;
            return this;
        }
        
        public Builder withInstanceGuid(String instanceGuid) {
            this.instanceGuid = instanceGuid;
            return this;
        }
        
        public Builder withEventTimestamp(DateTime eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
            return this;
        }

        public UploadRequest build() {
            // contentLength defaults to 0.
            long actualContentLength = contentLength != null ? contentLength : 0;

            // encrypted and zipped default to true.
            boolean actualEncrypted = encrypted != null ? encrypted : true;
            boolean actualZipped = zipped != null ? zipped : true;

            return new UploadRequest(name, actualContentLength, contentMd5, contentType, actualEncrypted, metadata,
                    actualZipped, instanceGuid, eventTimestamp);
        }
    }
}
