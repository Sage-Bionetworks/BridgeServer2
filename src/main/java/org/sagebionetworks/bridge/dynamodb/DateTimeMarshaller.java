package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import org.joda.time.DateTime;

public class DateTimeMarshaller implements DynamoDBTypeConverter<String, DateTime> {

    @Override
    public String convert(DateTime dateTime) {
        return dateTime.toString();
    }

    @Override
    public DateTime unconvert(String dateTimeString) {
        return DateTime.parse(dateTimeString);
    }
}
