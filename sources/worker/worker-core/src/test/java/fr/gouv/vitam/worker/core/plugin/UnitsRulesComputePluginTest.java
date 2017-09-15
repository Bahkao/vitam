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

package fr.gouv.vitam.worker.core.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.model.processing.UriPrefix;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.FileRules;
import fr.gouv.vitam.functional.administration.common.RuleMeasurementEnum;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.core.impl.HandlerIOImpl;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.net.ssl.*")
@PrepareForTest({WorkspaceClientFactory.class, AdminManagementClientFactory.class})
public class UnitsRulesComputePluginTest {


    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());


    UnitsRulesComputePlugin plugin = new UnitsRulesComputePlugin();

    private WorkspaceClient workspaceClient;
    private AdminManagementClientFactory adminManagementClientFactory;
    private AdminManagementClient adminManagementClient;

    private static final String ARCHIVE_UNIT_RULE = "unitsRulesComputePlugin/AU_COMPUTE_ENDDATE_SAMPLE.json";
    private static final String ARCHIVE_UNIT_RULE_MGT_ONLY =
        "unitsRulesComputePlugin/AU_COMPUTE_ENDDATE_SAMPLE_MANAGEMENT_ONLY.json";
    private static final String ARBO_MD_RG_COMPLEXE_ROOT = "unitsRulesComputePlugin/ARBO_MD_RG_COMPLEXE_ROOT.json";
    private static final String ARBO_MD_NON_EXISTING_RULE = "unitsRulesComputePlugin/ARBO_MD_NON_EXISTING_RULE.json";
    private static final String AU_SIP_MGT_MD_OK1 = "unitsRulesComputePlugin/AU_SIP_MGT_MD_OK1.json";
    private final static String FAKE_URL = "http://localhost:1111";
    private InputStream input;
    private JsonNode archiveUnit;
    private List<IOParameter> in;
    private HandlerIOImpl action;
    private static final int UNIT_INPUT_RANK = 0;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private Integer tenantId = 0;

    @Before
    public void setUp() throws Exception {
        File tempFolder = temporaryFolder.newFolder();
        System.setProperty("vitam.tmp.folder", tempFolder.getAbsolutePath());

        SystemPropertyUtil.refresh();

        PowerMockito.mockStatic(AdminManagementClientFactory.class);
        adminManagementClientFactory = mock(AdminManagementClientFactory.class);

        workspaceClient = mock(WorkspaceClient.class);

        adminManagementClient = mock(AdminManagementClient.class);

        action = new HandlerIOImpl(workspaceClient, GUIDFactory.newGUID().toString(), GUIDFactory.newGUID().toString());

        when(AdminManagementClientFactory.getInstance()).thenReturn(adminManagementClientFactory);
        when(adminManagementClientFactory.getClient()).thenReturn(adminManagementClient);

        input = PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_RULE);
        archiveUnit = JsonHandler.getFromInputStream(input);
        
        in = new ArrayList<>();
        in.add(new IOParameter()
            .setUri(new ProcessingUri(UriPrefix.MEMORY, "unitId")));
        action.addInIOParameters(in);
    }


    @After
    public void tearDown() throws IOException {
        if (input != null) {
            input.close();
        }
        action.partialClose();
    }

    @RunWithCustomExecutor
    @Test
    public void givenWorkspaceExistWhenExecuteThenReturnResponseOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        
        action.getInput().clear();
        action.getInput().add(archiveUnit);
        
        when(adminManagementClient.getRuleByID("ID100")).thenReturn(getRulesInReferential("ID100", "StorageRule"));
        when(adminManagementClient.getRuleByID("ID101"))
            .thenReturn(getRulesInReferential("ID101", "ClassificationRule"));
        when(adminManagementClient.getRuleByID("ID102")).thenReturn(getRulesInReferential("ID102", "AccessRule"));

        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferential());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        // check objectName file updated
        JsonNode storageRule0 = archiveUnit.get("ArchiveUnit").get("Management").get("StorageRule").get("Rules").get(0);
        assertNotNull(storageRule0);
        assertNotNull(storageRule0.get("EndDate"));
        assertEquals("2016-04-10", storageRule0.get("EndDate").asText());
    }

    @Test
    public void givenWorkspaceExistWhenExecuteThenReturnResponseKO() throws Exception {

        action.getInput().clear();
        action.getInput().add(archiveUnit);
        
        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferentialPartial());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);

    }

    @Test
    public void givenWorkspaceExistAndEmptyRulesButManagementRulesWhenExecuteThenReturnResponseOK() throws Exception {
        
        JsonNode archiveUnit_MGT_only = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(ARCHIVE_UNIT_RULE_MGT_ONLY));
        action.getInput().clear();
        action.getInput().add(archiveUnit_MGT_only);
        
        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferential());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        // check objectName file updated
        JsonNode management = archiveUnit_MGT_only.get("ArchiveUnit").get("Management");
        assertNotNull(management);
        assertNull(management.get("StorageRule"));
    }

    @RunWithCustomExecutor
    @Test
    public void givenWorkspaceArchiveUnitFileExistWhenExecuteThenReturnResponseOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
 
        action.getInput().clear();
        action.getInput().add(archiveUnit);
        
        when(adminManagementClient.getRuleByID("ID100")).thenReturn(getRulesInReferential("ID100", "StorageRule"));
        when(adminManagementClient.getRuleByID("ID101"))
            .thenReturn(getRulesInReferential("ID101", "ClassificationRule"));
        when(adminManagementClient.getRuleByID("ID102")).thenReturn(getRulesInReferential("ID102", "AccessRule"));

        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferential());
        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL).setUrlMetadata(FAKE_URL)
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName.json").setCurrentStep("currentStep").setContainerName("containerName");
        when(workspaceClient.getObject(anyObject(), anyObject()))
            .thenReturn(Response.status(Status.OK).entity(archiveUnit).build());
        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        // check objectName file updated
        JsonNode storageRule0 = archiveUnit.get("ArchiveUnit").get("Management").get("StorageRule").get("Rules").get(0);
        assertNotNull(storageRule0);
        assertNotNull(storageRule0.get("EndDate"));
        assertEquals("2016-04-10", storageRule0.get("EndDate").asText());
    }

    @Test
    public void givenArboMdRgComplexeROOTWhenExecuteThenReturnResponseOK() throws Exception {
        reset(adminManagementClient);

        JsonNode archiveUnit_ARBO_MD_RG_COMPLEXE = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(ARBO_MD_RG_COMPLEXE_ROOT));
        action.getInput().clear();
        action.getInput().add(archiveUnit_ARBO_MD_RG_COMPLEXE);
        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferentialForArboMdRgComplexe());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        // check objectName file updated
        JsonNode accessRule0 = archiveUnit_ARBO_MD_RG_COMPLEXE.get("ArchiveUnit").get("Management").get("AccessRule").get("Rules").get(0);
        assertNotNull(accessRule0);
        assertNotNull(accessRule0.get("EndDate"));
        assertEquals("2120-01-01", accessRule0.get("EndDate").asText());
    }

    @Test
    public void givenNonExistingRuleWhenExecuteThenReturnResponseKO() throws Exception {
        reset(adminManagementClient);

        JsonNode archiveUnit_ARBO_MD_RG_COMPLEXE = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(ARBO_MD_NON_EXISTING_RULE));
        action.getInput().clear();
        action.getInput().add(archiveUnit_ARBO_MD_RG_COMPLEXE);
        
        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferentialForNonExistingRule());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);

    }



    @RunWithCustomExecutor
    @Test
    public void givenWrongRuleTypeWhenExecuteThenReturnResponseKO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        action.getInput().clear();
        action.getInput().add(archiveUnit);
        
        when(adminManagementClient.getRuleByID("ID100")).thenReturn(getRulesInReferential("ID100", "StorageRule"));
        when(adminManagementClient.getRuleByID("ID101"))
            .thenReturn(getRulesInReferential("ID101", "ClassificationRule"));
        // Should be AccessRule
        when(adminManagementClient.getRuleByID("ID102")).thenReturn(getRulesInReferential("ID102", "StorageRule"));

        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferential());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.KO);
    }

    @RunWithCustomExecutor
    @Test
    public void givenArchiveUnitMgtMdOk1WhenExecuteThenReturnResponseOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        reset(adminManagementClient);
        
        JsonNode archiveUnit_MGT_MD = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(AU_SIP_MGT_MD_OK1));
        action.getInput().clear();
        action.getInput().add(archiveUnit_MGT_MD);
        saveWorkspacePutObject("Units/objectName");

        when(adminManagementClient.getRuleByID("ID100")).thenReturn(getRulesInReferential("ID100", "StorageRule"));
        when(adminManagementClient.getRuleByID("ID101"))
            .thenReturn(getRulesInReferential("ID101", "DisseminationRule"));
        when(adminManagementClient.getRuleByID("ID102")).thenReturn(getRulesInReferential("ID102", "ReuseRule"));
        when(adminManagementClient.getRuleByID("ID103"))
            .thenReturn(getRulesInReferential("ID103", "ClassificationRule"));

        when(adminManagementClient.getRules(anyObject())).thenReturn(getRulesInReferentialForAuMgtMdOk());

        final WorkerParameters params =
            WorkerParametersFactory.newWorkerParameters().setUrlWorkspace(FAKE_URL)
                .setUrlMetadata("http://localhost:8083")
                .setObjectNameList(Lists.newArrayList("objectName"))
                .setObjectName("objectName").setCurrentStep("currentStep").setContainerName("containerName");

        final ItemStatus response = plugin.execute(params, action);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        // check objectName file updated
        JsonNode storageRule1 = archiveUnit_MGT_MD.get("ArchiveUnit").get("Management").get("StorageRule").get("Rules").get(1);
        assertNotNull(storageRule1);
        assertNotNull(storageRule1.get("EndDate"));
        assertEquals("2016-04-10", storageRule1.get("EndDate").asText());
    }

    private void saveWorkspacePutObject(String filename) throws ContentAddressableStorageServerException {
        doAnswer(invocation -> {
            InputStream inputStream = invocation.getArgumentAt(2, InputStream.class);
            java.nio.file.Path file =
                java.nio.file.Paths.get(System.getProperty("vitam.tmp.folder") + "/" + action.getContainerName() + "_" +
                    action.getWorkerId() + "/" + filename.replaceAll("/", "_"));
            java.nio.file.Files.copy(inputStream, file);
            return null;
        }).when(workspaceClient).putObject(org.mockito.Matchers.anyString(),
            org.mockito.Matchers.eq(filename), org.mockito.Matchers.any(InputStream.class));
    }

    private JsonNode getRulesInReferential(String ruleId, String ruleType) {

        final ArrayNode root = JsonHandler.createArrayNode();
        root.add(createRule(ruleId, ruleType, "3", RuleMeasurementEnum.MONTH.getType()));
        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root);
        return rule;
    }

    private JsonNode getRulesInReferential() {

        final ArrayNode root = JsonHandler.createArrayNode();
        root.add(createRule("ID420", "StorageRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID470", "ClassificationRule", "2", RuleMeasurementEnum.DAY.getType()));
        root.add(createRule("ID019", "AccessRule", "3", RuleMeasurementEnum.MONTH.getType()));

        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root);
        return rule;
    }

    private JsonNode getRulesInReferentialForAuMgtMdOk() {

        final ArrayNode root = JsonHandler.createArrayNode();
        root.add(createRule("ID017", "StorageRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID018", "AppraisalRule", "2", RuleMeasurementEnum.DAY.getType()));
        root.add(createRule("ID019", "AccessRule", "1", RuleMeasurementEnum.YEAR.getType()));
        root.add(createRule("ID020", "DisseminationRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID022", "ReuseRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID024", "ClassificationRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID420", "StorageRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID450", "DisseminationRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID460", "ReuseRule", "3", RuleMeasurementEnum.MONTH.getType()));
        root.add(createRule("ID470", "ClassificationRule", "3", RuleMeasurementEnum.MONTH.getType()));

        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root);
        return rule;
    }

    private JsonNode getRulesInReferentialForArboMdRgComplexe() {

        final ArrayNode root = JsonHandler.createArrayNode();
        root.add(createRule("R1", "StorageRule", "1", RuleMeasurementEnum.YEAR.getType()));
        root.add(createRule("R6", "StorageRule", "6", RuleMeasurementEnum.YEAR.getType()));
        root.add(createRule("ACC-00035", "AccessRule", "100", RuleMeasurementEnum.YEAR.getType()));

        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root);
        return rule;
    }

    private JsonNode getRulesInReferentialForNonExistingRule() {

        final ArrayNode root = JsonHandler.createArrayNode();
        root.add(createRule("R1", "StorageRule", "1", RuleMeasurementEnum.YEAR.getType()));

        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root);
        return rule;
    }

    private JsonNode getRulesInReferentialPartial() {
        final ObjectNode accessRule = JsonHandler.createObjectNode();
        accessRule.put(FileRules.RULEID, "ID470");
        accessRule.put(FileRules.RULEDESCRIPTION, "rule content");
        accessRule.put(FileRules.RULEDURATION, "2");
        accessRule.put(FileRules.RULEMEASUREMENT, RuleMeasurementEnum.DAY.getType());


        final ObjectNode reuseRule2 = JsonHandler.createObjectNode();
        reuseRule2.put(FileRules.RULEID, "ID019");
        reuseRule2.put(FileRules.RULEDESCRIPTION, "rule description");
        reuseRule2.put(FileRules.RULEDURATION, "3");
        reuseRule2.put(FileRules.RULEMEASUREMENT, RuleMeasurementEnum.MONTH.getType());

        final ArrayNode root1 = JsonHandler.createArrayNode();
        root1.add(accessRule);
        root1.add(reuseRule2);

        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.set("$results", root1);
        return rule;
    }

    private ObjectNode createRule(String ruleId, String ruleType, String ruleDuration, String ruleMeasurement) {
        final ObjectNode rule = JsonHandler.createObjectNode();
        rule.put(FileRules.RULEID, ruleId);
        rule.put(FileRules.RULEDESCRIPTION, "rule description");
        rule.put(FileRules.RULETYPE, ruleType);
        rule.put(FileRules.RULEDURATION, ruleDuration);
        rule.put(FileRules.RULEMEASUREMENT, ruleMeasurement);
        return rule;
    }
}
