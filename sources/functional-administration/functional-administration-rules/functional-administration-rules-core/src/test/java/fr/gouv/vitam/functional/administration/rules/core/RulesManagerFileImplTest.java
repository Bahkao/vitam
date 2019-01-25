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
package fr.gouv.vitam.functional.administration.rules.core;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.flipkart.zjsonpatch.JsonDiff;
import com.google.common.collect.Lists;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.elasticsearch.ElasticsearchRule;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.SchemaValidationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.FileRulesModel;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.ErrorReport;
import fr.gouv.vitam.functional.administration.common.FileRules;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.ReportConstants;
import fr.gouv.vitam.functional.administration.common.counter.SequenceType;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesCsvException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesDeleteException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesDurationException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesImportInProgressException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesUpdateException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.AdminManagementConfiguration;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessAdminFactory;
import fr.gouv.vitam.functional.administration.common.server.ElasticsearchAccessFunctionalAdmin;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Warning : To avoid error on import rules (actually we cannot update) and to be able to test each case, the tenant ID
 * is changed for each call.
 */
public class RulesManagerFileImplTest {

    private static final String PREFIX = GUIDFactory.newGUID().getId();

    @ClassRule
    public static MongoRule mongoRule =
        new MongoRule(VitamCollection.getMongoClientOptions());

    @ClassRule
    public static ElasticsearchRule elasticsearchRule = new ElasticsearchRule();

    private static final String ACCESS_RULE = "AccessRule";
    private static final String ACC_00003 = "ACC-00003";
    private static final String APPRAISAL_RULE = "AppraisalRule";
    private static final String FILE_TO_TEST_OK = "jeu_ok.csv";
    private static final String FILE_DURATION_EXCEED = "regle_test_duration.csv";
    private static final String FILE_TO_TEST_KO = "jeu_donnees_KO_regles_CSV_DuplicatedReference.csv";
    private static final String FILE_TO_TEST_KO_EMPTY_LINE = "jeu_donnees_KO_regles_CSV_empty_line.csv";
    private static final String FILE_TO_TEST_RULES_DURATION_KO = "jeu_donnees_KO_regles_CSV_test.csv";
    private static final String FILE_TO_COMPARE = "jeu_donnees_OK_regles_CSV.csv";
    private static final String FILE_UPDATE_RULE_TYPE = "jeu_donnees_OK_regles_CSV_update_ruleType.csv";

    private static final String FILE_UPDATE_RULE_DURATION = "jeu_donnees_OK_regles_CSV_update_ruleDuration.csv";
    private static final String FILE_UPDATE_RULE_DESC = "jeu_donnees_OK_regles_CSV_update_ruleDesc.csv";

    private static final String FILE_DELETE_RULE = "jeu_donnees_OK_regles_CSV_delete_rule.csv";
    private static final String FILE_TO_TEST_KO_INVALID_FORMAT = "jeu_donnees_KO_regles_CSV_invalid_format.csv";
    private static final String FILE_TO_TEST_KO_MISSING_COLUMN = "jeu_donnees_KO_regles_CSV_missing_column.csv";
    private static final String STP_IMPORT_RULES = "STP_IMPORT_RULES";
    private static final String USED_UPDATED_RULE_RESULT = "used_updated_rule_result.json";
    private static final String USED_DELETED_RULE_RESULT = "used_deleted_rule_result.json";
    private static final Integer TENANT_ID = 0;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final static String HOST_NAME = "127.0.0.1";
    private static VitamCounterService vitamCounterService;
    static RulesManagerFileImpl rulesFileManager;
    private static MongoDbAccessAdminImpl dbImpl;

    @Mock
    private LogbookOperationsClientFactory logbookOperationsClientFactory;



    @Mock
    private MetaDataClientFactory metaDataClientFactory;

    @Mock
    private FunctionalBackupService functionalBackupService;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static List<MongoDbNode> nodes = new ArrayList<>();

    public final ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        FunctionalAdminCollections.beforeTestClass(mongoRule.getMongoDatabase(), PREFIX,
            new ElasticsearchAccessFunctionalAdmin(ElasticsearchRule.VITAM_CLUSTER,
                Lists.newArrayList(new ElasticsearchNode("localhost", ElasticsearchRule.TCP_PORT))));

        File tempFolder = temporaryFolder.newFolder();
        System.setProperty("vitam.tmp.folder", tempFolder.getAbsolutePath());
        SystemPropertyUtil.refresh();

        final List<ElasticsearchNode> esNodes = new ArrayList<>();
        esNodes.add(new ElasticsearchNode(HOST_NAME, ElasticsearchRule.TCP_PORT));

        nodes.add(new MongoDbNode("localhost", mongoRule.getDataBasePort()));

