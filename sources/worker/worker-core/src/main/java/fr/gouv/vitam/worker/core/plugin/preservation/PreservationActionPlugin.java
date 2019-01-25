/*
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
 */
package fr.gouv.vitam.worker.core.plugin.preservation;

import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.batch.report.model.PreservationReportModel;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientInternalException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.administration.ActionPreservation;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.worker.core.plugin.preservation.model.InputPreservation;
import fr.gouv.vitam.worker.core.plugin.preservation.model.OutputPreservation;
import fr.gouv.vitam.worker.core.plugin.preservation.model.ParametersPreservation;
import fr.gouv.vitam.worker.core.plugin.preservation.model.PreservationDistributionLine;
import fr.gouv.vitam.worker.core.plugin.preservation.model.ResultPreservation;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResult;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResult.OutputExtra;
import fr.gouv.vitam.worker.core.plugin.preservation.model.WorkflowBatchResults;
import fr.gouv.vitam.worker.core.plugin.preservation.service.PreservationReportService;
import fr.gouv.vitam.worker.core.utils.PluginHelper.EventDetails;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.IOUtils;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static fr.gouv.vitam.common.LocalDateUtil.now;
import static fr.gouv.vitam.common.accesslog.AccessLogUtils.getNoLogAccessLog;
import static fr.gouv.vitam.common.stream.StreamUtils.consumeAnyEntityAndClose;
import static fr.gouv.vitam.storage.engine.common.model.DataCategory.OBJECT;
import static fr.gouv.vitam.worker.core.plugin.PluginHelper.tryDeleteLocalPreservationFiles;
import static fr.gouv.vitam.worker.core.utils.PluginHelper.buildItemStatus;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;

public class PreservationActionPlugin extends ActionHandler {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(PreservationActionPlugin.class);

    private static final String INPUT_FILES = "input-files";
    public static final String OUTPUT_FILES = "output-files";
    public static final String DEFAULT_STORAGE_STRATEGY = "default";

    private static final String PLUGIN_NAME = "PRESERVATION_ACTION";
    private static final String PARAMETERS_JSON = "parameters.json";
    private static final String RESULT_JSON = "result.json";
    private static final String EXECUTABLE_FILE_NAME = "griffin";

    private final String griffinInputFolder;
    private final String execFolder;

    private final StorageClientFactory storageClientFactory;
    private final PreservationReportService reportService;

    public PreservationActionPlugin() {
        this(StorageClientFactory.getInstance(),
            new PreservationReportService(),
            VitamConfiguration.getVitamGriffinInputFilesFolder(),
            VitamConfiguration.getVitamGriffinExecFolder()
        );
    }

    @VisibleForTesting
    public PreservationActionPlugin(StorageClientFactory storage, PreservationReportService report, String inputFolder,
        String execFolder) {
        this.storageClientFactory = storage;
        this.reportService = report;
        this.griffinInputFolder = inputFolder;
        this.execFolder = execFolder;
    }

    @Override
    public List<ItemStatus> executeList(WorkerParameters workerParameters, HandlerIO handler)
        throws ProcessingException {
        List<PreservationDistributionLine> entries = IntStream.range(0, workerParameters.getObjectNameList().size())
            .mapToObj(index -> mapToParamsPreservationDistributionFile(workerParameters, index))
            .collect(Collectors.toList());

        String griffinId = entries.get(0).getGriffinId();
        String batchId = GUIDFactory.newGUID().getId();

        try {
            Path batchDirectory = createBatchDirectory(griffinId, batchId);

            copyInputFiles(batchDirectory, entries);
            createParametersBatchFile(entries, batchDirectory, workerParameters.getRequestId(), batchId);

            int timeout = entries.get(0).getTimeout();
            ResultPreservation result = launchGriffin(griffinId, batchDirectory, timeout);

            List<WorkflowBatchResult> workflowResults = generateWorkflowBatchResults(result, entries);

            handler.setCurrentObjectId(WorkflowBatchResults.NAME);
            handler.addOutputResult(0, new WorkflowBatchResults(batchDirectory, workflowResults));

            createReport(result, entries, VitamThreadUtils.getVitamSession().getTenantId());

            return workflowResults.stream()
                .map(w -> buildItemStatus(PLUGIN_NAME, w.getGlobalStatus(),
                    EventDetails.of(String.format("%s executed", PLUGIN_NAME))))
                .collect(Collectors.toList());
        } catch (Exception e) {
            tryDeleteLocalPreservationFiles(Paths.get(griffinInputFolder, griffinId, batchId));
            throw new ProcessingException(e);
        }
    }

    private PreservationDistributionLine mapToParamsPreservationDistributionFile(WorkerParameters workerParameters,
        int index) {
        PreservationDistributionLine preservationDistributionLine;
        try {
            preservationDistributionLine = JsonHandler
                .getFromJsonNode(workerParameters.getObjectMetadataList().get(index),
                    PreservationDistributionLine.class);
        } catch (InvalidParseOperationException e) {
            throw new VitamRuntimeException(e);
        }
        return preservationDistributionLine;
    }

    private Path createBatchDirectory(String griffinId, String batchId) throws IOException {
        Path griffinDirectory = Paths.get(griffinInputFolder, griffinId);
        if (!griffinDirectory.toFile().exists()) {
            Files.createDirectory(griffinDirectory);
        }
        return Files.createDirectory(griffinDirectory.resolve(batchId));
    }

