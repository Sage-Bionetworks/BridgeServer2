package org.sagebionetworks.bridge.json;

import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.MimeType;

import com.fasterxml.jackson.core.JsonGenerator;

public class MimeTypeSerializerTest {
    
    @Mock
    private JsonGenerator mockJGen;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void test() throws Exception {
        new MimeTypeSerializer().serialize(MimeType.TEXT, mockJGen, null);
        verify(mockJGen).writeString(stringCaptor.capture());
        assertEquals(stringCaptor.getValue(), "text/plain");
    }
}
