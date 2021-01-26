package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.crc.gbf.external.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

@Component
public class GBFOrderService {
    private static Logger LOG = LoggerFactory.getLogger(GBFOrderService.class);

    public static String GBF_API_KEY = "gbf.api.key";
    public static String GBF_PLACE_ORDER_URL = "gbf.order.place.url";
    public static String GBF_ORDER_STATUS_URL = "gbf.order.status.url";
    public static String GBF_CONFIRMATION_URL = "gbf.ship.confirmation.url";

    public static String GBF_SHIPPING_ERROR_KEY = "Error";
    public static String GBF_SERVICE_ERROR_MESSAGE = "Error calling order service";

    private String gbfOrderUrl;
    private String getGbfOrderStatusUrl;
    private String gbfConfirmationUrl;
    private String gbfApiKey;

    private ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JodaModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ObjectMapper xmlMapper = new XmlMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Autowired
    final void setBridgeConfig(BridgeConfig config) {
        this.gbfApiKey = config.get(GBF_API_KEY);
        this.gbfOrderUrl = config.get(GBF_PLACE_ORDER_URL);
        this.getGbfOrderStatusUrl = config.get(GBF_ORDER_STATUS_URL);
        this.gbfConfirmationUrl = config.get(GBF_CONFIRMATION_URL);
    }

    public void placeOrder(Order order, boolean isTest) {
        String orderXml;
        try {
            orderXml = xmlMapper.writeValueAsString(new Order.Orders(Lists.newArrayList(order)));
        } catch (JsonProcessingException e) {
            LOG.error("placeOrder failed to serialize Order XML", e);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }

        HttpResponse httpResponse = postJson(gbfOrderUrl, gbfApiKey, new PlaceOrderRequest(orderXml, isTest));

        PlaceOrderResponse response;
        try {
            if (httpResponse.getStatusLine().getStatusCode() > 400) {
                LOG.warn("placeOrder received {} error with contents: {}", httpResponse.getStatusLine().getStatusCode(),
                        IOUtils.toString(httpResponse.getEntity().getContent(), StandardCharsets.UTF_8));
                throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
            }

            response = jsonMapper.readValue(httpResponse.getEntity().getContent(), PlaceOrderResponse.class);
        } catch (IOException e) {
            LOG.error("placeOrder failed to read contents of HttpResponse", e);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }

        if (!response.success) {
            LOG.warn("placeOrder received error from remote service: {}", response.message);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }
    }

    public CheckOrderStatusResponse checkOrderStatus(String... orderIds) {
        CheckOrderStatusRequest request = new CheckOrderStatusRequest(Arrays.asList(orderIds));

        HttpResponse httpResponse = postJson(getGbfOrderStatusUrl, gbfApiKey, request);

        try {
            if (httpResponse.getStatusLine().getStatusCode() > 400) {
                LOG.warn("placeOrder checkOrderStatus {} error with contents: {}", httpResponse.getStatusLine().getStatusCode(),
                        IOUtils.toString(httpResponse.getEntity().getContent(), StandardCharsets.UTF_8));
                throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
            }

            return jsonMapper.readValue(httpResponse.getEntity().getContent(), CheckOrderStatusResponse.class);
        } catch (IOException e) {
            LOG.error("checkOrderStatus could not parse response Json", e);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }
    }

    public ShippingConfirmations requestShippingConfirmations(LocalDate startDate, LocalDate endDate) {
        ConfirmShippingRequest requestBody = new ConfirmShippingRequest(startDate, endDate);

        HttpResponse response = postJson(gbfConfirmationUrl, gbfApiKey, requestBody);

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode > 400) {
            BridgeServiceException e;
            try {
                String resBody = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8.name());
                LOG.warn("Received exception with code: {} and response body: {}", statusCode, resBody);

                e = new BridgeServiceException(resBody, statusCode);
            } catch (Exception exception) {
                LOG.error("Unknown remote exception with code: {}", statusCode);
                e = new BridgeServiceException("Unknown Remote Exception", 500);
            }
            throw e;
        }

        return parseShippingConfirmations(response);
    }

    ShippingConfirmations parseShippingConfirmations(HttpResponse response) {
        String responseXml;
        try {
            ConfirmShippingResponse responseObj = jsonMapper.readValue(response.getEntity().getContent(), ConfirmShippingResponse.class);
            responseXml = responseObj.XML;
        } catch (IOException e) {
            LOG.error("parseShippingConfirmations failed to read Json from remote response", e);
            throw new BridgeServiceException("Error parsing response Json.");
        }
        return parseShippingConfirmations(responseXml);
    }

    // handles XML conforming to either <Response><Error>... or <ShippingConfirmations>
    ShippingConfirmations parseShippingConfirmations(String responseXml) {
        LOG.debug("responseXml: " + responseXml);
        try {
            JsonNode root = xmlMapper.readTree(responseXml);
            if (root.has(GBF_SHIPPING_ERROR_KEY)) {
                throw new BadRequestException(root.get(GBF_SHIPPING_ERROR_KEY).asText());
            }
        } catch (JsonProcessingException e) {
            LOG.error("parseShippingConfirmations failed to parse XML as tree", e);
            throw new BridgeServiceException("Error parsing response XML.");
        }

        try {
            return xmlMapper.readValue(responseXml, ShippingConfirmations.class);
        } catch (JsonProcessingException e) {
            LOG.error("parseShippingConfirmations failed while parsing ShippingConfirmations XML", e);
            throw new BridgeServiceException("Error parsing ShippingConfirmations XML.");
        }
    }


    HttpResponse postJson(String url, String bearerToken, Object jsonObj) {
        Request request = null;
        try {
            request = Request.Post(url)
                    .bodyString(jsonMapper.writeValueAsString(jsonObj), APPLICATION_JSON);
        } catch (JsonProcessingException e) {
            LOG.error("Error writing to Json when calling url: {}", url, e);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }
        request.addHeader("Authorization", "Bearer " + bearerToken);

        try {
            return request.execute().returnResponse();
        } catch (IOException e) {
            LOG.error("Error posting Json to url: {}", url, e);
            throw new BridgeServiceException(GBF_SERVICE_ERROR_MESSAGE);
        }
    }
}

