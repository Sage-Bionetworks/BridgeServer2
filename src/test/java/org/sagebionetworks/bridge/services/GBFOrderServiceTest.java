package org.sagebionetworks.bridge.services;

import static com.google.common.base.Charsets.UTF_8;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.services.GBFOrderService.GBF_API_KEY;
import static org.sagebionetworks.bridge.services.GBFOrderService.GBF_CONFIRMATION_URL;
import static org.sagebionetworks.bridge.services.GBFOrderService.GBF_ORDER_STATUS_URL;
import static org.sagebionetworks.bridge.services.GBFOrderService.GBF_PLACE_ORDER_URL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.crc.gbf.external.CheckOrderStatusRequest;
import org.sagebionetworks.bridge.models.crc.gbf.external.CheckOrderStatusResponse;
import org.sagebionetworks.bridge.models.crc.gbf.external.ConfirmShippingRequest;
import org.sagebionetworks.bridge.models.crc.gbf.external.ConfirmShippingResponse;
import org.sagebionetworks.bridge.models.crc.gbf.external.Order;
import org.sagebionetworks.bridge.models.crc.gbf.external.PlaceOrderResponse;
import org.sagebionetworks.bridge.models.crc.gbf.external.ShippingConfirmation;
import org.sagebionetworks.bridge.models.crc.gbf.external.ShippingConfirmations;

public class GBFOrderServiceTest {

    private static final String CONFIRMATION_URL = "https://example.com/conf";
    private static final String PLACE_URL = "https://example.com/place";
    private static final String STATUS_URL = "https://example.com/status";

    @InjectMocks
    @Spy
    private GBFOrderService service;

    @Mock
    private BridgeConfig mockBridgeConfig;

