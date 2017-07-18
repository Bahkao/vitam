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
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.tsp.TimeStampResponse;
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
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.model.IOParameter;
import fr.gouv.vitam.processing.common.model.ProcessingUri;
import fr.gouv.vitam.processing.common.model.UriPrefix;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class})
public class VerifyTimeStampActionHandlerTest {
    VerifyTimeStampActionHandler verifyTimeStampActionHandler;
    private static final String DETAIL_EVENT_TRACEABILITY = "VerifyTimeStamp/EVENT_DETAIL_DATA.json";

    private static final String TOKEN = "VerifyTimeStamp/token.tsp";

    private static final String TOKEN_FAKE = "VerifyTimeStamp/token_fake.tsp";

    private static final String FAKE_URL = "http://localhost:8080";
    private HandlerIOImpl action;
    private GUID guid;
    private WorkerParameters params;
    private static final Integer TENANT_ID = 0;
    private WorkspaceClient workspaceClient;
    private WorkspaceClientFactory workspaceClientFactory;
    private List<IOParameter> in;

    private static final String HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP = "COMPARE_TOKEN_TIMESTAMP";
    private static final String HANDLER_SUB_ACTION_VALIDATE_TOKEN_TIMESTAMP = "VALIDATE_TOKEN_TIMESTAMP";

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Before
    public void setUp() throws Exception {
        guid = GUIDFactory.newGUID();
        params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL).setUrlMetadata(FAKE_URL)
                .setObjectName("objectName.json").setCurrentStep("currentStep").setContainerName(guid.getId());
        workspaceClient = mock(WorkspaceClient.class);
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        PowerMockito.mockStatic(StorageClientFactory.class);
        workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        action = new HandlerIOImpl(guid.getId(), "workerId");
    }

    @After
    public void end() {
        action.partialClose();
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampThenOK()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();
        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp.conf");
        
        final InputStream tokenFile =
            PropertiesUtils.getResourceAsStream(TOKEN);


        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp")))
                .thenReturn(Response.status(Status.OK).entity(tokenFile).build());

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.OK, response.getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP).getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_VALIDATE_TOKEN_TIMESTAMP).getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampWithErrorWorkspaceThenFATAL()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();
        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp.conf");

        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp"))).thenThrow(new ContentAddressableStorageNotFoundException("Token is not existing"));

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.FATAL, response.getGlobalStatus());
    }


    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampWithFakeKeystoreThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();

        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp-fake.conf");
        final InputStream tokenFile =
            PropertiesUtils.getResourceAsStream(TOKEN);


        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp")))
                .thenReturn(Response.status(Status.OK).entity(tokenFile).build());

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP).getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_VALIDATE_TOKEN_TIMESTAMP).getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampWithUnexistingKeystoreThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();

        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp-fake-unexisting.conf");
        final InputStream tokenFile =
            PropertiesUtils.getResourceAsStream(TOKEN);


        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp")))
                .thenReturn(Response.status(Status.OK).entity(tokenFile).build());

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP).getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_VALIDATE_TOKEN_TIMESTAMP).getGlobalStatus());
    }


    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampWithMultipleKeysInKeystoreThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();

        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp-mulitple-keys.conf");
        final InputStream tokenFile =
            PropertiesUtils.getResourceAsStream(TOKEN);


        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp")))
                .thenReturn(Response.status(Status.OK).entity(tokenFile).build());

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.OK, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP).getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_VALIDATE_TOKEN_TIMESTAMP).getGlobalStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void testVerifyTimeStampCorruptThenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "TraceabilityOperationDetails/EVENT_DETAIL_DATA.json")));
        action.addOutIOParameters(in);
        action.addOuputResult(0, PropertiesUtils.getResourceFile(DETAIL_EVENT_TRACEABILITY));
        action.reset();
        action.addInIOParameters(in);

        verifyTimeStampActionHandler = new VerifyTimeStampActionHandler();
        verifyTimeStampActionHandler.setConfPathForTest("verify-timestamp.conf");
        final InputStream tokenFile =
            PropertiesUtils.getResourceAsStream(TOKEN_FAKE);


        when(workspaceClient.getObject(anyObject(), eq(SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            "token.tsp")))
                .thenReturn(Response.status(Status.OK).entity(tokenFile).build());

        final ItemStatus response = verifyTimeStampActionHandler.execute(params, action);
        assertEquals(StatusCode.KO, response.getGlobalStatus());
        assertEquals(StatusCode.KO, response.getItemsStatus().get(verifyTimeStampActionHandler.getId())
            .getItemsStatus().get(HANDLER_SUB_ACTION_COMPARE_TOKEN_TIMESTAMP).getGlobalStatus());
    }

}
