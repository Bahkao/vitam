package fr.gouv.vitam.functional.administration.griffin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.VitamSession;
import fr.gouv.vitam.common.model.administration.GriffinModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.Griffin;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessReferential;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import org.apache.commons.lang3.ThreadUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static fr.gouv.vitam.common.guid.GUIDFactory.newGUID;
import static fr.gouv.vitam.common.guid.GUIDReader.getGUID;
import static fr.gouv.vitam.common.json.JsonHandler.getFromString;
import static fr.gouv.vitam.common.thread.VitamThreadUtils.getVitamSession;
import static fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections.GRIFFIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GriffinServiceTest {
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Mock private MongoDbAccessReferential mongoDbAccess;

    private GriffinService griffinService;

    @Mock private FunctionalBackupService functionalBackupService;

    @Mock private LogbookOperationsClientFactory logbookOperationsClientFactory;

    @Mock private LogbookOperationsClient logbookOperationsClient;

    @Before
    public void setUp() {
        griffinService = new GriffinService(mongoDbAccess, functionalBackupService, logbookOperationsClientFactory);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsClient);
        GUID guid = newGUID();

        getVitamSession().setTenantId(1);
        getVitamSession().setRequestId(guid);
    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedValidateGriffinWhenNameIsNull() throws Exception {
        //Given
        GriffinModel griffinModel = new GriffinModel(null, "id", "exName", "version");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedValidateGriffinWhenNameIsNullOrEmpty() throws Exception {
        //Given
        GriffinModel griffinModel = new GriffinModel(null, "id", "exName", "version");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

        //Given
        griffinModel.setName("");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedWhenImportTwoDuplicatedGriffinIdentifiers() throws Exception {
        //Given
        GriffinModel griffinModel1 = new GriffinModel("name", "id", "exName", "version");
        GriffinModel griffinModel2 = new GriffinModel("name", "id", "exName", "version");


        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Lists.newArrayList(griffinModel1, griffinModel2)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Duplicate griffin");

    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedValidateGriffinWhenIdentifierIsNullOrEmpty() throws Exception {
        //Given
        GriffinModel griffinModel = new GriffinModel("name", null, "exName", "version");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

        //Given
        griffinModel.setIdentifier("");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedValidateGriffinWhenExecutableNameIsNullOrEmpty() throws Exception {
        //Given
        GriffinModel griffinModel = new GriffinModel("name", "id", null, "version");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

        //Given
        griffinModel.setExecutableName("");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

    }

    @Test
    @RunWithCustomExecutor
    public void shouldFailedValidateGriffinWhenExecutableVersionIsNullOrEmpty() throws Exception {
        //Given
        GriffinModel griffinModel = new GriffinModel("name", "id", "exName", null);

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

        //Given
        griffinModel.setExecutableVersion("");

        // Then
        assertThatThrownBy(() -> griffinService.importGriffin(Collections.singletonList(griffinModel)))
            .isInstanceOf(ReferentialException.class).hasMessageContaining("Invalid griffin");

    }


    @Test
    @RunWithCustomExecutor
    public void givenGriffinsInDataBaseShouldCollectInsertUpdateAndDeleteList() throws Exception {

        //Given
        List<GriffinModel> allGriffinInDatabase = new ArrayList<>();
        String modelString1 = "{\"#id\":\"Id1\",\"Name\":\"1\",\"Identifier\":\"IDENTIFIER1\"}";
        String modelString2 = "{\"#id\":\"Id2\",\"Name\":\"2\",\"Identifier\":\"IDENTIFIER2\"}";
        String modelString3 = "{\"#id\":\"Id3\",\"Name\":\"3\",\"Identifier\":\"IDENTIFIER3\"}";

        allGriffinInDatabase.add(getFromString(modelString2, GriffinModel.class));
        allGriffinInDatabase.add(getFromString(modelString3, GriffinModel.class));

        List<GriffinModel> listToImport = new ArrayList<>();

        listToImport.add(getFromString(modelString1, GriffinModel.class));
        listToImport.add(getFromString(modelString2, GriffinModel.class));

        List<GriffinModel> listToInsert = new ArrayList<>();
        List<GriffinModel> listToUpdate = new ArrayList<>();
        List<String> listToDelete = new ArrayList<>();

        DbRequestResult dbRequestResult = mock(DbRequestResult.class);

        //When
        when(dbRequestResult.getDocuments(Griffin.class, GriffinModel.class)).thenReturn(allGriffinInDatabase);

        when(mongoDbAccess.findDocuments(any(JsonNode.class), eq(GRIFFIN))).thenReturn(dbRequestResult);

        griffinService.classifyDataInInsertUpdateOrDeleteLists(listToImport, listToInsert, listToUpdate, listToDelete,
            allGriffinInDatabase);

        //Then
        assertThat(listToDelete.size()).isEqualTo(1);
        assertThat(listToInsert.size()).isEqualTo(1);
        assertThat(listToUpdate.size()).isEqualTo(1);
    }

    @Test
    @RunWithCustomExecutor
    public void shouldImportGriffin() throws Exception {
        //Given
        List<GriffinModel> listToImport = JsonHandler.getFromFileAsTypeRefence(
            PropertiesUtils.getResourceFile("griffins.json"),
            new TypeReference<List<GriffinModel>>() {
            }
        );

        List<GriffinModel> allGriffinInDatabase = new ArrayList<>();

        DbRequestResult dbRequestResult = mock(DbRequestResult.class);

        //When
        when(dbRequestResult.getDocuments(Griffin.class, GriffinModel.class)).thenReturn(allGriffinInDatabase);

        when(mongoDbAccess.findDocuments(any(JsonNode.class), eq(GRIFFIN))).thenReturn(dbRequestResult);

        String requestId = getVitamSession().getRequestId();
        File griffinFile = PropertiesUtils.getResourceFile(
            "griffin_logbook_operation.json");
        JsonNode griffinOperation = JsonHandler.getFromFile(griffinFile);
        when(logbookOperationsClient.selectOperationById(requestId)).thenReturn(griffinOperation);

        RequestResponse<GriffinModel> requestResponse = griffinService.importGriffin(listToImport);
        ArgumentCaptor<LogbookOperationParameters> event1Captor = forClass(LogbookOperationParameters.class);
        ArgumentCaptor<LogbookOperationParameters> event2Captor = forClass(LogbookOperationParameters.class);

        //Then
        JsonNode result = JsonHandler.toJsonNode(requestResponse);
        int total = result.get("$hits").get("total").asInt();

        assertThat(total).isEqualTo(3);


        verify(logbookOperationsClient, times(1)).create(event1Captor.capture());
        verify(logbookOperationsClient, times(1)).update(event2Captor.capture());

        assertThat(event1Captor.getValue().getParameterValue(LogbookParameterName.outcomeDetail))
            .isEqualTo("IMPORT_GRIFFIN.STARTED");
        assertThat(event2Captor.getValue().getParameterValue(LogbookParameterName.outcomeDetail))
            .isEqualTo("IMPORT_GRIFFIN.OK");

        verify(functionalBackupService).saveCollectionAndSequence(getGUID(requestId), "STP_BACKUP_GRIFFIN", GRIFFIN,
            requestId);
    }

    @Test
    @RunWithCustomExecutor
    public void shouldGetGriffinById() throws Exception {
        //Given
        DbRequestResult dbRequestResult = new DbRequestResult();
        when(mongoDbAccess.findDocuments(any(), eq(GRIFFIN))).thenReturn(dbRequestResult);
        //When
        RequestResponse<GriffinModel> griffin = griffinService.findGriffin(getFromString("{}"));
        //Then
        assertThat(griffin).isNotNull();
    }
}
