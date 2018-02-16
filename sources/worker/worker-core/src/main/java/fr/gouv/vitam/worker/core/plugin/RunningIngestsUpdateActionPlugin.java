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
package fr.gouv.vitam.worker.core.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.query.action.UpdateActionHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.multiple.UpdateMultiQuery;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.InternalServerException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UpdateWorkflowConstants;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.common.utils.ArchiveUnitUpdateUtils;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * CheckArchiveUnitSchema Plugin.<br>
 */

public class RunningIngestsUpdateActionPlugin extends ActionHandler {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(RunningIngestsUpdateActionPlugin.class);

    private StoreMetadataObjectActionHandler storeMetadataObjectActionHandler =
        new StoreMetaDataUnitActionPlugin();

    private static final String RUNNING_INGESTS_UPDATE_TASK_ID = "UPDATE_RUNNING_INGESTS";
    private static final int RANK_RUNNING_INGESTS_FILE = 0;
    private HandlerIO handlerIO;
    private Map<String, List<JsonNode>> updatedRulesByType = new HashMap<String, List<JsonNode>>();
    private ProcessingManagementClient processManagementClient;
    private MetaDataClient metaDataClient;
    private LogbookLifeCyclesClient logbookLifeCycleClient;
    private ArchiveUnitUpdateUtils archiveUnitUpdateUtils = new ArchiveUnitUpdateUtils();

    private static final String RESULTS = "$results";
    private static final String ID = "#id";

    private static final String MANAGEMENT_KEY = "#management";
    private static final String FIELDS_KEY = "$fields";
    private static final String RULES_KEY = "Rules";

    private static final long SLEEP_TIME = 10000l;
    private static final long NB_TRY = 600; // equivalent to 60 minutes

    private static final String PROCESS_ID_FIELD = "operationId";

