package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

public class UploadFormatHandlerTest {
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2020-04-29T16:31:19.146-0700").getMillis();

    private IosSchemaValidationHandler2 mockV1LegacyHandler;
    private GenericUploadFormatHandler mockV2GenericHandler;
    private UploadFormatHandler uploadFormatHandler;

    @BeforeMethod
    public void setup() {
        mockV1LegacyHandler = mock(IosSchemaValidationHandler2.class);
        mockV2GenericHandler = mock(GenericUploadFormatHandler.class);

        uploadFormatHandler = new UploadFormatHandler();
        uploadFormatHandler.setV1LegacyHandler(mockV1LegacyHandler);
        uploadFormatHandler.setV2GenericHandler(mockV2GenericHandler);

        // Mock now.
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterMethod
    public void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    private static UploadValidationContext setupContextWithFormat(UploadFormat format) {
        // Make info.json.
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        if (format != null) {
            infoJsonNode.put(UploadUtil.FIELD_FORMAT, format.toString());
        }

        // Make context.
        UploadValidationContext context = new UploadValidationContext();
        context.setInfoJsonNode(infoJsonNode);
        context.setHealthDataRecord(HealthDataRecord.create());
        return context;
    }

    @Test
    public void defaultFormat() throws Exception {
        UploadValidationContext context = setupContextWithFormat(null);
        uploadFormatHandler.handle(context);
        verify(mockV1LegacyHandler).handle(context);
        verifyZeroInteractions(mockV2GenericHandler);
        verifyCommonAttributes(context);
    }

    @Test
    public void v1Legacy() throws Exception {
        UploadValidationContext context = setupContextWithFormat(UploadFormat.V1_LEGACY);
        uploadFormatHandler.handle(context);
        verify(mockV1LegacyHandler).handle(context);
        verifyZeroInteractions(mockV2GenericHandler);
        verifyCommonAttributes(context);
    }

    @Test
    public void v2Generic() throws Exception {
        UploadValidationContext context = setupContextWithFormat(UploadFormat.V2_GENERIC);
        uploadFormatHandler.handle(context);
        verify(mockV2GenericHandler).handle(context);
        verifyZeroInteractions(mockV1LegacyHandler);
        verifyCommonAttributes(context);
    }

    @Test
    public void noInfoJson() throws Exception {
        // Make context w/o info.json.
        UploadValidationContext context = new UploadValidationContext();
        context.setHealthDataRecord(HealthDataRecord.create());

        // Execute and validate.
        uploadFormatHandler.handle(context);
        verifyZeroInteractions(mockV1LegacyHandler, mockV2GenericHandler);
        verifyCommonAttributes(context);
    }

    private static void verifyCommonAttributes(UploadValidationContext ctx) {
        HealthDataRecord record = ctx.getHealthDataRecord();
        assertEquals(record.getCreatedOn().longValue(), MOCK_NOW_MILLIS);
    }
}
