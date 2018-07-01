/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019) <p> contact.vitam@culture.gouv.fr <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently. <p> This software is governed by the CeCILL 2.1 license under French law and
 * abiding by the rules of distribution of free software. You can use, modify and/ or redistribute the software under
 * the terms of the CeCILL 2.1 license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". <p> As a counterpart to the access to the source code and rights to copy, modify and
 * redistribute granted by the license, users are provided only with a limited warranty and the software's author, the
 * holder of the economic rights, and the successive licensors have only limited liability. <p> In this respect, the
 * user's attention is drawn to the risks associated with loading, using, modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software, that may mean that it is complicated to
 * manipulate, and that also therefore means that it is reserved for developers and experienced professionals having
 * in-depth computer knowledge. Users are therefore encouraged to load and test the software's suitability as regards
 * their requirements in conditions enabling the security of their systems and/or data to be ensured and, more
 * generally, to use and operate it in the same conditions as regards security. <p> The fact that you are presently
 * reading this means that you have had knowledge of the CeCILL 2.1 license and that you accept its terms.
 */
package fr.gouv.vitam.processing.distributor.v2;

import static com.google.common.base.MoreObjects.firstNonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.Distribution;
import fr.gouv.vitam.common.model.processing.DistributionKind;
import fr.gouv.vitam.common.model.processing.DistributionType;
import fr.gouv.vitam.common.model.processing.PauseOrCancelAction;
import fr.gouv.vitam.common.model.processing.Step;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.common.utils.VitamReaderSpliterator;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.HandlerNotFoundException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.WorkerFamilyNotFoundException;
import fr.gouv.vitam.processing.common.exception.WorkerNotFoundException;
import fr.gouv.vitam.processing.common.model.DistributorIndex;
import fr.gouv.vitam.processing.common.model.PauseRecover;
import fr.gouv.vitam.processing.common.parameter.DefaultWorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.data.core.ProcessDataAccess;
import fr.gouv.vitam.processing.data.core.ProcessDataAccessImpl;
import fr.gouv.vitam.processing.data.core.management.ProcessDataManagement;
import fr.gouv.vitam.processing.data.core.management.WorkspaceProcessDataManagement;
import fr.gouv.vitam.processing.distributor.api.IWorkerManager;
import fr.gouv.vitam.processing.distributor.api.ProcessDistributor;
import fr.gouv.vitam.worker.core.distribution.ChainedFileModel;
import fr.gouv.vitam.processing.model.JsonLineModel;
import fr.gouv.vitam.worker.client.exception.PauseCancelException;
import fr.gouv.vitam.worker.client.exception.WorkerUnreachableException;
import fr.gouv.vitam.worker.common.DescriptionStep;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.apache.commons.lang3.StringUtils;

/**
 * The Process Distributor call the workers and intercept the response for manage a post actions step
 * <p>
 * <p>
 * <pre>
 * TODO P1:
 * - handle listing of items through a limited arraylist (memory) and through iterative (async) listing from
 * Workspace
 * - handle result in FATAL mode from one distributed item to stop the distribution in FATAL mode (do not
 * continue)
 * - try to handle distribution on 1 or on many as the same loop (so using a default arrayList of 1)
 * - handle error level using order in enum in ProcessResponse.getGlobalProcessStatusCode instead of manually comparing:
 *  <code>
 *    for (final EngineResponse response : responses) {
 *       tempStatusCode = response.getStatus();
 *       if (statusCode.ordinal() > tempStatusCode.ordinal()) {
 *           statusCode = tempStatusCode;
 *       }
 *      if (statusCode.ordinal() > StatusCode.KO.ordinal()) {
 *           break;
 *       }
 *     }
 *  </code>
 * </pre>
 */
