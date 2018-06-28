package fr.gouv.vitam.worker.core.plugin.reclassification;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.plugin.reclassification.dsl.ParsedReclassificationDslRequest;
import fr.gouv.vitam.worker.core.plugin.reclassification.dsl.ParsedReclassificationDslRequestEntry;
import fr.gouv.vitam.worker.core.plugin.reclassification.dsl.ReclassificationRequestDslParser;
import fr.gouv.vitam.worker.core.plugin.reclassification.model.ReclassificationEventDetails;
import fr.gouv.vitam.worker.core.plugin.reclassification.model.ReclassificationOrders;
import fr.gouv.vitam.worker.core.plugin.reclassification.utils.UnitGraphInfoLoader;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.ACCESS_CONTRACT_NOT_FOUND_OR_NOT_ACTIVE;
import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.ACCESS_DENIED_OR_MISSING_UNITS;
import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.CANNOT_ATTACH_DETACH_SAME_PARENT_UNITS;
import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.COULD_NOT_PARSE_RECLASSIFICATION_REQUEST;
import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.NO_ACCESS_CONTRACT_PROVIDED;
import static fr.gouv.vitam.worker.core.plugin.reclassification.ReclassificationPreparationLoadRequestHandler.NO_UNITS_TO_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWithCustomExecutor
public class ReclassificationPreparationLoadRequestHandlerTest {

    private final static int MAX_BULK_THRESHOLD = 1000;

    @ClassRule
    public static RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private AdminManagementClientFactory adminManagementClientFactory;
    @Mock
    private AdminManagementClient adminManagementClient;

    @Mock
    private MetaDataClientFactory metaDataClientFactory;
    @Mock
    private MetaDataClient metaDataClient;

    @Mock
    private ReclassificationRequestDslParser reclassificationRequestDslParser;

    @Mock
    private UnitGraphInfoLoader unitGraphInfoLoader;

    @Mock
    private HandlerIO handlerIO;

    private WorkerParameters parameters;

    private ReclassificationPreparationLoadRequestHandler reclassificationPreparationLoadRequestHandler;

    @Before
    public void init() throws Exception {
        doReturn(adminManagementClient).when(adminManagementClientFactory).getClient();
        doReturn(metaDataClient).when(metaDataClientFactory).getClient();

        int tenant = 0;
        VitamThreadUtils.getVitamSession().setTenantId(tenant);
        String operationId = GUIDFactory.newRequestIdGUID(tenant).toString();
        VitamThreadUtils.getVitamSession().setRequestId(operationId);

        String objectId = GUIDFactory.newGUID().toString();
        parameters = WorkerParametersFactory.newWorkerParameters().setWorkerGUID(GUIDFactory
            .newGUID()).setContainerName(operationId)
            .setObjectNameList(Lists.newArrayList(objectId))
            .setObjectName(objectId).setCurrentStep("StepName");

        reclassificationPreparationLoadRequestHandler =
            new ReclassificationPreparationLoadRequestHandler(adminManagementClientFactory, metaDataClientFactory,
                unitGraphInfoLoader, reclassificationRequestDslParser, MAX_BULK_THRESHOLD, 1000, 1000);
    }

