package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import org.joda.time.DateTime;

/**
 * Older DynamoDB models use a Long where we really intended to be working with 
 * a DateTime (we stored time as a long so we could perform queries on this 
 * column; DDB by default uses an ISO 8601 string representation of Date).
 */
public class DateTimeToLongMarshaller implements DynamoDBTypeConverter<Long, DateTime> {

    @Override
    public Long convert(DateTime dateTime) {
      return (dateTime == null) ? null : dateTime.getMillis();
    }

    @Override
    public DateTime unconvert(Long value) {
      return (value == null) ? null : new DateTime(value);
    }

}
