package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.time.DateUtils;

/**
 * This handler reads the "format" field form info.json, and then determines whether to call IosSchemaValidationHandler
 * or GenericUploadFormatHandler. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataFileMap} and updates the existing record in
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecord}.
 */
@Component
public class UploadFormatHandler implements UploadValidationHandler {
    private IosSchemaValidationHandler2 v1LegacyHandler;
    private GenericUploadFormatHandler v2GenericHandler;

    /** V1 Legacy handler, aka IosSchemaValidationHandler. */
    @Autowired
    public final void setV1LegacyHandler(IosSchemaValidationHandler2 v1LegacyHandler) {
        this.v1LegacyHandler = v1LegacyHandler;
    }

    /** V2 Generic handler, aka GenericUploadFormatHandler. */
    @Autowired
    public final void setV2GenericHandler(GenericUploadFormatHandler v2GenericHandler) {
        this.v2GenericHandler = v2GenericHandler;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        // Set the default createdOn to the current time. The other handlers can overwrite it as needed.
        // Don't set a timezone, since it's indeterminate.
        HealthDataRecord record = context.getHealthDataRecord();
        record.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());

        JsonNode infoJson = context.getInfoJsonNode();
        if (infoJson == null) {
            // info.json is NOT guaranteed to exist. If it doesn't, assume there's no format, and skip the below.
            return;
        }

        // Parse upload format. If not specified, it defaults to v1_legacy.
        UploadFormat format = UploadFormat.V1_LEGACY;
        String formatString = JsonUtils.asText(infoJson, UploadUtil.FIELD_FORMAT);
        if (StringUtils.isNotBlank(formatString)) {
            // Note that our API has enums as all lower case by convention. However, Java enum convention is all
            // uppercase.
            format = UploadFormat.valueOf(formatString.toUpperCase());
        }

        // Choose handler based on format.
        switch (format) {
            case V1_LEGACY:
                v1LegacyHandler.handle(context);
                break;
            case V2_GENERIC:
                v2GenericHandler.handle(context);
                break;
        }
    }
}
