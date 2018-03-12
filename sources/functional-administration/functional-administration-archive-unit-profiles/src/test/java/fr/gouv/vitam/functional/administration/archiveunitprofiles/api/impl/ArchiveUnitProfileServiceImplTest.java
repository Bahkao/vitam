/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019) <p> contact.vitam@culture.gouv.fr <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently. <p> This software is governed by the CeCILL 2.1 license under French law and
 * abiding by the rules of distribution of free software. You can use, modify and/ or redistribute the software under
 * the terms of the CeCILL 2.1 license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". <p> As a counterpart to the access to the source code and rights to copy, modify and
 * redistribute granted by the license, users are provided only with a limited warranty and the software's author, the
 * holder of the economic rights, and the successive licensors have only limited liability. <p> In this respect, the
 * user's attention is drawn to the risks associated with loading, using, modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software, that may mean that it is complicated to
 * manipulate, and that also therefore means that it is reserved for developers and experienced professionals having
 * in-depth computer knowledge. Users are therefore encouraged to load and test the software's suitability as regards
 * their requirements in conditions enabling the security of their systems and/or data to be ensured and, more
 * generally, to use and operate it in the same conditions as regards security. <p> The fact that you are presently
 * reading this means that you have had knowledge of the CeCILL 2.1 license and that you accept its terms.
 */

package fr.gouv.vitam.functional.administration.archiveunitprofiles.api.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.parser.request.adapter.SingleVarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileModel;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.archiveunitprofiles.api.ArchiveUnitProfileService;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;

