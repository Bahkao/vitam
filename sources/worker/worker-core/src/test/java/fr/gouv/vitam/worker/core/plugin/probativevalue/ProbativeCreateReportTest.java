/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
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
 */
package fr.gouv.vitam.worker.core.plugin.probativevalue;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.PreservationRequest;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.functional.administration.common.BackupService;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.distribution.JsonLineModel;
import fr.gouv.vitam.worker.core.plugin.preservation.TestHandlerIO;
import fr.gouv.vitam.worker.core.plugin.probativevalue.ProbativeCreateReport.ReportVersion2;
import fr.gouv.vitam.worker.core.plugin.probativevalue.pojo.ProbativeReportEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.gouv.vitam.common.model.StatusCode.FATAL;
import static fr.gouv.vitam.common.model.StatusCode.OK;
import static fr.gouv.vitam.storage.engine.common.model.DataCategory.REPORT;
import static fr.gouv.vitam.worker.core.plugin.evidence.EvidenceService.JSON;
import static fr.gouv.vitam.worker.core.plugin.preservation.TestWorkerParameter.TestWorkerParameterBuilder.workerParameterBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProbativeCreateReportTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Mock
    private BackupService backupService;

    @InjectMocks
    private ProbativeCreateReport probativeCreateReport;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @RunWithCustomExecutor
    public void should_backup_report() throws Exception {
        // Given
        String probativeEntryIdInDistributionFile = "BATMAN_GOT_ID";

        PreservationRequest request = new PreservationRequest();
        ProbativeReportEntry object = ProbativeReportEntry
            .koFrom(LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()), Collections.singletonList("unitId"),
                "groupId", "objectId", "usageVersion");

        TestHandlerIO handler = new TestHandlerIO();
        handler.transferFileToWorkspace("request", pojoToFile(request), false, false);
        handler.setInputStreamFromWorkspace(
            writeDistributionFile(new JsonLineModel(probativeEntryIdInDistributionFile, 0, null)));
        handler.transferFileToWorkspace(probativeEntryIdInDistributionFile, pojoToFile(object), false, false);
        handler.setNewLocalFile(tempFolder.newFile(GUIDFactory.newGUID().getId()));

        String containerName = handler.getContainerName();
        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        // When
        probativeCreateReport.execute(build, handler);

        // Then
        verify(backupService)
            .storeIntoOffers(eq(containerName), eq(containerName + JSON), eq(REPORT), eq(containerName + JSON),
                eq(VitamConfiguration.getDefaultStrategy()));
    }

    @Test
    @RunWithCustomExecutor
    public void should_store_report_atomically_when_report_not_exists_in_workspace() throws Exception {
        // Given
        String probativeEntryIdInDistributionFile = "BATMAN_GOT_ID";
        String containerName = "MyContainerName";

        HandlerIO handler = mock(HandlerIO.class);
        doReturn(false).when(handler).isExistingFileInWorkspace(containerName + JSON);
        doReturn(containerName).when(handler).getContainerName();
        doReturn(tempFolder.newFile(GUIDFactory.newGUID().getId())).when(handler).getNewLocalFile(anyString());

        PreservationRequest request = new PreservationRequest();
        doReturn(pojoToFile(request)).when(handler).getFileFromWorkspace("request");

        doReturn(writeDistributionFile(new JsonLineModel(probativeEntryIdInDistributionFile, 0, null)))
            .when(handler).getInputStreamFromWorkspace("distributionFile.jsonl");

        ProbativeReportEntry object = ProbativeReportEntry
            .koFrom(LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()), Collections.singletonList("unitId"),
                "groupId", "objectId", "usageVersion");
        doReturn(pojoToFile(object)).when(handler).getFileFromWorkspace(probativeEntryIdInDistributionFile);

        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        // When
        ItemStatus execute = probativeCreateReport.execute(build, handler);

        // Then
        assertThat(execute.getGlobalStatus()).isEqualTo(StatusCode.OK);
        verify(handler).transferAtomicFileToWorkspace(eq(containerName + JSON), any());
        verify(backupService)
            .storeIntoOffers(eq(containerName), eq(containerName + JSON), eq(REPORT), eq(containerName + JSON),
                eq(VitamConfiguration.getDefaultStrategy()));
    }

    @Test
    @RunWithCustomExecutor
    public void should_backup_existing_report_when_report_already_stored_in_workspace() throws Exception {
        // Given
        String containerName = "MyContainerName";

        HandlerIO handler = mock(HandlerIO.class);
        doReturn(true).when(handler).isExistingFileInWorkspace(containerName + JSON);
        doReturn(containerName).when(handler).getContainerName();

        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        // When
        probativeCreateReport.execute(build, handler);

        // Then
        verify(handler, never()).getFileFromWorkspace(any());
        verify(handler, never()).transferAtomicFileToWorkspace(anyString(), any());
        verify(backupService)
            .storeIntoOffers(eq(containerName), eq(containerName + JSON), eq(REPORT), eq(containerName + JSON),
                eq(VitamConfiguration.getDefaultStrategy()));
    }

    @Test
    @RunWithCustomExecutor
    public void should_return_item_status_OK() throws Exception {
        // Given
        String probativeEntryIdInDistributionFile = "BATMAN_GOT_ID";

        PreservationRequest request = new PreservationRequest();
        ProbativeReportEntry object = ProbativeReportEntry
            .koFrom(LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()), Collections.singletonList("unitId"),
                "groupId", "objectId", "usageVersion");

        TestHandlerIO handler = new TestHandlerIO();
        handler.transferFileToWorkspace("request", pojoToFile(request), false, false);
        handler.setInputStreamFromWorkspace(
            writeDistributionFile(new JsonLineModel(probativeEntryIdInDistributionFile, 0, null)));
        handler.transferFileToWorkspace(probativeEntryIdInDistributionFile, pojoToFile(object), false, false);
        handler.setNewLocalFile(tempFolder.newFile(GUIDFactory.newGUID().getId()));

        String containerName = handler.getContainerName();
        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        // When
        ItemStatus itemStatus = probativeCreateReport.execute(build, handler);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(OK);
        assertThat(itemStatus.getMasterData().get(LogbookParameterName.eventDetailData.name()))
            .isEqualTo(JsonHandler.unprettyPrint(new ReportVersion2()));
    }

    @Test
    @RunWithCustomExecutor
    public void should_return_item_status_FATAL_when_something_wrong() throws Exception {
        // Given
        String probativeEntryIdInDistributionFile = "BATMAN_GOT_ID";

        PreservationRequest request = new PreservationRequest();
        ProbativeReportEntry object = ProbativeReportEntry
            .koFrom(LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()), Collections.singletonList("unitId"),
                "groupId", "objectId", "usageVersion");

        TestHandlerIO handler = new TestHandlerIO();
        handler.transferFileToWorkspace("request", pojoToFile(request), false, false);
        handler.setInputStreamFromWorkspace(
            writeDistributionFile(new JsonLineModel(probativeEntryIdInDistributionFile, 0, null)));
        handler.transferFileToWorkspace(probativeEntryIdInDistributionFile, pojoToFile(object), false, false);
        handler.setNewLocalFile(tempFolder.newFile(GUIDFactory.newGUID().getId()));

        String containerName = handler.getContainerName();
        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        when(backupService.storeIntoOffers(anyString(), anyString(), any(), any(), anyString()))
            .thenThrow(new IllegalStateException());

        // When
        ItemStatus itemStatus = probativeCreateReport.execute(build, handler);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(FATAL);
    }

    @Test
    @RunWithCustomExecutor
    public void should_return_item_status_KO_when_cannot_deserialize_report_entry() throws Exception {
        // Given
        String probativeEntryIdInDistributionFile = "BATMAN_GOT_ID";

        PreservationRequest request = new PreservationRequest();

        Map<String, List<Integer>> notReportEntryObject = new HashMap<>();
        notReportEntryObject.put("unitId", Arrays.asList(1, 2, 3));
        notReportEntryObject.put("objectGroupId", Arrays.asList(1, 2, 3));

        TestHandlerIO handler = new TestHandlerIO();
        handler.transferFileToWorkspace("request", pojoToFile(request), false, false);
        handler.setInputStreamFromWorkspace(
            writeDistributionFile(new JsonLineModel(probativeEntryIdInDistributionFile, 0, null)));
        handler.transferFileToWorkspace(probativeEntryIdInDistributionFile, pojoToFile(notReportEntryObject), false,
            false);
        handler.setNewLocalFile(tempFolder.newFile(GUIDFactory.newGUID().getId()));

        String containerName = handler.getContainerName();
        WorkerParameters build = workerParameterBuilder()
            .withContainerName(containerName)
            .build();

        // When
        ItemStatus itemStatus = probativeCreateReport.execute(build, handler);

        // Then
        assertThat(itemStatus.getGlobalStatus()).isEqualTo(FATAL);
    }

    private File pojoToFile(Object object) throws IOException {
        File file = tempFolder.newFile();
        objectMapper.writeValue(file, object);
        return file;
    }

    private InputStream writeDistributionFile(JsonLineModel distribution) throws IOException {
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(distribution));
    }
}
