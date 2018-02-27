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
package fr.gouv.vitam.functional.administration.evidence;


import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.query.BooleanQuery;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.LifeCycleStatusCode;
import fr.gouv.vitam.common.model.LifeCycleTraceabilitySecureFileObject;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;

import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbName;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.core.Response;


import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.and;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.gte;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.lte;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EvidenceServiceTest {
    private static final Integer TENANT_ID = 0;
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());
    @Mock MetaDataClientFactory metaDataClientFactory;

    @Mock MetaDataClient metaDataClient;
    @Mock LogbookOperationsClientFactory logbookOperationsClientFactory;
    @Mock LogbookOperationsClient logbookOperationsClient;
    @Mock LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory;
    @Mock LogbookLifeCyclesClient logbookLifeCyclesClient;
    @Mock StorageClientFactory storageClientFactory;
    @Mock StorageClient storageClient;
    private static JsonNode OFFERS_INFO;

    static {
        try {
            OFFERS_INFO =
                JsonHandler.getFromString("{\"offer-fs-1.service.consul\":{\"objectName\":\"aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq.json\",\"type\":\"unit\",\"digest\":\"66ebea803269b6c768fda751718b3a984c8e4d339c38aeedd8de812ab4362f0ab1225606aada1635652bece913e59779d662aa7e843713fa85291b91a5608246\",\"fileSize\":5334,\"fileOwner\":\"Vitam_0\",\"lastAccessDate\":\"2018-02-21T11:18:05.674924Z\",\"lastModifiedDate\":\"2018-02-20T11:14:55.123572Z\"}}");
        } catch (InvalidParseOperationException e) {
        }
    }

    private static String SELECT_LOGBOOK_SECUR_OP =
        "{\"$query\":{\"$and\":[{\"$eq\":{\"evType\":\"LOGBOOK_LC_SECURISATION\"}},{\"$eq\":{\"events.outDetail\":\"LOGBOOK_LC_SECURISATION.OK\"}},{\"$lte\":{\"events.evDetData.StartDate\":\"2018-02-20T11:14:54.872\"}},{\"$gte\":{\"events.evDetData.EndDate\":\"2018-02-20T11:14:54.872\"}}]},\"$filter\":{\"$limit\":1,\"$orderby\":{\"events.evDateTime\":-1}},\"$projection\":{}}";
    private static String RESULT_SELECT_LOGBOOK_SECUR_OP =
        "{\"httpCode\":200,\"$hits\":{\"total\":1,\"offset\":0,\"limit\":1,\"size\":1},\"$results\":[{\"evId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evParentId\":null,\"evType\":\"LOGBOOK_LC_SECURISATION\",\"evDateTime\":\"2018-02-20T11:15:05.010\",\"evDetData\":\"{\\n  \\\"LogType\\\" : \\\"LIFECYCLE\\\",\\n  \\\"StartDate\\\" : \\\"2018-02-20T08:29:37.835\\\",\\n  \\\"EndDate\\\" : \\\"2018-02-20T11:14:54.872\\\",\\n  \\\"Hash\\\" : \\\"1uEHtQeA3eXIduiBV9wt5qCZD3VmuiTD68mnMMQp3VWF9QUl3ME8aFv/GVXvVY9PMU73xu6Tjn1eHqs4FOHfDQ==\\\",\\n  \\\"TimeStampToken\\\" : \\\"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\\\",\\n  \\\"PreviousLogbookTraceabilityDate\\\" : \\\"2018-02-20T08:16:26.088\\\",\\n  \\\"MinusOneMonthLogbookTraceabilityDate\\\" : \\\"1970-01-01T00:00:00\\\",\\n  \\\"NumberOfElement\\\" : 40,\\n  \\\"FileName\\\" : \\\"0_LogbookLifecycles_20180220_111512.zip\\\",\\n  \\\"Size\\\" : 40903,\\n  \\\"DigestAlgorithm\\\" : \\\"SHA512\\\"\\n}\",\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"STARTED\",\"outDetail\":\"LOGBOOK_LC_SECURISATION.STARTED\",\"outMessg\":\"Début de la sécurisation des journaux du cycle de vie\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"logbook\\\",\\\"ServerId\\\":1344943190,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":136983638}\",\"agIdApp\":null,\"evIdAppSession\":null,\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"agIdExt\":null,\"rightsStatementIdentifier\":null,\"obId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"obIdReq\":null,\"obIdIn\":null,\"events\":[{\"evId\":\"aedqaaaaacghcijwaagqwalbwlwlpvqaaaaq\",\"evParentId\":\"aedqaaaaacghcijwaagqwalbwlwlpviaaaaq\",\"evType\":\"FINALIZE_LC_TRACEABILITY.OP_SECURISATION_STORAGE\",\"evDateTime\":\"2018-02-20T11:15:13.494\",\"evDetData\":null,\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"OK\",\"outDetail\":\"FINALIZE_LC_TRACEABILITY.OP_SECURISATION_STORAGE.OK\",\"outMessg\":\"Succès du stockage des journaux du cycle de vie Detail=  OK:1\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"processing\\\",\\\"ServerId\\\":1148264758,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":208740662}\",\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\"},{\"evId\":\"aedqaaaaacghcijwaagqwalbwlwltuiaaaaq\",\"evParentId\":null,\"evType\":\"LOGBOOK_LC_SECURISATION\",\"evDateTime\":\"2018-02-20T11:15:14.002\",\"evDetData\":null,\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"OK\",\"outDetail\":\"LOGBOOK_LC_SECURISATION.OK\",\"outMessg\":\"Succès de la sécurisation des journaux du cycle de vie\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"processing\\\",\\\"ServerId\\\":1148264758,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":208740662}\",\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"obId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\"}],\"#tenant\":0,\"#version\":9,\"#lastPersistedDate\":\"2018-02-20T11:15:14.007\"}],\"$context\":{\"$query\":{\"$and\":[{\"$eq\":{\"evType\":\"LOGBOOK_LC_SECURISATION\"}},{\"$eq\":{\"events.outDetail\":\"LOGBOOK_LC_SECURISATION.OK\"}},{\"$lte\":{\"events.evDetData.StartDate\":\"2018-02-20T11:14:54.872\"}},{\"$gte\":{\"events.evDetData.EndDate\":\"2018-02-20T11:14:54.872\"}}]},\"$filter\":{\"$limit\":1,\"$orderby\":{\"events.evDateTime\":-1}},\"$projection\":{}}}";


    private static final String RESULT_SELECT_ISLAST =
        "{\"httpCode\":200,\"$hits\":{\"total\":1,\"offset\":0,\"limit\":1,\"size\":1},\"$results\":[{\"evId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evParentId\":null,\"evType\":\"LOGBOOK_LC_SECURISATION\",\"evDateTime\":\"2018-02-20T11:15:05.010\",\"evDetData\":\"{\\n  \\\"LogType\\\" : \\\"LIFECYCLE\\\",\\n  \\\"StartDate\\\" : \\\"2018-02-20T08:29:37.835\\\",\\n  \\\"EndDate\\\" : \\\"2018-02-20T11:14:54.872\\\",\\n  \\\"Hash\\\" : \\\"1uEHtQeA3eXIduiBV9wt5qCZD3VmuiTD68mnMMQp3VWF9QUl3ME8aFv/GVXvVY9PMU73xu6Tjn1eHqs4FOHfDQ==\\\",\\n  \\\"TimeStampToken\\\" : \\\"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\\\",\\n  \\\"PreviousLogbookTraceabilityDate\\\" : \\\"2018-02-20T08:16:26.088\\\",\\n  \\\"MinusOneMonthLogbookTraceabilityDate\\\" : \\\"1970-01-01T00:00:00\\\",\\n  \\\"NumberOfElement\\\" : 40,\\n  \\\"FileName\\\" : \\\"0_LogbookLifecycles_20180220_111512.zip\\\",\\n  \\\"Size\\\" : 40903,\\n  \\\"DigestAlgorithm\\\" : \\\"SHA512\\\"\\n}\",\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"STARTED\",\"outDetail\":\"LOGBOOK_LC_SECURISATION.STARTED\",\"outMessg\":\"Début de la sécurisation des journaux du cycle de vie\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"logbook\\\",\\\"ServerId\\\":1344943190,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":136983638}\",\"agIdApp\":null,\"evIdAppSession\":null,\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"agIdExt\":null,\"rightsStatementIdentifier\":null,\"obId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"obIdReq\":null,\"obIdIn\":null,\"events\":[{\"evId\":\"aedqaaaaacghcijwaagqwalbwlwlpvqaaaaq\",\"evParentId\":\"aedqaaaaacghcijwaagqwalbwlwlpviaaaaq\",\"evType\":\"FINALIZE_LC_TRACEABILITY.OP_SECURISATION_STORAGE\",\"evDateTime\":\"2018-02-20T11:15:13.494\",\"evDetData\":null,\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"OK\",\"outDetail\":\"FINALIZE_LC_TRACEABILITY.OP_SECURISATION_STORAGE.OK\",\"outMessg\":\"Succès du stockage des journaux du cycle de vie Detail=  OK:1\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"processing\\\",\\\"ServerId\\\":1148264758,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":208740662}\",\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\"},{\"evId\":\"aedqaaaaacghcijwaagqwalbwlwltuiaaaaq\",\"evParentId\":null,\"evType\":\"LOGBOOK_LC_SECURISATION\",\"evDateTime\":\"2018-02-20T11:15:14.002\",\"evDetData\":null,\"evIdProc\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"evTypeProc\":\"TRACEABILITY\",\"outcome\":\"OK\",\"outDetail\":\"LOGBOOK_LC_SECURISATION.OK\",\"outMessg\":\"Succès de la sécurisation des journaux du cycle de vie\",\"agId\":\"{\\\"Name\\\":\\\"5ca8d99a4a94\\\",\\\"Role\\\":\\\"processing\\\",\\\"ServerId\\\":1148264758,\\\"SiteId\\\":1,\\\"GlobalPlatformId\\\":208740662}\",\"evIdReq\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\",\"obId\":\"aecaaaaaacecuncwaaflmalbwlwjnhyaaaaq\"}],\"#tenant\":0,\"#version\":9,\"#lastPersistedDate\":\"2018-02-20T11:15:14.007\"}],\"$context\":{\"$query\":{\"$and\":[{\"$eq\":{\"evType\":\"LOGBOOK_LC_SECURISATION\"}},{\"$eq\":{\"events.outDetail\":\"LOGBOOK_LC_SECURISATION.OK\"}}]},\"$filter\":{\"$limit\":1,\"$orderby\":{\"events.evDateTime\":-1}},\"$projection\":{}}}";
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsClient);
        when(logbookLifeCyclesClientFactory.getClient()).thenReturn(logbookLifeCyclesClient);
        when(storageClientFactory.getClient()).thenReturn(storageClient);
    }


    @RunWithCustomExecutor()
    @Test
    public void auditEvidenceNominalCaseForUnit()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        //GIVEN
        EvidenceService evidenceService =
            new EvidenceService(metaDataClientFactory, logbookOperationsClientFactory, logbookLifeCyclesClientFactory,
                storageClientFactory);

        JsonNode unitMd = getUnitMd();
        JsonNode liceCycle = getLifcycle();
        JsonNode logbook = getLogbook();


        //WHEN
        RequestResponseOK<JsonNode> response1 = new RequestResponseOK<JsonNode>().addResult(unitMd);

        when(metaDataClient.getUnitByIdRaw("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq")).thenReturn(response1);
        when(logbookLifeCyclesClient
            .selectUnitLifeCycleById(eq("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq"), any(), eq(LifeCycleStatusCode.LIFE_CYCLE_COMMITTED)))
            .thenReturn(liceCycle);

        JsonNode select = getSelectlogbookLCsecure();

        JsonNode select2 = getSelect2();

        when(logbookOperationsClient.selectOperationById(anyString(), any())).thenReturn(logbook);
        when(logbookOperationsClient.selectOperation(select))
            .thenReturn(JsonHandler.getFromString(RESULT_SELECT_LOGBOOK_SECUR_OP));

        when(logbookOperationsClient.selectOperation(select2))
            .thenReturn(JsonHandler.getFromString(RESULT_SELECT_ISLAST));

        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(PropertiesUtils.getResourceAsStream("0_LogbookLifecycles_20180220_111512.zip"));

        when(storageClient.getContainerAsync(anyString(), anyString(), eq(DataCategory.LOGBOOK))).thenReturn(responseMock);
        when(storageClient.getInformation(anyString(), eq(DataCategory.UNIT),anyString(), any())).thenReturn(OFFERS_INFO);


        evidenceService.launchEvidence("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq", LifeCycleTraceabilitySecureFileObject.MetadataType.UNIT);

        ArgumentCaptor<LogbookOperationParameters> captorCreate =
            ArgumentCaptor.forClass(LogbookOperationParameters.class);
        ArgumentCaptor<LogbookOperationParameters> captorUpdate =
            ArgumentCaptor.forClass(LogbookOperationParameters.class);


        //THEN
        verify(logbookOperationsClient).create(captorCreate.capture());

        LogbookOperationParameters logbookOperationParameters = captorCreate.getValue();
        assertThat(logbookOperationParameters.getParameterValue(LogbookParameterName.outcome)).isEqualTo("STARTED");
        assertThat(logbookOperationParameters.getParameterValue(LogbookParameterName.eventType))
            .isEqualTo("EVIDENCEAUDIT");

        verify(logbookOperationsClient,times(3)).update(captorUpdate.capture());
        List<LogbookOperationParameters> events = captorUpdate.getAllValues();

        assertThat(events.get(0).getParameterValue(LogbookParameterName.outcome)).isEqualTo("OK");
        assertThat(events.get(0).getParameterValue(LogbookParameterName.outcomeDetail)).isEqualTo("EVIDENCEAUDIT_DATABASE.OK");
        assertThat(events.get(0).getParameterValue(LogbookParameterName.eventType)).isEqualTo("EVIDENCEAUDIT_DATABASE");

        assertThat(events.get(1).getParameterValue(LogbookParameterName.outcome)).isEqualTo("OK");
        assertThat(events.get(1).getParameterValue(LogbookParameterName.outcomeDetail)).isEqualTo("EVIDENCEAUDIT_STORAGE.OK");
        assertThat(events.get(1).getParameterValue(LogbookParameterName.eventType)).isEqualTo("EVIDENCEAUDIT_STORAGE");


        assertThat(events.get(2).getParameterValue(LogbookParameterName.outcome)).isEqualTo("OK");
        assertThat(events.get(2).getParameterValue(LogbookParameterName.eventType)).isEqualTo("EVIDENCEAUDIT");
        assertThat(events.get(2).getParameterValue(LogbookParameterName.outcomeDetail)).isEqualTo("EVIDENCEAUDIT.OK");

        assertThat(events.get(2).getParameterValue(LogbookParameterName.outcomeDetail)).isEqualTo("EVIDENCEAUDIT.OK");

        String detData =
            "{\"DatabaseMetadataAndLifecycleDigest\":\"66ebea803269b6c768fda751718b3a984c8e4d339c38aeedd8de812ab4362f0ab1225606aada1635652bece913e59779d662aa7e843713fa85291b91a5608246\",\"OfferMetadataAndLifeCycleDigests\":{\"offer-fs-1.service.consul\":\"66ebea803269b6c768fda751718b3a984c8e4d339c38aeedd8de812ab4362f0ab1225606aada1635652bece913e59779d662aa7e843713fa85291b91a5608246\"}}";

        assertThat(detData).isEqualTo(events.get(1).getParameterValue(LogbookParameterName.eventDetailData));

    }

    @RunWithCustomExecutor()
    @Test
    public void auditEvidenceWhenUnitNotSecure()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        //GIVEN
        EvidenceService evidenceService =
            new EvidenceService(metaDataClientFactory, logbookOperationsClientFactory, logbookLifeCyclesClientFactory,
                storageClientFactory);

        JsonNode unitMd = getUnitMd();
        JsonNode liceCycle = getLifcycle();
        JsonNode logbook = getLogbook();


        //WHEN
        RequestResponseOK<JsonNode> response1 = new RequestResponseOK<JsonNode>().addResult(unitMd);

        when(metaDataClient.getUnitByIdRaw("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq")).thenReturn(response1);
        when(logbookLifeCyclesClient
            .selectUnitLifeCycleById(eq("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq"), any(), eq(LifeCycleStatusCode.LIFE_CYCLE_COMMITTED)))
            .thenReturn(liceCycle);

        JsonNode select = getSelectlogbookLCsecure();


        when(logbookOperationsClient.selectOperationById(anyString(), any())).thenReturn(logbook);

        when(logbookOperationsClient.selectOperation(select))
            .thenThrow(new LogbookClientNotFoundException("not found"));

        evidenceService.launchEvidence("aeaqaaaaaaguu2zzaazsualbwlwdgwaaaaaq", LifeCycleTraceabilitySecureFileObject.MetadataType.UNIT);

        ArgumentCaptor<LogbookOperationParameters> captorCreate =
            ArgumentCaptor.forClass(LogbookOperationParameters.class);
        ArgumentCaptor<LogbookOperationParameters> captorUpdate =
            ArgumentCaptor.forClass(LogbookOperationParameters.class);


        //THEN
        verify(logbookOperationsClient).create(captorCreate.capture());

        LogbookOperationParameters logbookOperationParameters = captorCreate.getValue();
        assertThat(logbookOperationParameters.getParameterValue(LogbookParameterName.outcome)).isEqualTo("STARTED");
        assertThat(logbookOperationParameters.getParameterValue(LogbookParameterName.eventType))
            .isEqualTo("EVIDENCEAUDIT");

        verify(logbookOperationsClient,times(1)).update(captorUpdate.capture());
        List<LogbookOperationParameters> events = captorUpdate.getAllValues();

        assertThat(events.get(0).getParameterValue(LogbookParameterName.outcome)).isEqualTo("WARNING");
        assertThat(events.get(0).getParameterValue(LogbookParameterName.outcomeDetail)).isEqualTo("EVIDENCEAUDIT.WARNING");
        assertThat(events.get(0).getParameterValue(LogbookParameterName.eventType)).isEqualTo("EVIDENCEAUDIT");
    }


    private JsonNode getSelectlogbookLCsecure() throws Exception {

        Select select = new Select();
        BooleanQuery query = and().add(
            QueryHelper.eq(LogbookMongoDbName.eventType.getDbname(), "LOGBOOK_LC_SECURISATION"),
            QueryHelper.eq("events.outDetail", "LOGBOOK_LC_SECURISATION.OK"),
            lte("events.evDetData.StartDate", "2018-02-20T11:14:54.872"),
            gte("events.evDetData.EndDate", "2018-02-20T11:14:54.872")
        );

        select.setQuery(query);
        select.setLimitFilter(0, 1);
        select.addOrderByDescFilter("events.evDateTime");
        return select.getFinalSelect();
    }

    private JsonNode getSelect2() throws Exception {

        Select select = new Select();

        BooleanQuery query = and().add(
            QueryHelper.eq(LogbookMongoDbName.eventType.getDbname(), "LOGBOOK_LC_SECURISATION"),
            QueryHelper.eq("events.outDetail", "LOGBOOK_LC_SECURISATION.OK")
        );

        select.setQuery(query);
        select.setLimitFilter(0, 1);
        select.addOrderByDescFilter("events.evDateTime");

        return select.getFinalSelect();
    }

    private JsonNode getUnitMd() throws FileNotFoundException, InvalidParseOperationException {
        return JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream("unitMd.json"));
    }

    private JsonNode getLifcycle() throws FileNotFoundException, InvalidParseOperationException {
        return JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream("lifeCycle.json"));
    }

    private JsonNode getLogbook() throws FileNotFoundException, InvalidParseOperationException {
        return JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream("logbookSecure.json"));
    }
}
