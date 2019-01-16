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
package fr.gouv.vitam.logbook.operations.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.server.mongodb.VitamMongoCursor;
import fr.gouv.vitam.common.model.RequestResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.MongoCursor;

import fr.gouv.vitam.common.database.index.model.IndexationResult;
import fr.gouv.vitam.common.database.parameter.IndexParameters;
import fr.gouv.vitam.common.database.server.elasticsearch.IndexationHelper;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.server.LogbookDbAccess;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookElasticsearchAccessFactory;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.logbook.common.server.exception.LogbookAlreadyExistsException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookDatabaseException;
import fr.gouv.vitam.logbook.common.server.exception.LogbookNotFoundException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({LogbookElasticsearchAccessFactory.class, IndexationHelper.class, WorkspaceClientFactory.class})
public class LogbookOperationsImplTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private LogbookOperationsImpl logbookOperationsImpl;
    private LogbookDbAccess mongoDbAccess;
    private LogbookOperationParameters logbookParameters;
    private WorkspaceClient workspaceClient;

    @Before
    public void setUp() throws Exception {
        mongoDbAccess = mock(LogbookDbAccess.class);
        logbookParameters = LogbookParametersFactory.newLogbookOperationParameters();
        logbookParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess, GUIDFactory
            .newOperationLogbookGUID(0).getId());

        // Mock workspace and mongoDbAccess to avoid error on operation backup
        final WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        workspaceClient = mock(WorkspaceClient.class);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        doNothing().when(workspaceClient).createContainer(anyString());
        doNothing().when(workspaceClient).putObject(anyString(), anyString(), anyObject());
        doNothing().when(workspaceClient).deleteObject(anyString(), anyString());
    }

    @Test(expected = LogbookDatabaseException.class)
    public void givenCreateOperationWhenErrorInMongoThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookDatabaseException.class).when(mongoDbAccess).createLogbookOperation(anyObject());

        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.create(logbookParameters);
    }

    @Test(expected = LogbookDatabaseException.class)
    public void givenUpdateOperationWhenErrorInMongoThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookDatabaseException.class).when(mongoDbAccess).updateLogbookOperation(anyObject());

        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.update(logbookParameters);
    }

    @Test(expected = LogbookNotFoundException.class)
    public void givenSelectOperationWhenErrorInMongoThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookDatabaseException.class).when(mongoDbAccess)
            .getLogbookOperations(JsonHandler.createObjectNode(), true);
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.select(null);
    }

    @Test(expected = LogbookAlreadyExistsException.class)
    public void givenCreateOperationWhenOperationAlreadyExistsThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookAlreadyExistsException.class).when(mongoDbAccess).createLogbookOperation(anyObject());

        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.create(logbookParameters);
    }

    @Test(expected = LogbookNotFoundException.class)
    public void givenUpdateOperationWhenOperationNotExistsThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookNotFoundException.class).when(mongoDbAccess).updateLogbookOperation(anyObject());

        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.update(logbookParameters);
    }

    @Test(expected = LogbookNotFoundException.class)
    public void givenSelectOperationWhenOperationNotExistsThenThrowLogbookException() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doThrow(LogbookNotFoundException.class).when(mongoDbAccess)
            .getLogbookOperations(JsonHandler.createObjectNode(), true);
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.select(null);
    }

    @Test
    public void getByIdTest() throws Exception {
        Mockito.reset(mongoDbAccess);
        GUID guid = GUIDFactory.newEventGUID(0);
        ObjectNode data = JsonHandler.createObjectNode().put("_id", guid.getId());
        Mockito.doReturn(new LogbookOperation(data)).when(mongoDbAccess).getLogbookOperation(guid.getId());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        LogbookOperation lo = logbookOperationsImpl.getById(guid.getId());
        assertNotNull(lo);
    }

    @RunWithCustomExecutor
    @Test
    public void createBulkLogbookOperationTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(0);
        Mockito.reset(mongoDbAccess);
        when(mongoDbAccess.getLogbookOperation(anyString())).thenReturn(new LogbookOperation(logbookParameters));
        LogbookOperationParameters[] param = new LogbookOperationParameters[2];
        param[0] = LogbookParametersFactory.newLogbookOperationParameters();
        param[1] = LogbookParametersFactory.newLogbookOperationParameters();
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.createBulkLogbookOperation(param);
    }

    @RunWithCustomExecutor
    @Test
    public void updateBulkLogbookOperationTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(0);
        Mockito.reset(mongoDbAccess);
        when(mongoDbAccess.getLogbookOperation(anyString())).thenReturn(new LogbookOperation(logbookParameters));
        LogbookOperationParameters[] param = new LogbookOperationParameters[2];
        param[0] = LogbookParametersFactory.newLogbookOperationParameters();
        param[1] = LogbookParametersFactory.newLogbookOperationParameters();
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.updateBulkLogbookOperation(param);
    }

    @Test
    public void selectOperationsByLastPersistenceDateIntervalTest() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doReturn(createFakeMongoCursor()).when(mongoDbAccess).getLogbookOperations(anyObject(), anyBoolean());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        MongoCursor cursor = logbookOperationsImpl.selectOperationsByLastPersistenceDateInterval(LocalDateTime.now(),
            LocalDateUtil.now());
        assertNotNull(cursor);
        assertTrue(cursor.hasNext());
    }


    @Test
    public void selectOperationsTest() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doReturn(createFakeMongoCursor()).when(mongoDbAccess).getLogbookOperations(anyObject(), anyBoolean());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        Select select = new Select();
        RequestResponse<LogbookOperation> response = logbookOperationsImpl.selectOperations(select.getFinalSelect());
        assertNotNull(response);
        assertEquals(1, response.toJsonNode().get("$hits").get("total").asLong());
    }


    @Test
    public void findFirstTraceabilityOperationOKAfterDateTest() throws Exception {
        Mockito.reset(mongoDbAccess);
        Mockito.doReturn(createFakeMongoCursor()).when(mongoDbAccess).getLogbookOperations(anyObject(), anyBoolean());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        LogbookOperation lo = logbookOperationsImpl.findFirstTraceabilityOperationOKAfterDate(LocalDateTime.now());
        assertNotNull(lo);
    }

    @Test
    public void reindexCollectionUnknownTest() {
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        IndexParameters parameters = new IndexParameters();
        parameters.setCollectionName("fakeName");
        List<Integer> tenants = new ArrayList<>();
        tenants.add(0);
        parameters.setTenants(tenants);
        IndexationResult result = logbookOperationsImpl.reindex(parameters);
        assertNull(result.getIndexOK());
        assertNotNull(result.getIndexKO());
        assertEquals(1, result.getIndexKO().size());
        assertEquals("fakeName_0_*", result.getIndexKO().get(0).getIndexName());
        assertEquals("Try to reindex a non operation logbook collection 'fakeName' with operation logbook module",
            result
                .getIndexKO().get(0).getMessage());
        assertEquals((Integer) 0, result.getIndexKO().get(0).getTenant());
    }

    @Test
    public void reindexCollectionUnauthorizedTest() {
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        IndexParameters parameters = new IndexParameters();
        parameters.setCollectionName("lifecycle_unit");
        List<Integer> tenants = new ArrayList<>();
        tenants.add(0);
        parameters.setTenants(tenants);
        IndexationResult result = logbookOperationsImpl.reindex(parameters);
        assertNull(result.getIndexOK());
        assertNotNull(result.getIndexKO());
        assertEquals(1, result.getIndexKO().size());
        assertEquals("lifecycle_unit_0_*", result.getIndexKO().get(0).getIndexName());
        assertEquals("Try to reindex a non operation logbook collection 'lifecycle_unit' with operation logbook module",
            result
                .getIndexKO().get(0).getMessage());
        assertEquals((Integer) 0, result.getIndexKO().get(0).getTenant());
    }

    @Test
    public void reindexIOExceptionTest() throws Exception {
        PowerMockito.mockStatic(IndexationHelper.class);
        when(IndexationHelper.reindex(anyObject(), anyObject(), anyObject(), anyObject()))
            .thenThrow(new IOException());
        when(IndexationHelper.getFullKOResult(anyObject(), anyString()))
            .thenCallRealMethod();
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        IndexParameters parameters = new IndexParameters();
        parameters.setCollectionName("operation");
        List<Integer> tenants = new ArrayList<>();
        tenants.add(0);
        parameters.setTenants(tenants);
        IndexationResult result = logbookOperationsImpl.reindex(parameters);
        assertNull(result.getIndexOK());
        assertNotNull(result.getIndexKO());
        assertEquals(1, result.getIndexKO().size());
        assertEquals("operation_0_*", result.getIndexKO().get(0).getIndexName());
        assertEquals((Integer) 0, result.getIndexKO().get(0).getTenant());
    }

    @Test(expected = DatabaseException.class)
    public void switchIndexKOTest() throws Exception {
        PowerMockito.spy(IndexationHelper.class);
        PowerMockito.doThrow(new DatabaseException("erreur")).when(IndexationHelper.class,
            "switchIndex", anyString(), anyString(), anyObject());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.switchIndex("alias", "index_name");
    }

    @Test
    public void switchIndexOKTest() throws Exception {
        PowerMockito.spy(IndexationHelper.class);
        PowerMockito.doNothing().when(IndexationHelper.class, "switchIndex", anyString(), anyString(), anyObject());
        logbookOperationsImpl = new LogbookOperationsImpl(mongoDbAccess);
        logbookOperationsImpl.switchIndex("alias", "index_name");
    }

    private VitamMongoCursor createFakeMongoCursor() {
        return new VitamMongoCursor(new MongoCursor() {
            boolean f = true;

            @Override
            public void close() {

            }

            @Override
            public boolean hasNext() {
                return f;
            }

            @Override
            public Object next() {
                if (f) {
                    GUID guid = GUIDFactory.newEventGUID(0);
                    ObjectNode data = JsonHandler.createObjectNode().put("_id", guid.getId());
                    f = false;
                    return new LogbookOperation(data);
                }
                return null;
            }

            @Override
            public Object tryNext() {
                return null;
            }

            @Override
            public ServerCursor getServerCursor() {
                return null;
            }

            @Override
            public ServerAddress getServerAddress() {
                return null;
            }
        }, 1, null);
    }

}
