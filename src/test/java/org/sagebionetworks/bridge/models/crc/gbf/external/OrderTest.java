package org.sagebionetworks.bridge.models.crc.gbf.external;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.Lists;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

public class OrderTest {
    @Test
    public void serialize() throws JsonProcessingException {
        Order o = new Order(true, "orderNumber", "clientAccount", LocalDate.now(),
                new Order.ShippingInfo(new Order.ShippingInfo.Address(
                        "Patient Name", "123 Abc St", "Apt 456", "New York",
                        "NY", "10001", "United States", "(858) 111-1234")
                        , "Fedex 2Day AM"), new Order.LineItem("FM-00049", 1));
        ObjectWriter m = new XmlMapper().writerWithDefaultPrettyPrinter();
        m.writeValueAsString(o);

        List<Order> orderList = Lists.newArrayList(o, o);
        m.writeValueAsString(orderList);

        Order.Orders orders = new Order.Orders(Lists.newArrayList(o, o));
        m.writeValueAsString(orders);
    }

    @Test
    public void deserializeShipConf() throws JsonProcessingException {
        ObjectMapper m = new XmlMapper()
                .registerModule(new JodaModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        String s = "<ShippingConfirmation OrderNumber=\"ID-000802\" Shipper=\"791113\" ShipVia=\"FedEx Ground\" ShipDate=\"3/23/2016\" ClientID=\"A10056001\">\n" +
                "\t\t<Tracking>016050765404963</Tracking>\n" +
                "\t\t<Item ItemNumber=\"1 Pack Kit\" LotNumber=\"J3AR6-10/31/2016\" ExpireDate=\"2016-10-31\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456789</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789012</ReturnTracking>\n" +
                "\t\t</Item>\n" +
                "\t\t<Item ItemNumber=\"1 Pack Kit\" LotNumber=\"J3AR6-10/31/2016\" ExpireDate=\"2016-10-31\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456790</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789023</ReturnTracking>\n" +
                "\t\t</Item> \n" +
                "\t</ShippingConfirmation>";
        ShippingConfirmation shippingConfirmation = m.readValue(s, ShippingConfirmation.class);
        m.writerWithDefaultPrettyPrinter().writeValueAsString(shippingConfirmation);
    }

    @Test
    public void deserializeShipConfs() throws JsonProcessingException {
        String s = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ShippingConfirmations>\n" +
                "\t<ShippingConfirmation OrderNumber=\"ID-000802\" Shipper=\"791113\" ShipVia=\"FedEx Ground\" ShipDate=\"3/23/2016\" ClientID=\"A10056001\">\n" +
                "\t\t<Tracking>016050765404963</Tracking>\n" +
                "\t\t<Item ItemNumber=\"1 Pack Kit\" LotNumber=\"J3AR6-10/31/2016\" ExpireDate=\"2016-10-31\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456789</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789012</ReturnTracking>\n" +
                "\t\t</Item>\n" +
                "\t\t<Item ItemNumber=\"1 Pack Kit\" LotNumber=\"J3AR6-10/31/2016\" ExpireDate=\"2016-10-31\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456790</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789023</ReturnTracking>\n" +
                "\t\t</Item> \n" +
                "\t</ShippingConfirmation>\n" +
                "\t<ShippingConfirmation OrderNumber=\"ID-000804\" Shipper=\"791203\" ShipVia=\"FedEx Overnight\" ShipDate=\"3/23/2016\" ClientID=\"A10000018\">\n" +
                "\t\t<Tracking>016050456760505</Tracking>\n" +
                "\t\t<Tracking>016050456504567</Tracking>\n" +
                "\t\t<Item ItemNumber=\"5 Pack Kit\" LotNumber=\"J3A63-09/30/2016\" ExpireDate=\"2016-09-30\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456791</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456792</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456793</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456794</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456795</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789034</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789045</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789056</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789067</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789078</ReturnTracking>\n" +
                "\t\t</Item>\n" +
                "\t\t<Item ItemNumber=\"5 Pack Kit\" LotNumber=\"J3A63-09/30/2016\" ExpireDate=\"2016-09-30\" ShippedQty=\"1\">\n" +
                "\t\t\t<TubeSerial>T123456796</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456797</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456798</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456799</TubeSerial>\n" +
                "\t\t\t<TubeSerial>T123456800</TubeSerial>\n" +
                "\t\t\t<ReturnTracking>123456789089</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789090</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789101</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789112</ReturnTracking>\n" +
                "\t\t\t<ReturnTracking>123456789123</ReturnTracking>\n" +
                "\t\t</Item>\n" +
                "\t</ShippingConfirmation>\n" +
                "</ShippingConfirmations>\n";
    
        XmlMapper m = new XmlMapper();
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    
    
        ShippingConfirmations shippingConfirmation = m.readValue(s, ShippingConfirmations.class);
        JsonNode node = m.readTree(s);
    
        m.writerWithDefaultPrettyPrinter().writeValueAsString(shippingConfirmation);
        new ObjectMapper().writeValueAsString(shippingConfirmation);
    }

    @Test
    public void deserializeError() throws JsonProcessingException {
        String s = "<Response>\r\n  <Error>Failed to parse date range</Error>\r\n</Response>";
    
        XmlMapper m = new XmlMapper();
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    
        JsonNode node = m.readTree(s);
    
        node.get("Error").asText();
    }
}