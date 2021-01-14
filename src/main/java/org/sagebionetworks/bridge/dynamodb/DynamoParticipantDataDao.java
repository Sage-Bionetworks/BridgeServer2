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
public class DynamoParticipantDataDao {
    // TODO: likewise to the question in DynamoParticipantState, does this implement ReportDataDao?
    // we'd have to overload the default functions with params that don't include times/dates

    private DynamoDBMapper mapper;

    @Resource(name = "participantStateMapper")
    final void setParticipantStateMapper(DynamoDBMapper participantStateMapper) {
        this.mapper = participantStateMapper;
    }

    public ResourceList<DynamoParticipantData> getReportData(ReportDataKey key) { //TODO: <? extends ReportData>
        checkNotNull(key);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        //hashKey.setKey(key.getKeyString());

        DynamoDBQueryExpression<DynamoParticipantData> query =
                new DynamoDBQueryExpression<DynamoParticipantData>().withHashKeyValues(hashKey);
        List<DynamoParticipantData> results = mapper.query(DynamoParticipantData.class, query);

        return new ResourceList<DynamoParticipantData>(results);
    }

    // getReportDataV4()

    // saveReportdata()
    public void saveReportData(ReportData reportData) {
        checkNotNull(reportData);
        mapper.save(reportData);
    }

    // deleteReportData()
    // deleteReportDataRecord()

    //TODO: organize imports once more finalized
}
