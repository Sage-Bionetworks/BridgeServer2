package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;

@JsonDeserialize
public class ShippingConfirmation {
    @JacksonXmlProperty(isAttribute = true)
    public String OrderNumber;

    @JacksonXmlProperty(isAttribute = true)
    public String Shipper;

    @JacksonXmlProperty(isAttribute = true)
    public String ShipVia;

    @JacksonXmlProperty(isAttribute = true)
    public String ShipDate;

    @JacksonXmlProperty(isAttribute = true)
    public String ClientID;

    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Item> Item;

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShippingConfirmation that = (ShippingConfirmation) o;
        return Objects.equal(OrderNumber, that.OrderNumber) && Objects.equal(
                Shipper, that.Shipper) && Objects.equal(ShipVia, that.ShipVia) &&
                Objects.equal(ShipDate, that.ShipDate) && Objects.equal(
                ClientID, that.ClientID) && Objects.equal(Item, that.Item);
    }

    @Override public int hashCode() {
        return Objects.hashCode(OrderNumber, Shipper, ShipVia, ShipDate, ClientID, Item);
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("OrderNumber", OrderNumber)
                .add("Shipper", Shipper)
                .add("ShipVia", ShipVia)
                .add("ShipDate", ShipDate)
                .add("ClientID", ClientID)
                .add("Item", Item)
                .toString();
    }
    
    @JsonDeserialize
    public static class Item {
        @JacksonXmlProperty(isAttribute = true)
        public String ItemNumber;

        @JacksonXmlProperty(isAttribute = true)
        public String LotNumber;

        @JacksonXmlProperty(isAttribute = true)
        public String ExpireDate;

        @JacksonXmlProperty(isAttribute = true)
        public int ShippedQty;
    
        @JacksonXmlProperty(isAttribute = true)
        public String TubeSerial;
    
        @JacksonXmlProperty(isAttribute = true)
        public String ReturnTracking;
    
        public void setTubeSerial(String tubeSerial) {
            TubeSerial = tubeSerial;
        }
    
        public void setReturnTracking(String returnTracking) {
            ReturnTracking = returnTracking;
        }
    
        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Item item = (Item) o;
            return ShippedQty == item.ShippedQty && Objects.equal(ItemNumber, item.ItemNumber) &&
                    Objects.equal(LotNumber, item.LotNumber) && Objects.equal(
                    ExpireDate, item.ExpireDate) && Objects.equal(TubeSerial, item.TubeSerial) &&
                    Objects.equal(ReturnTracking, item.ReturnTracking);
        }

        @Override public int hashCode() {
            return Objects.hashCode(ItemNumber, LotNumber, ExpireDate, ShippedQty, TubeSerial, ReturnTracking);
        }

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("ItemNumber", ItemNumber)
                    .add("LotNumber", LotNumber)
                    .add("ExpireDate", ExpireDate)
                    .add("ShippedQty", ShippedQty)
                    .add("TubeSerial", TubeSerial)
                    .add("ReturnTracking", ReturnTracking)
                    .toString();
        }
    }
}