    private void copyInputFiles(Path batchDirectory, List<PreservationDistributionLine> entries)
        throws IOException, VitamException {
        try (StorageClient storageClient = storageClientFactory.getClient()) {
            Path inputFilesDirectory = Files.createDirectory(batchDirectory.resolve(INPUT_FILES));
            for (PreservationDistributionLine entryParams : entries) {
                copyBinaryFile(entryParams, storageClient, inputFilesDirectory);
            }
        }
    }

    private void copyBinaryFile(PreservationDistributionLine entryParams, StorageClient storageClient,
        Path inputFilesDirectory) throws VitamException, IOException {
        final Response fileResponse = storageClient
            .getContainerAsync(DEFAULT_STORAGE_STRATEGY, entryParams.getObjectId(), OBJECT, getNoLogAccessLog());
        try (InputStream src = fileResponse.readEntity(InputStream.class)) {
            Path target = inputFilesDirectory.resolve(entryParams.getObjectId());
            Files.copy(src, target, REPLACE_EXISTING);
        } finally {
            consumeAnyEntityAndClose(fileResponse);
        }
    }

    private void createParametersBatchFile(List<PreservationDistributionLine> lines, Path batchDirectory,
        String requestId, String batchId) throws VitamException {
        List<InputPreservation> inputPreservations = lines.stream()
            .map(this::mapToInput)
            .collect(Collectors.toList());
        List<ActionPreservation> preservationActions = lines.get(0).getActionPreservationList();

        boolean debug = lines.get(0).isDebug();
        ParametersPreservation parametersPreservation =
            new ParametersPreservation(requestId, batchId, inputPreservations, preservationActions, debug);
        Path parametersPath = batchDirectory.resolve(PARAMETERS_JSON);
        JsonHandler.writeAsFile(parametersPreservation, parametersPath.toFile());
    }

    private InputPreservation mapToInput(PreservationDistributionLine entryParams) {
        return new InputPreservation(entryParams.getObjectId(), entryParams.getFormatId());
    }

    private ResultPreservation launchGriffin(String griffinId, Path batchDirectory, int timeout)
        throws VitamException, IOException, InterruptedException {
        Path griffinExecutable = Paths.get(execFolder, griffinId, EXECUTABLE_FILE_NAME);

        List<String> command = Arrays.asList(griffinExecutable.toString(), batchDirectory.toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        Process griffin = processBuilder.start();
        griffin.waitFor(timeout, MINUTES);

        if (griffin.exitValue() > 0) {
            LOGGER.error(
                "Griffin {} exited with value {}, stdErr: {}, stdOut: {}.",
                griffinId,
                griffin.exitValue(),
                IOUtils.toString(griffin.getErrorStream(), Charset.defaultCharset()),
                IOUtils.toString(griffin.getInputStream(), Charset.defaultCharset())
            );
        }

        return JsonHandler.getFromFile(batchDirectory.resolve(RESULT_JSON).toFile(), ResultPreservation.class);
    }

    private List<WorkflowBatchResult> generateWorkflowBatchResults(ResultPreservation result,
        List<PreservationDistributionLine> entries) {
        return entries.stream()
            .map(e -> mapToWorkflowBatchResult(e, result))
            .collect(Collectors.toList());
    }

    private WorkflowBatchResult mapToWorkflowBatchResult(PreservationDistributionLine e, ResultPreservation result) {
        List<OutputExtra> outputExtras = result.getOutputs()
            .get(e.getObjectId())
            .stream()
            .map(OutputExtra::of)
            .collect(Collectors.toList());
        return WorkflowBatchResult.of(e.getId(), e.getUnitId(), e.getTargetUse(), result.getRequestId(), outputExtras, e.getSourceUse());
    }

    private void createReport(ResultPreservation resultPreservation, List<PreservationDistributionLine> entries,
        int tenant)
        throws VitamClientInternalException {
        List<PreservationReportModel> reportModels = toReportModel(resultPreservation, entries, tenant, now());
        reportService.appendPreservationEntries(resultPreservation.getRequestId(), reportModels);
    }

    private List<PreservationReportModel> toReportModel(ResultPreservation outputs,
        List<PreservationDistributionLine> entries, int tenant, LocalDateTime now) {
        return outputs.getOutputs().entrySet()
            .stream()
            .flatMap(entry -> entry.getValue()
                .stream()
                .map(value -> getPreservationReportModel(outputs, tenant, now, value, entries))
            ).collect(Collectors.toList());
    }

    private PreservationReportModel getPreservationReportModel(ResultPreservation outputs, int tenant,
        LocalDateTime now, OutputPreservation value,
        List<PreservationDistributionLine> entries) {
        PreservationDistributionLine model =
            IterableUtils.find(entries, j -> j.getObjectId().equals(value.getInputPreservation().getName()));

        return new PreservationReportModel(
            outputs.getRequestId(),
            outputs.getId(),
            tenant,
            now.toString(),
            value.getStatus(),
            model.getUnitId(),
            model.getObjectId(),
            value.getAction(),
            value.getAnalyseResult(),
            value.getInputPreservation().getName(),
            value.getOutputName()
        );
    }
}