        LogbookOperationsClientFactory.changeMode(null);
        dbImpl = create(new DbConfigurationImpl(nodes, mongoRule.getMongoDatabase().getName()));
        List<Integer> tenants = new ArrayList<>();
        Integer tenantsList[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        tenants.addAll(Arrays.asList(tenantsList));
        createRuleDurationConfigration(tenants);
        vitamCounterService = new VitamCounterService(dbImpl, tenants, null);

        ElasticsearchAccessAdminFactory.create(
            new AdminManagementConfiguration(nodes, mongoRule.getMongoDatabase().getName(),
                ElasticsearchRule.VITAM_CLUSTER, esNodes));

    }

    @Before
    public void setUp() {
        MongoDbAccessAdminImpl dbAccess = create(
            new DbConfigurationImpl(nodes, mongoRule.getMongoDatabase().getName()));

        rulesFileManager =
            new RulesManagerFileImpl(dbAccess,
                vitamCounterService, functionalBackupService, logbookOperationsClientFactory, metaDataClientFactory);

    }

    @AfterClass
    public static void tearDownAfterClass() throws IOException, VitamException {
        FunctionalAdminCollections.afterTestClass(true);
    }

    /**
     * Warning : To avoid error on import rules (actually we cannot update) and to be able to test each case, the tenant
     * ID is changed for each call.
     */
    @Test
    @RunWithCustomExecutor
    public void testimportRulesFile() throws Exception {
        int tenantId = 0;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));



        Map<Integer, List<ErrorReport>> errors = new HashMap<>();
        List<FileRulesModel> usedDeletedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdatedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdateRulesForUpdateUnit = new ArrayList<>();
        List<FileRulesModel> insertRules = new ArrayList<>();
        Set<String> notUsedDeletedRules = new HashSet<>();
        Set<String> notUsedUpdatedRules = new HashSet<>();
        try {
            rulesFileManager.checkFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
                errors, usedDeletedRules, usedUpdatedRules, usedUpdateRulesForUpdateUnit, insertRules,
                notUsedDeletedRules, notUsedUpdatedRules);
        } catch (final FileRulesException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        } catch (FileRulesDeleteException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        } catch (FileRulesUpdateException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        }

        try {
            rulesFileManager.checkFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_KO)),
                errors, usedDeletedRules, usedUpdatedRules, usedUpdateRulesForUpdateUnit, insertRules,
                notUsedDeletedRules, notUsedUpdatedRules);
            fail("Check file with FILE_TO_TEST_KO should throw exception");
        } catch (final FileRulesCsvException e) {
            exception.expect(FileRulesCsvException.class);
        } catch (FileRulesDeleteException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        } catch (FileRulesUpdateException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        }

        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)), FILE_TO_TEST_OK);

        Select selectTenant = new Select();
        selectTenant.setQuery(eq("#tenant", tenantId));
        List<FileRules> fileRules =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(selectTenant.getFinalSelect()));
        assertEquals(22, fileRules.size());

        final Select select = new Select();
        select.setQuery(eq("RuleId", "APP-00005"));
        final RequestResponseOK<FileRules> fileList = rulesFileManager.findDocuments(select.getFinalSelect());
        final String id = fileList.getResults().get(0).getString("RuleId");
        final FileRules file = rulesFileManager.findDocumentById(id);


        assertEquals(file.getRuleid(), fileList.getResults().get(0).getRuleid());

        assertThat(file.getVersion()).isEqualTo(1);
    }

    @Test
    @RunWithCustomExecutor
    public void testimportRulesFileWithEmptyLine() throws Exception {
        int tenantId = 12;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        Map<Integer, List<ErrorReport>> errors = new HashMap<>();
        List<FileRulesModel> usedDeletedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdatedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdateRulesForUpdateUnit = new ArrayList<>();
        List<FileRulesModel> insertRules = new ArrayList<>();
        Set<String> notUsedDeletedRules = new HashSet<>();
        Set<String> notUsedUpdatedRules = new HashSet<>();

        assertThatThrownBy(() -> rulesFileManager.checkFile(
            new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_KO_EMPTY_LINE)), errors, usedDeletedRules,
            usedUpdatedRules, usedUpdateRulesForUpdateUnit, insertRules, notUsedDeletedRules, notUsedUpdatedRules))
            .isInstanceOf(FileRulesCsvException.class)
            .hasMessageContaining("Index for header 'RuleType' is 1 but CSVRecord only has 1 values!");

    }

    @Test(expected = FileRulesDurationException.class)
    @RunWithCustomExecutor
    public void testImportRuleDurationExceed() throws Exception {
        int tenantId = 1;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        try {
            VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
            rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_DURATION_EXCEED)),
                FILE_DURATION_EXCEED);
        } catch (final FileRulesException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        } catch (FileRulesDeleteException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        } catch (FileRulesUpdateException e) {
            fail("Check file with FILE_TO_TEST_OK should not throw exception");
        }
    }

    /**
     * Warning : To avoid error on import rules (actually we cannot update) and to be able to test each case, the tenant
     * ID is changed for each call.
     */
    @Test
    @RunWithCustomExecutor
    public void testNoImportInProgess() throws Exception {
        int tenantId = 2;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);


        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);

        when(client.selectOperation(Matchers.anyObject())).thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)), FILE_TO_TEST_OK);

        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        when(client.selectOperation(Matchers.anyObject())).thenReturn(getEmptyJsonResponse());
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)), FILE_TO_TEST_OK);
    }

    /**
     * Warning : To avoid error on import rules (actually cannot update) and to be able to test each case, the tenant ID
     * is changed for each call.
     */
    @Test(expected = FileRulesImportInProgressException.class)
    @RunWithCustomExecutor
    public void testImportInProgess() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(3);
        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);
        when(client.selectOperation(Matchers.anyObject())).thenReturn(
            getJsonResult("COMMIT_RULES", 60));
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(TENANT_ID));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)), FILE_TO_TEST_OK);
    }

    private JsonNode getJsonResult(String evType, int tenantId) throws Exception {
        return JsonHandler.getFromString(String.format("{\n" +
            "     \"httpCode\": 200,\n" +
            "     \"$hits\": {\n" +
            "          \"total\": 1,\n" +
            "          \"offset\": 0,\n" +
            "          \"limit\": 1,\n" +
            "          \"size\": 1\n" +
            "     },\n" +
            "     \"$results\": [\n" +
            "          {\n" +
            "               \"_id\": \"aecaaaaaacgbcaacaa76eak44s3of6iaaaaq\",\n" +
            "               \"events\": [\n" +
            "                    {\n" +
            "                         \"evType\": \"%s\"\n" +
            "                    }\n" +
            "               ],\n" +
            "               \"_v\": 0,\n" +
            "               \"_tenant\": %d\n" +
            "          }\n" +
            "     ],\n" +
            "     \"$context\": {\n" +
            "          \"$query\": {\n" +
            "               \"$eq\": {\n" +
            "                    \"events.evType\": \"STP_IMPORT_RULES\"\n" +
            "               }\n" +
            "          },\n" +
            "          \"$filter\": {\n" +
            "               \"$limit\": 1,\n" +
            "               \"$orderby\": {\n" +
            "                    \"evDateTime\": -1\n" +
            "               }\n" +
            "          },\n" +
            "          \"$projection\": {\n" +
            "               \"$fields\": {\n" +
            "                    \"#id\": 1,\n" +
            "                    \"events.evType\": 1\n" +
            "               }\n" +
            "          }\n" +
            "     }\n" +
            "}", evType, tenantId));
    }

    private JsonNode getEmptyJsonResponse() throws Exception {
        return JsonHandler.getFromString("{\n" +
            "     \"httpCode\": 200,\n" +
            "     \"$hits\": {\n" +
            "          \"total\": 0,\n" +
            "          \"offset\": 0,\n" +
            "          \"limit\": 1,\n" +
            "          \"size\": 0\n" +
            "     },\n" +
            "     \"$results\": [],\n" +
            "     \"$context\": {\n" +
            "          \"$query\": {\n" +
            "               \"$eq\": {\n" +
            "                    \"events.evType\": \"STP_IMPORT_RULES\"\n" +
            "               }\n" +
            "          },\n" +
            "          \"$filter\": {\n" +
            "               \"$limit\": 1,\n" +
            "               \"$orderby\": {\n" +
            "                    \"evDateTime\": -1\n" +
            "               }\n" +
            "          },\n" +
            "          \"$projection\": {\n" +
            "               \"$fields\": {\n" +
            "                    \"#id\": 1,\n" +
            "                    \"events.outcome\": 1\n" +
            "               }\n" +
            "          }\n" +
            "     }\n" +
            "}");
    }

    @Test
    @RunWithCustomExecutor
    public void shouldInsertUpdateAndDeleteNewFileRules() throws Exception {
        int tenantId = 4;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final Select select = new Select();

        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);
        when(client.selectOperation(Matchers.anyObject())).thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());


        try {
            List<FileRules> fileRules = new ArrayList<>();
            try {
                select.setQuery(eq("#tenant", tenantId));
                fileRules = convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            } catch (ReferentialException e) {
            }
            if (fileRules.size() == 0) {
                VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
                rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
                    FILE_TO_TEST_OK);
            }
            List<FileRules> fileRulesAfterImport =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(22, fileRulesAfterImport.size());
            // FILE_TO_COMPARE => insert 1 rule, delete 1 rule, update 1 rule
            rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_COMPARE)),
                FILE_TO_COMPARE);
            List<FileRules> fileRulesAfterInsert =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(22, fileRulesAfterInsert.size());

        } catch (ReferentialException | InvalidParseOperationException | IOException |
            InvalidCreateOperationException e) {
            fail("ReferentialException " + e.getCause());
        }
    }


    @Test
    @RunWithCustomExecutor
    public void shouldUpdateRulesType() throws DatabaseException, ReferentialException, SchemaValidationException {
        int tenantId = 5;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        MongoDbAccessAdminImpl dbAccess = create(
            new DbConfigurationImpl(nodes, mongoRule.getMongoDatabase().getName()));
        dbAccess.deleteCollection(FunctionalAdminCollections.RULES);
        final Select select = new Select();
        try {
            LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
            when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
            when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
                .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

            MetaDataClient metaDataClient = mock(MetaDataClient.class);
            when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
            when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());

            List<FileRules> fileRules = new ArrayList<>();
            try {
                select.setQuery(eq("#tenant", tenantId));
                fileRules = convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            } catch (ReferentialException e) {
            }
            if (fileRules.size() == 0) {
                VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(TENANT_ID));
                rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
                    FILE_TO_TEST_OK);
            }
            List<FileRules> fileRulesAfterImport =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(22, fileRulesAfterImport.size());
            for (FileRules fileRulesAfter : fileRulesAfterImport) {
                if (ACC_00003.equals(fileRulesAfter.getRuleid())) {
                    assertEquals(ACCESS_RULE, fileRulesAfter.getRuletype());
                }
            }
            // FILE_TO_COMPARE => insert 1 rule, delete 1 rule, update 1 rule
            VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(TENANT_ID));
            rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_UPDATE_RULE_TYPE)),
                FILE_UPDATE_RULE_TYPE);
            List<FileRules> fileRulesAfterInsert =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(23, fileRulesAfterInsert.size());
            for (FileRules fileRulesUpdated : fileRulesAfterInsert) {
                if (ACC_00003.equals(fileRulesUpdated.getRuleid())) {
                    assertEquals(APPRAISAL_RULE, fileRulesUpdated.getRuletype());
                }
                if ("APP-00006".equals(fileRulesUpdated.getRuleid())) {
                    assertEquals(APPRAISAL_RULE, fileRulesUpdated.getRuletype());
                }
            }


        } catch (ReferentialException | InvalidParseOperationException | IOException |
            InvalidCreateOperationException | LogbookClientException e) {
            fail("ReferentialException " + e.getCause());
            e.printStackTrace();
        } catch (Exception e) {
            fail("Exception " + e.getCause());
            e.printStackTrace();
        }
    }

    @Test
    @RunWithCustomExecutor
    public void shouldDoNothingOnFileRulesReferential() throws Exception {
        int tenantId = 6;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);

        final Select select = new Select();

        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);
        when(client.selectOperation(Matchers.anyObject())).thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());

        try {
            select.setQuery(eq("#tenant", tenantId));
            List<FileRules> fileRules =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));

            if (fileRules.size() == 0) {
                VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
                rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
                    FILE_TO_TEST_OK);
            }
            List<FileRules> fileRulesAfterImport =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(22, fileRulesAfterImport.size());
            rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
                FILE_TO_TEST_OK);
            List<FileRules> fileRulesAfterInsert =
                convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
            assertEquals(22, fileRulesAfterInsert.size());
            assertEquals(fileRulesAfterInsert.stream().findAny().get().get(VitamFieldsHelper.version()),
                vitamCounterService.getSequence(tenantId, SequenceType.RULES_SEQUENCE));

        } catch (ReferentialException | InvalidParseOperationException | IOException |
            InvalidCreateOperationException e) {
            e.printStackTrace();
            fail("ReferentialException " + e.getCause());
        }
    }

    private List<FileRules> convertResponseResultToFileRules(RequestResponseOK<FileRules> response) {
        if (response != null) {
            return response.getResults();
        } else {
            return new ArrayList<>();
        }
    }

    @Test
    @RunWithCustomExecutor
    public void should_retrieve_FileRulesCsvException_when_csv_with_bad_format_is_upload() throws Exception {
        int tenantId = 7;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));

        // mock Storage

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());


        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);
        when(client.selectOperation(Matchers.anyObject())).thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        assertThatThrownBy(
            () -> rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_KO)),
                FILE_TO_TEST_KO)).isInstanceOf(FileRulesCsvException.class).hasMessageContaining("Invalid CSV File");

    }

    @Test
    @RunWithCustomExecutor
    public void should_not_duplicate_error_on_each_line_in_report_when_error_append()
        throws Exception {
        int tenantId = 8;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final InputStream inputStream =
            getInputStreamAndInitialiseMockWhenCheckRulesFile(FILE_TO_TEST_RULES_DURATION_KO);

        // Then
        final JsonNode errorReportAtJson = JsonHandler.getFromInputStream(inputStream);
        final JsonNode errorNode = errorReportAtJson.get("error");
        assertThat(errorNode.get("line 10").isArray());
        if (errorNode.get("line 10").isArray()) {

            final ArrayNode line10ArrayNode = (ArrayNode) errorNode.get("line 10");
            assertThat(line10ArrayNode.size()).isNotEqualTo(23);
            assertThat(line10ArrayNode.size()).isEqualTo(2);
            assertThat(line10ArrayNode.get(0).get("Code").asText())
                .contains("STP_IMPORT_RULES_RULEID_DUPLICATION.KO");
            assertThat(line10ArrayNode.get(0).get("Message").asText()).contains(
                "Il existe plusieurs fois le même RuleId. Ce RuleId doit être unique dans l'ensemble du référentiel");
            assertThat(line10ArrayNode.get(0).get("Information additionnelle").asText()).contains(
                "APP-00001");
        }
    }

    @Test
    @RunWithCustomExecutor
    public void should_contains_outMessg_in_error_report_when_csv_with_bad_format_is_upload()
        throws Exception {
        // Given
        // mock Storage
        final InputStream inputStream =
            getInputStreamAndInitialiseMockWhenCheckRulesFile(FILE_TO_TEST_RULES_DURATION_KO);
        // Then
        final JsonNode errorReportAtJson = JsonHandler.getFromInputStream(inputStream);
        final JsonNode errorNode = errorReportAtJson.get(ReportConstants.JDO_DISPLAY);

        assertThat(errorNode.get("outMessg").asText())
            .isEqualTo("Échec du processus d'import du référentiel des règles de gestion");
    }

    @Test
    @RunWithCustomExecutor
    public void should_contains_outMessg_in_error_report_when_csv_with_no_rule_type_is_upload()
        throws Exception {
        // Given
        // mock Storage
        final InputStream inputStream =
            getInputStreamAndInitialiseMockWhenCheckRulesFile(FILE_TO_TEST_KO_INVALID_FORMAT);
        // Then
        final JsonNode errorReportAtJson = JsonHandler.getFromInputStream(inputStream);
        final JsonNode errorNode = errorReportAtJson.get(ReportConstants.JDO_DISPLAY);

        assertThat(errorNode.get("outMessg").asText())
            .isEqualTo("Échec du processus d'import du référentiel des règles de gestion");
    }


    @Test
    @RunWithCustomExecutor
    public void should_contains_report_in_error_report_when_csv_invalid()
        throws Exception {
        // Given
        // mock Storage
        final InputStream inputStream =
            getInputStreamAndInitialiseMockWhenCheckRulesFile(FILE_TO_TEST_KO_MISSING_COLUMN);
        // Then
        final JsonNode errorReportAtJson = JsonHandler.getFromInputStream(inputStream);
        final JsonNode operationNode = errorReportAtJson.get(ReportConstants.JDO_DISPLAY);
        final JsonNode errorNode = errorReportAtJson.get(ReportConstants.ERROR);
        final ArrayNode line2ArrayNode = (ArrayNode) errorNode.get("line 2");

        assertThat(operationNode.get("outMessg").asText())
            .isEqualTo("Échec du processus d'import du référentiel des règles de gestion");

        assertThat(line2ArrayNode.get(0).get("Code").asText())
            .contains("STP_IMPORT_RULES_NOT_CSV_FORMAT.KO");

        assertThat(line2ArrayNode.get(0).get("Message").asText()).contains(
            "Le fichier importé n'est pas au format CSV");
    }

    @Test
    @RunWithCustomExecutor
    public void should_contains_outMessg_in_error_report_when_csv_with_missing_Column()
        throws Exception {
        // Given
        // mock Storage
        final InputStream inputStream =
            getInputStreamAndInitialiseMockWhenCheckRulesFile(FILE_TO_TEST_KO_MISSING_COLUMN);
        // Then
        final JsonNode errorReportAtJson = JsonHandler.getFromInputStream(inputStream);
        final JsonNode errorNode = errorReportAtJson.get(ReportConstants.JDO_DISPLAY);

        assertThat(errorNode.get("outMessg").asText())
            .isEqualTo("Échec du processus d'import du référentiel des règles de gestion");
    }

    private InputStream getInputStreamAndInitialiseMockWhenCheckRulesFile(String filename)
        throws MetaDataDocumentSizeException, MetaDataExecutionException, InvalidParseOperationException,
        MetaDataClientServerException, FileRulesException, VitamDBException {

        // mock logbook client
        LogbookOperationsClient client = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(client);

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());



        assertThatCode(() -> when(client.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, 0))).doesNotThrowAnyException();

        Map<Integer, List<ErrorReport>> errorsMap = new HashMap<>();
        List<FileRulesModel> usedDeletedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdatedRules = new ArrayList<>();
        List<FileRulesModel> usedUpdateRulesForUpdateUnit = new ArrayList<>();
        List<FileRulesModel> insertRules = new ArrayList<>();
        Set<String> notUsedDeletedRules = new HashSet<>();
        Set<String> notUsedUpdatedRules = new HashSet<>();


        // When
        assertThatThrownBy(() -> rulesFileManager
            .checkFile(new FileInputStream(PropertiesUtils.findFile(filename)), errorsMap,
                usedDeletedRules, usedUpdatedRules, usedUpdateRulesForUpdateUnit, insertRules, notUsedDeletedRules,
                notUsedUpdatedRules))
            .isInstanceOf(ReferentialException.class);

        return rulesFileManager.generateErrorReport(errorsMap, usedDeletedRules, usedUpdatedRules, StatusCode.KO,
            null);
    }



    @Test
    @RunWithCustomExecutor
    public void should_contains_evid_and_outMessg_in_report_when_csv_with_notUsedUpdatedRules_is_upload()
        throws Exception {
        int tenantId = 9;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final Select select = new Select();
        // given
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());



        File file = folder.newFolder();
        final Path report = Paths.get(file.getAbsolutePath(), "report.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(report);
        // when
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
            FILE_TO_TEST_OK);
        // then
        verify(functionalBackupService, times(1)).saveFile(any(InputStream.class), any(GUID.class), anyString(),
            eq(DataCategory.REPORT), anyString());
        final JsonNode fromFile = JsonHandler.getFromFile(report.toFile());
        final JsonNode jdoNode = fromFile.get(ReportConstants.JDO_DISPLAY);
        final String evId = jdoNode.get("evId").asText();
        final String outMessg = jdoNode.get("outMessg").asText();
        assertThat(evId).isNotEmpty();
        assertThat(outMessg)
            .contains("Succès du processus d'enregistrement de la copie du référentiel des règles de gestion");

        // when
        List<FileRules> fileRulesAfterImport =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(22, fileRulesAfterImport.size());

        for (FileRules fileRulesAfter : fileRulesAfterImport) {
            if (ACC_00003.equals(fileRulesAfter.getRuleid())) {
                assertEquals(ACCESS_RULE, fileRulesAfter.getRuletype());
            }
        }


        final Path reportAfterUpdate = Paths.get(file.getAbsolutePath(), "reportAfterUpdate.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(reportAfterUpdate);

        // FILE_TO_COMPARE => insert 1 rule, delete 1 rule, update 1 rule
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_UPDATE_RULE_TYPE)),
            FILE_UPDATE_RULE_TYPE);
        List<FileRules> fileRulesAfterInsert =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(23, fileRulesAfterInsert.size());

        final JsonNode reportAfterUpdateNode = JsonHandler.getFromFile(reportAfterUpdate.toFile());
        final JsonNode jdoAfterUpdateNode = reportAfterUpdateNode.get(ReportConstants.JDO_DISPLAY);
        final String evIdAfterUpdate = jdoAfterUpdateNode.get("evId").asText();
        final String outMessgAfterUpdate = jdoNode.get("outMessg").asText();
        assertThat(evIdAfterUpdate).isNotEmpty();
        assertThat(outMessgAfterUpdate)
            .contains("Succès du processus d'enregistrement de la copie du référentiel des règles de gestion");

        for (FileRules fileRulesUpdated : fileRulesAfterInsert) {
            if (ACC_00003.equals(fileRulesUpdated.getRuleid())) {
                assertEquals(APPRAISAL_RULE, fileRulesUpdated.getRuletype());
            }
            if ("APP-00006".equals(fileRulesUpdated.getRuleid())) {
                assertEquals(APPRAISAL_RULE, fileRulesUpdated.getRuletype());
            }
        }
    }

    @Test
    @RunWithCustomExecutor
    public void should_assert_consistency_of_report_when_update_Rule_linked_to_au_append() throws Exception {
        int tenantId = 10;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final Select select = new Select();
        // given
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any()))
            .thenReturn(JsonHandler.getFromFile(PropertiesUtils.findFile(USED_UPDATED_RULE_RESULT)));


        File file = folder.newFolder();
        final Path report = Paths.get(file.getAbsolutePath(), "report.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(report);
        // when
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
            FILE_TO_TEST_OK);
        // then
        verify(functionalBackupService, times(1)).saveFile(any(InputStream.class), any(GUID.class), anyString(),
            eq(DataCategory.REPORT), anyString());
        final JsonNode fromFile = JsonHandler.getFromFile(report.toFile());
        final JsonNode jdoNode = fromFile.get(ReportConstants.JDO_DISPLAY);
        final String evId = jdoNode.get("evId").asText();
        final String outMessg = jdoNode.get("outMessg").asText();
        assertThat(evId).isNotEmpty();
        assertThat(outMessg)
            .contains("Succès du processus d'enregistrement de la copie du référentiel des règles de gestion");


        // when
        List<FileRules> fileRulesAfterImport =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(22, fileRulesAfterImport.size());

        for (FileRules fileRulesAfter : fileRulesAfterImport) {
            if (ACC_00003.equals(fileRulesAfter.getRuleid())) {
                assertEquals(ACCESS_RULE, fileRulesAfter.getRuletype());
            }
        }

        final Path reportAfterUpdate = Paths.get(file.getAbsolutePath(), "reportAfterUpdate.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(reportAfterUpdate);

        // FILE_TO_COMPARE => insert 1 rule, delete 1 rule, update 1 rule
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_UPDATE_RULE_DURATION)),
            FILE_UPDATE_RULE_DURATION);
        List<FileRules> fileRulesAfterInsert =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(23, fileRulesAfterInsert.size());
        final JsonNode reportAfterUpdateNode = JsonHandler.getFromFile(reportAfterUpdate.toFile());
        final JsonNode jdoAfterUpdateNode = reportAfterUpdateNode.get(ReportConstants.JDO_DISPLAY);
        final String evIdAfterUpdate = jdoAfterUpdateNode.get("evId").asText();
        final String outMessgAfterUpdate = jdoAfterUpdateNode.get("outMessg").asText();
        assertThat(evIdAfterUpdate).isNotEmpty();
        assertThat(outMessgAfterUpdate)
            .contains("Avertissement lors du processus d'import du référentiel des règles de gestion");

        for (FileRules fileRulesUpdated : fileRulesAfterInsert) {
            if (ACC_00003.equals(fileRulesUpdated.getRuleid())) {
                assertEquals(ACCESS_RULE, fileRulesUpdated.getRuletype());
                assertEquals("26", fileRulesUpdated.getRuleduration());
            }
        }

        File file2 = folder.newFolder();
        final Path reportAfterUpdate2 = Paths.get(file2.getAbsolutePath(), "reportAfterUpdate.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(reportAfterUpdate2);

        // FILE_TO_COMPARE => update 1 rule, but only one descritption
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_UPDATE_RULE_DESC)),
            FILE_UPDATE_RULE_DESC);
        List<FileRules> fileRulesAfterInsert2 =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(23, fileRulesAfterInsert2.size());
        final JsonNode reportAfterUpdateNode2 = JsonHandler.getFromFile(reportAfterUpdate2.toFile());
        final JsonNode jdoAfterUpdateNode2 = reportAfterUpdateNode2.get(ReportConstants.JDO_DISPLAY);
        final String evIdAfterUpdate2 = jdoAfterUpdateNode2.get("evId").asText();
        final String outMessgAfterUpdate2 = jdoAfterUpdateNode2.get("outMessg").asText();
        assertThat(evIdAfterUpdate2).isNotEmpty();
        assertThat(outMessgAfterUpdate2)
            .contains("Succès du processus d'enregistrement de la copie du référentiel des règles de gestion");

        for (FileRules fileRulesUpdated : fileRulesAfterInsert2) {
            if (ACC_00003.equals(fileRulesUpdated.getRuleid())) {
                assertEquals(ACCESS_RULE, fileRulesUpdated.getRuletype());
                assertEquals("26", fileRulesUpdated.getRuleduration());
            }
        }

    }

    @Test
    @RunWithCustomExecutor
    public void should_throw_exception_when_csv_with_usedDeletedRuleIds_is_upload()
        throws Exception {
        int tenantId = 11;
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final Select select = new Select();
        // given

        // init data with first import
        LogbookOperationsClient logbookOperationsclient = mock(LogbookOperationsClient.class);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsclient);
        when(logbookOperationsclient.selectOperation(Matchers.anyObject()))
            .thenReturn(getJsonResult(STP_IMPORT_RULES, tenantId));

        MetaDataClient metaDataClient = mock(MetaDataClient.class);
        when(metaDataClientFactory.getClient()).thenReturn(metaDataClient);
        when(metaDataClient.selectUnits(any())).thenReturn(JsonHandler.createArrayNode());

        File file = folder.newFolder();
        final Path report = Paths.get(file.getAbsolutePath(), "report.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(report);

        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newOperationLogbookGUID(tenantId));
        rulesFileManager.importFile(new FileInputStream(PropertiesUtils.findFile(FILE_TO_TEST_OK)),
            FILE_TO_TEST_OK);

        List<FileRules> fileRulesAfterImport =
            convertResponseResultToFileRules(rulesFileManager.findDocuments(select.getFinalSelect()));
        assertEquals(22, fileRulesAfterImport.size());
        final Path reportAfterUpdate = Paths.get(file.getAbsolutePath(), "reportAfterUpdate.json");
        getInputStreamAndInitialiseMockWhenImportFileRules(reportAfterUpdate);

        // prepare for import ko with linked deleted rule
        when(metaDataClient.selectUnits(any()))
            .thenReturn(JsonHandler.getFromFile(PropertiesUtils.findFile(USED_DELETED_RULE_RESULT)));
        final ArgumentCaptor<LogbookOperationParameters> logOpParamsCaptor =
            ArgumentCaptor.forClass(LogbookOperationParameters.class);
        doNothing().when(logbookOperationsclient).update(logOpParamsCaptor.capture());

        // when
        assertThatThrownBy(() -> rulesFileManager
            .importFile(new FileInputStream(PropertiesUtils.findFile(FILE_DELETE_RULE)), FILE_DELETE_RULE))
            .isInstanceOf(FileRulesException.class);
        List<LogbookOperationParameters> logbooks = logOpParamsCaptor.getAllValues();
        assertThat(logbooks).hasSize(2);
        assertThat(logbooks.get(0).getTypeProcess()).isEqualTo(LogbookTypeProcess.MASTERDATA);
        assertThat(logbooks.get(0).getStatus()).isEqualTo(StatusCode.KO);
        assertThat(logbooks.get(0).getParameterValue(LogbookParameterName.eventType)).isEqualTo("CHECK_RULES");
        assertThat(logbooks.get(1).getTypeProcess()).isEqualTo(LogbookTypeProcess.MASTERDATA);
        assertThat(logbooks.get(1).getStatus()).isEqualTo(StatusCode.KO);
        assertThat(logbooks.get(1).getParameterValue(LogbookParameterName.eventType)).isEqualTo("STP_IMPORT_RULES");


    }

    private void getInputStreamAndInitialiseMockWhenImportFileRules(Path report)
        throws Exception {

        doAnswer(invocation -> {
            InputStream argumentAt = invocation.getArgumentAt(0, InputStream.class);
            Files.copy(argumentAt, report);
            return null;
        }).when(functionalBackupService).saveFile(any(InputStream.class), any(GUID.class), anyString(),
            eq(DataCategory.REPORT), anyString());
    }

    private static void createRuleDurationConfigration(List<Integer> tenants) {
        Map<Integer, Map<String, String>> durationList = new HashMap<>();
        Map<String, String> duration = new HashMap<>();
        duration.put(APPRAISAL_RULE, "6 day");
        duration.put(ACCESS_RULE, "5");
        for (Integer tenant : tenants) {

            durationList.put(tenant, duration);
        }
        new VitamRuleService(durationList);
    }

    @Test
    public void testJsonDiff() throws Exception {
        String s1 = "{\"a\":1,\"b\":2,\"c\":[{\"a\":1,\"b\":2},{\"a\":3,\"b\":4}]}";
        String s2 =
            "{\"a\":1,\"b\":2,\"c\":[{\"xa\":199,\"b\":20},{\"a\":1,\"b\":2}],\"e\":{\"alpha\":123,\"bravo\":1234}}";

        JsonNode json1 = JsonHandler.getFromString(s1);
        JsonNode json2 = JsonHandler.getFromString(s2);

        JsonNode target = JsonDiff.asJson(json1, json2);
        assertEquals(target.toString(),
            "[{\"op\":\"add\",\"path\":\"/c/0\",\"value\":{\"xa\":199,\"b\":20}},{\"op\":\"remove\",\"path\":\"/c/2\"},{\"op\":\"add\",\"path\":\"/e\",\"value\":{\"alpha\":123,\"bravo\":1234}}]");
        target = JsonDiff.asJson(json1, json1);
        assertEquals(target.toString(), "[]");

        assertTrue(
            rulesFileManager.checkRuleConformity(JsonHandler.createArrayNode(), JsonHandler.createArrayNode(), 0));
    }

}