public class ArchiveUnitProfileServiceImplTest {

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor());

    private static final Integer TENANT_ID = 1;
    private static final Integer EXTERNAL_TENANT = 2 ;

    static JunitHelper junitHelper;
    static final String COLLECTION_NAME = "ArchiveUnitProfile";
    static final String DATABASE_HOST = "localhost";
    static final String DATABASE_NAME = "vitam-test";
    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoClient client;
    private static VitamCounterService vitamCounterService;
    private static MongoDbAccessAdminImpl dbImpl;
    static Map<Integer, List<String>> externalIdentifiers;

    static ArchiveUnitProfileServiceImpl archiveUnitProfileService;
    static FunctionalBackupService functionalBackupService = Mockito.mock(FunctionalBackupService.class);
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
        Map <Integer,List<String>> listEnableExternalIdentifiers = new HashMap<>();
        List<String > list_tenant = new ArrayList<>();
        list_tenant.add("PROFILE");
        listEnableExternalIdentifiers.put(EXTERNAL_TENANT,list_tenant);

        vitamCounterService = new VitamCounterService(dbImpl, tenants, listEnableExternalIdentifiers);

        LogbookOperationsClientFactory.changeMode(null);

        archiveUnitProfileService =
            new ArchiveUnitProfileServiceImpl(MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, DATABASE_NAME)),
                vitamCounterService, functionalBackupService);

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(mongoPort);
        client.close();
        archiveUnitProfileService.close();
    }

    @After
    public void afterTest() {
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        collection.deleteMany(new Document());
        reset(functionalBackupService);
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestFindAllThenReturnEmpty() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final RequestResponseOK<ArchiveUnitProfileModel> profileModelList =
            archiveUnitProfileService.findArchiveUnitProfiles(JsonHandler.createObjectNode());
        assertThat(profileModelList.getResults()).isEmpty();
    }

    @Test
    @RunWithCustomExecutor
    public void givenWellFormedArchiveUnitProfileMetadataThenImportSuccessfully() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        assertThat(response.isOk()).isTrue();
        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);
    }

    @Test
    @RunWithCustomExecutor
    public void givenATestMissingIdentifierReturnBadRequest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_missing_identifier.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        assertThat(response.isOk()).isFalse();
        verifyZeroInteractions(functionalBackupService);
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestIdentifiers() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_duplicate_identifier.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        assertThat(response.isOk()).isFalse();
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestDuplicateNames() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_duplicate_name.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        assertThat(response.isOk()).isFalse();
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestNotAllowedNotNullIdInCreation() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");

        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        // Try to recreate the same profile but with id
        response = archiveUnitProfileService.createArchiveUnitProfiles(responseCast.getResults());

        assertThat(response.isOk()).isFalse();
    }


    @Test
    @RunWithCustomExecutor
    public void givenTestFindByFakeID() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        // find profile with the fake id should return Status.OK

        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq("#id", "fakeid"));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();
        /*
         * String q = "{ \"$query\" : [ { \"$eq\" : { \"_id\" : \"fake_id\" } } ] }"; JsonNode queryDsl =
         * JsonHandler.getFromString(q);
         */
        final RequestResponseOK<ArchiveUnitProfileModel> profileModelList = archiveUnitProfileService.findArchiveUnitProfiles(queryDsl);

        assertThat(profileModelList.getResults()).isEmpty();
    }

    /**
     * Check that the created access conrtact have the tenant owner after persisted to database
     *
     * @throws Exception
     */
    @Test
    @RunWithCustomExecutor
    public void givenTestTenantOwner() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        // We juste test the first profile
        final ArchiveUnitProfileModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        String id1 = acm.getIdentifier();
        assertThat(id1).isNotNull();

        ArchiveUnitProfileModel one = archiveUnitProfileService.findByIdentifier(id1);
        assertThat(one).isNotNull();
        assertThat(one.getName()).isEqualTo(acm.getName());
        assertThat(one.getTenant()).isNotNull();
        assertThat(one.getTenant()).isEqualTo(Integer.valueOf(TENANT_ID));
    }

    /**
     * Profile of tenant 1, try to get the same profile with id mongo but with tenant 2 This sgould not return the
     * profile as tenant 2 is not the owner of the profile
     *
     * @throws Exception
     */
    @Test
    @RunWithCustomExecutor
    public void givenTestNotTenantOwner() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        // We juste test the first profile
        final ArchiveUnitProfileModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        final String id1 = acm.getId();
        assertThat(id1).isNotNull();

        VitamThreadUtils.getVitamSession().setTenantId(2);
        final ArchiveUnitProfileModel one = archiveUnitProfileService.findByIdentifier(id1);
        assertThat(one).isNull();
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestImportExternalIdentifier_KO() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        // We juste test the first profile
        final ArchiveUnitProfileModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        String id1 = acm.getIdentifier();
        assertThat(id1).isNotNull();

        ArchiveUnitProfileModel one = archiveUnitProfileService.findByIdentifier(id1);

        assertThat(one).isNotNull();

        assertThat(one.getName()).isEqualTo(acm.getName());
    }
    @Test
    @RunWithCustomExecutor
    public void givenTestImportExternalIdentifier() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        // We juste test the first profile
        final ArchiveUnitProfileModel acm = responseCast.getResults().iterator().next();
        assertThat(acm).isNotNull();

        String id1 = acm.getIdentifier();
        assertThat(id1).isNotNull();


        ArchiveUnitProfileModel one = archiveUnitProfileService.findByIdentifier(id1);

        assertThat(one).isNotNull();

        assertThat(one.getName()).isEqualTo(acm.getName());
    }
    
    @Test
    @RunWithCustomExecutor
    public void givenTestFindAllThenReturnTwoProfiles() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast = (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        final RequestResponseOK<ArchiveUnitProfileModel> profileModelListSearch =
            archiveUnitProfileService.findArchiveUnitProfiles(JsonHandler.createObjectNode());
        assertThat(profileModelListSearch.getResults()).hasSize(1);
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestFindByIdentifier() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileMetadataProfile = PropertiesUtils.getResourceFile("AUP_ok_id.json");
        final List<ArchiveUnitProfileModel> profileModelList =
            JsonHandler.getFromFileAsTypeRefence(fileMetadataProfile, new TypeReference<List<ArchiveUnitProfileModel>>() {});
        final RequestResponse response = archiveUnitProfileService.createArchiveUnitProfiles(profileModelList);

        final RequestResponseOK<ArchiveUnitProfileModel> responseCast =
            (RequestResponseOK<ArchiveUnitProfileModel>) response;
        assertThat(responseCast.getResults()).hasSize(1);

        final ArchiveUnitProfileModel acm = profileModelList.iterator().next();
        assertThat(acm).isNotNull();

        final String id1 = acm.getId();
        assertThat(id1).isNotNull();

        final String identifier = acm.getIdentifier();
        assertThat(identifier).isNotNull();

        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        final Select select = new Select();
        parser.parse(select.getFinalSelect());
        parser.addCondition(QueryHelper.eq("Identifier", identifier));
        final JsonNode queryDsl = parser.getRequest().getFinalSelect();

        final RequestResponseOK<ArchiveUnitProfileModel> profileModelListFound =
            archiveUnitProfileService.findArchiveUnitProfiles(queryDsl);
        assertThat(profileModelListFound.getResults()).hasSize(1);

        final ArchiveUnitProfileModel acmFound = profileModelListFound.getResults().iterator().next();
        assertThat(acmFound).isNotNull();
        assertThat(acmFound.getIdentifier()).isEqualTo(identifier);
    }
}
