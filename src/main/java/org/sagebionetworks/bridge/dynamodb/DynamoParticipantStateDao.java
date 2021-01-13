package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class DynamoParticipantStateDao {
    // TODO: likewise to the question in DynamoParticipantState, does this implement ReportDataDao?

    private DynamoDBMapper mapper;

    @Resource(name = "participantStateMapper")
    final void setParticipantStateMapper(DynamoDBMapper participantStateMapper) {
        this.mapper = participantStateMapper;
    }

    public ResourceList<DynamoParticipantState> getReportData(ReportDataKey key) { //TODO: <? extends ReportData>
        checkNotNull(key);

        DynamoParticipantState hashKey = new DynamoParticipantState();
        hashKey.setKey(key.getKeyString());

        DynamoDBQueryExpression<DynamoParticipantState> query =
                new DynamoDBQueryExpression<DynamoParticipantState>().withHashKeyValues(hashKey);
        List<DynamoParticipantState> results = mapper.query(DynamoParticipantState.class, query);

        return new ResourceList<DynamoParticipantState>(results);
    }
    // getReportDataV4()
    // saveReportdata()
    // deleteReportData()
    // deleteReportDataRecord()
}
