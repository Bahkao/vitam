package fr.gouv.vitam.worker.core.handler;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import fr.gouv.vitam.common.client.ClientMockResultHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.ActivationStatus;
import fr.gouv.vitam.common.model.administration.IngestContractModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialNotFoundException;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({AdminManagementClientFactory.class})
public class CheckIngestContractActionHandlerTest {
    CheckIngestContractActionHandler handler;
    private static final String HANDLER_ID = "CHECK_CONTRACT_INGEST";
    private AdminManagementClient adminClient;
    private AdminManagementClientFactory adminManagementClientFactory;
    private GUID guid;

    private static final Integer TENANT_ID = 0;
    private static final String FAKE_URL = "http://localhost:8083";
    private static final String CONTRACT_NAME = "ArchivalAgreement0";
    private static final String CONTRACT_IDENTIFIER = "ArchivalAgreement0";
    private static final String COMMENT = "comment";
    private static final String ORIGINATING_AGENCY_IDENTIFIER = "OriginatingAgencyIdentifier";
    private static final String SUBMISSION_AGENCY_IDENTIFIER = "SubmissionAgencyIdentifier";
    private static final String ARCHIVAL_AGREEMENT = "ArchivalAgreement";
    private static final String MESSAGE_IDENTIFIER = "MessageIdentifier";

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private HandlerIO handlerIO = mock(HandlerIO.class);

    @Before
    public void setUp() throws ProcessingException, FileNotFoundException {
        PowerMockito.mockStatic(AdminManagementClientFactory.class);
        adminClient = mock(AdminManagementClient.class);
        guid = GUIDFactory.newGUID();
        adminManagementClientFactory = mock(AdminManagementClientFactory.class);
        PowerMockito.when(AdminManagementClientFactory.getInstance()).thenReturn(adminManagementClientFactory);
        PowerMockito.when(AdminManagementClientFactory.getInstance().getClient()).thenReturn(adminClient);
    }

    @Test
    @RunWithCustomExecutor
    public void givenSipWithValidContractReferenceFoundThenReturnResponseOK()
        throws InvalidParseOperationException,
        AdminManagementClientServerException,
        ReferentialNotFoundException {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        VitamThreadUtils.getVitamSession().setContextId("FakeContext");

        when(adminClient.findIngestContractsByID(anyObject())).thenReturn(createIngestContract(ActivationStatus.ACTIVE));
        when(adminClient.findContextById(anyObject())).thenReturn(ClientMockResultHelper.getContexts(200));
        when(handlerIO.getInput(0)).thenReturn(getMandatoryValueMapInstance(true));

        handler = new CheckIngestContractActionHandler();
        assertEquals(CheckIngestContractActionHandler.getId(), HANDLER_ID);

        ItemStatus response = handler.execute(getWorkerParametersInstance(), handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        reset(adminClient);
        when(adminClient.findIngestContractsByID(anyObject()))
            .thenReturn(createIngestContract(ActivationStatus.INACTIVE));
        response = handler.execute(getWorkerParametersInstance(), handlerIO);
        assertEquals(response.getGlobalOutcomeDetailSubcode(), "CONTRACT_INACTIVE");
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
    }

    @Test
    @RunWithCustomExecutor
    public void givenSipWithoutContractThenReturnResponseKO()
        throws XMLStreamException, IOException, ProcessingException, InvalidParseOperationException,
        InvalidCreateOperationException, AdminManagementClientServerException,
        ContentAddressableStorageNotFoundException, ContentAddressableStorageServerException,
        ReferentialNotFoundException {

        when(handlerIO.getInput(0)).thenReturn(getMandatoryValueMapInstance(false));

        handler = new CheckIngestContractActionHandler();
        assertEquals(CheckIngestContractActionHandler.getId(), HANDLER_ID);

        ItemStatus response = handler.execute(getWorkerParametersInstance(), handlerIO);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
        Assertions.assertThat(response.getEvDetailData()).contains("Error ingest contract not found in the Manifest");
    }

    /**
     * Create an instance of IngestContract.
     *
     * @param status
     * @return the created instance.
     * @throws InvalidParseOperationException
     */
    private static RequestResponse<IngestContractModel> createIngestContract(ActivationStatus status)
        throws InvalidParseOperationException {
        IngestContractModel contract = new IngestContractModel();
        contract.setIdentifier(CONTRACT_IDENTIFIER);
        contract.setStatus(status);
        return ClientMockResultHelper.createReponse(contract);
    }

    /**
     * Create an instance of mandatoryValueMap with/without IngestContract.
     *
     * @param withIngestContract with or withour IngestConstract
     * @return the created instance.
     */
    private Map<String, Object> getMandatoryValueMapInstance(boolean withIngestContract) {
        final Map<String, Object> mandatoryValueMap = new HashMap<>();
        mandatoryValueMap.put(COMMENT, "Dossier d'agent Nicolas Perrin");
        mandatoryValueMap.put(SUBMISSION_AGENCY_IDENTIFIER, "Vitam");
        mandatoryValueMap.put(ORIGINATING_AGENCY_IDENTIFIER, ORIGINATING_AGENCY_IDENTIFIER);
        mandatoryValueMap.put(MESSAGE_IDENTIFIER, "Dossier d'agent Nicolas Perrin");
        if (withIngestContract) {
            mandatoryValueMap.put(ARCHIVAL_AGREEMENT, CONTRACT_NAME);
        }

        return mandatoryValueMap;
    }

    /**
     * Create an instance of WorkerParameters with fake data.
     *
     * @return the created instance.
     */
    private WorkerParameters getWorkerParametersInstance() {
        final WorkerParameters params = WorkerParametersFactory.newWorkerParameters()
            .setUrlWorkspace(FAKE_URL)
            .setUrlMetadata(FAKE_URL)
            .setObjectNameList(Lists.newArrayList("objectName.json"))
            .setObjectName("objectName.json")
            .setCurrentStep("STP_INGEST_CONTROL_SIP")
            .setContainerName(guid.getId());

        return params;
    }

}
