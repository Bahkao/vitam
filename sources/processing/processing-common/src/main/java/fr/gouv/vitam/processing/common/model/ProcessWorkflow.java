/**
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
 */
package fr.gouv.vitam.processing.common.model;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.ProcessExecutionStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;

/**
 * Proccess Workflow contains a different operations and status attribute
 */
public class ProcessWorkflow {


    private static final String MANDATORY_PARAMETER = "Mandatory parameter";

    private String operationId;

    private String messageIdentifier;
    
    private String prodService;

    private StatusCode globalStatusCode = StatusCode.UNKNOWN;

    private Map<String, ProcessStep> orderedProcessStep = new LinkedHashMap<>();

    private Date processDate = new Date();

    private ProcessAction executionMode;

    private ProcessExecutionStatus executionStatus = ProcessExecutionStatus.RUNNING;

    private LogbookTypeProcess logbookTypeProcess;

    private Integer tenantId;

    /**
     * @return the globalStatusCode
     */
    public StatusCode getGlobalStatusCode() {
        return globalStatusCode;
    }

    /**
     * @param StatusCode the StatusCode to set
     *
     * @return this
     */
    public ProcessWorkflow setGlobalStatusCode(StatusCode StatusCode) {
        ParametersChecker.checkParameter(MANDATORY_PARAMETER, globalStatusCode);
        // TODO FATAL or KO ---> OK or warning (after replay worklow 's action)
        // update globalStatus
        this.globalStatusCode = globalStatusCode.compareTo(StatusCode) > 0
            ? globalStatusCode : StatusCode;

        return this;
    }


    /**
     * @return the processDate
     */
    public Date getProcessDate() {
        return processDate;
    }

    /**
     * @param processDate the processDate to set
     *
     * @return this
     */
    public ProcessWorkflow setProcessDate(Date processDate) {
        this.processDate = processDate;
        return this;
    }

    /**
     * @return the operationId
     */

    public String getOperationId() {
        return operationId;
    }

    /**
     * @param operationId the operationId to set
     *
     * @return this
     */

    public ProcessWorkflow setOperationId(String operationId) {
        this.operationId = operationId;
        return this;
    }

    /**
     * @return the orderedProcessStep
     */
    public Map<String, ProcessStep> getOrderedProcessStep() {
        return orderedProcessStep;
    }

    /**
     * @param orderedProcessStep the orderedProcessStep to set
     *
     * @return this
     */
    public ProcessWorkflow setOrderedProcessStep(Map<String, ProcessStep> orderedProcessStep) {
        this.orderedProcessStep = orderedProcessStep;
        return this;
    }

    /**
     * @return the executionMode
     */
    public ProcessAction getExecutionMode() {
        return executionMode;
    }

    /**
     * @param executionMode the executionMode to set
     *
     * @return this
     */
    public ProcessWorkflow setExecutionMode(ProcessAction executionMode) {
        this.executionMode = executionMode;
        return this;
    }

    /**
     * @return the executionStatus
     */
    public ProcessExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    /**
     * @param executionStatus the executionStatus to set
     *
     * @return this
     */
    public ProcessWorkflow setExecutionStatus(ProcessExecutionStatus executionStatus) {
        if (executionStatus != null) {
            this.executionStatus = executionStatus;
        }
        return this;
    }
    

    /**
     * @return the messageIdentifier
     */
    public String getMessageIdentifier() {
        return messageIdentifier;
    }

    /**
     * @param messageIdentifier the messageIdentifier to set
     *
     * @return this
     */
    public ProcessWorkflow setMessageIdentifier(String messageIdentifier) {
        this.messageIdentifier = messageIdentifier;
        return this;
    }

    /**
     * @return the prodService
     */

    public String getProdService() {
        return prodService;
    }

    /**
     * @param prodService the prodService to set
     *
     * @return this
     */    
    public ProcessWorkflow setProdService(String prodService) {
        this.prodService = prodService;
        return this;
    }
    

    /**
     * @return the logbookTypeProcess
     */
    public LogbookTypeProcess getLogbookTypeProcess() {
        return logbookTypeProcess;
    }

    /**
     * @param logbookTypeProcess the logbookTypeProcess
     * @return this
     */
    public ProcessWorkflow setLogbookTypeProcess(LogbookTypeProcess logbookTypeProcess) {
        this.logbookTypeProcess = logbookTypeProcess;
        return this;
    }

    /**
     * @return the tenant
     */
    public Integer getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantId to set
     * @return this
     */
    public ProcessWorkflow setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
        return this;
    }
}
