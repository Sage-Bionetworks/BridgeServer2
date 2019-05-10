package org.sagebionetworks.bridge;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.CREATED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;

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
        assertEquals(status.code(), CREATED);        
    }
    
    /**
     * Create calls in our API are POSTs that return 202 (Accepted).
     */
    public static void assertAccept(Class<?> controller, String methodName) throws Exception {
        assertMethodAnn(controller, methodName, PostMapping.class);
        ResponseStatus status = assertMethodAnn(controller, methodName, ResponseStatus.class);
        assertEquals(status.code(), ACCEPTED);        
    }
    
    public static void mockRequestBody(HttpServletRequest mockRequest, String json) throws Exception {
        ServletInputStream stream = new CustomServletInputStream(json);
        when(mockRequest.getInputStream()).thenReturn(stream);
    }

    public static void mockRequestBody(HttpServletRequest mockRequest, Object object) throws Exception {
        // Use BridgeObjectMapper or you will get an error when serializing objects with a filter 
        String json = BridgeObjectMapper.get().writeValueAsString(object);
        ServletInputStream stream = new CustomServletInputStream(json);
        when(mockRequest.getInputStream()).thenReturn(stream);
    }
    
    /**
     * Mocks this DAO method behavior so that you can verify that AccountDao.editAccount() was called, and 
     * that your mock account was correctly edited.
     * @param mockAccountDao
     *      A mocked version of the AccountDao interface
     * @param mockAccount
     *      A mocked version of the Account interface
     */
    @SuppressWarnings("unchecked")
    public static void mockEditAccount(AccountDao mockAccountDao, Account mockAccount) {
        mockingDetails(mockAccountDao).isMock();
        mockingDetails(mockAccount).isMock();
        doAnswer(invocation -> {
            Consumer<Account> accountEdits = invocation.getArgumentAt(2, Consumer.class);
            accountEdits.accept(mockAccount);
            return null;
        }).when(mockAccountDao).editAccount(any(), any(), any());
    }
}
