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
package fr.gouv.vitam.processing.management.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.StateNotAllowedException;
import fr.gouv.vitam.common.exception.WorkflowNotFoundException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessPause;
import fr.gouv.vitam.common.model.ProcessQuery;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.ProcessDetail;
import fr.gouv.vitam.common.model.processing.WorkFlow;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.processing.common.automation.IEventsState;
import fr.gouv.vitam.processing.common.config.ServerConfiguration;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.exception.ProcessingStorageWorkspaceException;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.model.ProcessWorkflow;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.processing.common.utils.ProcessPopulator;
import fr.gouv.vitam.processing.data.core.ProcessDataAccess;
import fr.gouv.vitam.processing.data.core.ProcessDataAccessImpl;
import fr.gouv.vitam.processing.data.core.management.ProcessDataManagement;
import fr.gouv.vitam.processing.data.core.management.WorkspaceProcessDataManagement;
import fr.gouv.vitam.processing.distributor.api.ProcessDistributor;
import fr.gouv.vitam.processing.engine.api.ProcessEngine;
import fr.gouv.vitam.processing.engine.core.ProcessEngineFactory;
import fr.gouv.vitam.processing.management.api.ProcessManagement;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;


/**
 * ProcessManagementImpl implementation of ProcessManagement API
 */
