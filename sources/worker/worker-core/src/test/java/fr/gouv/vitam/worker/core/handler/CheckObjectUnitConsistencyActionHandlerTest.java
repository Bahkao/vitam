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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.IOParameter;
import fr.gouv.vitam.processing.common.model.ProcessingUri;
import fr.gouv.vitam.processing.common.model.UriPrefix;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, LogbookLifeCyclesClientFactory.class})
public class CheckObjectUnitConsistencyActionHandlerTest {

    CheckObjectUnitConsistencyActionHandler handler;
    private static final String HANDLER_ID = "CHECK_CONSISTENCY";

    private static final String OBJECT_GROUP_ID_TO_GUID_MAP = "OBJECT_GROUP_ID_TO_GUID_MAP_obj.json";
    private static final String OG_AU = "OG_TO_ARCHIVE_ID_MAP_obj.json";

    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;

    private LogbookLifeCyclesClient logbookLifeCyclesClient;
    private static final String OBJ = "obj";

    private final WorkerParameters params = WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
        .newGUID()).setContainerName(OBJ).setUrlWorkspace("http://localhost:8083")
        .setUrlMetadata("http://localhost:8083").setObjectName(OBJ)
        .setCurrentStep("TEST").setLogbookTypeProcess(LogbookTypeProcess.INGEST);

    @Before
    public void setUp() {
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        workspaceClient = mock(WorkspaceClient.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(WorkspaceClientFactory.getInstance().getClient()).thenReturn(workspaceClient);
        PowerMockito.mockStatic(LogbookLifeCyclesClientFactory.class);
        final LogbookLifeCyclesClientFactory factory = mock(LogbookLifeCyclesClientFactory.class);
        PowerMockito.when(LogbookLifeCyclesClientFactory.getInstance()).thenReturn(factory);
        logbookLifeCyclesClient = mock(LogbookLifeCyclesClient.class);
        PowerMockito.when(factory.getClient()).thenReturn(logbookLifeCyclesClient);
    }

    @Test
    public void givenObjectUnitConsistencyCheckWhenNotFindBDOWithoutOGAndOGNonReferencedByArchiveUnitThenResponseOK()
        throws ContentAddressableStorageNotFoundException, ContentAddressableStorageServerException,
        InvalidParseOperationException, IOException, ProcessingException {
        final HandlerIOImpl action;
        final List<IOParameter> in;
        action = new HandlerIOImpl(OBJ, "workerId");
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "MEMORY:MapsMemory/OG_TO_ARCHIVE_ID_MAP.json")));
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "MEMORY:MapsMemory/OBJECT_GROUP_ID_TO_GUID_MAP.json")));
        action.reset();
        action.addOutIOParameters(in);
        final Map<String, Object> map = new HashMap<>();
        map.put("id9", "id8");
        action.addOuputResult(0, map);
        action.addOuputResult(1, map);
        action.reset();
        action.addInIOParameters(in);

        handler = new CheckObjectUnitConsistencyActionHandler();

        assertEquals(CheckObjectUnitConsistencyActionHandler.getId(), HANDLER_ID);
        final ItemStatus response = handler.execute(params, action);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
        assertThat(response.getItemsStatus().get(HANDLER_ID).getStatusMeter().get(StatusCode.OK.getStatusLevel()))
            .isEqualTo(1);
        action.close();
    }

    @Test
    public void givenObjectUnitConsistencyCheckWhenFindBDOWithoutOGAndOGNonReferencedByArchiveUnitThenResponseKO()
        throws ContentAddressableStorageNotFoundException, ContentAddressableStorageServerException,
        InvalidParseOperationException, IOException, ProcessingException {
        final HandlerIOImpl action;
        final List<IOParameter> in;
        action = new HandlerIOImpl(OBJ, "workerId");
        in = new ArrayList<>();
        in.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.MEMORY, "file1")));
        in.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.MEMORY, "file2")));
        action.reset();
        action.addOutIOParameters(in);
        action.addOuputResult(0, JsonHandler.getMapFromInputStream(PropertiesUtils.getResourceAsStream(OG_AU)));
        action.addOuputResult(1,
            JsonHandler.getMapFromInputStream(PropertiesUtils.getResourceAsStream(OBJECT_GROUP_ID_TO_GUID_MAP)));
        action.reset();
        action.addInIOParameters(in);

        handler = new CheckObjectUnitConsistencyActionHandler();

        assertEquals(CheckObjectUnitConsistencyActionHandler.getId(), HANDLER_ID);
        final ItemStatus response = handler.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        assertThat(response.getItemsStatus().get(HANDLER_ID).getStatusMeter().get(StatusCode.KO.getStatusLevel()))
            .isEqualTo(1);
        action.close();
    }

    @Test(expected = ProcessingException.class)
    public void givenObjectUnitConsistencyWithEmptyHandlerIOThrowsException()
        throws ContentAddressableStorageNotFoundException, ContentAddressableStorageServerException,
        InvalidParseOperationException, IOException, ProcessingException {

        final HandlerIO action = new HandlerIOImpl("", "");
        handler = new CheckObjectUnitConsistencyActionHandler();
        handler.execute(params, action);
    }

    // @Test(expected = ProcessingException.class)
    // public void givenObjectUnitConsistencyWithOnlytOneInputThrowsException()
    // throws ContentAddressableStorageNotFoundException, ContentAddressableStorageServerException,
    // InvalidParseOperationException, IOException, ProcessingException {
    //
    // HandlerIO action = new HandlerIO("", "");
    // action.addInput(PropertiesUtils.getResourceFile(OG_AU));
    // handler = new CheckObjectUnitConsistencyActionHandler();
    // handler.execute(params, action);
    // }
}
