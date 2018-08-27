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
package fr.gouv.vitam.access.internal.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalExecutionException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalRuleExecutionException;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.client.VitamRequestIterator;
import fr.gouv.vitam.common.database.builder.query.action.Action;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.request.multiple.UpdateMultiQuery;
import fr.gouv.vitam.common.database.parser.request.multiple.RequestParserHelper;
import fr.gouv.vitam.common.database.parser.request.multiple.RequestParserMultiple;
import fr.gouv.vitam.common.database.parser.request.multiple.UpdateParserMultiple;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.UpdatePermissionException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.ActivationStatus;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleUnitParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.metadata.client.MetaDataClientRest;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({MetaDataClientFactory.class, LogbookOperationsClientFactory.class,
    LogbookLifeCyclesClientFactory.class, StorageClientFactory.class, WorkspaceClientFactory.class})
public class AccessInternalModuleImplTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private AccessInternalModuleImpl accessModuleImpl;

    private MetaDataClient metaDataClient;
    private WorkspaceClient workspaceClient;

    private LogbookOperationsClient logbookOperationClient;
    private LogbookLifeCyclesClient logbookLifeCycleClient;
    private StorageClient storageClient;
    private static JunitHelper junitHelper;
    private static int serverPort;

    private static final String SAMPLE_OBJECTGROUP_FILENAME = "sample_objectGroup_document.json";
    private static JsonNode sampleObjectGroup;

    private static final String ACCESS_CONTRACT_NO_PERMISSION = "access_contract_no_update_allowed.json";
    private static final String ACCESS_CONTRACT_DESC_ONLY = "access_contract_update_desc_only.json";
    private static final String ACCESS_CONTRACT_ALL_PERMISSION = "access_contract_update_all_allowed.json";

    private static final String QUERY =
        "{\"$queries\": [{ \"$path\": \"aaaaa\" }],\"$filter\": { },\"$projection\": {}}";
    private static final String QUERY_UPDATE =
        "{\"$root\": { },\"$queries\": [{ \"$path\": \"aaaaa\" }],\"$filter\": { },\"$action\": {}}";
    private static final String SELECT_AU_RESPONSE =
        "{\"#id\": \"aeaqaaaaaagbcaacaa3woak5by7by4yaaaba\",\"#sps\": [\"RATP\"]," +
            "\"#sp\": \"RATP\",\"#management\": {" + "\"StorageRule\": {" + "\"Rules\": [{" +
            "\"Rule\": \"STO-00001\"," + "\"StartDate\": \"2000-01-01\"," + "\"EndDate\": \"2001-01-01\"}]," +
            "\"FinalAction\": \"Copy\"" + "}," + "\"AppraisalRule\": {" + "\"Rules\": [{" + "\"Rule\": \"APP-00002\"," +
            "\"StartDate\": \"2000-01-01\"," + "\"EndDate\": \"2005-01-01\"" + "}]," + "\"FinalAction\": \"Destroy\"," +
            "\"Inheritance\": {" + "\"PreventInheritance\":true" + "}}," + "\"AccessRule\": {" + "\"Inheritance\": {" +
            "\"PreventRulesId\":\"ID-NoRule\"" + "}}," + "\"DisseminationRule\": {" + "\"Rules\": [{" +
            "\"Rule\": \"DIS-00001\"," + "\"StartDate\": \"2000-01-01\"," + "\"EndDate\": \"2025-01-01\"}]" + "}," +
            "\"ClassificationRule\": {" + "\"Rules\": [{" + "\"Rule\": \"CLASS-00001\"," +
            "\"StartDate\": \"2000-01-01\"," + "\"ClassificationLevel\": \"Secret Défense\"," +
            "\"ClassificationOwner\": \"RATP\"," + "\"EndDate\": \"2010-01-01\"}]" + "}," +
            "\"OriginatingAgency\": \"RATP\"},\"DescriptionLevel\": \"RecordGrp\"," +
            "\"Title\": \"Eglise de Pantin\",\"Titles\": {\"fr\": \"Eglise de Pantin\"}," +
            "\"Description\": \"Desc\",\"Descriptions\": {\"fr\": \"Desc\"}," +
            "\"StartDate\": \"2017-04-04T08:07:06\",\"EndDate\": \"2017-04-04T08:07:06\"," +
            "\"_ops\": [\"aedqaaaaacgbcaacabfkuak5by7buaiaaaaq\"],\"_unitType\": \"INGEST\",\"_v\": 0,\"_tenant\": 0," +
            "\"_max\": 2,\"_min\": 1,\"_up\": [\"aeaqaaaaaagbcaacaa3woak5by7by4aaaaba\"],\"_nbc\": 1," +
            "\"_us\": [\"aeaqaaaaaagbcaacaa3woak5by7by4aaaaba\"],\"_uds\": [{\"aeaqaaaaaagbcaacaa3woak5by7by4aaaaba\": 1}]}";
    // Note: this is an incorrect response since missing Qualifiers but _id is correct but within
    private static final String FAKE_METADATA_RESULT = "{$results:[{'_id':123}]}";
    private static final String FAKE_METADATA_MULTIPLE_RESULT =
        "{$results:[ {" + "\"qualifier\" : \"BinaryMaster\"," + "\"versions\" : [ {" +
            "  \"_id\" : \"aeaaaaaaaagbcaacabg7sak4p3tku6yaaaaq\"," + " \"DataObjectVersion\" : \"BinaryMaster_1\"," +
            " \"FormatIdentification\" : {" + " \"FormatLitteral\" : \"Acrobat PDF 1.4 - Portable Document Format\"," +
            " \"MimeType\" : \"application/pdf\"," + " \"FormatId\" : \"fmt/18\"" + " }," + "\"FileInfo\" : {" +
            "\"Filename\" : \"Suivi des op\u00E9rations d\'entr\u00E9es vs. recherche via registre des fonds.pdf\"," +
            "\"LastModified\" : \"2016-08-05T09:28:15.000+02:00\"" + "}" + "} ]" + "} ]}";


    private static final String QUERY_DESCRIPTION =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"Description\":\"Test\"}}]}";

    private static final String QUERY_STRING = "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," +
        "\"$action\":[" + "{\"$set\":{\"Title\":\"Eglise de Pantin Modfii\u00E9\"}}," +
        "{\"$set\":{\"SubmissionAgency.Identifier\":\"ServiceversantID\"}}," +
        "{\"$set\":{\"#management.StorageRule\":{Rules:[{\"Rule\":\"STO-00001\",\"StartDate\":\"2000-01-01\"}],\"FinalAction\":\"RestrictAccess\"}}}," +
        "{\"$unset\":[\"#management.DisseminationRule\"]}," +
        "{\"$set\":{\"#management.ClassificationRule\":{Rules:[{\"Rule\":\"CLASS-00002\",\"StartDate\":\"2017-07-01\"}]}}}]}";

    private static final String QUERY_MULTIPLE_STRING =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.ClassificationRule\":{Rules:[" +
            "{\"Rule\":\"CLASS-00002\",\"StartDate\":\"2017-07-01\"}," +
            "{\"Rule\":\"CLASS-00003\",\"StartDate\":\"2017-07-01\"}" + "]}}}]}";

    private static final String QUERY_FINAL_ACTION =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.StorageRule\":{Rules:[{\"Rule\":\"STO-00002\",\"StartDate\":\"2017-07-01\"}],\"FinalAction\":\"Copy\"}}}]}";

    private static final String QUERY_CREATE_STRING =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.ReuseRule\":{Rules:[" + "{\"Rule\":\"REU-00001\",\"StartDate\":\"2017-07-01\"}" +
            "]}}}]}";

    private static final String QUERY_STRING_WITH_END =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.StorageRule\":{Rules:[{\"Rule\":\"STO-00001\",\"StartDate\":\"2000-01-01\"}],\"FinalAction\":\"RestrictAccess\"}}}," +
            "{\"$unset\":[\"#management.DisseminationRule\"]}," +
            "{\"$set\":{\"#management.ClassificationRule\":{Rules:[" +
            "{\"Rule\":\"CLASS-00002\",\"StartDate\":\"2017-07-01\",\"EndDate\":\"2017-08-02\"}]}}}]}";

    private static final String QUERY_STRING_WITH_PI_DELETED =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$unset\":[\"#management.AppraisalRule\"]}]}";

    private static final String QUERY_STRING_WITH_RNRI_DELETED =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$unset\":[\"#management.AccessRule\"]}]}";

    private static final String QUERY_STRING_WITH_WRONG_CATEGORY_FINAL_ACTION =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.ClassificationRule\":{Rules:[{\"Rule\":\"CLASS-00002\",\"StartDate\":\"2017-07-01\"}],\"FinalAction\":\"Keep\"}}}]}";

    private static final String QUERY_STRING_WITH_WRONG_FINAL_ACTION =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.StorageRule\":{Rules:[{\"Rule\":\"STO-00002\",\"StartDate\":\"2017-07-01\"}],\"FinalAction\":\"Keep\"}}}]}";

    private static final String QUERY_STRING_WITH_WRONG_CATEGORY =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.ClassificationRule\":{Rules:[{\"Rule\":\"STO-00002\",\"StartDate\":\"2017-07-01\"}]}}}]}";

    private static final String QUERY_PREVENT_INHERITANCE =
        "{\"$roots\":[\"managementRulesUpdate\"],\"$query\":[],\"$filter\":{}," + "\"$action\":[" +
            "{\"$set\":{\"#management.AccessRule.Inheritance.PreventInheritance\":\"false\"}}]}}";

    private static final String REAL_DATA_RESULT_PATH = "sample_data_results.json";
    private static final String REAL_DATA_RESULT_MULTI_PATH = "sample_data_multi_results.json";
    private static final UpdateMultiQuery updateQuery = new UpdateMultiQuery();
    private static final Integer TENANT_ID = 0;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        junitHelper = JunitHelper.getInstance();
        serverPort = junitHelper.findAvailablePort();
        sampleObjectGroup = JsonHandler.getFromFile(PropertiesUtils.findFile(SAMPLE_OBJECTGROUP_FILENAME));
        updateQuery.addActions(new SetAction("name", "test"));
    }

    @AfterClass
    public static void tearDownAfterClass() {
        junitHelper.releasePort(serverPort);
        VitamClientFactory.resetConnections();
    }

    // this guid was generated with tenant = 0
    private static final String ID = "aeaqaaaaaaevelkyaa6teak73hlewtiaaabq";
    private static final String REQUEST_ID = "aeaqaaaaaitxll67abarqaktftcfyniaaaaq";

    /**
     * @param query
     * @return
     * @throws InvalidParseOperationException
     */
    public JsonNode fromStringToJson(String query) throws InvalidParseOperationException {
        return JsonHandler.getFromString(query);

    }

    @Before
    public void setUp() {
        final MetaDataClientFactory mockedMetadataFactory = mock(MetaDataClientFactory.class);
        PowerMockito.mockStatic(MetaDataClientFactory.class);
        metaDataClient = mock(MetaDataClientRest.class);
        PowerMockito.when(MetaDataClientFactory.getInstance()).thenReturn(mockedMetadataFactory);
        PowerMockito.when(mockedMetadataFactory.getClient()).thenReturn(metaDataClient);
        logbookLifeCycleClient = mock(LogbookLifeCyclesClient.class);
        logbookOperationClient = mock(LogbookOperationsClient.class);
        final LogbookLifeCyclesClientFactory factorylc = mock(LogbookLifeCyclesClientFactory.class);
        final LogbookOperationsClientFactory factoryop = mock(LogbookOperationsClientFactory.class);
        PowerMockito.mockStatic(LogbookLifeCyclesClientFactory.class);
        PowerMockito.when(LogbookLifeCyclesClientFactory.getInstance()).thenReturn(factorylc);
        PowerMockito.when(factorylc.getClient()).thenReturn(logbookLifeCycleClient);
        PowerMockito.mockStatic(LogbookOperationsClientFactory.class);
        PowerMockito.when(LogbookOperationsClientFactory.getInstance()).thenReturn(factoryop);
        PowerMockito.when(factoryop.getClient()).thenReturn(logbookOperationClient);
        storageClient = mock(StorageClient.class);
        final StorageClientFactory factoryst = mock(StorageClientFactory.class);
        PowerMockito.mockStatic(StorageClientFactory.class);
        PowerMockito.when(StorageClientFactory.getInstance()).thenReturn(factoryst);
        PowerMockito.when(factoryst.getClient()).thenReturn(storageClient);

        final WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        PowerMockito.mockStatic(WorkspaceClientFactory.class);
        workspaceClient = mock(WorkspaceClient.class);
        PowerMockito.when(WorkspaceClientFactory.getInstance()).thenReturn(workspaceClientFactory);
        PowerMockito.when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);

        accessModuleImpl =
            new AccessInternalModuleImpl(storageClient, logbookOperationClient, logbookLifeCycleClient,
                workspaceClient);
    }

    @Test
    public void given_correct_dsl_When_select_thenOK()
        throws Exception {
        when(metaDataClient.selectUnits(anyObject())).thenReturn(JsonHandler.createObjectNode());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_dsl_When_select_thenTrows_IllegalArgumentException()
        throws Exception {
        accessModuleImpl.selectUnit(fromStringToJson(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void given_test_AccessExecutionException()
        throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient).selectUnits(anyObject());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_DSLWhen_select_units_ThenThrows_InvalidParseOperationException()
        throws Exception {
        PowerMockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient).selectUnits(anyObject());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));

    }

    @Test(expected = IllegalArgumentException.class)
    public void given__DSLWhen_select_units_ThenThrows_MetadataInvalidSelectException()
        throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient).selectUnits(anyObject());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void given_DSLWhen_select_units_ThenThrows_MetaDataDocumentSizeException()
        throws Exception {
        Mockito.doThrow(new MetaDataDocumentSizeException("")).when(metaDataClient).selectUnits(anyObject());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void given_clientProblem_When_select_units_ThenThrows_AccessExecutionException()
        throws Exception {
        Mockito.doThrow(new ProcessingException("")).when(metaDataClient).selectUnits(anyObject());
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    // select Unit Id
    @Test
    public void given_correct_dsl_When_selectunitById_thenOK()
        throws Exception {
        when(metaDataClient.selectUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.createObjectNode());
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), ID);
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_dsl_When_selectunitById_thenTrows_IllegalArgumentException()
        throws Exception {
        accessModuleImpl.selectUnitbyId(fromStringToJson(""), ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void given_test_AccessExecutionException_unitById()
        throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient).selectUnitbyId(anyObject(), anyObject());
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), ID);
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_DSLWhen_select_unitById_ThenThrows_InvalidParseOperationException()
        throws Exception {
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient).selectUnitbyId(anyObject(),
            anyObject());
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), ID);
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void given__DSLWhen_select_unitById_ThenThrows_MetadataInvalidSelectException()
        throws Exception {
        Mockito.doThrow(new MetaDataDocumentSizeException("")).when(metaDataClient).selectUnitbyId(anyObject(),
            anyObject());
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void given_emptyOrNullIdUnit_when_selectUnitbyId_thenthrows_IllegalArgumentException() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient)
            .selectUnitbyId(fromStringToJson(QUERY), "");
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), "");
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_DSLWhen_selectUnitById_ThenThrows_InvalidParseOperationException()
        throws Exception {
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient)
            .selectUnitbyId(fromStringToJson(QUERY), ID);
        accessModuleImpl.selectUnitbyId(fromStringToJson(QUERY), ID);
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_DSLWhen_selectUnit_ThenThrows_InvalidParseOperationException()
        throws Exception {
        final JsonNode jsonQuery = JsonHandler.getFromString(QUERY);
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient)
            .selectUnits(jsonQuery);
        accessModuleImpl.selectUnit(fromStringToJson(QUERY));
    }

    @Test(expected = IllegalArgumentException.class)
    public void given_emptyOrNullIdUnit_when_selectOGbyId_thenthrows_IllegalArgumentException() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient)
            .selectObjectGrouptbyId(fromStringToJson(QUERY), "");
        accessModuleImpl.selectObjectGroupById(fromStringToJson(QUERY), "");
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void given_metadataAccessProblem_throw_AccessExecutionException() throws Exception {
        Mockito.doThrow(new ProcessingException("Fake error")).when(metaDataClient)
            .selectObjectGrouptbyId(fromStringToJson(QUERY), "ds");
        accessModuleImpl.selectObjectGroupById(fromStringToJson(QUERY), "ds");
    }

    @Test
    public void given_selectObjectGroupById_OK()
        throws Exception {
        when(metaDataClient.selectObjectGrouptbyId(fromStringToJson(QUERY), ID))
            .thenReturn(sampleObjectGroup);
        final JsonNode result = accessModuleImpl.selectObjectGroupById(fromStringToJson(QUERY), ID);
        assertEquals(sampleObjectGroup, result);
    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_DSLWhen_selectOGById_ThenThrows_InvalidParseOperationException()
        throws Exception {
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient)
            .selectObjectGrouptbyId(fromStringToJson(QUERY), ID);
        accessModuleImpl.selectObjectGroupById(fromStringToJson(QUERY), ID);
    }

    // update by id - start
    @Test
    @RunWithCustomExecutor
    public void given_correct_dsl_When_updateUnitById_thenOK()
        throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);

        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());

        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"#id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"#diff\":\"-    \\\"Title\\\" : " +
            "\\\"MyTitle\\\",\\n+    \\\"Title\\\" : \\\"Modified title\\\",\\n-    \\\"MyBoolean\\\" : false,\\n+   " +
            " \\\"MyBoolean\\\" : true,\"}]}"));

        accessModuleImpl.updateUnitbyId(new UpdateMultiQuery().getFinalUpdate(), id, REQUEST_ID);

        // check if diff for update sent to lfc is correct
        final LogbookLifeCycleUnitParameters capture = logbookLFCUnitParametersArgsCaptor.getValue();
        assertNotNull(capture.getParameterValue(LogbookParameterName.eventDetailData));
        JsonNode lfcParams = JsonHandler.getFromString(capture.getParameterValue(LogbookParameterName.eventDetailData));
        assertEquals(
            "-    \"Title\" : \"MyTitle\",\n+    \"Title\" : \"Modified title\",\n-    \"MyBoolean\" : false,\n+    \"MyBoolean\" : true,",
            lfcParams.get("diff").textValue());
    }

    @Test
    @RunWithCustomExecutor
    public void given_throw_desc_only_error_When_updateUnitById_thenOK()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_DESC_ONLY));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);

        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\",\"#management\":{}," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());

        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"#id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"#diff\":\"-    \\\"Title\\\" : " +
            "\\\"MyTitle\\\",\\n+    \\\"Title\\\" : \\\"Modified title\\\",\\n-    \\\"MyBoolean\\\" : false,\\n+   " +
            " \\\"MyBoolean\\\" : true,\"}]}"));

        try {
            String query = QUERY_MULTIPLE_STRING.replace("managementRulesUpdate", id);
            accessModuleImpl.updateUnitbyId(JsonHandler.getFromString(query), id, REQUEST_ID);
            fail("Should throw exception");
        } catch (UpdatePermissionException e) {
            assertEquals("UPDATE_UNIT_DESC_PERMISSION", e.getMessage());
        }
    }

    @Test
    @RunWithCustomExecutor
    public void given_throw_permission_error_When_updateUnitById_thenOK()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_NO_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);

        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());

        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"#id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"#diff\":\"-    \\\"Title\\\" : " +
            "\\\"MyTitle\\\",\\n+    \\\"Title\\\" : \\\"Modified title\\\",\\n-    \\\"MyBoolean\\\" : false,\\n+   " +
            " \\\"MyBoolean\\\" : true,\"}]}"));

        try {
            accessModuleImpl.updateUnitbyId(JsonHandler.getFromString(QUERY_DESCRIPTION), id, REQUEST_ID);
            fail("Should throw exception");
        } catch (UpdatePermissionException e) {
            assertEquals("UPDATE_UNIT_PERMISSION", e.getMessage());
        }
    }

    // update by id - start
    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void given_notfound_guid_When_updateUnitById_thenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);

        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":0,\"size\":0,\"limit\":0,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());

        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);

        accessModuleImpl.updateUnitbyId(JsonHandler.getFromString(QUERY_STRING), id, REQUEST_ID);

    }

    @Test(expected = InvalidParseOperationException.class)
    @RunWithCustomExecutor
    public void given_error_schema_When_updateUnitById_thenKO()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);
        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());
        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject()))
            .thenThrow(new InvalidParseOperationException("InvalidParseOperationException"));

        accessModuleImpl.updateUnitbyId(new UpdateMultiQuery().getFinalUpdate(), id, REQUEST_ID);

    }

    @Test
    @RunWithCustomExecutor
    public void given_dsl_nodiff_When_updateUnitById_thenOK()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);
        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture(),
            anyObject());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(jsonResult)
            .setHttpCode(Status.OK.getStatusCode());
        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"#id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\"}]}"));

        accessModuleImpl.updateUnitbyId(new UpdateMultiQuery().getFinalUpdate(), id, REQUEST_ID);

        // check if diff for update sent to lfc is null
        final LogbookLifeCycleUnitParameters capture = logbookLFCUnitParametersArgsCaptor.getValue();
        assertNotNull(capture.getParameterValue(LogbookParameterName.eventDetailData));
        JsonNode lfcParams = JsonHandler.getFromString(capture.getParameterValue(LogbookParameterName.eventDetailData));
        assertTrue(lfcParams.get("diff").isNull());

    }

    @Test(expected = InvalidParseOperationException.class)
    public void given_empty_dsl_When_updateUnitById_thenTrows_IllegalArgumentException()
        throws Exception {
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());

        accessModuleImpl.updateUnitbyId(fromStringToJson(""), ID, REQUEST_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    @RunWithCustomExecutor
    public void given_test_AccessExecutionException_updateUnitById()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient)
            .updateUnitbyId(fromStringToJson(QUERY), ID);

        accessModuleImpl.updateUnitbyId(fromStringToJson(QUERY), ID, REQUEST_ID);
    }

    @Test(expected = MetaDataNotFoundException.class)
    @RunWithCustomExecutor
    public void given_test_updateUnitById_withWrongGUID()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        accessModuleImpl.updateUnitbyId(fromStringToJson(QUERY), "dfsdfsdf", REQUEST_ID);
    }

    @Test(expected = InvalidParseOperationException.class)
    @RunWithCustomExecutor
    public void given_empty_DSLWhen_updateUnitById_ThenThrows_InvalidParseOperationException()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient)
            .updateUnitbyId(anyObject(), anyObject());

        accessModuleImpl.updateUnitbyId(fromStringToJson(QUERY_UPDATE), ID, REQUEST_ID);
    }

    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void given_DSLWhen_updateUnitById_ThenThrows_MetaDataDocumentSizeException()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenThrow(new MetaDataDocumentSizeException(""));

        accessModuleImpl.updateUnitbyId(updateQuery.getFinalUpdate(), ID, REQUEST_ID);
    }

    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void given_DSL_When_updateUnitById_ThenThrows_MetaDataExecutionException()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.createObjectNode());
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        Mockito.doThrow(new MetaDataExecutionException("")).when(metaDataClient)
            .updateUnitbyId(anyObject(), anyObject());

        accessModuleImpl.updateUnitbyId(updateQuery.getFinalUpdate(), ID, REQUEST_ID);
    }

    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void given_LogbookProblem_When_updateUnitById_ThenThrows_AccessExecutionException()
        throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        JsonNode accessContractFile = JsonHandler.getFromFile(PropertiesUtils.findFile(ACCESS_CONTRACT_ALL_PERMISSION));
        AccessContractModel accessContractModel = JsonHandler.getFromStringAsTypeRefence(accessContractFile.toString(),
            new TypeReference<AccessContractModel>() {
            });
        accessContractModel.setIdentifier("FakeIdentifier");
        VitamThreadUtils.getVitamSession().setContract(accessContractModel);

        final ArgumentCaptor<LogbookLifeCycleUnitParameters> logbookLFCUnitParametersArgsCaptor =
            ArgumentCaptor.forClass(LogbookLifeCycleUnitParameters.class);

        Mockito.doThrow(new LogbookClientNotFoundException("")).when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(logbookLFCUnitParametersArgsCaptor.capture());

        final String id = "aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq";
        RequestResponse<JsonNode> requestResponseUnit = new RequestResponseOK<JsonNode>()
            .addResult(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
                "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}"))
            .setHttpCode(Status.OK.getStatusCode());

        // Mock select unit response
        when(metaDataClient.getUnitByIdRaw(anyObject())).thenReturn(requestResponseUnit);
        // mock get lifecyle
        when(logbookLifeCycleClient.selectUnitLifeCycleById(anyObject(), anyObject(), anyObject()))
            .thenReturn(JsonHandler.getFromString("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false}," +
                "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"evType\":\"Process_SIP_unitary\"," +
                "\"evTypeProc\":\"INGEST\",\"evDateTime\":\"2016-09-28T11:44:28.548\"," +
                "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
                "\"events\":[\"val1\",\"val2\"],\"#tenant\":0}]}"));
        JsonNode jsonResult = JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"_id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"Title\":\"MyTitle\"," +
            "\"Description\":\"Ma description est bien détaillée\",\"CreatedDate\":\"2016-09-28T11:44:28.548\"," +
            "\"MyInt\":20,\"MyBoolean\":false,\"MyFloat\":2.0,\"ArrayVar\":[\"val1\",\"val2\"]," +
            "\"Array2Var\":[\"val1\",\"val2\"],\"_tenant\":0,\"_max\":1,\"_min\":1,\"_up\":[],\"_nbc\":0}]}");
        when(metaDataClient.selectUnitbyId(anyObject(), anyString())).thenReturn(jsonResult);
        // Mock update unit response
        when(metaDataClient.updateUnitbyId(anyObject(), anyObject())).thenReturn(JsonHandler.getFromString("{\"$hits" +
            "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
            "\"$results\":[{\"#id\":\"aeaqaaaaaaaaaaabaasdaakxocodoiyaaaaq\",\"#diff\":\"-    \\\"Title\\\" : " +
            "\\\"MyTitle\\\",\\n+    \\\"Title\\\" : \\\"Modified title\\\",\\n-    \\\"MyBoolean\\\" : false,\\n+   " +
            " \\\"MyBoolean\\\" : true,\"}]}"));


        accessModuleImpl.updateUnitbyId(new UpdateMultiQuery().getFinalUpdate(), id, REQUEST_ID);

    }

    @Test(expected = IllegalArgumentException.class)
    public void given_emptyOrNullIdUnit_when_updateUnitbyId_thenthrows_IllegalArgumentException() throws Exception {
        Mockito.doNothing().when(logbookOperationClient).update(anyObject());
        Mockito.doNothing().when(logbookLifeCycleClient).update(anyObject());
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient)
            .updateUnitbyId(fromStringToJson(QUERY), "");

        accessModuleImpl.updateUnitbyId(fromStringToJson(QUERY), "", REQUEST_ID);
    }

    private void setAccessLogInfoInVitamSession() {
        AccessContractModel contract = new AccessContractModel();
        contract.setAccessLog(ActivationStatus.ACTIVE);
        VitamThreadUtils.getVitamSession().setContract(contract);
    }

    @Test
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroup_OK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(fromStringToJson(FAKE_METADATA_RESULT));
        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(new ByteArrayInputStream(FAKE_METADATA_RESULT.getBytes()));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenReturn(responseMock);
        Response reponseFinal = accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");
        assertNotNull(reponseFinal);

        final InputStream stream2 = StreamUtils.toInputStream(FAKE_METADATA_RESULT);
        InputStream entity = (InputStream) reponseFinal.getEntity();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamUtils.copy(entity, output);

        assertArrayEquals(output.toByteArray(), IOUtils.toByteArray(stream2));
    }

    @Test
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroupRealData_OK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(JsonHandler.getFromFile(PropertiesUtils.getResourceFile(REAL_DATA_RESULT_PATH)));
        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(PropertiesUtils.getResourceAsStream(REAL_DATA_RESULT_PATH));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenReturn(responseMock);
        Response reponseFinal = accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");

        assertNotNull(reponseFinal);

        InputStream entity = (InputStream) reponseFinal.getEntity();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamUtils.copy(entity, output);

        byte[] src = output.toByteArray();
        JsonNode jsonNode = JsonHandler.getFromBytes(src);
        assertNotNull(jsonNode);
        assertEquals(jsonNode.get("$results").get(0).get("#qualifiers").get(0).get("versions").get(0)
            .get("FormatIdentification").get("MimeType").asText(), "image/png");
        assertEquals(
            jsonNode.get("$results").get(0).get("#qualifiers").get(0).get("versions").get(0).get("FileInfo")
                .get("Filename").asText(),
            "Vitam-S\u00E9nsibilisation de l' API-V1.0.png");
    }

    @Test
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroupRealData_WARN() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(JsonHandler.getFromFile(PropertiesUtils.getResourceFile(REAL_DATA_RESULT_MULTI_PATH)));
        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(PropertiesUtils.getResourceAsStream(REAL_DATA_RESULT_MULTI_PATH));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenReturn(responseMock);
        Response reponseFinal = accessModuleImpl.getOneObjectFromObjectGroup(ID, "Thumbnail", 0, "unit0");
        assertNotNull(reponseFinal);

        InputStream entity = (InputStream) reponseFinal.getEntity();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamUtils.copy(entity, output);

        byte[] src = output.toByteArray();
        JsonNode jsonNode = JsonHandler.getFromBytes(src);
        assertNotNull(jsonNode);
        assertEquals(jsonNode.get("$results").get(0).get("#qualifiers").get(0).get("versions").get(0)
            .get("FormatIdentification").get("MimeType").asText(), "image/png");
        assertEquals(jsonNode.get("$results").get(0).get("#qualifiers").get(0).get("versions").get(0)
            .get("FormatIdentification").get("Filename").asText(), "Wrong name");

        /*
         * assertNotNull(abd); final Response binaryMasterResponse = abd.getOriginalResponse();
         * assertNotNull(binaryMasterResponse); assertEquals("image/png", abd.getMimetype()); assertEquals("Wrong name",
         * abd.getFilename());
         */
    }

    // #2604 - Fix Multiple result for download is not allow for Http 1.0
    @Test
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroup_With_One_Result() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(fromStringToJson(FAKE_METADATA_RESULT));
        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(new ByteArrayInputStream(FAKE_METADATA_RESULT.getBytes()));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenReturn(responseMock);
        Response response = accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");
        assertNotNull(response);
        InputStream entity = (InputStream) response.getEntity();
    }

    @Test
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroup_With_ObjectMapping_Result() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(fromStringToJson(FAKE_METADATA_MULTIPLE_RESULT));
        final Response responseMock = mock(Response.class);
        when(responseMock.readEntity(InputStream.class))
            .thenReturn(new ByteArrayInputStream(FAKE_METADATA_MULTIPLE_RESULT.getBytes()));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenReturn(responseMock);
        Response response = accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");
        assertNotNull(response);
        InputStream entity = (InputStream) response.getEntity();
    }

    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroup_With_Result_Null() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString())).thenReturn(null);
        accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");
    }

    @Test(expected = AccessInternalExecutionException.class)
    @RunWithCustomExecutor
    public void testGetOneObjectFromObjectGroup_With_StorageClient_Error() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        setAccessLogInfoInVitamSession();
        when(metaDataClient.selectObjectGrouptbyId(anyObject(), anyString()))
            .thenReturn(fromStringToJson(FAKE_METADATA_RESULT));
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject()))
            .thenThrow(new StorageServerClientException("Test wanted exception"));
        accessModuleImpl.getOneObjectFromObjectGroup(ID, "BinaryMaster", 0, "unit0");
    }

    @Test
    @RunWithCustomExecutor
    public void testCheckRulesOnUpdateAU() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        when(metaDataClient.selectUnitbyId(anyObject(), anyString()))
            .thenReturn(fromStringToJson("{\"$hits" +
                "\":{\"total\":1,\"size\":1,\"limit\":1,\"time_out\":false},\"$context\":{}," +
                "\"$results\":[" + SELECT_AU_RESPONSE + "]}"));

        RequestParserMultiple results;

        results = executeCheck(QUERY_STRING);
        assertEquals(5, results.getRequest().getActions().size());

        results = executeCheck(QUERY_MULTIPLE_STRING);
        assertEquals(1, results.getRequest().getActions().size());
        assertEquals(2, results.getRequest().getActions().get(0).getCurrentObject()
            .get("#management.ClassificationRule.Rules").size());

        results = executeCheck(QUERY_CREATE_STRING);
        assertEquals(1, results.getRequest().getActions().size());
        assertEquals(1,
            results.getRequest().getActions().get(0).getCurrentObject().get("#management.ReuseRule.Rules").size());

        try {
            executeCheck(QUERY_STRING_WITH_END);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_CREATE_RULE_END_DATE.name(), e.getMessage());
        }

        try {
            executeCheck(QUERY_STRING_WITH_PI_DELETED);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_DELETE_CATEGORY_INHERITANCE.name(), e.getMessage());
        }

        try {
            executeCheck(QUERY_STRING_WITH_RNRI_DELETED);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_DELETE_CATEGORY_INHERITANCE.name(), e.getMessage());
        }

        try {
            executeCheck(QUERY_STRING_WITH_WRONG_FINAL_ACTION);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_CREATE_RULE_FINAL_ACTION.name(), e.getMessage());
        }

        try {
            executeCheck(QUERY_STRING_WITH_WRONG_CATEGORY_FINAL_ACTION);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_CREATE_RULE_FINAL_ACTION.name(), e.getMessage());
        }

        try {
            executeCheck(QUERY_STRING_WITH_WRONG_CATEGORY);
            fail("Should throw exception");
        } catch (AccessInternalRuleExecutionException e) {
            // Should throw exception
            assertEquals(VitamCode.ACCESS_INTERNAL_UPDATE_UNIT_CREATE_RULE_CATEGORY.name(), e.getMessage());
        }
    }

    @Test
    public void should_ONLY_update_request_with_final_action_when_updating_final_action() throws Exception {
        // Given
        UpdateParserMultiple parser = new UpdateParserMultiple();
        String updateFinalAction =
            "{\"$roots\":[\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\"],\"$query\":[],\"$filter\":{},\"$action\":[{\"$set\":{\"#management.StorageRule.FinalAction\":\"Transfer\"}}]}";
        parser.parse(fromStringToJson(updateFinalAction));

        JsonNode unitArchiveWithRules = fromStringToJson(
            "{\"$results\":[{\"DescriptionLevel\":\"RecordGrp\",\"Title\":\"dossier2\",\"Description\":\"batman\",\"StartDate\":\"2016-06-03T15:28:00\",\"EndDate\":\"2016-06-03T15:28:00\",\"SedaVersion\":\"2.1\",\"#id\":\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\",\"#tenant\":0,\"#unitups\":[],\"#min\":1,\"#max\":1,\"#allunitups\":[],\"#management\":{\"StorageRule\":{\"Rules\":[{\"Rule\":\"STO-00001\",\"StartDate\":\"2002-01-01\",\"EndDate\":\"2003-01-01\"}],\"FinalAction\":\"Copy\"}},\"#unitType\":\"INGEST\",\"#operations\":[\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\"],\"#opi\":\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\",\"#originating_agency\":\"FRAN_NP_009913\",\"#originating_agencies\":[\"FRAN_NP_009913\"],\"#storage\":{\"offerIds\":[\"offer-fs-1.service.consul\"],\"strategyId\":\"default\",\"#nbc\":1},\"#version\":48}]}");
        when(metaDataClient.selectUnitbyId(any(), anyString())).thenReturn(unitArchiveWithRules);

        // When
        accessModuleImpl.checkAndUpdateRuleQuery(parser);

        // Then
        assertThat(parser.getRequest().getActions())
            .extracting(Action::toString)
            .containsOnly("{\"$set\":{\"#management.StorageRule.FinalAction\":\"Transfer\"}}");
    }

    @Test
    public void should_fail_silently_when_update_request_with_empty_rules() throws Exception {
        // Given
        UpdateParserMultiple parser = new UpdateParserMultiple();
        String updateFinalAction =
            "{\"$roots\":[\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\"],\"$query\":[],\"$filter\":{},\"$action\":[{\"$set\":{\"#management.StorageRule\":{\"Rules\":[],\"FinalAction\":\"Copy\"}}}]}";
        parser.parse(fromStringToJson(updateFinalAction));

        JsonNode unitArchiveWithRules = fromStringToJson(
            "{\"$results\":[{\"DescriptionLevel\":\"RecordGrp\",\"Title\":\"dossier2\",\"Description\":\"batman\",\"StartDate\":\"2016-06-03T15:28:00\",\"EndDate\":\"2016-06-03T15:28:00\",\"SedaVersion\":\"2.1\",\"#id\":\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\",\"#tenant\":0,\"#unitups\":[],\"#min\":1,\"#max\":1,\"#allunitups\":[],\"#management\":{\"StorageRule\":{\"Rules\":[{\"Rule\":\"STO-00001\",\"StartDate\":\"2002-01-01\",\"EndDate\":\"2003-01-01\"}],\"FinalAction\":\"Copy\"}},\"#unitType\":\"INGEST\",\"#operations\":[\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\"],\"#opi\":\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\",\"#originating_agency\":\"FRAN_NP_009913\",\"#originating_agencies\":[\"FRAN_NP_009913\"],\"#storage\":{\"offerIds\":[\"offer-fs-1.service.consul\"],\"strategyId\":\"default\",\"#nbc\":1},\"#version\":48}]}");
        when(metaDataClient.selectUnitbyId(any(), anyString())).thenReturn(unitArchiveWithRules);

        // When
        accessModuleImpl.checkAndUpdateRuleQuery(parser);

        // Then
        assertThat(parser.getRequest().getActions()).isEmpty();
    }

    @Test
    public void should_NOT_update_rules_with_final_action_request() throws Exception {
        // Given
        UpdateParserMultiple parser = new UpdateParserMultiple();
        String updateFinalAction =
            "{\"$roots\":[\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\"],\"$query\":[],\"$filter\":{},\"$action\":[{\"$set\":{\"#management.StorageRule.FinalAction\":\"Transfer\"}}]}";
        parser.parse(fromStringToJson(updateFinalAction));

        JsonNode unitArchiveWithRules = fromStringToJson(
            "{\"$results\":[{\"DescriptionLevel\":\"RecordGrp\",\"Title\":\"dossier2\",\"Description\":\"batman\",\"StartDate\":\"2016-06-03T15:28:00\",\"EndDate\":\"2016-06-03T15:28:00\",\"SedaVersion\":\"2.1\",\"#id\":\"aeaqaaaaaaftu7s5aakq6alerwedliqaaabq\",\"#tenant\":0,\"#unitups\":[],\"#min\":1,\"#max\":1,\"#allunitups\":[],\"#management\":{},\"#unitType\":\"INGEST\",\"#operations\":[\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\"],\"#opi\":\"aeeaaaaaacftu7s5aakr6alerwebi4aaaaaq\",\"#originating_agency\":\"FRAN_NP_009913\",\"#originating_agencies\":[\"FRAN_NP_009913\"],\"#storage\":{\"offerIds\":[\"offer-fs-1.service.consul\"],\"strategyId\":\"default\",\"#nbc\":1},\"#version\":48}]}");
        when(metaDataClient.selectUnitbyId(any(), anyString())).thenReturn(unitArchiveWithRules);

        // When
        accessModuleImpl.checkAndUpdateRuleQuery(parser);

        // Then
        assertThat(parser.getRequest().getActions())
            .extracting(Action::toString)
            .containsOnly("{\"$set\":{\"#management.StorageRule.FinalAction\":\"Transfer\"}}");
    }

    @Test
    public void givenCorrectDslWhenSelectObjectsThenOK()
            throws Exception {
        when(metaDataClient.selectObjectGroups(anyObject())).thenReturn(JsonHandler.createObjectNode());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    @Test(expected = InvalidParseOperationException.class)
    public void givenEmptyDslWhenSelectObjectsThenTrowsIllegalArgumentException()
            throws Exception {
        accessModuleImpl.selectObjects(fromStringToJson(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenSelectObjectsTestAccessExecutionException()
            throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient).selectObjectGroups(anyObject());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    @Test(expected = InvalidParseOperationException.class)
    public void givenEmptyDSLWhenSelectObjectsThenThrowsInvalidParseOperationException()
            throws Exception {
        PowerMockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient).selectObjectGroups(anyObject());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));

    }

    @Test(expected = IllegalArgumentException.class)
    public void givenDSLWhenSelectObjectsThenThrowsMetadataInvalidSelectException()
            throws Exception {
        Mockito.doThrow(new IllegalArgumentException("")).when(metaDataClient).selectObjectGroups(anyObject());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void givenDSLWhenSelectObjectsThenThrowsMetaDataDocumentSizeException()
            throws Exception {
        Mockito.doThrow(new MetaDataDocumentSizeException("")).when(metaDataClient).selectObjectGroups(anyObject());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    @Test(expected = AccessInternalExecutionException.class)
    public void givenClientProblemWhenSelectObjectsThenThrowsAccessExecutionException()
            throws Exception {
        Mockito.doThrow(new ProcessingException("")).when(metaDataClient).selectObjectGroups(anyObject());
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    @Test(expected = InvalidParseOperationException.class)
    public void givenEmptyDSLWhenSlectObjectsThenThrowsInvalidParseOperationException()
            throws Exception {
        final JsonNode jsonQuery = JsonHandler.getFromString(QUERY);
        Mockito.doThrow(new InvalidParseOperationException("")).when(metaDataClient)
                .selectObjectGroups(jsonQuery);
        accessModuleImpl.selectObjects(fromStringToJson(QUERY));
    }

    private RequestParserMultiple executeCheck(String queryString)
        throws InvalidParseOperationException, AccessInternalRuleExecutionException, AccessInternalExecutionException {
        JsonNode queryJson = JsonHandler.getFromString(queryString);

        final RequestParserMultiple parser = RequestParserHelper.getParser(queryJson);
        if (!(parser instanceof UpdateParserMultiple)) {
            parser.getRequest().reset();
            throw new IllegalArgumentException("Request is not an update operation");
        }

        accessModuleImpl.checkAndUpdateRuleQuery((UpdateParserMultiple) parser);

        return parser;
    }

    private VitamRequestIterator<JsonNode> getMockedResponseForListContainer() {

        final List<JsonNode> nodeList = new ArrayList<>();
        ObjectNode node = JsonHandler.createObjectNode();
        node.put("objectId", "0_s_a_l_20180810040000000_20180810080000000_id.log");
        nodeList.add(node);
        node = JsonHandler.createObjectNode();
        node.put("objectId", "0_s_a_l_20180810090000000_20180810150000000_id.log");
        nodeList.add(node);
        node = JsonHandler.createObjectNode();
        node.put("objectId", "0_s_a_l_20180810160000000_20180810190000000_id.log");
        nodeList.add(node);

        return new VitamRequestIterator<JsonNode>(storageClient, "listContainer", "test", JsonNode.class, null, null) {
            List<JsonNode> nodes = nodeList;
            Integer actualSize = 0;

            @Override public boolean hasNext() {
                return actualSize < 3;
            }

            @Override public JsonNode next() {
                JsonNode next = nodes.get(actualSize);
                actualSize++;
                return next;
            }
        };
    }

    private void initMocksForAccessLog() throws Exception {
        when(storageClient.listContainer(anyString(), anyObject())).thenReturn(getMockedResponseForListContainer());
        when(storageClient.getContainerAsync(anyString(), anyString(), anyObject(), anyObject())).thenReturn(Response.ok(null).build());
        when(workspaceClient.isExistingContainer(anyString())).thenReturn(true);
        doNothing().when(workspaceClient).putObject(anyString(), anyString(), anyObject());
        doNothing().when(workspaceClient).compress(anyString(), anyObject());
        doNothing().when(workspaceClient).createContainer(anyString());
        Response response =
            Response.ok(new ByteArrayInputStream("ResponseOK".getBytes())).status(Response.Status.OK).build();
        when(workspaceClient.getObject(anyString(), anyString())).thenReturn(response);
    }

    @Test
    @RunWithCustomExecutor
    public void givenNoInputDateThenCheckNbFilesPutInWorkspace() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        initMocksForAccessLog();

        ObjectNode params = JsonHandler.createObjectNode();

        accessModuleImpl.getAccessLog(params);
        verify(workspaceClient, times(3)).putObject(anyString(), anyString(), anyObject());
    }



    @Test
    @RunWithCustomExecutor
    public void givenInputStartDateAfterFileDateThenCheckNbFilesPutInWorkspace() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        initMocksForAccessLog();

        ObjectNode params = JsonHandler.createObjectNode();
        params.set("StartDate", new TextNode("2018-08-10T12:00:00.000Z"));

        accessModuleImpl.getAccessLog(params);
        verify(workspaceClient, times(2)).putObject(anyString(), anyString(), anyObject());
    }


    @Test
    @RunWithCustomExecutor
    public void givenInputEndDateBeforeFileDateThenCheckNbFilesPutInWorkspace() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        initMocksForAccessLog();

        ObjectNode params = JsonHandler.createObjectNode();
        params.set("EndDate", new TextNode("2018-08-10T12:00:00.000Z"));

        accessModuleImpl.getAccessLog(params);
        verify(workspaceClient, times(2)).putObject(anyString(), anyString(), anyObject());
    }
}
