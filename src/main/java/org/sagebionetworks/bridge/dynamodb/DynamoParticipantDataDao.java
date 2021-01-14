package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.sagebionetworks.bridge.models.ParticipantData;
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

    public ResourceList<? extends ParticipantData> getReportData(String userId, String configId) {
        checkNotNull(userId);
        checkNotNull(configId);

        DynamoParticipantData hashKey = new DynamoParticipantData();
        hashKey.setUserId(userId);
        // TODO: in DynamoReportData, a ReportDataKey is used. Here, is it sufficient to use userId? If so, remove configId or add to hashKey

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
