package LDV.java.nl.rijksoverheid.moz.logboekdataverwerking;

import io.opentelemetry.api.trace.StatusCode;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class LogboekContext {

    private String ProcessingActivityId;
    private String DataSubjectId;
    private String DataSubjectType;
    private StatusCode status;

    public String getProcessingActivityId() {
        return ProcessingActivityId;
    }

    public void setProcessingActivityId(String processingActivityId) {
        ProcessingActivityId = processingActivityId;
    }

    public String getDataSubjectId() {
        return DataSubjectId;
    }

    public void setDataSubjectId(String dataSubjectId) {
        DataSubjectId = dataSubjectId;
    }

    public String getDataSubjectType() {
        return DataSubjectType;
    }

    public void setDataSubjectType(String dataSubjectType) {
        DataSubjectType = dataSubjectType;
    }

    public StatusCode getStatus() {
        return status;
    }

    public void setStatus(StatusCode status) {
        this.status = status;
    }

}
