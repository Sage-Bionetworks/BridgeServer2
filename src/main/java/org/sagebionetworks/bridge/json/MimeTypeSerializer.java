package org.sagebionetworks.bridge.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import org.sagebionetworks.bridge.models.apps.MimeType;

@SuppressWarnings({"serial"})
public class MimeTypeSerializer extends StdSerializer<MimeType> {

    public MimeTypeSerializer() {
        super(MimeType.class, false);
    }
    
    @Override
    public void serialize(MimeType type, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(type.toString());
    }

}
