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
package fr.gouv.vitam.functional.administration.context.core;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.VitamClientFactory;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.query.action.UpdateActionHelper;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.builder.request.single.Update;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.ContextModel;
import fr.gouv.vitam.common.model.administration.IngestContractModel;
import fr.gouv.vitam.common.model.administration.SecurityProfileModel;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.Context;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessFunctionalAdmin;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.context.api.ContextService;
import fr.gouv.vitam.functional.administration.contract.api.ContractService;
import fr.gouv.vitam.functional.administration.security.profile.core.SecurityProfileService;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.and;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.common.database.collections.VitamCollection.getMongoClientOptions;
import static fr.gouv.vitam.common.guid.GUIDFactory.newOperationLogbookGUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class ContextServiceImplTest {

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor());

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static final Integer TENANT_ID = 1;
    private static final Integer EXTERNAL_TENANT = 2;

    static final String PREFIX = GUIDFactory.newGUID().getId();

    @ClassRule
    public static MongoRule mongoRule =
        new MongoRule(getMongoClientOptions(Lists.newArrayList(Context.class)));

    private static VitamCounterService vitamCounterService;
    private static MongoDbAccessAdminImpl dbImpl;
    private static FunctionalBackupService functionalBackupService;
    private final static String HOST_NAME = "127.0.0.1";

    static ContextService contextService;

    static SecurityProfileService securityProfileService;
    static ContractService<IngestContractModel> ingestContractService;
    static ContractService<AccessContractModel> accessContractService;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {

        FunctionalAdminCollections.beforeTestClass(mongoRule.getMongoDatabase(), PREFIX,
            new ElasticsearchAccessFunctionalAdmin(ElasticsearchRule.VITAM_CLUSTER,
                Lists.newArrayList(new ElasticsearchNode("localhost", ElasticsearchRule.TCP_PORT))));

        final List<MongoDbNode> nodes = new ArrayList<>();
        nodes.add(new MongoDbNode("localhost", mongoRule.getDataBasePort()));

        dbImpl =
            MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, mongoRule.getMongoDatabase().getName()));

        final List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(HOST_NAME, ElasticsearchRule.TCP_PORT));
        ElasticsearchAccessAdminFactory.create(ElasticsearchRule.VITAM_CLUSTER, esNodes);

        final List tenants = new ArrayList<>();
        tenants.add(new Integer(TENANT_ID));
        tenants.add(new Integer(EXTERNAL_TENANT));
        VitamConfiguration.setTenants(tenants);
        VitamConfiguration.setAdminTenant(TENANT_ID);
        Map<Integer, List<String>> listEnableExternalIdentifiers = new HashMap<>();
        List<String> list_tenant = new ArrayList<>();
        list_tenant.add("CONTEXT");
        listEnableExternalIdentifiers.put(EXTERNAL_TENANT, list_tenant);

        vitamCounterService = new VitamCounterService(dbImpl, tenants, listEnableExternalIdentifiers);
        LogbookOperationsClientFactory.changeMode(null);

        securityProfileService = mock(SecurityProfileService.class);
        String securityProfileIdentifier = "SEC_PROFILE-000001";
        when(securityProfileService.findOneByIdentifier(securityProfileIdentifier)).thenReturn(
            Optional.of(new SecurityProfileModel("guid", securityProfileIdentifier, "SEC PROFILE", true,
                Collections.EMPTY_SET)));

        functionalBackupService = mock(FunctionalBackupService.class);
        ingestContractService = mock(ContractService.class);
        accessContractService = mock(ContractService.class);

        contextService =
            new ContextServiceImpl(MongoDbAccessAdminFactory
                .create(new DbConfigurationImpl(nodes, mongoRule.getMongoDatabase().getName())),
                vitamCounterService, ingestContractService, accessContractService, securityProfileService,
                functionalBackupService);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        contextService.close();
        FunctionalAdminCollections.afterTestClass(true);
        VitamClientFactory.resetConnections();
    }

    @After
    public void afterTest() {
        FunctionalAdminCollections.afterTest(
            Lists.newArrayList(FunctionalAdminCollections.CONTEXT, FunctionalAdminCollections.INGEST_CONTRACT));
        reset(ingestContractService);
        reset(functionalBackupService);
    }

    @Before
    public void setUp() throws Exception {
        VitamThreadUtils.getVitamSession().setRequestId(newOperationLogbookGUID(TENANT_ID));

    }

    @Test
    @RunWithCustomExecutor
    public void givenContextImportAndUpdateTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileIngest = PropertiesUtils.getResourceFile("referential_contracts_ok.json");
        final List<IngestContractModel> ingestContractModels =
            JsonHandler.getFromFileAsTypeRefence(fileIngest, new TypeReference<List<IngestContractModel>>() {
            });
        String INGEST_CONTRACT_ID = "IC-000001";
        when(ingestContractService.findByIdentifier(INGEST_CONTRACT_ID)).thenReturn(ingestContractModels.get(0));

        final File fileContexts = PropertiesUtils.getResourceFile("contexts_empty.json");
        final List<ContextModel> ModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContexts, new TypeReference<List<ContextModel>>() {
            });

        RequestResponse<ContextModel> response = contextService.createContexts(ModelList);
        assertThat(response.isOk()).isTrue();

        verifyZeroInteractions(ingestContractService);
        verifyZeroInteractions(accessContractService);

        verify(functionalBackupService).saveCollectionAndSequence(any(),
            Mockito.eq(ContextServiceImpl.CONTEXTS_BACKUP_EVENT), Mockito.eq(FunctionalAdminCollections.CONTEXT),
            any());
        verifyNoMoreInteractions(functionalBackupService);
        reset(functionalBackupService);

        final ObjectNode permissionsNode = JsonHandler.createObjectNode();
        final ObjectNode permissionNode = JsonHandler.createObjectNode();
        permissionNode.put("tenant", TENANT_ID);

        permissionNode.set("IngestContracts", JsonHandler.createArrayNode().add(INGEST_CONTRACT_ID));
        permissionNode.set("AccessContracts", JsonHandler.createArrayNode());
        permissionsNode.set("Permissions", JsonHandler.createArrayNode().add(permissionNode));
        final SetAction setPermission = UpdateActionHelper.set(permissionsNode);
        final Select select = new Select();
        select.setQuery(eq("Name", "My_Context_1"));
        final ContextModel context =
            contextService
                .findContexts(select.getFinalSelect())
                .getDocuments(Context.class, ContextModel.class).get(0);


        final Update update = new Update();
        update.addActions(setPermission);
        update.setQuery(and().add(eq("Permissions.tenant", 0))
            .add(eq("#id", context.getId())));

        JsonNode queryDslForUpdate = update.getFinalUpdate();
        final RequestResponse<ContextModel> updateResponse =
            contextService.updateContext(context.getIdentifier(), queryDslForUpdate);

        assertTrue(updateResponse.isOk());

        verify(functionalBackupService).saveCollectionAndSequence(any(),
            Mockito.eq(ContextServiceImpl.CONTEXTS_BACKUP_EVENT), Mockito.eq(FunctionalAdminCollections.CONTEXT),
            any());
        verifyNoMoreInteractions(functionalBackupService);
        reset(functionalBackupService);

        verify(ingestContractService).findByIdentifier(INGEST_CONTRACT_ID);
        reset(ingestContractService);

        String INVALID_INGEST_CONTRACT_IDENTIFIER = "IC-000001500000";
        permissionNode.set("IngestContracts", JsonHandler.createArrayNode().add(INVALID_INGEST_CONTRACT_IDENTIFIER));
        permissionsNode.set("Permissions", JsonHandler.createArrayNode().add(permissionNode));
        final SetAction setInvalidPermission = UpdateActionHelper.set(permissionsNode);
        update.getActions().clear();
        update.addActions(setInvalidPermission);
        final RequestResponse<ContextModel> updateError =
            contextService.updateContext(context.getIdentifier(), update.getFinalUpdate());
        assertFalse(updateError.isOk());

        verifyZeroInteractions(functionalBackupService);

        verify(ingestContractService).findByIdentifier(INVALID_INGEST_CONTRACT_IDENTIFIER);
        verifyZeroInteractions(ingestContractService);
    }


    @Test
    @RunWithCustomExecutor
    public void givenContextImportAndDeleteTest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        final File fileIngest = PropertiesUtils.getResourceFile("referential_contracts_ok.json");
        final List<IngestContractModel> ingestContractModels =
            JsonHandler.getFromFileAsTypeRefence(fileIngest, new TypeReference<List<IngestContractModel>>() {
            });
        String INGEST_CONTRACT_ID = "IC-000001";
        when(ingestContractService.findByIdentifier(INGEST_CONTRACT_ID)).thenReturn(ingestContractModels.get(0));

        final File fileContexts = PropertiesUtils.getResourceFile("contexts_empty.json");
        final List<ContextModel> ModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContexts, new TypeReference<List<ContextModel>>() {
            });

        RequestResponse<ContextModel> response = contextService.createContexts(ModelList);
        assertThat(response.isOk()).isTrue();

        final Select select = new Select();
        select.setQuery(eq("Name", "My_Context_1"));
        final ContextModel context =
            contextService
                .findContexts(select.getFinalSelect())
                .getDocuments(Context.class, ContextModel.class).get(0);

        reset(functionalBackupService);

        final RequestResponse<ContextModel> deleteError = contextService.deleteContext(context.getIdentifier(), false);
        assertTrue(deleteError.isOk());
        assertThat(deleteError.getHttpCode()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(functionalBackupService, times(1)).saveCollectionAndSequence(any(), any(), any(), any());
    }


    @Test
    @RunWithCustomExecutor
    public void givenContextImportedWithInvalidIngestContract() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        String INGEST_CONTRACT_ID = "NON-EXISTING-CONTRACT";
        when(ingestContractService.findByIdentifier(INGEST_CONTRACT_ID)).thenReturn(null);

        final File fileContexts = PropertiesUtils.getResourceFile("KO_contexts_invalid_contract.json");
        final List<ContextModel> ModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContexts, new TypeReference<List<ContextModel>>() {
            });

        RequestResponse<ContextModel> response = contextService.createContexts(ModelList);
        assertThat(response.isOk()).isFalse();

        verifyZeroInteractions(accessContractService);
        verifyZeroInteractions(functionalBackupService);
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestImportExternalIdentifier() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);

        final File fileContexts = PropertiesUtils.getResourceFile("contexts_empty_1.json");
        final List<ContextModel> ModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContexts, new TypeReference<List<ContextModel>>() {
            });

        RequestResponse<ContextModel> response = contextService.createContexts(ModelList);
        assertThat(response.isOk()).isTrue();

        verify(functionalBackupService).saveCollectionAndSequence(any(),
            Mockito.eq(ContextServiceImpl.CONTEXTS_BACKUP_EVENT), Mockito.eq(FunctionalAdminCollections.CONTEXT),
            any());
        verifyNoMoreInteractions(functionalBackupService);

        verifyZeroInteractions(ingestContractService);
    }

    @Test
    @RunWithCustomExecutor
    public void givenTestImportExternalIdentifier_KO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(EXTERNAL_TENANT);
        final File fileContexts = PropertiesUtils.getResourceFile("contexts_empty.json");
        final List<ContextModel> ModelList =
            JsonHandler.getFromFileAsTypeRefence(fileContexts, new TypeReference<List<ContextModel>>() {
            });

        RequestResponse<ContextModel> response = contextService.createContexts(ModelList);
        assertThat(response.isOk()).isFalse();

        verifyZeroInteractions(ingestContractService);
        verifyZeroInteractions(functionalBackupService);
    }
}