    /**
     * Empty constructor UnitsRulesComputePlugin
     */
    public RunningIngestsUpdateActionPlugin() {
        // Empty
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {
        handlerIO = handler;
        final ItemStatus itemStatus = new ItemStatus(RUNNING_INGESTS_UPDATE_TASK_ID);
        processManagementClient =
            ProcessingManagementClientFactory.getInstance().getClient();
        metaDataClient =
            MetaDataClientFactory.getInstance().getClient();
        logbookLifeCycleClient =
            LogbookLifeCyclesClientFactory.getInstance().getClient();
        try {
            getRunningIngests(params);
            itemStatus.increment(StatusCode.OK);
        } catch (ProcessingException e) {
            LOGGER.error("Processing exception", e);
            itemStatus.increment(StatusCode.FATAL);
        } catch (InvalidParseOperationException e) {
            LOGGER.error("Cannot parse json", e);
            itemStatus.increment(StatusCode.KO);
        } finally {
            processManagementClient.close();
            metaDataClient.close();
            logbookLifeCycleClient.close();
        }
        return new ItemStatus(RUNNING_INGESTS_UPDATE_TASK_ID).setItemsStatus(RUNNING_INGESTS_UPDATE_TASK_ID,
            itemStatus);
    }

    private void getRunningIngests(WorkerParameters params) throws ProcessingException, InvalidParseOperationException {
        try {
            JsonNode rulesUpdated = JsonHandler.getFromInputStream(this.handlerIO.getInputStreamFromWorkspace(
                UpdateWorkflowConstants.PROCESSING_FOLDER + "/" + UpdateWorkflowConstants.UPDATED_RULES_JSON));
            for (final JsonNode rule : rulesUpdated) {
                if (!updatedRulesByType.containsKey(rule.get("RuleType").asText())) {
                    List<JsonNode> listRulesByType = new ArrayList<JsonNode>();
                    listRulesByType.add(rule);
                    updatedRulesByType.put(rule.get("RuleType").asText(), listRulesByType);
                } else {
                    List<JsonNode> listRulesByType = updatedRulesByType.get(rule.get("RuleType").asText());
                    listRulesByType.add(rule);
                    updatedRulesByType.put(rule.get("RuleType").asText(), listRulesByType);
                }
            }
            // do something with this file
            if (rulesUpdated.isArray() && rulesUpdated.size() > 0) {
                JsonNode runningIngests = JsonHandler.getFromFile((File) handlerIO.getInput(RANK_RUNNING_INGESTS_FILE));
                if (runningIngests.isArray() && runningIngests.size() > 0) {
                    List<JsonNode> listIngest = JsonHandler.toArrayList((ArrayNode) runningIngests);
                    long nbTry = 0;
                    while (true) {
                        for (Iterator<JsonNode> it = listIngest.iterator(); it.hasNext(); ) {
                            JsonNode currentIngest = it.next();
                            checkAndProcessIngest(currentIngest, it, params);
                        }
                        if (listIngest.size() == 0) {
                            break;
                        } else {
                            try {
                                Thread.sleep(SLEEP_TIME);
                            } catch (InterruptedException e) {
                                SysErrLogger.FAKE_LOGGER.ignoreLog(e);
                            }
                            if (nbTry == NB_TRY) {
                                break;
                            }
                            nbTry++;
                        }
                    }
                }
            } else {
                LOGGER.warn("No rules updated");
            }
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException |
            IOException e) {
            LOGGER.error("Workspace error: Cannot get file", e);
            throw new ProcessingException(e);
        }
    }


    private void checkAndProcessIngest(JsonNode currentIngest, Iterator<JsonNode> iterator, WorkerParameters params)
        throws ProcessingException {
        String operationId = currentIngest.get(PROCESS_ID_FIELD).asText();

        final WorkerParameters paramsCopy = WorkerParametersFactory.newWorkerParameters();
        paramsCopy.setContainerName(params.getContainerName());
        paramsCopy.setCurrentStep(params.getCurrentStep());
        paramsCopy.setUrlWorkspace(params.getUrlWorkspace());
        paramsCopy.setUrlMetadata(params.getUrlMetadata());
        paramsCopy.setObjectNameList(new ArrayList<>());
        try {
            ItemStatus status = processManagementClient.getOperationProcessStatus(operationId);
            if (ProcessState.COMPLETED.equals(status.getGlobalState())) {

                // Treat only OK or WARNING ingests else remove from list and return
                if (status.getGlobalStatus().isGreaterOrEqualToKo()) {
                    iterator.remove();
                    return;
                }

                ObjectNode projectionNode = JsonHandler.createObjectNode();
                final SelectMultiQuery selectMultiple = new SelectMultiQuery();
                ObjectNode objectNode = JsonHandler.createObjectNode();
                objectNode.put(ID, 1);
                objectNode.put(MANAGEMENT_KEY, 1);
                projectionNode.set(FIELDS_KEY, objectNode);
                ArrayNode arrayNode = JsonHandler.createArrayNode();
                selectMultiple
                    .setQuery(QueryHelper.and().add(QueryHelper.in(VitamFieldsHelper.operations(), operationId)));
                selectMultiple.addRoots(arrayNode);
                selectMultiple.addProjection(projectionNode);
                final JsonNode unitsResultNode = metaDataClient.selectUnits(selectMultiple.getFinalSelect());
                if (unitsResultNode != null) {
                    ArrayNode resultUnitsArray = (ArrayNode) unitsResultNode.get(RESULTS);
                    // if size > 0, that means AU are to be checked updated
                    if (resultUnitsArray != null && resultUnitsArray.size() > 0) {
                        for (final JsonNode unitNode : resultUnitsArray) {
                            UpdateMultiQuery query = new UpdateMultiQuery();
                            String auGuid = unitNode.get(ID).asText();
                            JsonNode managementNode = unitNode.get(MANAGEMENT_KEY);
                            int nbUpdates = 0;
                            for (String key : updatedRulesByType.keySet()) {
                                JsonNode categoryNode = managementNode.get(key);
                                if (categoryNode != null &&
                                    categoryNode.get(RULES_KEY) != null) {
                                    if (archiveUnitUpdateUtils.updateCategoryRules(
                                        (ArrayNode) categoryNode.get(RULES_KEY),
                                        updatedRulesByType.get(key), query, key)) {
                                        nbUpdates++;
                                    }
                                }
                            }
                            if (nbUpdates > 0) {
                                try {
                                    query.addActions(
                                        UpdateActionHelper
                                            .push(VitamFieldsHelper.operations(), params.getContainerName()));
                                    JsonNode updateResultJson =
                                        metaDataClient.updateUnitbyId(query.getFinalUpdate(), auGuid);
                                    archiveUnitUpdateUtils.logLifecycle(params, auGuid, StatusCode.OK,
                                        archiveUnitUpdateUtils.getDiffMessageFor(updateResultJson, auGuid),
                                        logbookLifeCycleClient);
                                    archiveUnitUpdateUtils.commitLifecycle(params.getContainerName(), auGuid,
                                        logbookLifeCycleClient);


                                    // Save updated archive unit in the storage offer
                                    paramsCopy.setObjectName(auGuid);
                                    saveMetadataWithLfcInTheStorage(paramsCopy);


                                } catch (MetaDataExecutionException | MetaDataDocumentSizeException |
                                    MetaDataClientServerException | InvalidCreateOperationException |
                                    InvalidParseOperationException | MetaDataNotFoundException e) {
                                    try {
                                        logbookLifeCycleClient.rollBackUnitsByOperation(params.getContainerName());
                                    } catch (LogbookClientBadRequestException | LogbookClientNotFoundException |
                                        LogbookClientServerException ex) {
                                        LOGGER.error("Couldn't rollback lifecycles", ex);
                                    }
                                    throw new ProcessingException(e);
                                }
                            }
                        }
                    }
                }
                // deal with archive units then remove the process from the list
                iterator.remove();
            }
        } catch (VitamClientException | InternalServerException | BadRequestException | MetaDataExecutionException |
            MetaDataDocumentSizeException | MetaDataClientServerException | InvalidParseOperationException |
            InvalidCreateOperationException e) {
            throw new ProcessingException(e);
        }
    }

    /**
     * @param workerParameters
     * @throws ProcessingException
     */
    private void saveMetadataWithLfcInTheStorage(WorkerParameters workerParameters) throws ProcessingException {
        try {
            ItemStatus itemStaus =
                storeMetadataObjectActionHandler.execute(workerParameters, handlerIO);

            if (itemStaus.getGlobalStatus().isGreaterOrEqualToKo()) {
                throw new ProcessingException(
                    String.format("The ArchiveUnit %s with LifeCyle isn't saved in the storage",
                        workerParameters.getObjectName()));
            }
        } catch (ContentAddressableStorageServerException e) {
            throw new ProcessingException(e);
        }
    }


    @VisibleForTesting
    void setStoreMetadataObjectActionHandler(
        StoreMetadataObjectActionHandler storeMetadataObjectActionHandler) {
        this.storeMetadataObjectActionHandler = storeMetadataObjectActionHandler;
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // Nothing to check
    }
}
