package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertSame;

import javax.persistence.PersistenceException;

import org.testng.annotations.Test;

public class AccountSecretsExceptionConverterTest {
    
    @Test
    public void before() {
        AccountSecretsExceptionConverter converter = new AccountSecretsExceptionConverter();
        PersistenceException ex = new PersistenceException();
        RuntimeException returnValue = converter.convert(ex, new Object());
        assertSame(returnValue, ex);
    }

}