    @Test
    public void execute_GivenExceptionWhenLoadingJsonRequestFromWorkspaceThenExpectFatal() throws Exception {

        doThrow(ProcessingException.class).when(handlerIO).getJsonFromWorkspace("request.json");

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.FATAL);
    }

    @Test
    public void execute_GivenInvalidDslRequestThenExpectKO() throws Exception {

        // Given
        doThrow(InvalidParseOperationException.class)
            .when(reclassificationRequestDslParser).parseReclassificationRequest(any());

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        assertThat(
            JsonHandler.getFromString(itemStatus.getEvDetailData(), ReclassificationEventDetails.class).getError())
            .isEqualTo(COULD_NOT_PARSE_RECLASSIFICATION_REQUEST);
    }

    @Test
    public void execute_GivenRequestWithTooManyQueriesThenExpectKO() throws Exception {

        // Given request with too many entries
        List<ParsedReclassificationDslRequestEntry> entries = new ArrayList<>();
        for (int i = 0; i < MAX_BULK_THRESHOLD + 1; i++) {
            entries.add(mock(ParsedReclassificationDslRequestEntry.class));
        }
        givenDslRequests(entries.toArray(new ParsedReclassificationDslRequestEntry[0]));

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        assertThat(
            JsonHandler.getFromString(itemStatus.getEvDetailData(), ReclassificationEventDetails.class).getError())
            .isEqualTo("Too many reclassification requests (count= 1001, max= 1000)");
    }

    @Test
    public void execute_GivenNoAccessContractProvidedThenKO() throws Exception {

        // Given
        givenDslRequests(mock(ParsedReclassificationDslRequestEntry.class));

        VitamThreadUtils.getVitamSession().setContractId(null);

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        assertThat(
            JsonHandler.getFromString(itemStatus.getEvDetailData(), ReclassificationEventDetails.class).getError())
            .isEqualTo(NO_ACCESS_CONTRACT_PROVIDED);
    }

    @Test
    public void execute_GivenAccessContractNotFoundThenKO() throws Exception {

        // Given
        givenDslRequests(mock(ParsedReclassificationDslRequestEntry.class));

        String accessContractId = "ContractId";
        VitamThreadUtils.getVitamSession().setContractId(accessContractId);
        RequestResponseOK<Object> emptyResponse = new RequestResponseOK<>();
        doReturn(emptyResponse).when(adminManagementClient).findAccessContracts(any());

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        assertThat(
            JsonHandler.getFromString(itemStatus.getEvDetailData(), ReclassificationEventDetails.class).getError())
            .isEqualTo(ACCESS_CONTRACT_NOT_FOUND_OR_NOT_ACTIVE);
    }

    @Test
    public void execute_GivenDocumentIdsNotFoundViaAccessContractThenExpectKO() throws Exception {

        // Given
        AccessContractModel accessContract = givenExistingAccessContract();

        ParsedReclassificationDslRequestEntry entry1 = new ParsedReclassificationDslRequestEntry(
            mock(SelectMultiQuery.class),
            new HashSet<>(Arrays.asList("id1", "id2")),
            new HashSet<>(Arrays.asList("id3"))
        );
        ParsedReclassificationDslRequestEntry entry2 = new ParsedReclassificationDslRequestEntry(
            mock(SelectMultiQuery.class),
            new HashSet<>(Arrays.asList("id4")),
            new HashSet<>(Collections.emptyList())
        );
        givenDslRequests(entry1, entry2);
        givenAccessibleParentUnitIds(accessContract, "id2");

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        ReclassificationEventDetails eventDetails = JsonHandler.getFromString(
            itemStatus.getEvDetailData(), ReclassificationEventDetails.class);
        assertThat(eventDetails.getError()).isEqualTo(ACCESS_DENIED_OR_MISSING_UNITS);
        assertThat(eventDetails.getMissingOrForbiddenUnits()).containsExactlyInAnyOrder("id1", "id3", "id4");
    }

    @Test
    public void execute_EmptyChildUnitIdsViaAccessContractThenExpectKO() throws Exception {

        // Given
        AccessContractModel accessContract = givenExistingAccessContract();

        SelectMultiQuery fakeSelectMultiQuery = mock(SelectMultiQuery.class);
        givenDslRequests(new ParsedReclassificationDslRequestEntry(
            fakeSelectMultiQuery,
            new HashSet<>(Arrays.asList("id1")),
            new HashSet<>(Arrays.asList("id2"))
        ));
        givenAccessibleParentUnitIds(accessContract, "id1", "id2");
        doReturn(Collections.emptySet()).when(unitGraphInfoLoader)
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, fakeSelectMultiQuery, accessContract);

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        ReclassificationEventDetails eventDetails = JsonHandler.getFromString(
            itemStatus.getEvDetailData(), ReclassificationEventDetails.class);
        assertThat(eventDetails.getError()).isEqualTo(NO_UNITS_TO_UPDATE);
    }

    @Test
    public void execute_GivenAttachmentAndDetachmentOfSameParentThenExpectKO() throws Exception {

        // Given
        AccessContractModel accessContract = givenExistingAccessContract();

        SelectMultiQuery fakeSelectMultiQuery1 = mock(SelectMultiQuery.class);
        SelectMultiQuery fakeSelectMultiQuery2 = mock(SelectMultiQuery.class);
        ParsedReclassificationDslRequestEntry entry1 = new ParsedReclassificationDslRequestEntry(
            fakeSelectMultiQuery1,
            new HashSet<>(Arrays.asList("id1")),
            new HashSet<>(Arrays.asList("id3"))
        );
        ParsedReclassificationDslRequestEntry entry2 = new ParsedReclassificationDslRequestEntry(
            fakeSelectMultiQuery2,
            new HashSet<>(Arrays.asList("id2")),
            new HashSet<>(Arrays.asList("id1"))
        );
        givenDslRequests(entry1, entry2);
        givenAccessibleParentUnitIds(accessContract, "id1", "id2", "id3");

        doReturn(new HashSet<>(Arrays.asList("id4"))).when(unitGraphInfoLoader)
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, fakeSelectMultiQuery1, accessContract);
        doReturn(new HashSet<>(Arrays.asList("id4", "id5"))).when(unitGraphInfoLoader)
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, fakeSelectMultiQuery2, accessContract);

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
        ReclassificationEventDetails eventDetails = JsonHandler.getFromString(
            itemStatus.getEvDetailData(), ReclassificationEventDetails.class);
        assertThat(eventDetails.getError()).isEqualTo(CANNOT_ATTACH_DETACH_SAME_PARENT_UNITS);
    }

    @Test
    public void execute_testOK() throws Exception {

        // Given
        AccessContractModel accessContract = givenExistingAccessContract();

        SelectMultiQuery fakeSelectMultiQuery1 = mock(SelectMultiQuery.class);
        SelectMultiQuery fakeSelectMultiQuery2 = mock(SelectMultiQuery.class);
        ParsedReclassificationDslRequestEntry entry1 = new ParsedReclassificationDslRequestEntry(
            fakeSelectMultiQuery1,
            new HashSet<>(Arrays.asList("id1", "id2")),
            new HashSet<>(Arrays.asList("id3"))
        );
        ParsedReclassificationDslRequestEntry entry2 = new ParsedReclassificationDslRequestEntry(
            fakeSelectMultiQuery2,
            new HashSet<>(Arrays.asList("id4")),
            new HashSet<>(Arrays.asList())
        );
        givenDslRequests(entry1, entry2);
        givenAccessibleParentUnitIds(accessContract, "id1", "id2", "id3", "id4");

        doReturn(new HashSet<>(Arrays.asList("id5"))).when(unitGraphInfoLoader)
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, fakeSelectMultiQuery1, accessContract);
        doReturn(new HashSet<>(Arrays.asList("id2", "id5"))).when(unitGraphInfoLoader)
            .selectUnitsByQueryDslAndAccessContract(metaDataClient, fakeSelectMultiQuery2, accessContract);

        // When
        ItemStatus itemStatus = reclassificationPreparationLoadRequestHandler.execute(parameters, handlerIO);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.OK);
        ArgumentCaptor<ReclassificationOrders> reclassificationOrdersArgumentCaptor
            = ArgumentCaptor.forClass(ReclassificationOrders.class);
        verify(handlerIO).addOutputResult(eq(0), reclassificationOrdersArgumentCaptor.capture(), eq(false));
        ReclassificationOrders reclassificationOrders = reclassificationOrdersArgumentCaptor.getValue();

        assertThat(reclassificationOrders.getChildToParentAttachments().keySet()).hasSize(2);

        assertThat(reclassificationOrders.getChildToParentAttachments().get("id5"))
            .containsExactlyInAnyOrder("id1", "id2", "id4");
        assertThat(reclassificationOrders.getChildToParentAttachments().get("id2")).containsExactlyInAnyOrder("id4");

        assertThat(reclassificationOrders.getChildToParentDetachments().keySet()).hasSize(1);
        assertThat(reclassificationOrders.getChildToParentDetachments().get("id5")).containsExactlyInAnyOrder("id3");

    }

    private void givenAccessibleParentUnitIds(AccessContractModel accessContract,
        String... ids)
        throws InvalidParseOperationException, InvalidCreateOperationException, VitamDBException,
        MetaDataDocumentSizeException, MetaDataExecutionException, MetaDataClientServerException {
        doReturn(new HashSet<>(Arrays.asList(ids)))
            .when(unitGraphInfoLoader).selectUnitsByIdsAndAccessContract(any(), any(), eq(accessContract));
    }

    private AccessContractModel givenExistingAccessContract()
        throws InvalidParseOperationException, AdminManagementClientServerException {
        String accessContractId = "ContractId";
        VitamThreadUtils.getVitamSession().setContractId(accessContractId);
        AccessContractModel accessContractModel = mock(AccessContractModel.class);
        doReturn(new RequestResponseOK<>().addResult(accessContractModel)).when(adminManagementClient)
            .findAccessContracts(any());
        return accessContractModel;
    }

    private void givenDslRequests(ParsedReclassificationDslRequestEntry... entries)
        throws Exception {

        JsonNode fake = mock(JsonNode.class);
        doReturn(fake).when(handlerIO).getJsonFromWorkspace("request.json");
        doReturn(new ParsedReclassificationDslRequest(Arrays.asList(entries))).when(reclassificationRequestDslParser)
            .parseReclassificationRequest(fake);
    }
}
