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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.exception.StateNotAllowedException;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessQuery;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.Action;
import fr.gouv.vitam.common.model.processing.ActionDefinition;
import fr.gouv.vitam.common.model.processing.Distribution;
import fr.gouv.vitam.common.model.processing.DistributionKind;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessBehavior;
import fr.gouv.vitam.common.model.processing.ProcessDetail;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.Step;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.common.model.processing.WorkFlow;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.processing.common.config.ServerConfiguration;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.model.ProcessWorkflow;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.processing.data.core.ProcessDataAccessImpl;
import fr.gouv.vitam.processing.data.core.management.WorkspaceProcessDataManagement;
import fr.gouv.vitam.processing.distributor.api.IWorkerManager;
import fr.gouv.vitam.processing.distributor.api.ProcessDistributor;
import fr.gouv.vitam.processing.distributor.v2.ProcessDistributorImpl;
import fr.gouv.vitam.processing.distributor.v2.WorkerManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.net.ssl.*"})
@PrepareForTest({WorkspaceProcessDataManagement.class, ProcessDataAccessImpl.class})
public class ProcessManagementImplTest {

    private ProcessManagementImpl processManagementImpl;
    private static final String CONTAINER_NAME = "container1";
    private static final String ID = "id1";
    private static WorkspaceProcessDataManagement processDataManagement;
    private IWorkerManager workerManager = null;
    private ProcessDistributor processDistributor = null;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Before
    public void setup() {
        PowerMockito.mockStatic(WorkspaceProcessDataManagement.class);
        processDataManagement = PowerMockito.mock(WorkspaceProcessDataManagement.class);
        PowerMockito.when(WorkspaceProcessDataManagement.getInstance()).thenReturn(processDataManagement);

        workerManager = new WorkerManager();
        processDistributor = new ProcessDistributorImpl(workerManager);
    }

    @Test(expected = ProcessingException.class)
    @RunWithCustomExecutor
    public void testResumeNotInitiatedWorkflow() throws ProcessingException, StateNotAllowedException {
        VitamThreadUtils.getVitamSession().setTenantId(1);
        PowerMockito.verifyNoMoreInteractions(processDataManagement);
        PowerMockito.when(processDataManagement.getProcessWorkflowFor(Matchers.eq(1), Matchers.anyString()))
            .thenReturn(new HashMap<>());
        processManagementImpl =
            new ProcessManagementImpl(new ServerConfiguration(), processDistributor);
        processManagementImpl.resume(
            WorkerParametersFactory.newWorkerParameters(ID, ID, CONTAINER_NAME, ID, Lists.newArrayList(ID),
                "http://localhost:8083",
                "http://localhost:8083"),
            1);
    }