public class ProcessDistributorImpl implements ProcessDistributor {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ProcessDistributorImpl.class);

    private static final String AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_GET_DISTIBUTOR_INDEX_FROM_WORKSPACE =
        "An exception has been thrown when trying to get distibutor index from workspace";
    private static final String DISTRIBUTOR_INDEX_NOT_FOUND_FOR_THE_OPERATION =
        "DistributorIndex not found for the operation";
    private static final String AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_PERSIST_DISTRIBUTOR_INDEX =
        "An Exception has been thrown when trying to persist DistributorIndex";

    private final ProcessDataAccess processDataAccess;
    private ProcessDataManagement processDataManagement;
    private final IWorkerManager workerManager;
    private Map<String, Step> currentSteps = new HashMap<>();
    private WorkspaceClientFactory workspaceClientFactory;

    /**
     * Empty constructor
     *
     * @param workerManager
     */
    public ProcessDistributorImpl(IWorkerManager workerManager) {
        this(workerManager, ProcessDataAccessImpl.getInstance(), WorkspaceProcessDataManagement.getInstance(),
            WorkspaceClientFactory.getInstance());
    }

    @VisibleForTesting
    public ProcessDistributorImpl(IWorkerManager workerManager, ProcessDataAccess processDataAccess,
        ProcessDataManagement processDataManagement, WorkspaceClientFactory workspaceClientFactory) {
        this.workerManager = workerManager;
        this.processDataAccess = processDataAccess;
        this.processDataManagement = processDataManagement;
        this.workspaceClientFactory = workspaceClientFactory;
        ParametersChecker
            .checkParameter("Parameters are required.", workerManager, processDataAccess, processDataManagement,
                workspaceClientFactory);
    }

    @Override
    synchronized public boolean pause(String operationId) {
        ParametersChecker.checkParameter("The parameter operationId is required", operationId);
        final Step step = currentSteps.get(operationId);
        if (null != step) {
            step.setPauseOrCancelAction(PauseOrCancelAction.ACTION_PAUSE);
            return true;
        }
        return false;
    }

    @Override
    synchronized public boolean cancel(String operationId) {
        ParametersChecker.checkParameter("The parameter operationId is required", operationId);
        final Step step = currentSteps.get(operationId);
        if (null != step) {
            step.setPauseOrCancelAction(PauseOrCancelAction.ACTION_CANCEL);
            return true;
        }
        return false;
    }

    /**
     * Temporary method for distribution supporting multi-list
     *
     * @param workParams   of type {@link WorkerParameters}
     * @param step         the execution step
     * @param operationId  the operation id
     * @param pauseRecover prevent recover from pause action
     * @return the final step status
     */
    @Override
    public ItemStatus distribute(WorkerParameters workParams, Step step, String operationId,
        PauseRecover pauseRecover) {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", workParams);
        ParametersChecker.checkParameter("Step is a mandatory parameter", step);
        ParametersChecker.checkParameter("workflowId is a mandatory parameter", operationId);
        ParametersChecker.checkParameter("pauseRecover is a mandatory parameter", pauseRecover);
        /*
         * use index only if pauseRecover of the processWorkflow
         * is PauseRecover.RECOVER_FROM_API_PAUSE or PauseRecover.RECOVER_FROM_SERVER_PAUSE
         * and pauseCancelAction of the step is PauseOrCancelAction.ACTION_RECOVER
         */
        boolean useDistributorIndex = !PauseRecover.NO_RECOVER.equals(pauseRecover) &&
            PauseOrCancelAction.ACTION_RECOVER.equals(step.getPauseOrCancelAction());

        final int tenantId = VitamThreadUtils.getVitamSession().getTenantId();
        step.setStepResponses(new ItemStatus(step.getStepName()));

        // Explicitly flush ElasticSearch indexes for the current tenant
        try (MetaDataClient metadataClient = MetaDataClientFactory.getInstance().getClient()) {
            try {
                metadataClient.flushUnits();
                metadataClient.flushObjectGroups();
            } catch (MetaDataClientServerException e) {
                step.getStepResponses().increment(StatusCode.FATAL);
                LOGGER.error("Illegal Argument Exception", e);
                return step.setPauseOrCancelAction(PauseOrCancelAction.ACTION_COMPLETE).getStepResponses();
            }
        }
        try {
            currentSteps.put(operationId, step);
            // update workParams
            workParams.putParameterValue(WorkerParameterName.workflowStatusKo,
                processDataAccess.findOneProcessWorkflow(operationId, tenantId).getStatus().name());

            List<String> objectsList = new ArrayList<>();
            if (step.getDistribution().getKind().equals(DistributionKind.LIST_ORDERING_IN_FILE)) {
                try (final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {
                    // Test regarding Unit to be indexed                    
                    if (DistributionType.Units == step.getDistribution().getType()) {
                        // get the file to retrieve the GUID
                        final Response response = workspaceClient.getObject(workParams.getContainerName(),
                            step.getDistribution().getElement());

                        final JsonNode levelFileJson;
                        try {
                            final InputStream levelFile = (InputStream) response.getEntity();
                            levelFileJson = JsonHandler.getFromInputStream(levelFile);
                        } finally {
                            workspaceClient.consumeAnyEntityAndClose(response);
                        }
                        final Iterator<Entry<String, JsonNode>> iteratorLevelFile = levelFileJson.fields();
                        while (iteratorLevelFile.hasNext()) {
                            final Entry<String, JsonNode> guidFieldList = iteratorLevelFile.next();
                            final String level = guidFieldList.getKey();
                            final JsonNode guid = guidFieldList.getValue();
                            if (guid != null && guid.size() > 0) {
                                for (final JsonNode _idGuid : guid) {
                                    // include the GUID in the new URI
                                    objectsList.add(_idGuid.asText() + JSON_EXTENSION);
                                }
                                boolean distributorIndexUsed =
                                    distributeOnList(workParams, step, level, objectsList, useDistributorIndex,
                                        tenantId);
                                /*
                                 * If the distributorIndex is used in the previous level
                                 * Then do not use index in the next level
                                 */
                                if (useDistributorIndex && distributorIndexUsed) {
                                    useDistributorIndex = false;
                                }
                                objectsList.clear();

                                // If fatal occurs, do not continue distribution
                                if (step.getStepResponses().getGlobalStatus().isGreaterOrEqualToFatal()) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (step.getDistribution().getKind().equals(DistributionKind.LIST_IN_DIRECTORY)) {
                // List from Storage
                try (final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

                    final List<URI> objectsListUri =
                        JsonHandler.getFromStringAsTypeRefence(
                            workspaceClient.getListUriDigitalObjectFromFolder(workParams.getContainerName(),
                                step.getDistribution().getElement())
                                .toJsonNode().get("$results").get(0).toString(),
                            new TypeReference<List<URI>>() {
                            });
                    for (URI uri : objectsListUri) {
                        objectsList.add(uri.getPath());
                    }
                    // Iterate over Objects List
                    distributeOnList(workParams, step, NOLEVEL, objectsList, useDistributorIndex, tenantId);
                }
            } else if (step.getDistribution().getKind().equals(DistributionKind.LIST_IN_FILE)) {
                try (final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {
                    // List from Workspace
                    Response response =
                        workspaceClient.getObject(workParams.getContainerName(), step.getDistribution().getElement());
                    final JsonNode ogIdList;
                    try {
                        ogIdList = JsonHandler.getFromInputStream((InputStream) response.getEntity());
                    } finally {
                        workspaceClient.consumeAnyEntityAndClose(response);
                    }
                    if (ogIdList.isArray()) {
                        for (JsonNode node : ogIdList) {
                            objectsList.add(node.textValue());
                        }
                    }
                    // Iterate over Objects List
                    distributeOnList(workParams, step, NOLEVEL, objectsList, useDistributorIndex, tenantId);
                }
            } else if (step.getDistribution().getKind().equals(DistributionKind.LIST_IN_LINKED_FILE)) {

                // distribute ordered list of chained files
                distributeChainedFiles(workParams.getContainerName(), step.getDistribution().getElement(),
                    workParams, step, useDistributorIndex, tenantId);

            } else if (step.getDistribution().getKind().equals(DistributionKind.LIST_IN_JSONL_FILE)) {

                // distribute on stream
                try (final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {
                    Response response =
                        workspaceClient.getObject(workParams.getContainerName(), step.getDistribution().getElement());
                    try (BufferedReader br = new BufferedReader(
                        new InputStreamReader((InputStream) response.getEntity(), StandardCharsets.UTF_8))) {

                        distributeOnStream(workParams, step, NOLEVEL, br,
                            useDistributorIndex, tenantId);
                    }
                }
            } else {
                // update the number of element to process
                if (step.getDistribution().getElement() == null ||
                    step.getDistribution().getElement().trim().isEmpty()) {
                    objectsList.add(workParams.getContainerName());
                } else {
                    objectsList.add(step.getDistribution().getElement());
                }
                distributeOnList(workParams, step, NOLEVEL, objectsList, useDistributorIndex, tenantId);
            }
        } catch (final IllegalArgumentException e) {
            step.getStepResponses().increment(StatusCode.FATAL);
            LOGGER.error("Illegal Argument Exception", e);
        } catch (final HandlerNotFoundException e) {
            step.getStepResponses().increment(StatusCode.FATAL);
            LOGGER.error("Handler Not Found Exception", e);
        } catch (final PauseCancelException e) {
            // Pause or Cancel occurred
            return step.getStepResponses();
        } catch (final Exception e) {
            step.getStepResponses().increment(StatusCode.FATAL);
            LOGGER.error(EXCEPTION_MESSAGE, e);
        } finally {
            currentSteps.remove(operationId);
        }
        return step.setPauseOrCancelAction(PauseOrCancelAction.ACTION_COMPLETE).getStepResponses();
    }

    /**
     * @param containerName
     * @param fileName
     * @param workParams
     * @param step
     * @param useDistributorIndex
     * @param tenantId
     * @throws ContentAddressableStorageNotFoundException
     * @throws ContentAddressableStorageServerException
     * @throws fr.gouv.vitam.common.exception.InvalidParseOperationException
     * @throws ProcessingException
     */
    private void distributeChainedFiles(String containerName, String fileName, WorkerParameters workParams, Step step,
        boolean useDistributorIndex, int tenantId)
        throws InvalidParseOperationException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException, ProcessingException {

        List<String> objectsList;

        try (final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {
            // get file's content
            final Response response = workspaceClient.getObject(containerName, fileName);

            ChainedFileModel chainedFile;
            try {
                chainedFile =
                    JsonHandler.getFromInputStream((InputStream) response.getEntity(), ChainedFileModel.class);
            } finally {
                workspaceClient.consumeAnyEntityAndClose(response);
            }

            if (chainedFile != null) {
                objectsList = Optional.ofNullable(chainedFile.getElements())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                // Iterate over Objects List
                if (!objectsList.isEmpty()) {
                    if (fileName != null) {
                        distributeOnList(workParams, step, fileName, objectsList, useDistributorIndex, tenantId);
                    } else {
                        distributeOnList(workParams, step, NOLEVEL, objectsList, useDistributorIndex, tenantId);
                    }
                }

                if (!StringUtils.isBlank(chainedFile.getNextFile())) {
                    distributeChainedFiles(workParams.getContainerName(), chainedFile.getNextFile(),
                        workParams, step, useDistributorIndex,
                        tenantId);
                }
            }
        }
    }

    /**
     * The returned boolean is used in case where useDistributorIndex is true
     * if the returned boolean false, means that we want that useDistributorIndex should keep true
     * Else if the returned boolean true, means we have already used the distibutorIndex,
     * Then in case of multi-level for the next level do not use the distributorIndex
     *
     * @param workerParameters
     * @param step
     * @param objectsList
     * @param tenantId
     * @return return true if distributor index is used false else
     * @throws ProcessingException
     */
    private boolean distributeOnList(WorkerParameters workerParameters, Step step, String level,
        List<String> objectsList, boolean initFromDistributorIndex, Integer tenantId) throws ProcessingException {

        final String operationId = workerParameters.getContainerName();
        final String requestId = VitamThreadUtils.getVitamSession().getRequestId();
        final String contractId = VitamThreadUtils.getVitamSession().getContractId();
        final String contextId = VitamThreadUtils.getVitamSession().getContextId();
        final String uniqueStepId = step.getId();
        if (objectsList == null || objectsList.isEmpty()) {
            step.getStepResponses().setItemsStatus(OBJECTS_LIST_EMPTY,
                new ItemStatus(OBJECTS_LIST_EMPTY).increment(step.getDistribution().getStatusOnEmptyDistribution()));
            return false;
        }

        int offset = 0;
        int sizeList = objectsList.size();
        boolean updateElementToProcess = true;
        DistributorIndex distributorIndex = null;
        final List<String> remainingElementsFromRecover = new ArrayList<>();
        /*
         * initFromDistributorIndex true if start after stop
         *
         * Get the distributor Index from the workspace
         * the current step identifier should be equals to the step identifier in the distributorIndex
         * else the current step is not correctly initialized in th state machine
         *
         * In the current step in case of the multiple level,
         * if the current level is not equals to the level in the initFromDistributorIndex
         * Then return false to passe to the next step
         */
        if (initFromDistributorIndex) {
            try {
                distributorIndex = processDataManagement.getDistributorIndex(DISTRIBUTOR_INDEX, operationId);
                if (null != distributorIndex) {
                    if (!distributorIndex.getStepId().equals(step.getId())) {
                        throw new ProcessingException(
                            "You run the wrong step " + step.getId() + ". The step from saved distributor index is : " +
                                distributorIndex.getStepId());
                    }
                    /*
                     * Handle the next level if the current level is not equals to the distributorIndex level
                     * This mean that the current level us already treated
                     */
                    if (!distributorIndex.getLevel().equals(level)) {
                        return false;
                    }
                    /*
                     * If all elements of the step are treated then response with the ItemStatus of the distributorIndex
                     */
                    if (distributorIndex.isLevelFinished()) {
                        step.setStepResponses(distributorIndex.getItemStatus());
                        return true;
                    } else {
                        /*
                         * Initialize from distributor index
                         */
                        offset = distributorIndex.getOffset();
                        distributorIndex.getItemStatus().getItemsStatus()
                            .remove(PauseOrCancelAction.ACTION_PAUSE.name());
                        step.setStepResponses(distributorIndex.getItemStatus());
                        /*
                         * As elements to process are calculated before stop of the server,
                         * do not recalculate them after restart
                         */
                        updateElementToProcess = false;
                        if (null != distributorIndex.getRemainingElements()) {
                            remainingElementsFromRecover.addAll(distributorIndex.getRemainingElements());
                        }
                    }
                } else {
                    throw new ProcessingException("DistributorIndex not found for the operation" + operationId);
                }
            } catch (ProcessingException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("Can't get distibutor index from workspace", e);
                throw new ProcessingException("Can't get distibutor index from workspace", e);
            }
        }
        /*
         * Update only if level is finished in the distributorIndex
         * In the cas of multiple level, we add the size of each level
         * Prevent adding twice the size of the current executing level
         */
        if (updateElementToProcess) {
            // update the number of element to process before start
            processDataAccess.updateStep(operationId, uniqueStepId, sizeList, false, tenantId);
        }

        final Set<ItemStatus> cancelled = new HashSet<>();
        final Set<ItemStatus> paused = new HashSet<>();
        boolean fatalOccurred = false;

        while (offset < sizeList && !fatalOccurred) {

            int bulkSize = findBulkSize(step.getDistribution());
            int batchSize = VitamConfiguration.getDistributeurBatchSize() * bulkSize;

            int nextOffset = sizeList > offset + batchSize ? offset + batchSize : sizeList;
            List<String> subList = objectsList.subList(offset, nextOffset);
            List<CompletableFuture<ItemStatus>> completableFutureList = new ArrayList<>();
            List<WorkerTask> currentWorkerTaskList = new ArrayList<>();

            /*
             * When server stop and in the batch of elements we have remaining elements (not yet treated)
             * Then after restart we treat only those not yet treated elements of this batch
             * If all elements of the batch were treated,
             * then at this point, we are automatically in the new batch
             * and we have to treat all elements of this batch
             */
            boolean emptyRemainingElements = remainingElementsFromRecover.isEmpty();

            if (!emptyRemainingElements) {
                subList = new ArrayList<>(subList);
                subList.retainAll(remainingElementsFromRecover);
            }

            int subOffset = 0;
            int subListSize = subList.size();

            while (subOffset < subListSize) {
                int nextSubOffset = subListSize > subOffset + bulkSize ?
                    subOffset + bulkSize : subListSize;

                List<String> newSubList = subList.subList(subOffset, nextSubOffset);

                workerParameters.setObjectNameList(newSubList);
                final WorkerTask task = new WorkerTask(
                    new DescriptionStep(step, ((DefaultWorkerParameters) workerParameters).newInstance()),
                    tenantId, requestId, contractId, contextId);

                currentWorkerTaskList.add(task);
                completableFutureList.add(prepare(task, operationId, tenantId));

                subOffset = nextSubOffset;
            }

            CompletableFuture<List<ItemStatus>> sequence = sequence(completableFutureList);
            CompletableFuture<ItemStatus> reduce = sequence
                .thenApplyAsync((List<ItemStatus> is) -> is.stream().reduce(step.getStepResponses(),
                    (identity, iterationItemStatus) -> {
                        if (PauseOrCancelAction.ACTION_CANCEL.name().equals(iterationItemStatus.getItemId()) &&
                            iterationItemStatus.getGlobalStatus().equals(StatusCode.UNKNOWN)) {
                            cancelled.add(iterationItemStatus);
                        }
                        if (PauseOrCancelAction.ACTION_PAUSE.name().equals(iterationItemStatus.getItemId()) &&
                            iterationItemStatus.getGlobalStatus().equals(StatusCode.UNKNOWN)) {
                            paused.add(iterationItemStatus);
                        }
                        return identity.setItemsStatus(iterationItemStatus);
                    }));
            try {
                // store information
                final ItemStatus itemStatus = reduce.get();
                /*
                 * As pause can occurs on not started WorkerTask,
                 * so we have to get the corresponding elements in order to execute them after restart
                 */
                List<String> remainingElements = new ArrayList<>();
                currentWorkerTaskList.forEach(e -> {
                    if (!e.isCompleted()) {
                        remainingElements.add(e.getObjectName());
                    }
                });


                if (itemStatus.getGlobalStatus().isGreaterOrEqualToFatal()) {
                    // Do not update index as we have to restart from old saved index
                    fatalOccurred = true;
                } else {
                    if (remainingElements.isEmpty()) {
                        offset = nextOffset;
                    }

                    distributorIndex =
                        new DistributorIndex(level, offset, itemStatus, requestId, uniqueStepId, remainingElements);

                    // All elements of the current level are treated so finish it
                    if (offset >= sizeList) {
                        distributorIndex.setLevelFinished(true);
                    }

                    // update persisted DistributorIndex if not Fatal
                    try {
                        processDataManagement.persistDistributorIndex(DISTRIBUTOR_INDEX, operationId, distributorIndex);
                        LOGGER
                            .debug("Store for the container " + operationId + " the DistributorIndex offset" + offset +
                                " GlobalStatus " + itemStatus.getGlobalStatus());
                    } catch (Exception e) {
                        LOGGER.error("Error while persist DistributorIndex", e);
                        throw new ProcessingException("Error while persist DistributorIndex", e);
                    }
                }

                if (cancelled.size() > 0) {
                    throw new PauseCancelException(PauseOrCancelAction.ACTION_CANCEL);
                }
                if (paused.size() > 0) {
                    throw new PauseCancelException(PauseOrCancelAction.ACTION_PAUSE);
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @VisibleForTesting
    Integer findBulkSize(Distribution distribution) {
        return firstNonNull(distribution.getBulkSize(), VitamConfiguration.getWorkerBulkSize());
    }

    /**
     * Distribution on stream.
     *
     * @param workerParameters
     * @param step
     * @param level
     * @param bufferedReader
     * @param initFromDistributorIndex
     * @param tenantId
     * @return
     */
    private boolean distributeOnStream(WorkerParameters workerParameters, Step step, String level,
        BufferedReader bufferedReader, boolean initFromDistributorIndex, Integer tenantId)
        throws ProcessingException {

        final String operationId = workerParameters.getContainerName();
        final String requestId = VitamThreadUtils.getVitamSession().getRequestId();
        final String contractId = VitamThreadUtils.getVitamSession().getContractId();
        final String contextId = VitamThreadUtils.getVitamSession().getContextId();

        if (bufferedReader == null) {
            step.getStepResponses().setItemsStatus(OBJECTS_LIST_EMPTY,
                new ItemStatus(OBJECTS_LIST_EMPTY).increment(step.getDistribution().getStatusOnEmptyDistribution()));
            return false;
        }

        // initialization
        int offset = 0;
        boolean updateElementToProcess = true;
        DistributorIndex distributorIndex = null;
        final List<String> remainingElementsFromRecover = new ArrayList<>();
        Spliterator<String> spliterator;

        /*
         * Check if the initialization is from the DistributorIndex :
         *
         * initFromDistributorIndex true if start after stop
         *
         * Get the distributor Index from the workspace
         * the current step identifier should be equals to the step identifier in the distributorIndex
         * else the current step is not correctly initialized in th state machine
         *
         * In the current step in case of the multiple level,
         * if the current level is not equals to the level in the initFromDistributorIndex
         * Then return false to passe to the next step
         */
        if (initFromDistributorIndex) {
            try {
                distributorIndex = processDataManagement.getDistributorIndex(DISTRIBUTOR_INDEX, operationId);
                if (distributorIndex != null) {
                    if (!distributorIndex.getStepId().equals(step.getId())) {
                        throw new ProcessingException(
                            "You run the wrong step " + step.getId() + ". The step from saved distributor index is : " +
                                distributorIndex.getStepId());
                    }

                    /*
                     * Handle the next level if the current level is not equal to the distributorIndex level,
                     * this means that the current level is already treated
                     */
                    if (!distributorIndex.getLevel().equals(level)) {
                        return false;
                    }

                    /*
                     * If all elements of the step are treated then response with the ItemStatus of the distributorIndex
                     */
                    if (distributorIndex.isLevelFinished()) {
                        step.setStepResponses(distributorIndex.getItemStatus());
                        return true;
                    } else {
                        /*
                         * Initialization from DistributorIndex
                         */
                        offset = distributorIndex.getOffset();
                        spliterator = bufferedReader.lines().skip(offset).spliterator();
                        distributorIndex.getItemStatus().getItemsStatus()
                            .remove(PauseOrCancelAction.ACTION_PAUSE.name());
                        step.setStepResponses(distributorIndex.getItemStatus());

                        /*
                         * As elements to process are calculated before stop of the server,
                         * do not recalculate them after restart
                         */
                        updateElementToProcess = false;
                        if (distributorIndex.getRemainingElements() != null &&
                            !distributorIndex.getRemainingElements().isEmpty()) {
                            remainingElementsFromRecover.addAll(distributorIndex.getRemainingElements());
                        }
                    }
                } else {
                    throw new ProcessingException(DISTRIBUTOR_INDEX_NOT_FOUND_FOR_THE_OPERATION + operationId);
                }
            } catch (ProcessingException e) {
                throw e;
            } catch (VitamException e) {
                LOGGER.error(AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_GET_DISTIBUTOR_INDEX_FROM_WORKSPACE, e);
                throw new ProcessingException(
                    AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_GET_DISTIBUTOR_INDEX_FROM_WORKSPACE, e);
            }
        } else {
            spliterator = new VitamReaderSpliterator(bufferedReader);
        }

        final Set<ItemStatus> cancelled = new HashSet<>();
        final Set<ItemStatus> paused = new HashSet<>();
        boolean fatalOccurred = false;
        boolean finishedStream = false;

        int bulkSize = findBulkSize(step.getDistribution());
        int globalBatchSize = VitamConfiguration.getDistributeurBatchSize() * bulkSize;
        while (!finishedStream && !fatalOccurred) {
            int nextOffset = offset + globalBatchSize;
            List<String> subList = new ArrayList<>();
            List<CompletableFuture<ItemStatus>> completableFutureList = new ArrayList<>();
            List<WorkerTask> currentWorkerTaskList = new ArrayList<>();
            List<String> finalSubList = subList;
            for (int i = offset; i < nextOffset; i++) {
                boolean notEndStream = spliterator.tryAdvance(value -> {
                    try {
                        JsonLineModel jsonLineModel = JsonHandler.getFromString(value, JsonLineModel.class);
                        if (jsonLineModel != null && jsonLineModel.getId() != null) {
                            finalSubList.add(jsonLineModel.getId());
                        }
                    } catch (InvalidParseOperationException e) {
                        LOGGER.error("Invalid Json", e);
                        throw new RuntimeException(e);
                    }
                });

                if (!notEndStream) {
                    finishedStream = true;
                    break;
                }
            }
            LOGGER.debug(String.valueOf(spliterator.estimateSize()));

           /*
            * Update only if level is finished in the distributorIndex
            * In the cas of multiple level, we add the size of each level
            * Prevent adding twice the size of the current executing level
            */
            if (updateElementToProcess) {
                // update the number of elements to process before start
                processDataAccess.updateStep(operationId, step.getId(), subList.size(), false, tenantId);
            }

          /*
           * When server stop and in the batch of elements we have remaining elements (not yet treated)
           * Then after restart we treat only those not yet treated elements of this batch
           * If all elements of the batch were treated,
           * then at this point, we are automatically in the new batch
           * and we have to treat all elements of this batch
           */
            if (!remainingElementsFromRecover.isEmpty()) {
                subList = new ArrayList<>(subList);
                subList.retainAll(remainingElementsFromRecover);
            }

            int subOffset = 0;
            int subListSize = subList.size();

            while (subOffset < subListSize) {
                int nextSubOffset = subListSize > subOffset + bulkSize ?
                    subOffset + bulkSize : subListSize;

                // split the list of items to be processed according to the capacity of the workers
                List<String> newSubList = subList.subList(subOffset, nextSubOffset);

                // prepare & instanciate the worker tasks
                workerParameters.setObjectNameList(newSubList);
                final WorkerTask workerTask =
                    new WorkerTask(
                        new DescriptionStep(step, ((DefaultWorkerParameters) workerParameters).newInstance()),
                        tenantId, requestId, contractId, contextId);

                currentWorkerTaskList.add(workerTask);
                completableFutureList.add(prepare(workerTask, operationId, tenantId));

                subOffset = nextSubOffset;
            }

            CompletableFuture<List<ItemStatus>> sequense = sequence(completableFutureList);
            CompletableFuture<ItemStatus> reduce = sequense
                .thenApplyAsync((List<ItemStatus> is) -> is.stream()
                    .reduce(step.getStepResponses(), (identity, iterationItemStatus) -> {
                        // compute cancelled actions
                        if (PauseOrCancelAction.ACTION_CANCEL.name().equals(iterationItemStatus.getItemId()) &&
                            iterationItemStatus.getGlobalStatus().equals(StatusCode.UNKNOWN)) {
                            cancelled.add(iterationItemStatus);
                        }
                        // compute paused actions
                        if (PauseOrCancelAction.ACTION_PAUSE.name().equals(iterationItemStatus.getItemId()) &&
                            iterationItemStatus.getGlobalStatus().equals(StatusCode.UNKNOWN)) {
                            paused.add(iterationItemStatus);
                        }
                        return identity.setItemsStatus(iterationItemStatus);
                    }));

            try {
                // store information
                final ItemStatus itemStatus = reduce.get();

                /**
                 * As pause can occurs on not started WorkerTask,
                 * so we have to get the corresponding elements in order to execute them after restart
                 */
                List<String> remainingElements =
                    currentWorkerTaskList.stream().filter(x -> !x.isCompleted()).map(e -> e.getObjectName())
                        .collect(Collectors.toList());

                if (itemStatus.getGlobalStatus().isGreaterOrEqualToFatal()) {
                    // Do not update index as we have to restart from old saved index
                    fatalOccurred = true;
                } else {
                    if (remainingElements.isEmpty()) {
                        offset = nextOffset;
                    }

                    // update && persist DistributorIndex if not Fatal
                    distributorIndex =
                        new DistributorIndex(level, offset, itemStatus, requestId, step.getId(), remainingElements);

                    // All elements of the current level are treated so finish it
                    if (finishedStream) {
                        distributorIndex.setLevelFinished(true);
                    }

                    try {
                        processDataManagement.persistDistributorIndex(DISTRIBUTOR_INDEX, operationId, distributorIndex);
                        LOGGER
                            .debug("Store for the container " + operationId + " the DistributorIndex offset" + offset +
                                " GlobalStatus " + itemStatus.getGlobalStatus());
                    } catch (VitamException e) {
                        LOGGER.error(AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_PERSIST_DISTRIBUTOR_INDEX, e);
                        throw new ProcessingException(
                            AN_EXCEPTION_HAS_BEEN_THROWN_WHEN_TRYING_TO_PERSIST_DISTRIBUTOR_INDEX, e);
                    }
                }

                if (cancelled.size() > 0) {
                    throw new PauseCancelException(PauseOrCancelAction.ACTION_CANCEL);
                }
                if (paused.size() > 0) {
                    throw new PauseCancelException(PauseOrCancelAction.ACTION_PAUSE);
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    /**
     * @param task
     * @param operationId
     * @param tenantId
     * @return
     */
    private CompletableFuture<ItemStatus> prepare(WorkerTask task, String operationId, int tenantId) {
        Step step = task.getStep();
        final WorkerFamilyManager wmf = workerManager.findWorkerBy(step.getWorkerGroupId());
        if (null == wmf) {
            LOGGER.error("No WorkerFamilyManager found for : " + step.getWorkerGroupId());
            return CompletableFuture.completedFuture(new ItemStatus(step.getStepName()).increment(StatusCode.FATAL));
        }
        return CompletableFuture
            .supplyAsync(task, wmf)
            .exceptionally((completionException) -> {
                LOGGER.error("Exception occured when executing task", completionException);
                Throwable cause = completionException.getCause();
                if (cause instanceof WorkerUnreachableException) {
                    WorkerUnreachableException wue = (WorkerUnreachableException) cause;
                    try {
                        workerManager.unregisterWorker(step.getWorkerGroupId(), wue.getWorkerId());
                    } catch (WorkerFamilyNotFoundException | WorkerNotFoundException | InterruptedException e1) {
                        LOGGER.error("Exception while unregister worker " + wue.getWorkerId(), cause);
                    }
                }
                return new ItemStatus(WORKER_CALL_EXCEPTION)
                    .setItemsStatus(WORKER_CALL_EXCEPTION,
                        new ItemStatus(WORKER_CALL_EXCEPTION).increment(StatusCode.FATAL));
            })
            .thenApply(is -> {
                //Do not update processed if pause or cancel occurs or if status is Fatal
                if (StatusCode.UNKNOWN.equals(is.getGlobalStatus()) || StatusCode.FATAL.equals(is.getGlobalStatus())) {
                    return is;
                }
                // update processed elements
                processDataAccess
                    .updateStep(operationId, step.getId(), task.getObjectNameList().size(), true, tenantId);
                return is;
            });
    }

    private static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allDoneFuture =
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        return allDoneFuture
            .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.<T>toList()));
    }

    @Override
    public void close() {
        // Nothing
    }
}