    private ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JodaModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private ObjectMapper xmlMapper = new XmlMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @BeforeMethod
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);
    
        when(mockBridgeConfig.get(GBF_PLACE_ORDER_URL)).thenReturn(PLACE_URL);
        when(mockBridgeConfig.get(GBF_CONFIRMATION_URL)).thenReturn(CONFIRMATION_URL);
        when(mockBridgeConfig.get(GBF_ORDER_STATUS_URL)).thenReturn(STATUS_URL);
    
        when(mockBridgeConfig.get(GBF_API_KEY)).thenReturn("apiKey");
    
        service.setBridgeConfig(mockBridgeConfig);
    }
    
    
    @Test
    public void placeOrder() throws IOException {
        Order mockOrder = mock(Order.class);
        
        PlaceOrderResponse response = new PlaceOrderResponse(true, null);
        HttpResponse mockResponse = createMockResponse(response);
        
        doReturn(mockResponse).when(service).postJson(eq(PLACE_URL), any(), any());
        
        service.placeOrder(mockOrder, true);
    
        verify(service).postJson(eq(PLACE_URL), any(), any());
        verify(service).handleGbfHttpStatusErrors(any());
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void placeOrderThrowsExceptionOnGbfResponseObjectSuccessFalse() throws IOException {
        Order mockOrder = mock(Order.class);
    
        PlaceOrderResponse response = new PlaceOrderResponse(false, null);
        HttpResponse mockResponse = createMockResponse(response);
    
        doReturn(mockResponse).when(service).postJson(eq(PLACE_URL), any(), any());
    
        service.placeOrder(mockOrder, true);
    }
    
    private HttpResponse createMockResponse(PlaceOrderResponse placeOrderResponse) throws IOException {
        HttpEntity mockEntity = mock(HttpEntity.class);
        doReturn(IOUtils.toInputStream(jsonMapper.writeValueAsString(placeOrderResponse), UTF_8))
                .when(mockEntity).getContent();
    
        StatusLine mockStatusLine = mock(StatusLine.class);
        doReturn(200).when(mockStatusLine).getStatusCode();
    
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockStatusLine).when(mockResponse).getStatusLine();
        doReturn(mockEntity).when(mockResponse).getEntity();
        
        return mockResponse;
    }

    @Test
    public void checkOrder() throws IOException {
        String order1 = "id1";
        String order2 = "id2";

        CheckOrderStatusResponse response = new CheckOrderStatusResponse(true, null, Lists.newArrayList());

        HttpEntity mockEntity = mock(HttpEntity.class);
        doReturn(IOUtils.toInputStream(jsonMapper.writeValueAsString(response), "UTF-8"))
                .when(mockEntity).getContent();

        StatusLine mockStatusLine = mock(StatusLine.class);
        doReturn(200).when(mockStatusLine).getStatusCode();
    
        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockStatusLine).when(mockResponse).getStatusLine();
        doReturn(mockEntity).when(mockResponse).getEntity();
    
        ArgumentCaptor<CheckOrderStatusRequest> captor = ArgumentCaptor.forClass(CheckOrderStatusRequest.class);
        doReturn(mockResponse).when(service).postJson(eq(STATUS_URL), any(), captor.capture());
    
        service.checkOrderStatus(order1, order2);
    
        verify(service).handleGbfHttpStatusErrors(any());
    
        assertEquals(Lists.newArrayList(order1, order2), captor.getValue().orderNumbers);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void handleGbfHttpStatusErrorsClientErrors() throws IOException {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(400);
        
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(IOUtils.toInputStream("Message", UTF_8));
        
        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(entity);
        
        service.handleGbfHttpStatusErrors(httpResponse);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void handleGbfHttpStatusErrorsServerErrors() throws IOException {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(500);
        
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(IOUtils.toInputStream("Message", UTF_8));
        
        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(entity);
        
        service.handleGbfHttpStatusErrors(httpResponse);
    }
    
    @Test
    public void getShippingConfirmations() throws IOException {
        ShippingConfirmation shippingConfirmation1 = new ShippingConfirmation();
        shippingConfirmation1.OrderNumber = "1";
        ShippingConfirmation.Item item1a = new ShippingConfirmation.Item();
        item1a.ItemNumber = "Item 1a";
        shippingConfirmation1.Item = Lists.newArrayList(item1a);
        
        ShippingConfirmation shippingConfirmation2 = new ShippingConfirmation();
        shippingConfirmation1.OrderNumber = "2";
        ShippingConfirmation.Item item2a = new ShippingConfirmation.Item();
        item2a.ItemNumber = "Item 2a";
        shippingConfirmation2.Item = Lists.newArrayList(item2a);


        ShippingConfirmations shippingConfirmations = new ShippingConfirmations();
        shippingConfirmations.shippingConfirmation = Lists.newArrayList(shippingConfirmation1, shippingConfirmation2);

        ConfirmShippingResponse confirmShippingResponse = new ConfirmShippingResponse(
                xmlMapper.writeValueAsString(shippingConfirmations));

        HttpEntity mockEntity = mock(HttpEntity.class);
        doReturn(IOUtils.toInputStream(jsonMapper.writeValueAsString(confirmShippingResponse), UTF_8))
                .when(mockEntity).getContent();

        StatusLine mockStatusLine = mock(StatusLine.class);
        doReturn(200).when(mockStatusLine).getStatusCode();

        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockStatusLine).when(mockResponse).getStatusLine();
        doReturn(mockEntity).when(mockResponse).getEntity();

        ArgumentCaptor<ConfirmShippingRequest> confirmShippingRequestArgumentCaptor =
                ArgumentCaptor.forClass(ConfirmShippingRequest.class);

        doReturn(mockResponse).when(service).postJson(eq(CONFIRMATION_URL), any(),
                confirmShippingRequestArgumentCaptor.capture());
        
        LocalDate startDate = LocalDate.now().minusDays(20);
        LocalDate endDate = LocalDate.now().plusDays(3);
        ShippingConfirmations shippingConfirmationsResult = service.requestShippingConfirmations(
                startDate, endDate);
        assertEquals(shippingConfirmation1, shippingConfirmationsResult.shippingConfirmation.get(0));
        assertEquals(shippingConfirmation2, shippingConfirmationsResult.shippingConfirmation.get(1));
        
        ConfirmShippingRequest confirmShippingRequest = confirmShippingRequestArgumentCaptor.getValue();
        assertEquals(startDate, confirmShippingRequest.startDate);
        assertEquals(endDate, confirmShippingRequest.endDate);
        
        verify(service).handleGbfHttpStatusErrors(any());
        verify(service).postJson(eq(CONFIRMATION_URL), any(), any());
    }


    @Test
    public void getShippingConfirmationsErrorXml() throws IOException {
        String errorMessage = "Failed to parse date range";
        String errorXml = "<Response><Error>" + errorMessage + "</Error></Response>";

        ConfirmShippingResponse confirmShippingResponse = new ConfirmShippingResponse(
                errorXml);

        HttpEntity mockEntity = mock(HttpEntity.class);
        doReturn(IOUtils.toInputStream(jsonMapper.writeValueAsString(confirmShippingResponse), UTF_8))
                .when(mockEntity).getContent();

        StatusLine mockStatusLine = mock(StatusLine.class);
        doReturn(200).when(mockStatusLine).getStatusCode();

        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockStatusLine).when(mockResponse).getStatusLine();
        doReturn(mockEntity).when(mockResponse).getEntity();

        doReturn(mockResponse).when(service).postJson(eq(CONFIRMATION_URL), any(), any());
        try {
            service.requestShippingConfirmations(
                    LocalDate.now().minusDays(2), LocalDate.now());
            fail("Exception expected");
        } catch (BadRequestException e) {
            assertEquals(errorMessage, e.getMessage());
        }
        verify(service).postJson(eq(CONFIRMATION_URL), any(), any());
    }

    @Test
    public void phones() {
        String phone = "206.547.2600";
        Phone p = new Phone(phone, "US");
        assertTrue(Phone.isValid(p));
    }
}