    @RunWithCustomExecutor
    @Test
    public void loadNoPersistedWorkflowTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(2);
        // No persisted Workflow
        PowerMockito.verifyNoMoreInteractions(processDataManagement);
        PowerMockito.when(processDataManagement.getProcessWorkflowFor(Matchers.eq(2), Matchers.anyString()))
            .thenReturn(new HashMap<>());
        processManagementImpl = new ProcessManagementImpl(new ServerConfiguration(), processDistributor);
        Assert.assertNotNull(processManagementImpl);
        List<ProcessWorkflow> processWorkflowList = processManagementImpl.findAllProcessWorkflow(2);
        Assert.assertNotNull(processWorkflowList);
        Assert.assertTrue(processWorkflowList.isEmpty());
    }

    @RunWithCustomExecutor
    @Test
    public void loadPersitedPausedWorkflowTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(3);
        PowerMockito.when(processDataManagement.getProcessWorkflowFor(Matchers.eq(3), Matchers.anyString()))
            .thenReturn(getPausedWorkflowMap());

        ServerConfiguration serverConfiguration = new ServerConfiguration();
        serverConfiguration.setUrlMetadata("fakeurl:1111");
        serverConfiguration.setUrlWorkspace("fakeurl:1112");
        processManagementImpl = new ProcessManagementImpl(serverConfiguration, processDistributor);
        Assert.assertNotNull(processManagementImpl);
        List<ProcessWorkflow> processWorkflowList = processManagementImpl.findAllProcessWorkflow(3);
        Assert.assertNotNull(processWorkflowList);
        Assert.assertFalse(processWorkflowList.isEmpty());
        Map<String, WorkFlow> workflowDefinitions = processManagementImpl.getWorkflowDefinitions();
        Assert.assertNotNull(workflowDefinitions);
        Assert.assertNotNull(workflowDefinitions.get("FILINGSCHEME"));
        Assert.assertNotNull(workflowDefinitions.get("PROCESS_SIP_UNITARY"));
        Assert.assertEquals("FILINGSCHEME",
            workflowDefinitions.get("FILINGSCHEME").getIdentifier());
        Assert.assertEquals(11,
            workflowDefinitions.get("PROCESS_SIP_UNITARY").getSteps().size());
        Assert.assertEquals(3,
            workflowDefinitions.get("PROCESS_SIP_UNITARY").getSteps().get(2).getActions().size());
        Assert.assertEquals("CHECK_UNIT_SCHEMA",
            workflowDefinitions.get("PROCESS_SIP_UNITARY").getSteps().get(2).getActions().get(0).getActionDefinition()
                .getActionKey());
    }

    @Test
    public void getFilteredProcessTest() throws Exception {
        ServerConfiguration serverConfiguration = new ServerConfiguration();
        serverConfiguration.setUrlMetadata("fakeurl:1111");
        serverConfiguration.setUrlWorkspace("fakeurl:1112");

        ProcessDataAccessImpl processDataAccess = mock(ProcessDataAccessImpl.class);

        PowerMockito.mockStatic(ProcessDataAccessImpl.class);

        when(ProcessDataAccessImpl.getInstance()).thenReturn(processDataAccess);

        when(processDataAccess.findAllProcessWorkflow(eq(0))).thenReturn(getPausedWorkflowList(5));

        VitamConfiguration.getConfiguration().setData(PropertiesUtils.getResourcePath("").toString());

        processManagementImpl = new ProcessManagementImpl(serverConfiguration, processDistributor);
        Assert.assertNotNull(processManagementImpl);

        ProcessQuery pq = new ProcessQuery();

        List<ProcessDetail> results = processManagementImpl.getFilteredProcess(pq, 0);
        assertThat(results).hasSize(5);

        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        LocalDate dateMax = LocalDate.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        pq.setStartDateMin(date.format(formatter));
        pq.setStartDateMax(dateMax.format(formatter));
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(1, results.size());

        dateMax = dateMax.plusDays(1);
        pq.setStartDateMax(dateMax.format(formatter));
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(2, results.size());

        dateMax = dateMax.plusDays(2);
        pq.setStartDateMax(dateMax.format(formatter));
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(4, results.size());

        dateMax = dateMax.plusDays(1);
        pq.setStartDateMax(dateMax.format(formatter));
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(5, results.size());

        date = date.plusDays(1);
        pq.setStartDateMin(date.format(formatter));
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(4, results.size());

        pq.setStartDateMin(null);
        pq.setStartDateMax(null);
        List<String> list = new ArrayList<>();
        list.add(StatusCode.OK.name());
        pq.setStatuses(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(5, results.size());

        list.clear();
        list.add(StatusCode.KO.name());
        pq.setStatuses(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(0, results.size());
        list.add(StatusCode.OK.name());
        pq.setStatuses(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(5, results.size());

        pq.setStatuses(null);
        pq.setId("operationId0");
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(1, results.size());

        pq.setId(null);
        list.clear();
        list.add(ProcessState.PAUSE.name());
        pq.setStates(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(5, results.size());

        list.clear();
        list.add(ProcessState.RUNNING.name());
        pq.setStates(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(0, results.size());
        list.add(ProcessState.PAUSE.name());
        pq.setStates(list);
        results = processManagementImpl.getFilteredProcess(pq, 0);
        Assert.assertEquals(5, results.size());
    }

    private Map<String, ProcessWorkflow> getPausedWorkflowMap() {
        Map<String, ProcessWorkflow> result = new HashMap<>();
        ProcessWorkflow processWorkflow = new ProcessWorkflow();
        processWorkflow.setTenantId(3);
        processWorkflow.setState(ProcessState.PAUSE);
        processWorkflow.setStatus(StatusCode.OK);
        processWorkflow.setLogbookTypeProcess(LogbookTypeProcess.INGEST);
        processWorkflow.setMessageIdentifier("MessageIdentifier");
        processWorkflow.setOperationId("operationId");
        for (int i = 0; i < 20; i++) {
            processWorkflow.getSteps().add(getProcessStep("key-map-" + i, "name-" + i, "element-" + i, "groupID-" + i));
        }
        processWorkflow.setProcessDate(new Date());

        result.put(processWorkflow.getOperationId(), processWorkflow);
        return result;
    }

    private List<ProcessWorkflow> getPausedWorkflowList(int nbProcess) {
        List<ProcessWorkflow> list = new ArrayList<>();
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        for (int j = 0; j < nbProcess; j++) {
            ProcessWorkflow processWorkflow = new ProcessWorkflow();
            processWorkflow.setTenantId(3);
            processWorkflow.setState(ProcessState.PAUSE);
            processWorkflow.setStatus(StatusCode.OK);
            processWorkflow.setLogbookTypeProcess(LogbookTypeProcess.INGEST);
            processWorkflow.setMessageIdentifier("MessageIdentifier");
            processWorkflow.setOperationId("operationId" + j);
            for (int i = 0; i < 20; i++) {
                processWorkflow.getSteps()
                    .add(getProcessStep("key-map-" + i, "name-" + i, "element-" + i, "groupID-" + i));
            }
            date = date.plusDays(j == 0 ? 0 : 1);
            processWorkflow.setProcessDate(Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()));
            list.add(processWorkflow);
        }
        return list;
    }

    private List<Action> getActions() {
        List<Action> actionsList = new ArrayList<>();
        for (int j = 0; j < 10; j++) {
            Action action = new Action();
            ActionDefinition actionDefinition = new ActionDefinition();
            actionDefinition.setBehavior(j % 2 == 0 ? ProcessBehavior.BLOCKING : ProcessBehavior.NOBLOCKING);
            actionDefinition.setActionKey("actionKey-" + j);
            List<IOParameter> in = new ArrayList<>();
            List<IOParameter> out = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                IOParameter ioParameter = new IOParameter();
                ioParameter.setName("io-name-" + i);
                ioParameter.setOptional(false);
                ProcessingUri processingUri = new ProcessingUri(UriPrefix.WORKSPACE, "path-" + i);
                ioParameter.setUri(processingUri);
                in.add(ioParameter);
                out.add(ioParameter);
            }
            actionDefinition.setIn(in);
            actionDefinition.setOut(out);
            action.setActionDefinition(actionDefinition);
            actionsList.add(action);
        }
        return actionsList;
    }

    private ProcessStep getProcessStep(String id, String name, String element, String groupId) {
        Step step = new Step();
        step.setId(id);
        step.setActions(getActions());
        step.setBehavior(ProcessBehavior.NOBLOCKING);
        Distribution distrib = new Distribution();
        distrib.setElement(element);
        distrib.setKind(DistributionKind.LIST);
        step.setDistribution(distrib);
        step.setStepName(name);
        ItemStatus itemStatus = new ItemStatus();
        itemStatus.setMessage("message");
        itemStatus.setItemId("itemId");
        itemStatus.setGlobalState(ProcessState.COMPLETED);
        step.setStepResponses(itemStatus);
        step.setWorkerGroupId(groupId);
        ProcessStep ps = new ProcessStep(step, 0, 0, "id");
        ps.setStepStatusCode(StatusCode.OK);
        return ps;
    }
}
