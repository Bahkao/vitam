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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.client.ClientMockResultHelper;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.utils.ExtractUriResponse;
import fr.gouv.vitam.worker.common.utils.SedaUtils;
import fr.gouv.vitam.worker.common.utils.SedaUtilsFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, MetaDataClientFactory.class, SedaUtilsFactory.class, AdminManagementClientFactory.class})
public class CheckDataObjectPackageActionHandlerTest {
    CheckDataObjectPackageActionHandler handler = new CheckDataObjectPackageActionHandler();
    private static final String SIP_ARBORESCENCE = "SIP_Arborescence.xml";
    private static final String STORAGE_INFO_JSON = "storageInfo.json";
    private AdminManagementClient adminManagementClient;
    private WorkspaceClient workspaceClient;
    private MetaDataClient metadataClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private AdminManagementClientFactory adminManagementClientFactory;
    private MetaDataClientFactory metadataClientFactory;
    private SedaUtils sedaUtils;
    private ExtractUriResponse extractUriResponseOK;
    private HandlerIOImpl action;
    private List<IOParameter> out;
    private List<IOParameter> in;
    private static final Integer TENANT_ID = 0;
    private final List<URI> uriListWorkspaceOK = new ArrayList<>();
    private final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace("http://localhost:8083")
                    .setUrlMetadata("http://localhost:8083")
                    .setObjectNameList(Lists.newArrayList("objectName.json"))
                    .setObjectName("objectName.json").setCurrentStep("currentStep")
                    .setLogbookTypeProcess(LogbookTypeProcess.INGEST)
                    .setContainerName("ExtractSedaActionHandlerTest");

    @Rule
    public RunWithCustomExecutorRule runInThread =
            new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws URISyntaxException, IOException {

        File tempFolder = temporaryFolder.newFolder();
        System.setProperty("vitam.tmp.folder", tempFolder.getAbsolutePath());
        SystemPropertyUtil.refresh();

        LogbookOperationsClientFactory.changeMode(null);
        LogbookLifeCyclesClientFactory.changeMode(null);

        mockStatic(WorkspaceClientFactory.class);
        workspaceClient = mock(WorkspaceClient.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(WorkspaceClientFactory.getInstance().getClient()).thenReturn(workspaceClient);

        mockStatic(AdminManagementClientFactory.class);
        adminManagementClient = mock(AdminManagementClient.class);

        adminManagementClientFactory = mock(AdminManagementClientFactory.class);
        PowerMockito.when(AdminManagementClientFactory.getInstance()).thenReturn(adminManagementClientFactory);
        PowerMockito.when(adminManagementClientFactory.getClient()).thenReturn(adminManagementClient);

        mockStatic(MetaDataClientFactory.class);
        metadataClient = mock(MetaDataClient.class);
        metadataClientFactory = mock(MetaDataClientFactory.class);
        PowerMockito.when(MetaDataClientFactory.getInstance()).thenReturn(metadataClientFactory);
        PowerMockito.when(MetaDataClientFactory.getInstance().getClient()).thenReturn(metadataClient);

        mockStatic(SedaUtilsFactory.class);
        sedaUtils = mock(SedaUtils.class);

        action = new HandlerIOImpl("ExtractSedaActionHandlerTest", "workerId");
        out = new ArrayList<>();
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "UnitsLevel/ingestLevelStack.json")));
        out.add(
                new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/DATA_OBJECT_TO_OBJECT_GROUP_ID_MAP.json")));
        out.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/DATA_OBJECT_ID_TO_GUID_MAP.json")));
        out.add(
                new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/OBJECT_GROUP_ID_TO_GUID_MAP.json")));
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.MEMORY, "MapsMemory/OG_TO_ARCHIVE_ID_MAP.json")));
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/DATA_OBJECT_ID_TO_DATA_OBJECT_DETAIL_MAP.json")));
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "Maps/ARCHIVE_ID_TO_GUID_MAP.json")));
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "ATR/globalSEDAParameters.json")));
        out.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.MEMORY, "MapsMemory/OBJECT_GROUP_ID_TO_GUID_MAP.json")));
        in = new ArrayList<>();
        in.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.VALUE, "true")));
        in.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.VALUE, "INGEST")));
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.WORKSPACE, "StorageInfo/storageInfo.json")));
        out.add(new IOParameter().setUri(new ProcessingUri(UriPrefix.WORKSPACE, "UpdateObjectGroup/existing_object_group.json")));

        uriListWorkspaceOK.add(new URI("content/file1.pdf"));
        uriListWorkspaceOK.add(new URI("content/file2.pdf"));
        uriListWorkspaceOK.add(new URI("manifest.xml"));
        extractUriResponseOK = new ExtractUriResponse();
        extractUriResponseOK.setUriListManifest(uriListWorkspaceOK);
    }

    @After
    public void clean() {
        action.partialClose();
    }

    @Test
    @RunWithCustomExecutor
    public void testHandlerWorking() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        assertNotNull(CheckDataObjectPackageActionHandler.getId());
        final InputStream seda_arborescence =
                PropertiesUtils.getResourceAsStream(SIP_ARBORESCENCE);
        final InputStream storageInfo =
            PropertiesUtils.getResourceAsStream(STORAGE_INFO_JSON);
        PowerMockito.when(SedaUtilsFactory.create(anyObject())).thenReturn(sedaUtils);

        when(sedaUtils.getAllDigitalObjectUriFromManifest()).thenReturn(extractUriResponseOK);
        when(workspaceClient.getObject(anyObject(), eq("SIP/manifest.xml")))
                .thenReturn(Response.status(Status.OK).entity(seda_arborescence).build());
        when(workspaceClient.getListUriDigitalObjectFromFolder(anyObject(), anyObject()))
                .thenReturn(new RequestResponseOK().addResult(uriListWorkspaceOK));
        when(workspaceClient.getObject(anyObject(), eq("StorageInfo/storageInfo.json")))
            .thenReturn(Response.status(Status.OK).entity(storageInfo).build());
        when(adminManagementClient.findIngestContractsByID(Matchers.anyString()))
                .thenReturn(ClientMockResultHelper.getIngestContracts());
        action.addOutIOParameters(out);
        action.addInIOParameters(in);
        final ItemStatus response = handler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());

        in = new ArrayList<>();
        in.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.VALUE, "false")));
        in.add(new IOParameter()
                .setUri(new ProcessingUri(UriPrefix.VALUE, "INGEST")));
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.WORKSPACE, "StorageInfo/storageInfo.json")));
        action.reset();
        action.addOutIOParameters(out);
        action.addInIOParameters(in);

        Map<String, Map<String, String>> versionMap = new HashMap<>();
        final Map<String, String> invalidVersionMap = new HashMap<>();
        final Map<String, String> validVersionMap = new HashMap<>();
        versionMap.put(SedaUtils.INVALID_DATAOBJECT_VERSION,invalidVersionMap);
        versionMap.put(SedaUtils.VALID_DATAOBJECT_VERSION,validVersionMap);

        Mockito.doReturn(versionMap).when(sedaUtils).checkSupportedDataObjectVersion(anyObject());
        final ItemStatus response2 = handler.execute(params, action);
        assertEquals(StatusCode.KO, response2.getGlobalStatus());
    }

}
