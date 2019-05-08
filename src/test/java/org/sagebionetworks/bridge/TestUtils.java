package org.sagebionetworks.bridge;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public class TestUtils {

    private static class CustomServletInputStream extends ServletInputStream {
        private ByteArrayInputStream buffer;
        public CustomServletInputStream(String content) {
            this.buffer = new ByteArrayInputStream(content.getBytes());
        }
        @Override
        public int read() throws IOException {
            return buffer.read();
        }
        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }
        @Override
        public boolean isReady() {
            return true;
        }
        @Override
        public void setReadListener(ReadListener listener) {
            throw new RuntimeException("Not implemented");
        }
    }
    
    public static ServletInputStream toInputStream(String content) {
        return new CustomServletInputStream(content);
    }
    
    public static void mockRequestBody(HttpServletRequest mockRequest, JsonNode node) throws Exception {
        ServletInputStream stream = new CustomServletInputStream(node.toString());
        when(mockRequest.getInputStream()).thenReturn(stream);
    }
    
    public static void mockRequestBody(HttpServletRequest mockRequest, Object object) throws Exception {
        String json = new ObjectMapper().writeValueAsString(object);
        ServletInputStream stream = new CustomServletInputStream(json);
        when(mockRequest.getInputStream()).thenReturn(stream);
    }
    
    public static String createJson(String json, Object... args) {
        return String.format(json.replaceAll("'", "\""), args);
    }
    
    /**
     * The correctness of annotations on controller methods is very important, so here is a utilty 
     * to add verification to tests.
     */
    private static <A extends Annotation, C extends Class<A>> A assertMethodAnn(Class<?> controller,
            String methodName, Class<A> annClazz) throws Exception {
        // For simplicity sake, avoid matching arguments. Controllers don't use method overloading.
        Method[] methods = controller.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                A ann = AnnotationUtils.findAnnotation(method, annClazz);
                assertNotNull(ann);
                return ann;
            }
        }
        fail("Did not find method: " + methodName);
        return null;
    }    
    
    public static void assertCrossOrigin(Class<?> controller) {
        Annotation ann = AnnotationUtils.findAnnotation(controller, CrossOrigin.class);
        assertNotNull(ann);
    }
    
    public static void assertGet(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, GetMapping.class);
    }
    
    public static void assertPost(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
    }

    public static void assertDelete(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, DeleteMapping.class);
    }
    
    /**
     * Create calls in our API are POSTs that return 201 (Created).
     */
    public static void assertCreate(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
        ResponseStatus status = assertMethodAnn(controller, methodName, ResponseStatus.class);
        assertEquals(status.code(), HttpStatus.CREATED);        
    }
    
    public static String randomName(Class<?> clazz) {
        return "test-" + clazz.getSimpleName().toLowerCase() + "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }
    
    public static final StudyParticipant getStudyParticipant(Class<?> clazz) {
        String randomName = TestUtils.randomName(clazz);
        return new StudyParticipant.Builder()
                .withFirstName("FirstName")
                .withLastName("LastName")
                .withExternalId("externalId")
                .withEmail("bridge-testing+"+randomName+"@sagebase.org")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1"))
                .withAttributes(new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build())
                .withLanguages(ImmutableList.of("fr")).build();
    }
    
}
