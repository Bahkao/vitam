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


package fr.gouv.vitam.worker.core.handler;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.and;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.database.builder.query.BooleanQuery;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, LogbookOperationsClientFactory.class,
    StorageClientFactory.class})
public class VerifyMerkleTreeActionHandlerTest {
    VerifyMerkleTreeActionHandler verifyMerkleTreeActionHandler;
    private static final String DETAIL_EVENT_TRACEABILITY = "EVENT_DETAIL_DATA.json";
    private static final String DETAIL_EVENT_TRACEABILITY_WRONG_ROOT = "EVENT_DETAIL_DATA_WRONG_ROOT.json";    
    private static final String FAKE_DETAIL_EVENT_TRACEABILITY = "sip.xml";
    private static final String MERKLE_TREE_JSON = "merkleTree.json";
    private static final String MERKLE_TREE_JSON_WRONG_ROOT = "merkleTreeWrongRoot.json";

    private static final String OPERATIONS_JSON = "operations.json";
    private static final String OPERATIONS_WRONG_DATES_JSON = "operations_wrong_dates.json";

    private static final String FAKE_URL = "http://localhost:8080";
    private HandlerIOImpl action;
    private GUID guid;
    private WorkerParameters params;
    private static final Integer TENANT_ID = 0;
    private LogbookOperationsClientFactory logbookOperationsClientFactory;
    private LogbookOperationsClient logbookOperationsClient;
    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private StorageClient storageClient;
    private StorageClientFactory storageClientFactory;
    private List<IOParameter> in;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Before
    public void setUp() throws Exception {
        guid = GUIDFactory.newGUID();
        params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL).setUrlMetadata(FAKE_URL)
                .setObjectName("objectName.json").setCurrentStep("currentStep").setContainerName(guid.getId());
        final fr.gouv.vitam.common.database.builder.request.single.Select select =
            new fr.gouv.vitam.common.database.builder.request.single.Select();
        final BooleanQuery query = and();
        query.add(eq("evIdProc", "aecaaaaaachgxr27absisak3tofyf4yaaaaq"));
        select.setQuery(query);
        select.getFinalSelect();
        Map<String, String> checkExtraParams = new HashMap<>();
        checkExtraParams.put(WorkerParameterName.logbookRequest.toString(), JsonHandler.unprettyPrint(query));
        params.setMap(checkExtraParams);

        logbookOperationsClient = mock(LogbookOperationsClient.class);
        storageClient = mock(StorageClient.class);
        workspaceClient = mock(WorkspaceClient.class);
        PowerMockito.mockStatic(LogbookOperationsClientFactory.class);
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        PowerMockito.mockStatic(StorageClientFactory.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        logbookOperationsClientFactory = mock(LogbookOperationsClientFactory.class);
        storageClientFactory = mock(StorageClientFactory.class);
        PowerMockito.when(LogbookOperationsClientFactory.getInstance()).thenReturn(logbookOperationsClientFactory);
        PowerMockito.when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsClient);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        PowerMockito.when(StorageClientFactory.getInstance()).thenReturn(storageClientFactory);
        PowerMockito.when(storageClientFactory.getClient()).thenReturn(storageClient);
        action = new HandlerIOImpl(guid.getId(), "workerId");
    }

    @After
    public void end() {
        action.partialClose();
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeThenOK()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY), false);
        action.reset();
        action.addInIOParameters(in);

        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        final InputStream operationsJson =
            PropertiesUtils.getResourceAsStream(OPERATIONS_JSON);
        final InputStream merkleTreeJson =
            PropertiesUtils.getResourceAsStream(MERKLE_TREE_JSON);

        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            OPERATIONS_JSON)))
                .thenReturn(Response.status(Status.OK).entity(operationsJson).build());
        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            MERKLE_TREE_JSON)))
                .thenReturn(Response.status(Status.OK).entity(merkleTreeJson).build());
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeWithCompareToLoggedHashKOThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY_WRONG_ROOT), false);
        action.reset();
        action.addInIOParameters(in);

        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        final InputStream operationsJson =
            PropertiesUtils.getResourceAsStream(OPERATIONS_JSON);
        final InputStream merkleTreeJson =
            PropertiesUtils.getResourceAsStream(MERKLE_TREE_JSON);

        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            OPERATIONS_JSON)))
                .thenReturn(Response.status(Status.OK).entity(operationsJson).build());
        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            MERKLE_TREE_JSON)))
                .thenReturn(Response.status(Status.OK).entity(merkleTreeJson).build());
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyMerkleTreeActionHandler.getId())
            .getItemsStatus().get("COMPARE_MERKLE_HASH_WITH_SAVED_HASH").getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyMerkleTreeActionHandler.getId())
            .getItemsStatus().get("COMPARE_MERKLE_HASH_WITH_INDEXED_HASH").getGlobalStatus());
    }
    
    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeWithCompareToSecuredHashKOThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY), false);
        action.reset();
        action.addInIOParameters(in);

        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        final InputStream operationsJson =
            PropertiesUtils.getResourceAsStream(OPERATIONS_JSON);
        final InputStream merkleTreeJson =
            PropertiesUtils.getResourceAsStream(MERKLE_TREE_JSON_WRONG_ROOT);

        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            OPERATIONS_JSON)))
                .thenReturn(Response.status(Status.OK).entity(operationsJson).build());
        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            MERKLE_TREE_JSON)))
                .thenReturn(Response.status(Status.OK).entity(merkleTreeJson).build());
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyMerkleTreeActionHandler.getId())
            .getItemsStatus().get("COMPARE_MERKLE_HASH_WITH_SAVED_HASH").getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyMerkleTreeActionHandler.getId())
            .getItemsStatus().get("COMPARE_MERKLE_HASH_WITH_INDEXED_HASH").getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeWithIncorrectDatesThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY), false);
        action.reset();
        action.addInIOParameters(in);

        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        final InputStream operationsJson =
            PropertiesUtils.getResourceAsStream(OPERATIONS_WRONG_DATES_JSON);
        final InputStream merkleTreeJson =
            PropertiesUtils.getResourceAsStream(MERKLE_TREE_JSON);

        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            OPERATIONS_JSON)))
                .thenReturn(Response.status(Status.OK).entity(operationsJson).build());
        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            MERKLE_TREE_JSON)))
                .thenReturn(Response.status(Status.OK).entity(merkleTreeJson).build());
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeWithIncorrectTraceabilityEventThenFATAL()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(FAKE_DETAIL_EVENT_TRACEABILITY), false);
        action.reset();
        action.addInIOParameters(in);
        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyMerkleTreeWithOperationJsonNotFoundThenFATAL()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY), false);
        action.reset();
        action.addInIOParameters(in);

        verifyMerkleTreeActionHandler = new VerifyMerkleTreeActionHandler();
        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            OPERATIONS_JSON)))
                .thenThrow(new ContentAddressableStorageNotFoundException(OPERATIONS_JSON + " not found"));
        final ItemStatus response = verifyMerkleTreeActionHandler.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }

}
