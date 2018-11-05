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
package fr.gouv.vitam.worker.core.plugin.massprocessing;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.multiple.UpdateMultiQuery;
import fr.gouv.vitam.common.database.parser.request.multiple.UpdateParserMultiple;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

public class CheckDistributionThresholdTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Mock
    private MetaDataClientFactory metaDataClientFactory;

    private static final int TENANT_ID = 0;

    private CheckDistributionThreshold checkDistributionThreshold;

    @Before
    public void setUp() throws Exception {
        VitamConfiguration.setDistributionThreshold(15L);

        checkDistributionThreshold = new CheckDistributionThreshold(metaDataClientFactory);
    }

    @Test
    public void givingUpdateQueryThenReturnSelectQuery() throws Exception {
        JsonNode queryNode = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithoutThreshold.json"));

        UpdateParserMultiple parser = new UpdateParserMultiple();
        parser.parse(queryNode);
        UpdateMultiQuery multiQuery = parser.getRequest();

        // transform update to select query
        SelectMultiQuery selectMultiQuery = checkDistributionThreshold.getSelectCountQueryQuery(multiQuery);

        // check query
        assertThat(selectMultiQuery).isNotNull();
        // check have same queries as update
        assertThat(selectMultiQuery.toString()).isEqualTo(selectMultiQuery.toString());
        // set filter.limit to 1
        assertThat(selectMultiQuery.getFilter().toString()).isEqualTo("{\"$limit\":1}");
    }

    @Test
    public void givingSelectQueryThenReturnSelectQuery() throws Exception {

        JsonNode queryNode = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/select_all_request.json"));

        UpdateParserMultiple parser = new UpdateParserMultiple();
        parser.parse(queryNode);
        UpdateMultiQuery multiQuery = parser.getRequest();

        // transform update to select query
        SelectMultiQuery selectMultiQuery = checkDistributionThreshold.getSelectCountQueryQuery(multiQuery);

        // check query
        assertThat(selectMultiQuery).isNotNull();
        // check have same queries as update
        assertThat(selectMultiQuery.toString()).isEqualTo(selectMultiQuery.toString());
        // set filter.limit to 1
        assertThat(selectMultiQuery.getFilter().toString()).isEqualTo("{\"$limit\":1}");
    }

    @Test
    @RunWithCustomExecutor
    public void whenCheckDistributionDefaultThresholdThenReturnOK() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithoutThreshold.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata5.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.OK);
    }

    @Test
    @RunWithCustomExecutor
    public void whenCheckDistributionOvercomeThresholdThenReturnOK() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithThreshold10.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata5.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.OK);
    }

    @Test
    @RunWithCustomExecutor
    public void whenCheckDistributionThresholdThenReturnWarning() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithThreshold25.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata20.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.WARNING);
    }

    @Test
    @RunWithCustomExecutor
    public void whenCheckDistributionThresholdThenReturnKO() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);

        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithoutThreshold.json"));

        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata20.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
    }


    @Test
    @RunWithCustomExecutor
    public void should_checkUpdateQuery() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);

        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/select_all_request.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata5.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.OK);

    }

    @Test
    @RunWithCustomExecutor
    public void whenCheckDistributionThresholdThenReturnOvercomeKO() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithThreshold10.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willReturn(
            JsonHandler.getFromInputStream(
                getClass().getResourceAsStream("/CheckDistributionThreshold/resultMetadata20.json")));

        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.KO);
    }

    @Test
    @RunWithCustomExecutor
    public void whenMetadataExceptionThenReturnFatal() throws Exception {
        // Given
        HandlerIO handlerIO = mock(HandlerIO.class);
        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        given(metaDataClientFactory.getClient()).willReturn(metaDataClient);
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode queryUnit = JsonHandler.getFromInputStream(
            getClass().getResourceAsStream("/CheckDistributionThreshold/queryWithThreshold10.json"));
        given(handlerIO.getJsonFromWorkspace("query.json")).willReturn(queryUnit);
        given(metaDataClient.selectUnits(any())).willThrow(MetaDataClientServerException.class);
        // When
        ItemStatus itemStatus =
            checkDistributionThreshold.execute(WorkerParametersFactory.newWorkerParameters(), handlerIO);

        // Then
        assertThat(itemStatus).isNotNull();
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(StatusCode.FATAL);
    }
}
