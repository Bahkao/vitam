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
package fr.gouv.vitam.worker.core.handler;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import fr.gouv.vitam.worker.core.exception.WorkerspaceQueueException;
import org.bson.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;

import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.client.DefaultClient;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.model.TraceabilityEvent;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookDocument;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbName;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.storage.engine.common.model.StorageCollectionType;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

/**
 *
 */
public class PrepareTraceabilityCheckProcessActionHandler extends ActionHandler {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(PrepareTraceabilityCheckProcessActionHandler.class);

    private static final String HANDLER_ID = "PREPARE_TRACEABILITY_CHECK";

    private static final String DEFAULT_STORAGE_STRATEGY = "default";

    private static final int TRACEABILITY_EVENT_DETAIL_RANK = 0;
    private boolean asyncIO = false;



    /**
     * @return HANDLER_ID
     */
    public static final String getId() {
        return HANDLER_ID;
    }

    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handler)
        throws ProcessingException {

        final ItemStatus itemStatus = new ItemStatus(HANDLER_ID);

        // 1- Get the TRACEABILITY operation to check
        LogbookOperation operationToCheck;
        try (LogbookOperationsClient logbookOperationsClient =
            LogbookOperationsClientFactory.getInstance().getClient()) {

            if (asyncIO) {
                handler.enableAsync(asyncIO);
            }
            RequestResponseOK requestResponseOK =
                RequestResponseOK.getFromJsonNode(logbookOperationsClient
                    .selectOperation(
                        JsonHandler.getFromString(param.getMapParameters().get(WorkerParameterName.logbookRequest))));

            List<ObjectNode> foundOperation = requestResponseOK.getResults();
            if (foundOperation == null || foundOperation.isEmpty() || foundOperation.size() > 1) {
                itemStatus.increment(StatusCode.KO);
                return itemStatus;
            }

            operationToCheck = new LogbookOperation(foundOperation.get(0));
            String operationType = (String) operationToCheck.get(LogbookMongoDbName.eventTypeProcess.getDbname());

            // check if it a traceability operation
            if (!LogbookTypeProcess.TRACEABILITY.equals(LogbookTypeProcess.valueOf(operationType))) {
                itemStatus.increment(StatusCode.KO);
                return itemStatus;
            }
        } catch (InvalidParseOperationException | LogbookClientException | IllegalArgumentException |
            WorkerspaceQueueException e) {
            LOGGER.error(e.getMessage(), e);
            itemStatus.increment(StatusCode.FATAL);
            return itemStatus;
        }

        Response response = null;
        // 2- TRACEABILITY operation found, so extract the ZIP file to start checking process
        try (WorkspaceClient workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            StorageClient storageClient = StorageClientFactory.getInstance().getClient()) {

            // Get last event to extract eventDetailData field
            ArrayList events = (ArrayList) operationToCheck.get(LogbookDocument.EVENTS);
            Document lastEvent = (Document) Iterables.getLast(events);

            // Create TraceabilityEvent instance
            String evDetData = lastEvent.getString(LogbookMongoDbName.eventDetailData.getDbname());
            JsonNode eventDetail = JsonHandler.getFromString(evDetData);

            TraceabilityEvent traceabilityEvent =
                JsonHandler.getFromJsonNode(eventDetail, TraceabilityEvent.class);
            String fileName = traceabilityEvent.getFileName();

            // 1- get zip file
            response =
                storageClient.getContainerAsync(DEFAULT_STORAGE_STRATEGY, fileName, StorageCollectionType.LOGBOOKS);
 
            // Idempotency - we check if a folder exist
            if (workspaceClient.isExistingContainer(param.getContainerName())) {
                try {
                    workspaceClient.deleteContainer(param.getContainerName(), true);
                    LOGGER.warn("Container was already existing, step is being replayed");
                } catch (ContentAddressableStorageNotFoundException e) {
                    LOGGER.warn("The container could not be deleted", e);
                }
            }
            
            // 2- unzip file
            handler.unzipInputStreamOnWorkspace(param.getContainerName(),
                SedaConstants.TRACEABILITY_OPERATION_DIRECTORY, CommonMediaType.ZIP,
                response.readEntity(InputStream.class), asyncIO);

            // 3- Add Output result : eventDetailData
            extractTraceabilityOperationDetails(handler, traceabilityEvent);

            itemStatus.increment(StatusCode.OK);

            if (asyncIO)
                handler.enableAsync(false);

        } catch (StorageServerClientException | StorageNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
            itemStatus.increment(StatusCode.FATAL);
        } catch (ContentAddressableStorageException | WorkerspaceQueueException e) {
            // Decompression Exception
            LOGGER.error(e.getMessage(), e);
            itemStatus.increment(StatusCode.FATAL);
        } catch (InvalidParseOperationException e) {
            LOGGER.error(e.getMessage(), e);
            itemStatus.increment(StatusCode.FATAL);
        } finally {
            DefaultClient.staticConsumeAnyEntityAndClose(response);
        }

        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {}

    private void extractTraceabilityOperationDetails(HandlerIO handlerIO, TraceabilityEvent eventDetailData)
        throws InvalidParseOperationException, ProcessingException {
        File tempFile = handlerIO.getNewLocalFile(handlerIO.getOutput(0).getPath());

        // Create json file
        JsonHandler.writeAsFile(eventDetailData, tempFile);

        // Put file in workspace
        handlerIO.addOuputResult(TRACEABILITY_EVENT_DETAIL_RANK, tempFile, true, false);
    }
}
