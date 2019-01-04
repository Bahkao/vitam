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
package fr.gouv.vitam.processing.management.client;


import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.client.MockOrRestClient;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.InternalServerException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.exception.WorkflowNotFoundException;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessPause;
import fr.gouv.vitam.common.model.ProcessQuery;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.processing.ProcessDetail;
import fr.gouv.vitam.common.model.processing.WorkFlow;
import fr.gouv.vitam.processing.common.ProcessingEntry;
import fr.gouv.vitam.processing.common.exception.ProcessingBadRequestException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.WorkerAlreadyExistsException;
import fr.gouv.vitam.processing.common.model.WorkerBean;

/**
 * Processing Management Client
 */
public interface ProcessingManagementClient extends MockOrRestClient {

    /**
     * Check if process workflow is completed of not TODO Move this method to OperationManagementClient
     * 
     * @param operationId
     * @return boolean true/false
     */
    boolean isOperationCompleted(String operationId);

    /**
     * Register a new worker knowing its family and with a WorkerBean. If a problem is encountered, an exception is
     * thrown.
     *
     * @param familyId the id of the family to which the worker has to be registered
     * @param workerId the id of the worker to be registered
     * @param workerDescription the description of the worker as a workerBean
     * @throws ProcessingBadRequestException if a bad request has been sent
     * @throws WorkerAlreadyExistsException if the worker family does not exist
     */
    void registerWorker(String familyId, String workerId, WorkerBean workerDescription)
        throws ProcessingBadRequestException, WorkerAlreadyExistsException;

    /**
     * Unregister a worker knowing its family and its workerId. If the familyId or the workerId is unknown, an exception
     * is thrown.
     *
     * @param familyId the id of the family to which the worker has to be registered
     * @param workerId the id of the worker to be registered
     * @throws ProcessingBadRequestException if the worker or the family does not exist
     */
    void unregisterWorker(String familyId, String workerId)
        throws ProcessingBadRequestException;

    /**
     * Other than INIT process, only operation id and action id are required
     * @param query
     * @param workflow not required
     * @param actionId
     * @return Response
     * @throws InternalServerException
     * @throws BadRequestException
     * @throws WorkflowNotFoundException
     */
    Response executeCheckTraceabilityWorkFlow(String checkOperationId, JsonNode query, String workflow, String actionId)
        throws InternalServerException, WorkflowNotFoundException;

    /**
     * Retrieve all the workflow definitions.
     * 
     * @return map of workflow definitions by id
     * @throws VitamClientException
     */
    RequestResponse<WorkFlow> getWorkflowDefinitions() throws VitamClientException;


    /**
     * initVitamProcess woth processing entry
     * @param contextId
     * @param entry
     */
    void initVitamProcess(String contextId, ProcessingEntry entry) throws InternalServerException, BadRequestException;


    /**
     * Removed the forced pause on the tenant and/or the type of process
     *
     * @param info
     */
    RequestResponse removeForcePause(ProcessPause info) throws ProcessingException;



    /**
     * Add a forced pause on the tenant and/or the type of process
     *
     * @param info
     */
    RequestResponse<ProcessPause> forcePause(ProcessPause info) throws ProcessingException;

    /**
     * getOperationProcessStatus:
     *
     * get operation process status**
     *
     * @param id : operation identifier*
     * @return ItemStatus response containing message and status*
     * @throws VitamClientException
     * @throws InternalServerException
     * @throws BadRequestException
     */

    ItemStatus getOperationProcessStatus(String id)
            throws VitamClientException, InternalServerException, BadRequestException;

    /**
     *
     * getOperationProcessExecutionDetails : get operation processing execution details
     *
     * @param id : operation identifier
     * @return Engine response containing message and status
     * @throws VitamClientException
     * @throws InternalServerException
     * @throws BadRequestException
     */

    ItemStatus getOperationProcessExecutionDetails(String id)
            throws VitamClientException, InternalServerException, BadRequestException;

    /**
     * cancelOperationProcessExecution : cancel processing operation
     *
     * @param id : operation identifier
     * @return ItemStatus response containing message and status
     * @throws VitamClientException
     * @throws InternalServerException
     * @throws BadRequestException
     */
    ItemStatus cancelOperationProcessExecution(String id)
            throws InternalServerException, VitamClientException;

    /**
     * updateOperationActionProcess : update operation processing status
     *
     *
     * @param actionId : identify the action to be executed by the workflow(next , pause,resume)
     * @param operationId : operation identifier
     * @return Response response containing message and status
     * @throws InternalServerException
     * @throws BadRequestException
     * @throws VitamClientException
     */
    RequestResponse<ItemStatus> updateOperationActionProcess(String actionId, String operationId)
            throws InternalServerException, VitamClientException;


    /**
     * ExecuteOperationProcess : execute an operation processing
     * Other than INIT process, only operation id and action id are required
     * @param operationId id of the operation (required)
     * @param workflow id of the workflow (optional)
     * @param contextId define the execution context of workflow (optional)
     * @param actionId identify the action to be executed by the workflow(next , pause,resume)  (required)
     * @return RequestResponse
     * @throws InternalServerException
     * @throws BadRequestException
     * @throws VitamClientException
     * @throws WorkflowNotFoundException
     */
    RequestResponse<JsonNode> executeOperationProcess(String operationId, String workflow, String contextId, String actionId)
            throws InternalServerException, VitamClientException, WorkflowNotFoundException;

    /**
     * initVitamProcess
     *
     * @param contextId
     * @param container
     * @param workflow
     * @throws InternalServerException
     * @throws VitamClientException
     * @throws BadRequestException
     */
    void initVitamProcess(String contextId, String container, String workflow)
            throws InternalServerException, VitamClientException, BadRequestException;

    /**
     * Retrieve all the workflow operations
     *
     * @param query Query model
     *
     * @return All details of the operations
     * @throws VitamClientException
     */
    RequestResponse<ProcessDetail> listOperationsDetails(ProcessQuery query) throws VitamClientException;
;
}
