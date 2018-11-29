/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/

package fr.gouv.vitam.worker.core.plugin.preservation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.gouv.vitam.batch.report.model.ActionTypePreservation;
import fr.gouv.vitam.batch.report.model.AnalyseResultPreservation;
import fr.gouv.vitam.batch.report.model.PreservationStatus;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class OutputPreservation {
    @JsonProperty("result")
    private String result;
    @JsonProperty("error")
    private String error;
    @JsonProperty("executed")
    private String executed;
    @JsonProperty("input")
    private InputPreservation input;
    @JsonProperty("outputName")
    private String outputName;
    @JsonProperty("status")
    private PreservationStatus status;
    @JsonProperty("analyseResult")
    private AnalyseResultPreservation analyseResult;
    @JsonProperty("action")
    private ActionTypePreservation action;

    public OutputPreservation() {
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getExecuted() {
        return executed;
    }

    public void setExecuted(String executed) {
        this.executed = executed;
    }

    public InputPreservation getInputPreservation() {
        return input;
    }

    public void setInputPreservation(InputPreservation input) {
        this.input = input;
    }

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public PreservationStatus getStatus() {
        return status;
    }

    public void setStatus(PreservationStatus status) {
        this.status = status;
    }

    public ActionTypePreservation getAction() {
        return action;
    }

    public void setAction(ActionTypePreservation action) {
        this.action = action;
    }

    public AnalyseResultPreservation getAnalyseResult() {
        return analyseResult;
    }

    public void setAnalyseResult(AnalyseResultPreservation analyseResult) {
        this.analyseResult = analyseResult;
    }

    @Override
    public String toString() {
        return "Output{" +
            "result='" + result + '\'' +
            ", error='" + error + '\'' +
            ", executed='" + executed + '\'' +
            ", input=" + input +
            ", outputName='" + outputName + '\'' +
            ", status=" + status +
            ", analyseResult=" + analyseResult +
            ", action=" + action +
            '}';
    }
}
