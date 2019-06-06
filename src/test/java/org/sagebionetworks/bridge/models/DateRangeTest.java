package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;

public class DateRangeTest {
    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullStartDate() {
        new DateRange(null, LocalDate.parse("2015-08-19"));
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void nullEndDate() {
        new DateRange(LocalDate.parse("2015-08-15"), null);
    }

    @Test
    public void startDateBeforeEndDate() {
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-15"), LocalDate.parse("2015-08-19"));
        assertEquals(dateRange.getStartDate().toString(), "2015-08-15");
        assertEquals(dateRange.getEndDate().toString(), "2015-08-19");
    }

    @Test
    public void startDateSameAsEndDate() {
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-17"), LocalDate.parse("2015-08-17"));
        assertEquals(dateRange.getStartDate().toString(), "2015-08-17");
        assertEquals(dateRange.getEndDate().toString(), "2015-08-17");
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void startDateAfterEndDate() {
        new DateRange(LocalDate.parse("2015-08-19"), LocalDate.parse("2015-08-15"));
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"startDate\":\"2015-08-03\",\n" +
                "   \"endDate\":\"2015-08-07\"\n" +
                "}";

        // convert to POJO
        DateRange dateRange = BridgeObjectMapper.get().readValue(jsonText, DateRange.class);
        assertEquals(dateRange.getStartDate().toString(), "2015-08-03");
        assertEquals(dateRange.getEndDate().toString(), "2015-08-07");

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(dateRange);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 3);
        assertEquals(jsonMap.get("startDate"), "2015-08-03");
        assertEquals(jsonMap.get("endDate"), "2015-08-07");
        assertEquals(jsonMap.get("type"), "DateRange");
    }
}