public class ProcessManagementImpl implements ProcessManagement {


    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ProcessManagementImpl.class);

    private static final String WORKFLOW_NOT_FOUND_MESSAGE = "Workflow doesn't exist";
    private static final Map<String, IEventsState> PROCESS_MONITORS = new ConcurrentHashMap<>();

    private ServerConfiguration config;
    private final ProcessDataAccess processData;
    private final Map<String, WorkFlow> poolWorkflow;
    private ProcessDistributor processDistributor;
    private final Map<Integer, List<LogbookTypeProcess>> pausedProcessesByTenant;
    private final List<Integer> pausedTenants;
    private final List<LogbookTypeProcess> pausedTypeProcesses;
    private Boolean pauseAll;
    /**
     * constructor of ProcessManagementImpl
     *
     * @param config configuration of process engine server
     * @param processDistributor
     * @throws ProcessingStorageWorkspaceException thrown when error occurred on loading paused process
     */
    public ProcessManagementImpl(ServerConfiguration config,
        ProcessDistributor processDistributor)
        throws ProcessingStorageWorkspaceException {

        ParametersChecker.checkParameter("Server config cannot be null", config);
        this.config = config;
        processData = ProcessDataAccessImpl.getInstance();
        poolWorkflow = new ConcurrentHashMap<>();
        this.pausedProcessesByTenant = new ConcurrentHashMap<Integer, List<LogbookTypeProcess>>();
        this.pausedTenants = new ArrayList<Integer>();
        this.pausedTypeProcesses = new ArrayList<LogbookTypeProcess>();
        pauseAll = Boolean.FALSE;
        this.processDistributor = processDistributor;
        new ProcessWorkFlowsCleaner(this, TimeUnit.HOURS);
        new WorkflowsLoader(this);

        try {
            // load all workflows
            ProcessPopulator.loadWorkflows(poolWorkflow);
        } catch (final WorkflowNotFoundException e) {
            LOGGER.error(WORKFLOW_NOT_FOUND_MESSAGE, e);
        }

        loadProcessFromWorkSpace(config.getUrlMetadata(), config.getUrlWorkspace());
    }

    @Override
    public void startProcess() {

        /**
         * Do not start process in test mode Before test you should add SystemPropertyUtil.set("vitam.test.junit",
         * "true");
         */
        if (VitamConfiguration.isIntegrationTest()) {
            return;
        }
        for (String operationId : PROCESS_MONITORS.keySet()) {
            final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

            if (!stateMachine.isRecover()) {
                continue;
            }

            try {

                VitamThreadUtils.getVitamSession().setTenantId(stateMachine.getTenant());
                VitamThreadUtils.getVitamSession().setRequestId(operationId);
                VitamThreadUtils.getVitamSession().setContextId(stateMachine.getContextId());

                final WorkerParameters workerParameters =
                    WorkerParametersFactory.newWorkerParameters()
                        .setUrlMetadata(config.getUrlMetadata())
                        .setUrlWorkspace(config.getUrlWorkspace())
                        .setLogbookTypeProcess(stateMachine.getLogbookTypeProcess())
                        .setContainerName(operationId)
                        .setRequestId(operationId)
                        .putParameterValue(WorkerParameterName.context, stateMachine.getWorkflowId());

                if (stateMachine.isStepByStep()) {
                    stateMachine.next(workerParameters);
                } else {
                    stateMachine.resume(workerParameters);
                }
            } catch (StateNotAllowedException | ProcessingException e) {
                LOGGER.error("Error while pause the processWorkflow : " + operationId, e);
            }
        }
    }

    /**
     * This method is used to properly stop all processworklfow Call stop on all running process workflow and propagate
     * this stop to the distributor The distributor should : - Unregister all worker and complete all opened connections
     * to the workers - Stop properly waiting tasks - Save index of element to be used in the next start
     */
    @Override
    public void stopProcess() {
        for (String operationId : PROCESS_MONITORS.keySet()) {
            final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

            if (stateMachine.isDone()) {
                continue;
            }

            try {
                stateMachine.shutdown();
            } catch (StateNotAllowedException e) {
                LOGGER.error("Error while pause the processWorkflow : " + operationId, e);
            }
        }
        PROCESS_MONITORS.clear();
    }

    @Override
    public ProcessWorkflow init(WorkerParameters workerParameters, String workflowId,
        LogbookTypeProcess logbookTypeProcess, Integer tenantId, String contextId, String applicationId)
        throws ProcessingException {

        // check data container and folder
        ProcessDataManagement dataManagement = WorkspaceProcessDataManagement.getInstance();
        dataManagement.createProcessContainer();
        dataManagement.createFolder(VitamConfiguration.getWorkspaceWorkflowsFolder());

        final ProcessWorkflow processWorkflow;
        if (ParametersChecker.isNotEmpty(workflowId)) {
            processWorkflow = processData
                .initProcessWorkflow(poolWorkflow.get(workflowId), workerParameters.getContainerName(),
                    logbookTypeProcess, tenantId, contextId, applicationId);
        } else {
            processWorkflow = processData
                .initProcessWorkflow(null, workerParameters.getContainerName(), LogbookTypeProcess.INGEST, tenantId, contextId, applicationId);
        }

        processWorkflow.setWorkflowId(workflowId);

        try {
            dataManagement.persistProcessWorkflow(VitamConfiguration.getWorkspaceWorkflowsFolder(),
                workerParameters.getContainerName(), processWorkflow);
        } catch (InvalidParseOperationException e) {
            throw new ProcessingException(e);
        }

        workerParameters.setLogbookTypeProcess(logbookTypeProcess);
        WorkspaceClientFactory.changeMode(config.getUrlWorkspace());

        final ProcessEngine processEngine = ProcessEngineFactory.get().create(workerParameters, processDistributor);
        final StateMachine stateMachine = StateMachineFactory.get().create(processWorkflow, processEngine);
        processEngine.setCallback(stateMachine);

        PROCESS_MONITORS.put(workerParameters.getContainerName(), stateMachine);

        return processWorkflow;
    }

    @Override
    public ItemStatus next(WorkerParameters workerParameters, Integer tenantId)
        throws ProcessingException,
        StateNotAllowedException {

        final String operationId = workerParameters.getContainerName();

        final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

        if (null == stateMachine) {
            throw new ProcessingException(
                "StateMachine not found with id " + operationId + ". Handle INIT before next");
        }

        final ProcessWorkflow processWorkflow = findOneProcessWorkflow(operationId, tenantId);

        stateMachine.next(workerParameters);


        return new ItemStatus(operationId)
            .increment(processWorkflow.getStatus())
            .setGlobalState(processWorkflow.getState())
            .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess().toString());
    }

    @Override
    public ItemStatus replay(WorkerParameters workerParameters, Integer tenantId)
        throws ProcessingException,
        StateNotAllowedException {

        final String operationId = workerParameters.getContainerName();

        final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

        if (null == stateMachine) {
            throw new ProcessingException(
                "StateMachine not found with id " + operationId + ". Handle INIT before next");
        }

        final ProcessWorkflow processWorkflow = findOneProcessWorkflow(operationId, tenantId);

        stateMachine.replay(workerParameters);


        return new ItemStatus(operationId)
            .increment(processWorkflow.getStatus())
            .setGlobalState(processWorkflow.getState())
            .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess().toString());
    }

    @Override
    public ItemStatus resume(WorkerParameters workerParameters, Integer tenantId, boolean useForcedPause)
        throws ProcessingException, StateNotAllowedException {
        final String operationId = workerParameters.getContainerName();

        final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

        if (null == stateMachine) {
            throw new ProcessingException(
                "StateMachine not found with id " + operationId + ". Handle INIT before next");
        }

        final ProcessWorkflow processWorkflow = findOneProcessWorkflow(operationId, tenantId);

        if (useForcedPause && isPauseForced(processWorkflow, tenantId)) {
            return next(workerParameters, tenantId);
        }

        stateMachine.resume(workerParameters);

        return new ItemStatus(operationId)
            .increment(processWorkflow.getStatus())
            .setGlobalState(processWorkflow.getState())
            .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess().toString());
    }

    @Override
    public ItemStatus pause(String operationId, Integer tenantId)
        throws ProcessingException, StateNotAllowedException {

        final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

        if (null == stateMachine) {
            throw new ProcessingException(
                "StateMachine not found with id " + operationId + ". Handle INIT before next");
        }

        final ProcessWorkflow processWorkflow = findOneProcessWorkflow(operationId, tenantId);

        stateMachine.pause();


        return new ItemStatus(operationId)
            .increment(processWorkflow.getStatus())
            .setGlobalState(processWorkflow.getState())
            .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess().toString());
    }

    /**
     * Ckeck if the processWorkflow.logbookTypeProcess or the tenantId are paused
     *
     * @param processWorkflow
     * @param tenantId
     * @return
     */
    private boolean isPauseForced(ProcessWorkflow processWorkflow, Integer tenantId) {

        //Check the pauseAll param, if true all the processes for all the tenants are paused
        if (Boolean.TRUE.equals(pauseAll)) {
            return true;
        }

        //Check the list of paused tenant
        if (pausedTenants.contains(tenantId)) {
            return true;
        }


        //Get the logbookTypeProcess of the workflow
        LogbookTypeProcess logbookTypeProcess = processWorkflow.getLogbookTypeProcess();

        //Check the list of paused processes
        if (pausedTypeProcesses.contains(logbookTypeProcess)) {
            return true;
        }

        //Check the list of paused process for the given tenant
        List<LogbookTypeProcess> pausedWorklowsByTenant = pausedProcessesByTenant.get(tenantId);
        if (pausedWorklowsByTenant != null && pausedWorklowsByTenant.contains(logbookTypeProcess)) {
            return true;
        }

        return false;
    }


    @Override
    public void forcePause(ProcessPause pause)
        throws ProcessingException {
        String type = pause.getType();
        Integer tenantId = pause.getTenant();
        Boolean pauseAll = pause.getPauseAll();


        if (type == null && tenantId == null && pauseAll == null) {
            throw new ProcessingException(
                "Type, tenant and pauseAll param cannot all be null");
        }

        this.pauseAll = pause.getPauseAll();

        LogbookTypeProcess processType = null;
        if (type != null && !type.isEmpty()) {
            try {
                processType = LogbookTypeProcess.getLogbookTypeProcess(type);
            } catch (IllegalArgumentException e) {
                throw new ProcessingException(
                    "Type " + type + " is not a valid process type");
            }
        }

        if (processType != null && tenantId != null) {
            //Get the list of paused process for the given tenant
            List<LogbookTypeProcess> pausedWorklowsByTenant = pausedProcessesByTenant.get(tenantId);
            if (pausedWorklowsByTenant == null) {
                pausedWorklowsByTenant = new ArrayList<LogbookTypeProcess>();
            }
            if (!pausedWorklowsByTenant.contains(processType)) {
                pausedWorklowsByTenant.add(tenantId, processType);
            }
            pausedProcessesByTenant.put(tenantId, pausedWorklowsByTenant);
        } else if (processType == null && tenantId != null) {
            if (!pausedTenants.contains(tenantId)) {
                pausedTenants.add(tenantId);
            }
        } else if (processType != null && tenantId == null) {
            if (!pausedTypeProcesses.contains(processType)) {
                pausedTypeProcesses.add(processType);
            }
        }

    }


    @Override
    public void removeForcePause(ProcessPause pause)
        throws ProcessingException {

        String type = pause.getType();
        Integer tenantId = pause.getTenant();
        Boolean pauseAll = pause.getPauseAll();

        if (type == null && tenantId == null && pauseAll == null) {
            throw new ProcessingException(
                "Type, tenant and pauseAll param cannot all be null");
        }
        //Remove the pauseAll
        if (Boolean.FALSE.equals(pauseAll)) {
            this.pauseAll = pauseAll;
        }


        LogbookTypeProcess processType = null;
        if (type != null && !type.isEmpty()) {
            try {
                processType = LogbookTypeProcess.getLogbookTypeProcess(type);
            } catch (IllegalArgumentException e) {
                throw new ProcessingException(
                    "Type " + type + "is not a valid process type");
            }
        }

        if (processType != null && tenantId != null) {
            //Get the list of paused process for the given tenant
            List<LogbookTypeProcess> pausedWorklowsByTenant = pausedProcessesByTenant.get(tenantId);
            if (pausedWorklowsByTenant != null && !pausedWorklowsByTenant.isEmpty()) {
                pausedWorklowsByTenant.remove(processType);
            }
            //remove the tenant from the pausedTenants list and from the pausedProcessesByTenant map
        } else if (processType == null && tenantId != null) {
            if (pausedTenants.contains(tenantId)) {
                pausedTenants.remove(tenantId);
            }
            if (pausedProcessesByTenant.containsKey(tenantId)) {
                pausedProcessesByTenant.remove(tenantId);
            }
            //remove the processType from the paused pausedTypeProcesses list and from the pausedProcessesByTenant map
        } else if (processType != null && tenantId == null) {
            if (pausedTypeProcesses.contains(processType)) {
                pausedTypeProcesses.remove(processType);
            }
            LogbookTypeProcess pr = processType;
            pausedProcessesByTenant.forEach((id, v) -> {
                if (v != null && !v.isEmpty()) {
                    v.remove(pr);
                }

            });
        }


    }

    @Override
    public ItemStatus cancel(String operationId, Integer tenantId)
        throws WorkflowNotFoundException, ProcessingException, StateNotAllowedException {


        final IEventsState stateMachine = PROCESS_MONITORS.get(operationId);

        if (null == stateMachine) {
            throw new ProcessingException(
                "StateMachine not found with id " + operationId + ". Handle INIT before next");
        }

        final ProcessWorkflow processWorkflow = findOneProcessWorkflow(operationId, tenantId);

        stateMachine.cancel();


        return new ItemStatus(operationId)
            .increment(processWorkflow.getStatus())
            .setGlobalState(processWorkflow.getState())
            .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess().toString());
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public List<ProcessWorkflow> findAllProcessWorkflow(Integer tenantId) {
        return processData.findAllProcessWorkflow(tenantId);
    }

    @Override
    public ProcessWorkflow findOneProcessWorkflow(String operationId, Integer tenantId) {
        return processData.findOneProcessWorkflow(operationId, tenantId);
    }

    @Override
    public Map<String, WorkFlow> getWorkflowDefinitions() {
        return poolWorkflow;
    }

    @Override
    public void reloadWorkflowDefinitions() {
        Integer period = this.getConfiguration().getWorkflowRefreshPeriod();
        long fromDate = Instant.now().minus(period, ChronoUnit.HOURS).toEpochMilli();

        ProcessPopulator.reloadWorkflows(poolWorkflow, fromDate);
    }

    public Map<Integer, Map<String, ProcessWorkflow>> getWorkFlowList() {
        return processData.getWorkFlowList();
    }

    @Override
    public Map<String, IEventsState> getProcessMonitorList() {
        return PROCESS_MONITORS;
    }

    private Map<String, IEventsState> loadProcessFromWorkSpace(String urlMetadata, String urlWorkspace)
        throws ProcessingStorageWorkspaceException {
        if (!PROCESS_MONITORS.isEmpty()) {
            return PROCESS_MONITORS;
        }

        final ProcessDataManagement datamanage = WorkspaceProcessDataManagement.getInstance();
        Map<String, ProcessWorkflow> map = datamanage.getProcessWorkflowFor(null, VitamConfiguration.getWorkspaceWorkflowsFolder());

        // Nothing to load
        if (map == null) {
            return PROCESS_MONITORS;
        }

        for (String operationId : map.keySet()) {
            ProcessWorkflow processWorkflow = map.get(operationId);
            if (processWorkflow.getState().equals(ProcessState.PAUSE)) {
                // Create & start ProcessEngine Thread
                WorkerParameters workerParameters =
                    WorkerParametersFactory.newWorkerParameters()
                        .setUrlMetadata(urlMetadata)
                        .setUrlWorkspace(urlWorkspace)
                        .setLogbookTypeProcess(processWorkflow.getLogbookTypeProcess())
                        .setContainerName(operationId)
                        .putParameterValue(WorkerParameterName.context, processWorkflow.getWorkflowId());

                final ProcessEngine processEngine = ProcessEngineFactory.get().create(workerParameters,
                    this.processDistributor);
                final StateMachine stateMachine = StateMachineFactory.get().create(processWorkflow, processEngine);
                processEngine.setCallback(stateMachine);

                PROCESS_MONITORS.put(workerParameters.getContainerName(), stateMachine);

            } else {
                if (StatusCode.UNKNOWN.equals(processWorkflow.getStatus())) {
                    processWorkflow.setStatus(StatusCode.UNKNOWN);
                    processWorkflow.setProcessCompletedDate(LocalDateTime.now());
                    processWorkflow.setState(ProcessState.COMPLETED);
                    try {
                        datamanage.persistProcessWorkflow(VitamConfiguration.getWorkspaceWorkflowsFolder(), operationId, processWorkflow);
                    } catch (InvalidParseOperationException e) {
                        // TODO: just log error is the good solution (here, we set to failed and unknown status on wrong
                        // persisted process) ?
                        LOGGER.error("Cannot set UNKNONW status and FAILED execution status on workflow {}, check " +
                            "processing datas",
                            operationId, e);
                    }
                }
            }
            ProcessDataAccessImpl.getInstance().addToWorkflowList(processWorkflow);
        }
        return PROCESS_MONITORS;
    }

    private static String getNextStepId(List<ProcessStep> steps) {
        for (ProcessStep processStep : steps) {
            // Actually the first step in UNKNOWN status is the next step to start
            // This is an ugly method to retrieve it, but there are no more informations
            if (processStep.getElementProcessed() == 0 && processStep.getElementToProcess() == 0 &&
                processStep.getStepStatusCode().equals(StatusCode.UNKNOWN)) {
                return processStep.getId();
            }
        }
        return null;
    }

    public List<ProcessDetail> getFilteredProcess(ProcessQuery query, Integer tenantId) {
        List<ProcessWorkflow> listWorkflows = this.findAllProcessWorkflow(tenantId);
        listWorkflows.sort((a, b) -> b.getProcessDate().compareTo(a.getProcessDate()));

        List<ProcessDetail> results = new ArrayList<>();

        for (ProcessWorkflow processWorkflow : listWorkflows) {
            ProcessDetail workflow = new ProcessDetail();
            workflow = getNextAndPreviousSteps(processWorkflow, workflow);
            if (query.getId() != null && !query.getId().equals(processWorkflow.getOperationId())) {
                continue;
            }
            if (query.getStates() != null && !query.getStates().isEmpty() &&
                !query.getStates().contains(processWorkflow.getState().name())) {
                continue;
            }
            if (query.getStatuses() != null && !query.getStatuses().isEmpty() &&
                !query.getStatuses().contains(processWorkflow.getStatus().name())) {
                continue;
            }
            if (query.getWorkflows() != null && !query.getWorkflows().isEmpty() &&
                !query.getWorkflows().contains(processWorkflow.getWorkflowId())) {
                continue;
            }
            if (query.getListSteps() != null && !query.getListSteps().isEmpty()) {
                if (!isContainsStep(query.getListSteps(), workflow)) {
                    continue;
                }
            }
            if (query.getListProcessTypes() != null && !query.getListProcessTypes().isEmpty() &&
                !query.getListProcessTypes().contains(processWorkflow.getLogbookTypeProcess().toString())) {
                continue;
            }
            if (query.getStartDateMin() != null && query.getStartDateMax() != null) {
                if (!isStartDateIn(query.getStartDateMin(), query.getStartDateMax(), processWorkflow)) {
                    continue;
                }
            }
            workflow.setOperationId(processWorkflow.getOperationId());
            workflow.setProcessType(processWorkflow.getLogbookTypeProcess().toString());
            workflow.setStepByStep(processWorkflow.isStepByStep());
            workflow.setGlobalState(processWorkflow.getState().name());
            workflow.setStepStatus(processWorkflow.getStatus().name());
            workflow.setProcessDate(LocalDateUtil.getFormattedDate(processWorkflow.getProcessDate()));
            results.add(workflow);
        }
        return results;
    }

    private boolean isContainsStep(List<String> stepsName, ProcessDetail workflow) {
        String previous = workflow.getPreviousStep();
        return previous != null && !previous.isEmpty() && stepsName.contains(previous);
    }

    private boolean isStartDateIn(String startDateMin, String startDateMax, ProcessWorkflow processWorkflow) {
        // ugly ! can we have time here (on javascript date picker) ?
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Date date = processWorkflow.getProcessDate();
        LocalDate ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC).toLocalDate();
        LocalDate startDateTimeMin = LocalDate.parse(startDateMin, formatter);
        LocalDate startDateTimeMax = LocalDate.parse(startDateMax, formatter);
        return ((ldt.isBefore(startDateTimeMax) || ldt.isEqual(startDateTimeMax)) &&
            (ldt.isAfter(startDateTimeMin) || ldt.isEqual(startDateTimeMin)));
    }

    // TODO: 5/27/17 refactor the following
    private ProcessDetail getNextAndPreviousSteps(ProcessWorkflow processWorkflow, ProcessDetail workflow) {
        String previousStep = "";
        String nextStep = "";
        String temporaryPreviousTask = "";
        Boolean currentStepFound = false;

        Iterator<ProcessStep> pwIterator = processWorkflow.getSteps().iterator();
        while (pwIterator.hasNext() && !currentStepFound) {

            final ProcessStep processStep = pwIterator.next();

            switch (processWorkflow.getState()) {
                case PAUSE:
                case RUNNING:
                    if (processStep.getStepStatusCode() == StatusCode.STARTED) {
                        previousStep = processStep.getStepName();
                        nextStep = pwIterator.hasNext() ? pwIterator.next().getStepName() : "";
                        workflow.setStepStatus("STARTED");
                        currentStepFound = true;
                    } else {
                        if (processStep.getStepStatusCode() == StatusCode.UNKNOWN) {
                            previousStep = temporaryPreviousTask;
                            nextStep = processStep.getStepName();
                            currentStepFound = true;
                        }
                    }
                    break;
                case COMPLETED:
                    if (processStep.getStepStatusCode() == StatusCode.KO ||
                        processStep.getStepStatusCode() == StatusCode.STARTED) {
                        previousStep = processStep.getStepName();
                        workflow.setStepStatus(StatusCode.KO.toString());
                        currentStepFound = true;
                    } else {
                        if (processStep.getStepStatusCode() == StatusCode.UNKNOWN) {
                            previousStep = temporaryPreviousTask;
                            workflow.setStepStatus(StatusCode.KO.toString());
                            currentStepFound = true;
                        }
                    }
                    break;
                default:
                    break;
            }
            temporaryPreviousTask = processStep.getStepName();

            workflow.setPreviousStep(previousStep);
            workflow.setNextStep(nextStep);
        }
        return workflow;
    }

    @Override
    public ServerConfiguration getConfiguration() {
        return config;

    }
}
