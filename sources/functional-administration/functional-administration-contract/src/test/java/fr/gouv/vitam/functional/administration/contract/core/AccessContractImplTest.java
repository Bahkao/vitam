/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 * <p>
 * contact.vitam@culture.gouv.fr
 * <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.functional.administration.contract.core;

import static fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections.AGENCIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.query.action.UpdateActionHelper;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.builder.request.single.Update;
import fr.gouv.vitam.common.database.parser.request.adapter.SingleVarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.AgenciesModel;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadFactory;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.AgenciesParser;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.contract.api.ContractService;
import fr.gouv.vitam.functional.administration.counter.VitamCounterService;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;


public class AccessContractImplTest {


    private static final String NEW_NAME = "New Name";

    private static final String NAME = "Name";

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor());

    private static final Integer TENANT_ID = 1;
    private static final Integer EXTERNAL_TENANT = 2;
    private static MongoDbAccessAdminImpl dbImpl;
    private static final SingleVarNameAdapter DEFAULT_VARNAME_ADAPTER = new SingleVarNameAdapter();

    static JunitHelper junitHelper;
    static final String COLLECTION_NAME = "AccessContract";
    static final String AGENCIES_COLLECTION_NAME = "Agencies";
    static final String DATABASE_HOST = "localhost";
    static final String DATABASE_NAME = "vitam-test";
    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoClient client;
    static MetaDataClient metaDataClientMock;
    static VitamCounterService vitamCounterService;


    static ContractService<AccessContractModel> accessContractService;
    static int mongoPort;


    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        final MongodStarter starter = MongodStarter.getDefaultInstance();
        junitHelper = JunitHelper.getInstance();
        mongoPort = junitHelper.findAvailablePort();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(mongoPort, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();
        client = new MongoClient(new ServerAddress(DATABASE_HOST, mongoPort));


        final List<MongoDbNode> nodes = new ArrayList<>();
        nodes.add(new MongoDbNode(DATABASE_HOST, mongoPort));

        dbImpl = MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, DATABASE_NAME));
        final List tenants = new ArrayList<>();
        tenants.add(new Integer(TENANT_ID));
        tenants.add(new Integer(EXTERNAL_TENANT));
        Map<Integer, List<String>> listEnableExternalIdentifiers = new HashMap<>();
        List<String> list_tenant = new ArrayList<>();
        list_tenant.add("ACCESS_CONTRACT");
        listEnableExternalIdentifiers.put(EXTERNAL_TENANT, list_tenant);
        vitamCounterService = new VitamCounterService(dbImpl, tenants, listEnableExternalIdentifiers);
        LogbookOperationsClientFactory.changeMode(null);

        metaDataClientMock = mock(MetaDataClient.class);

        accessContractService =
            new AccessContractImpl(MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, DATABASE_NAME)),
                vitamCounterService, metaDataClientMock);
        final File fileAgencies = PropertiesUtils.getResourceFile("agencies.csv");

        final Thread thread = VitamThreadFactory.getInstance().newThread(() -> {
            RequestResponse<AgenciesModel> response = null;
            try {
                VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

                insertDocuments(AgenciesParser.readFromCsv(new FileInputStream(fileAgencies)), TENANT_ID);

                VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
                insertDocuments(AgenciesParser.readFromCsv(new FileInputStream(fileAgencies)), EXTERNAL_TENANT);
            } catch (VitamException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        thread.start();
        thread.join();

    }



    private static void insertDocuments(List<AgenciesModel> agenciesToInsert, int tenant)
        throws InvalidParseOperationException, ReferentialException {

        ArrayNode agenciesNodeToPersist = JsonHandler.createArrayNode();

        for (final AgenciesModel agency : agenciesToInsert) {

            agency.setId(GUIDFactory.newEventGUID(tenant).getId());
            agency.setTenant(tenant);
            agenciesNodeToPersist.add(JsonHandler.toJsonNode(agency));
        }
        if (!agenciesToInsert.isEmpty()) {
            dbImpl.insertDocuments(agenciesNodeToPersist, AGENCIES, 0).close();
        }

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(mongoPort);
        client.close();
        accessContractService.close();
        client.getDatabase(DATABASE_NAME).getCollection(AGENCIES_COLLECTION_NAME);
    }


    @After
    public void afterTest() {
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        collection.deleteMany(new Document());
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestWellFormedContractThenImportSuccessfully() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isTrue();
        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);
        assertThat(responseCast.getResults().get(0).getIdentifier()).contains("AC-000");
        assertThat(responseCast.getResults().get(1).getIdentifier()).contains("AC-000");
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestMissingNameReturnBadRequest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_missingName.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isFalse();
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestDuplicateNames() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_duplicate.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isFalse();
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestNotAllowedNotNullIdInCreation() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");

        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);

        // Try to recreate the same contract but with id
        response = accessContractService.createContracts(responseCast.getResults());

        assertThat(response.isOk()).isFalse();
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestAlreadyExistsContract() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");

        List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);


        // unset ids
        accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isFalse();
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractTestFindByFakeID() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        // find accessContract with the fake id should return Status.OK

        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq("#id", "fakeid"));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();
        /*
         * String q = "{ \"$query\" : [ { \"$eq\" : { \"_id\" : \"fake_id\" } } ] }"; JsonNode queryDsl =
         * JsonHandler.getFromString(q);
         */
        final RequestResponseOK<AccessContractModel> accessContractModelList =
            accessContractService.findContracts(queryDsl);

        assertThat(accessContractModelList.getResults()).isEmpty();
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractTestUpdateAccessContractStatus() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final String documentName = "aName";
        final String inactiveStatus = "INACTIVE";
        final String activeStatus = "ACTIVE";
        // Create document
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");

        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);


        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq(NAME, documentName));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();
        final RequestResponseOK<AccessContractModel> accessContractModelList2 =
            accessContractService.findContracts(queryDsl);
        assertThat(accessContractModelList2.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : accessContractModelList2.getResults()) {
            assertThat(activeStatus.equals(accessContractModel.getStatus()));
        }

        // Test update for access contract Status => inactive
        final String now = LocalDateUtil.now().toString();
        final UpdateParserSingle updateParser = new UpdateParserSingle(new SingleVarNameAdapter());
        final SetAction setActionStatusInactive = UpdateActionHelper.set("Status", inactiveStatus);
        final SetAction setActionDesactivationDateInactive = UpdateActionHelper.set("DeactivationDate", now);
        final SetAction setActionLastUpdateInactive = UpdateActionHelper.set("LastUpdate", now);

        final Update update = new Update();
        update.setQuery(QueryHelper.eq(NAME, documentName));
        update.addActions(setActionStatusInactive, setActionDesactivationDateInactive, setActionLastUpdateInactive);
        updateParser.parse(update.getFinalUpdate());
        final JsonNode queryDslForUpdate = updateParser.getRequest().getFinalUpdate();


        RequestResponse<AccessContractModel> updateContractStatus =
            accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslForUpdate);
        assertThat(updateContractStatus).isNotExactlyInstanceOf(VitamError.class);

        final RequestResponseOK<AccessContractModel> accessContractModelListForassert =
            accessContractService.findContracts(queryDsl);
        assertThat(accessContractModelListForassert.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : accessContractModelListForassert.getResults()) {
            assertThat(inactiveStatus.equals(accessContractModel.getStatus())).isTrue();
            assertThat(activeStatus.equals(accessContractModel.getStatus())).isFalse();
            assertThat(accessContractModel.getDeactivationdate()).isNotEmpty();
            assertThat(accessContractModel.getLastupdate()).isNotEmpty();
        }

        ObjectNode versionNode = JsonHandler.createObjectNode();
        versionNode.set(AccessContractModel.DATA_OBJECT_VERSION, JsonHandler.createArrayNode().add("fjsdf"));
        final SetAction setActionUsage = UpdateActionHelper.set(versionNode);
        update.getActions().clear();
        update.addActions(setActionUsage);
        RequestResponse<AccessContractModel> updateContractStatus2 =
            accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslForUpdate);
        assertThat(updateContractStatus2.isOk()).isFalse();

        // Test update for access contract Status => Active
        final UpdateParserSingle updateParserActive = new UpdateParserSingle(new SingleVarNameAdapter());
        final SetAction setActionStatusActive = UpdateActionHelper.set("Status", activeStatus);
        final SetAction setActionDesactivationDateActive = UpdateActionHelper.set("ActivationDate", now);
        final SetAction setActionLastUpdateActive = UpdateActionHelper.set("LastUpdate", now);
        final Update updateStatusActive = new Update();
        updateStatusActive.setQuery(QueryHelper.eq(NAME, documentName));
        updateStatusActive.addActions(setActionStatusActive, setActionDesactivationDateActive,
            setActionLastUpdateActive);
        updateParserActive.parse(updateStatusActive.getFinalUpdate());
        final JsonNode queryDslStatusActive = updateParserActive.getRequest().getFinalUpdate();


        accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslStatusActive);


        final RequestResponseOK<AccessContractModel> accessContractModelListForassert2 =
            accessContractService.findContracts(queryDsl);
        assertThat(accessContractModelListForassert2.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : accessContractModelListForassert2.getResults()) {
            assertThat(inactiveStatus.equals(accessContractModel.getStatus())).isFalse();
            assertThat(activeStatus.equals(accessContractModel.getStatus())).isTrue();
            assertThat(accessContractModel.getActivationdate()).isNotEmpty();
            assertThat(accessContractModel.getLastupdate()).isNotEmpty();
        }
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractTestUpdateAccessContractOriginatingAgency() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final String documentName = "aName";
        final String inactiveStatus = "INACTIVE";
        final String activeStatus = "ACTIVE";

        // Create document
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");

        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);


        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq(NAME, documentName));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();
        responseCast = accessContractService.findContracts(queryDsl);
        assertThat(responseCast.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : responseCast.getResults()) {
            assertThat(activeStatus.equals(accessContractModel.getStatus()));
        }

        // Test update for access contract Status => inactive
        final String now = LocalDateUtil.now().toString();
        final UpdateParserSingle updateParser = new UpdateParserSingle(new SingleVarNameAdapter());
        final SetAction setActionStatusInactive = UpdateActionHelper.set("Status", inactiveStatus);
        final SetAction setActionName = UpdateActionHelper.set(NAME, NEW_NAME);
        final SetAction setActionDesactivationDateInactive = UpdateActionHelper.set("DeactivationDate", now);
        final SetAction setActionLastUpdateInactive = UpdateActionHelper.set("LastUpdate", now);
        final Update update = new Update();
        update.setQuery(QueryHelper.eq(NAME, documentName));
        update.addActions(setActionName, setActionStatusInactive, setActionDesactivationDateInactive,
            setActionLastUpdateInactive);
        updateParser.parse(update.getFinalUpdate());
        JsonNode queryDslForUpdate = updateParser.getRequest().getFinalUpdate();
        RequestResponse<AccessContractModel> updateContractStatus =
            accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslForUpdate);
        assertThat(updateContractStatus).isNotExactlyInstanceOf(VitamError.class);

        final Select newSelect = new Select();
        newSelect.setQuery(QueryHelper.eq(NAME, NEW_NAME));
        final RequestResponseOK<AccessContractModel> accessContractModelListForassert =
            accessContractService.findContracts(newSelect.getFinalSelect());
        assertThat(accessContractModelListForassert.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : accessContractModelListForassert.getResults()) {
            assertThat(inactiveStatus.equals(accessContractModel.getStatus())).isTrue();
            assertThat(activeStatus.equals(accessContractModel.getStatus())).isFalse();
            assertThat(accessContractModel.getEveryOriginatingAgency()).isFalse();
            assertThat(accessContractModel.getDeactivationdate()).isNotEmpty();
            assertThat(accessContractModel.getLastupdate()).isNotEmpty();
        }

        // Test update for access contract Status => Active
        final UpdateParserSingle updateParserActive = new UpdateParserSingle(new SingleVarNameAdapter());
        final SetAction setActionEveryOriginatingAgency = UpdateActionHelper.set("EveryOriginatingAgency", true);
        final SetAction setActionLastUpdateActive = UpdateActionHelper.set("LastUpdate", now);
        final Update updateStatusActive = new Update();
        updateStatusActive.setQuery(QueryHelper.eq(NAME, NEW_NAME));
        updateStatusActive.addActions(setActionEveryOriginatingAgency, setActionLastUpdateActive);
        updateParserActive.parse(updateStatusActive.getFinalUpdate());
        JsonNode queryDslStatusActive = updateParserActive.getRequest().getFinalUpdate();
        accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslStatusActive);

        final RequestResponseOK<AccessContractModel> accessContractModelListForassert2 =
            accessContractService.findContracts(newSelect.getFinalSelect());
        assertThat(accessContractModelListForassert2.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : accessContractModelListForassert2.getResults()) {
            assertThat(inactiveStatus.equals(accessContractModel.getStatus())).isTrue();
            assertThat(activeStatus.equals(accessContractModel.getStatus())).isFalse();
            assertThat(accessContractModel.getEveryOriginatingAgency()).isTrue();
            assertThat(accessContractModel.getActivationdate()).isNotEmpty();
            assertThat(accessContractModel.getLastupdate()).isNotEmpty();
        }
    }

    /**
     * Check that the created access conrtact have the tenant owner after persisted to database
     *
     * @throws Exception
     */
    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestTenantOwner() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);

        // We juste test the first contract
        final AccessContractModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        String id1 = acm.getIdentifier();
        assertThat(id1).isNotNull();


        final AccessContractModel one = accessContractService.findByIdentifier(id1);

        assertThat(one).isNotNull();

        assertThat(one.getName()).isEqualTo(acm.getName());

        assertThat(one.getTenant()).isNotNull();
        assertThat(one.getTenant()).isEqualTo(Integer.valueOf(TENANT_ID));

    }


    /**
     * Access contract of tenant 1, try to get the same contract with id mongo but with tenant 2 This sgould not return
     * the contract as tenant 2 is not the owner of the access contract
     *
     * @throws Exception
     */
    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestNotTenantOwner() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);

        // We juste test the first contract
        final AccessContractModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        final String id1 = acm.getId();
        assertThat(id1).isNotNull();


        VitamThreadUtils.getVitamSession().setTenantId(2);

        final AccessContractModel one = accessContractService.findByIdentifier(id1);

        assertThat(one).isNull();

    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestfindByIdentifier() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);

        // We juste test the first contract
        final AccessContractModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        String id1 = acm.getIdentifier();
        assertThat(id1).isNotNull();


        final AccessContractModel one = accessContractService.findByIdentifier(id1);

        assertThat(one).isNotNull();

        assertThat(one.getName()).isEqualTo(acm.getName());
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestImportExternalIdentifierKO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);
        assertThat(response.isOk()).isFalse();
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestImportExternalIdentifier() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok_Identifier.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);
        assertThat(response.isOk()).isTrue();
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestFindAllThenReturnEmpty() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final RequestResponseOK<AccessContractModel> accessContractModelList =
            accessContractService.findContracts(JsonHandler.createObjectNode());
        assertThat(accessContractModelList.getResults()).isEmpty();
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestFindAllThenReturnTwoContracts() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);

        final RequestResponseOK<AccessContractModel> accessContractModelListSearch =
            accessContractService.findContracts(JsonHandler.createObjectNode());
        assertThat(accessContractModelListSearch.getResults()).hasSize(2);
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestFindByName() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        final RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(2);


        final AccessContractModel acm = accessContractModelList.iterator().next();
        assertThat(acm).isNotNull();

        final String id1 = acm.getId();
        assertThat(id1).isNotNull();

        final String name = acm.getName();
        assertThat(name).isNotNull();


        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq(NAME, name));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();


        final RequestResponseOK<AccessContractModel> accessContractModelListFound =
            accessContractService.findContracts(queryDsl);
        assertThat(accessContractModelListFound.getResults()).hasSize(1);

        final AccessContractModel acmFound = accessContractModelListFound.getResults().iterator().next();
        assertThat(acmFound).isNotNull();


        assertThat(acmFound.getId()).isEqualTo(id1);
        assertThat(acmFound.getName()).isEqualTo(name);

    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestNotExistingRootUnits() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_not_exists_root_units.json");

        when(metaDataClientMock.selectUnits(anyObject())).thenReturn(new RequestResponseOK<>().toJsonNode());
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isFalse();
        assertThat(response.toString()).contains("RootUnits (GUID1,GUID2,GUID3) not found in database");
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestNotAllExistingRootUnits() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_not_exists_root_units.json");

        RequestResponseOK<JsonNode> res = new RequestResponseOK<>();
        res.addResult(JsonHandler.createObjectNode().put("#id", "GUID1"));
        res.addResult(JsonHandler.createObjectNode().put("#id", "GUID3"));

        when(metaDataClientMock.selectUnits(anyObject())).thenReturn(res.toJsonNode());

        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isFalse();
        assertThat(response.toString()).contains("RootUnits (GUID2) not found in database");
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestExistingRootUnitsOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_ok_root_units.json");


        RequestResponseOK<JsonNode> res = new RequestResponseOK<>();
        res.addResult(JsonHandler.createObjectNode().put("#id", "GUID1"));
        res.addResult(JsonHandler.createObjectNode().put("#id", "GUID2"));
        res.addResult(JsonHandler.createObjectNode().put("#id", "GUID3"));

        when(metaDataClientMock.selectUnits(anyObject())).thenReturn(res.toJsonNode());
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isTrue();
        assertThat(((RequestResponseOK) response).getResults()).hasSize(2);
        assertThat(((RequestResponseOK<AccessContractModel>) response).getResults().get(0).getName()).contains("aName");
        assertThat(((RequestResponseOK<AccessContractModel>) response).getResults().get(1).getName())
            .contains("aName1");
    }

    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestEmptyRootUnitsOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_empty_root_units.json");

        final List<AccessContractModel> accessContractModelList =
                JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
                });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        assertThat(response.isOk()).isTrue();
        assertThat(((RequestResponseOK) response).getResults()).hasSize(2);
        assertThat(((RequestResponseOK<AccessContractModel>) response).getResults().get(0).getName()).contains("aName");
        assertThat(((RequestResponseOK<AccessContractModel>) response).getResults().get(1).getName())
                .contains("aName1");
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractsTestOriginatingAgenciesNotExistsThenKO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_not_exists_agencies.json");
        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);
        assertThat(response.isOk()).isFalse();
    }


    @Test
    @RunWithCustomExecutor
    public void givenAccessContractTestUpdateAccessContractOriginatingAgencyNotExistsThenKO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final String documentName = "aName";
        final String inactiveStatus = "INACTIVE";
        final String activeStatus = "ACTIVE";

        // Create document
        final File fileContracts = PropertiesUtils.getResourceFile("contracts_access_no_agencies.json");

        final List<AccessContractModel> accessContractModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<AccessContractModel>>() {
            });
        final RequestResponse response = accessContractService.createContracts(accessContractModelList);

        RequestResponseOK<AccessContractModel> responseCast = (RequestResponseOK<AccessContractModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);


        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq(NAME, documentName));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();
        responseCast = accessContractService.findContracts(queryDsl);
        assertThat(responseCast.getResults()).isNotEmpty();
        for (final AccessContractModel accessContractModel : responseCast.getResults()) {
            assertThat(activeStatus.equals(accessContractModel.getStatus()));
        }

        // Test update existing originatingAgencies
        final String now = LocalDateUtil.now().toString();
        UpdateParserSingle updateParser = new UpdateParserSingle(new SingleVarNameAdapter());
        List agencies = new ArrayList();
        agencies.add("FR_ORG_AGEN");
        final SetAction setActionStatusInactive =
            UpdateActionHelper.set(AccessContractModel.ORIGINATING_AGENCIES, agencies);
        final SetAction setActionLastUpdateInactive = UpdateActionHelper.set("LastUpdate", now);
        Update update = new Update();
        update.setQuery(QueryHelper.eq(NAME, documentName));
        update.addActions(setActionStatusInactive, setActionLastUpdateInactive);
        updateParser.parse(update.getFinalUpdate());
        JsonNode queryDslForUpdate = updateParser.getRequest().getFinalUpdate();
        RequestResponse<AccessContractModel> updateContractStatus =
            accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(), queryDslForUpdate);
        assertThat(updateContractStatus.isOk()).isTrue();

        updateParser = new UpdateParserSingle(new SingleVarNameAdapter());
        agencies = new ArrayList();
        agencies.add("NotExistingOriginatingAgencies");
        update = new Update();
        update.setQuery(QueryHelper.eq(NAME, documentName));
        update.addActions(UpdateActionHelper.set(AccessContractModel.ORIGINATING_AGENCIES, agencies),
            UpdateActionHelper.set("LastUpdate", now));
        updateParser.parse(update.getFinalUpdate());
        updateContractStatus =
            accessContractService.updateContract(accessContractModelList.get(0).getIdentifier(),
                updateParser.getRequest().getFinalUpdate());
        assertThat(updateContractStatus.isOk()).isFalse();

    }

}
