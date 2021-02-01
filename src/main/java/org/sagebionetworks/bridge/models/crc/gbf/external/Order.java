package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.joda.time.LocalDate;

import java.util.List;

public class Order {
    @JacksonXmlProperty(isAttribute = true)
    private boolean Test;

    public final String OrderNumber;

    public final String ClientAccount;

    public final String OrderDate;

    public final ShippingInfo ShippingInfo;

    public final LineItem LineItem;

    public Order(boolean test, String orderNumber, String clientAccount, LocalDate orderDate, Order.ShippingInfo shippingInfo, Order.LineItem lineItem) {
        OrderNumber = orderNumber;
        ClientAccount = clientAccount;
        OrderDate = orderDate.toString("MM/dd/yyyy");
        ShippingInfo = shippingInfo;
        LineItem = lineItem;
        Test = test;
    }

    public static class LineItem {
        public final String ItemNumber;
        public final int ItemQuantity;

        public LineItem(String itemNumber, int itemQuantity) {
            ItemNumber = itemNumber;
            ItemQuantity = itemQuantity;
        }
    }
    public static class ShippingInfo {
        public final Address Address;
        public final String ShipMethod;

        public ShippingInfo(ShippingInfo.Address address, String shipMethod) {
            Address = address;
            ShipMethod = shipMethod;
        }

        public static class Address {
            public final String Company;
            public final String AddressLine1;
            public final String AddressLine2;
            public final String City;
            public final String State;
            public final String ZipCode;
            public final String Country;
            public final String PhoneNumber;

            public Address(String company, String addressLine1, String addressLine2, String city, String state,
                           String zipCode, String country, String phoneNumber) {
                Company = company;
                AddressLine1 = addressLine1;
                AddressLine2 = addressLine2;
                City = city;
                State = state;
                ZipCode = zipCode;
                Country = country;
                PhoneNumber = phoneNumber;
            }
        }
    }

    public static class Orders {
        @JacksonXmlElementWrapper(useWrapping = false)
        public final List<Order> Order;

        public Orders(List<Order> orders) {
            Order = orders;
        }
    }
}
