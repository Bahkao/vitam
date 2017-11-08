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
package fr.gouv.vitam.ihmrecette.appserver.performance;

import fr.gouv.vitam.access.external.client.AdminExternalClient;
import fr.gouv.vitam.access.external.client.AdminExternalClientFactory;
import fr.gouv.vitam.access.external.client.VitamPoolingClient;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.logbook.LogbookOperation;
import fr.gouv.vitam.ihmdemo.core.UserInterfaceTransactionManager;
import fr.gouv.vitam.ingest.external.client.IngestExternalClient;
import fr.gouv.vitam.ingest.external.client.IngestExternalClientFactory;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static fr.gouv.vitam.common.model.ProcessAction.RESUME;
import static fr.gouv.vitam.logbook.common.parameters.Contexts.DEFAULT_WORKFLOW;

/**
 *
 */
public class PerformanceService {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(PerformanceService.class);
    public static final String DEFAULT_CONTRACT_NAME = "test_perf";
    public static final int NUMBER_OF_RETRY = 100;
    private final IngestExternalClientFactory ingestClientFactory;
    private final AdminExternalClientFactory adminClientFactory;

    private AtomicBoolean performanceTestInProgress = new AtomicBoolean(false);

    /**
     *
     */
    private final Path sipDirectory;

    /**
     *
     */
    private final Path performanceReportDirectory;

    /**
     * @param sipDirectory base sip directory
     * @param performanceReportDirectory base report directory
     */
    public PerformanceService(Path sipDirectory, Path performanceReportDirectory) {
        this(IngestExternalClientFactory.getInstance(), AdminExternalClientFactory.getInstance(), sipDirectory,
            performanceReportDirectory);
    }

    public PerformanceService(IngestExternalClientFactory ingestClientFactory,
        AdminExternalClientFactory adminClientFactory, Path sipDirectory,
        Path performanceReportDirectory) {
        this.sipDirectory = sipDirectory;
        this.ingestClientFactory = ingestClientFactory;
        this.adminClientFactory = adminClientFactory;
        this.performanceReportDirectory = performanceReportDirectory;
    }

    /**
     * indicate if a report is in progress
     *
     * @return boolean true/false
     */
    public boolean inProgress() {
        return performanceTestInProgress.get();
    }

    /**
     * @param model
     * @param fileName report name
     * @param tenantId tenant
     * @throws IOException
     */
    public void launchPerformanceTest(PerformanceModel model, String fileName, int tenantId) throws IOException {

        if (model.getParallelIngest() != null) {
            launchTestInParallel(model, fileName, tenantId);
            return;
        }
        if (model.getDelay() != null) {
            launchTestInSequence(model, fileName, tenantId);
        }
    }

    private void launchTestInSequence(PerformanceModel model, String fileName, int tenantId) throws IOException {
        try (ReportGenerator reportGenerator = new ReportGenerator(performanceReportDirectory.resolve(fileName));) {
            int numberOfRetry = model.getNumberOfRetry() == null ? NUMBER_OF_RETRY : model.getNumberOfRetry();

            performanceTestInProgress.set(true);

            Flowable.interval(0, model.getDelay(), TimeUnit.MILLISECONDS)
                .take(model.getNumberOfIngest())
                .map(i -> upload(model, tenantId))
                .flatMap(
                    operationId -> Flowable.just(operationId)
                        .observeOn(Schedulers.io())
                        .map(id -> waitEndOfIngest(tenantId, numberOfRetry, id)))
                .subscribe(operationId -> generateReport(reportGenerator, operationId, tenantId),
                    throwable -> {
                        LOGGER.error("end performance test with error", throwable);
                        performanceTestInProgress.set(false);
                    }, () -> {
                        try {
                            reportGenerator.close();
                            performanceTestInProgress.set(false);
                            LOGGER.info("end performance test");
                        } catch (IOException e) {
                            LOGGER.error("unable to close report", e);
                        }
                    });
        }
    }

    private void launchTestInParallel(PerformanceModel model, String fileName, int tenantId) throws IOException {
        ExecutorService launcherPerformanceExecutor = Executors.newFixedThreadPool(model.getParallelIngest());
        ExecutorService reportExecutor = Executors.newSingleThreadExecutor();

        LOGGER.info("start performance test");

        try (ReportGenerator reportGenerator = new ReportGenerator(performanceReportDirectory.resolve(fileName));) {

            performanceTestInProgress.set(true);

            List<CompletableFuture<Void>> collect = IntStream.range(0, model.getNumberOfIngest())
                .mapToObj(
                    i -> CompletableFuture.supplyAsync(() -> uploadSIP(model, tenantId), launcherPerformanceExecutor))
                .map(
                    future -> future.thenAcceptAsync((id) -> generateReport(reportGenerator, id, tenantId),
                        reportExecutor))
                .collect(Collectors.toList());

            CompletableFuture<List<Void>> allDone = sequence(collect);

            allDone.thenRun(() -> {
                try {
                    reportGenerator.close();
                    launcherPerformanceExecutor.shutdown();
                    reportExecutor.shutdown();
                    performanceTestInProgress.set(false);
                    LOGGER.info("end performance test");
                } catch (IOException e) {
                    LOGGER.error("unable to close report", e);
                }
            }).exceptionally((e) -> {
                LOGGER.error("end performance test with error", e);
                performanceTestInProgress.set(false);
                return null;
            });
        }
    }

    private void generateReport(ReportGenerator reportGenerator, String operationId, int tenantId) {
        try {
            LOGGER.debug("generate report");
            final RequestResponse<LogbookOperation> requestResponse =
                UserInterfaceTransactionManager.selectOperationbyId(operationId, tenantId, DEFAULT_CONTRACT_NAME,
                    getAppSessionId());

            if (requestResponse.isOk()) {
                RequestResponseOK<LogbookOperation> requestResponseOK =
                    (RequestResponseOK<LogbookOperation>) requestResponse;

                final LogbookOperation logbookOperation = requestResponseOK.getFirstResult();
                reportGenerator.generateReport(operationId, logbookOperation);
            }
        } catch (IOException | VitamException | ParseException e) {
            LOGGER.error("unable to generate report", e);
        }
    }

    private String uploadSIP(PerformanceModel model, Integer tenantId) {
        // TODO: client is it thread safe ?
        LOGGER.debug("launch unitary test");
        try (IngestExternalClient client = ingestClientFactory.getClient();
            AdminExternalClient adminClient = adminClientFactory.getClient();
            InputStream sipInputStream = Files.newInputStream(sipDirectory.resolve(model.getFileName()),
                StandardOpenOption.READ)) {

            RequestResponse<Void> response =
                client.ingest(new VitamContext(tenantId), sipInputStream, DEFAULT_WORKFLOW.name(), RESUME.name());

            final String operationId = response.getHeaderString(GlobalDataRest.X_REQUEST_ID);

            int numberOfRetry = model.getNumberOfRetry() == null ? NUMBER_OF_RETRY : model.getNumberOfRetry();
            final VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(adminClient);
            vitamPoolingClient
                .wait(tenantId, operationId, ProcessState.COMPLETED, numberOfRetry, 1000L, TimeUnit.MILLISECONDS);

            LOGGER.debug("finish unitary test");
            return operationId;
        } catch (final Exception e) {
            LOGGER.error("unable to upload sip", e);
            return null;
        }
    }

    private String waitEndOfIngest(int tenantId, int numberOfRetry, String operationId) {
        LOGGER.debug("wait end of ingest");
        try (AdminExternalClient adminClient = adminClientFactory.getClient();) {
            final VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(adminClient);
            vitamPoolingClient.wait(tenantId, operationId, ProcessState.COMPLETED, numberOfRetry, 1000L,
                TimeUnit.MILLISECONDS);
            LOGGER.debug("finish unitary test");
            return operationId;
        } catch (final Exception e) {
            LOGGER.error("unable to upload sip", e);
            return null;
        }
    }

    private String upload(PerformanceModel model, int tenantId) {
        LOGGER.debug("launch unitary ingest");

        try (InputStream sipInputStream = Files.newInputStream(sipDirectory.resolve(model.getFileName()),
            StandardOpenOption.READ); IngestExternalClient client = ingestClientFactory.getClient()) {

            RequestResponse<Void> response =
                client
                    .ingest(new VitamContext(tenantId), sipInputStream, DEFAULT_WORKFLOW.name(), RESUME.name());

            return response.getHeaderString(GlobalDataRest.X_REQUEST_ID);
        } catch (final Exception e) {
            LOGGER.error("unable to upload sip", e);
            return null;
        }
    }

    /**
     * transform a list of future on future of list
     *
     * @param futures list of futures
     * @param <T>
     * @return future of list
     */
    private <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allDoneFuture =
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        return allDoneFuture
            .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.<T>toList()));
    }

    /**
     * list all the sip
     *
     * @return list of Path
     * @throws IOException
     */
    public List<Path> listSipDirectory() throws IOException {
        List<Path> paths = new ArrayList<>();

        Files.walkFileTree(sipDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().toLowerCase().endsWith("zip")) {
                    paths.add(sipDirectory.relativize(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return paths;
    }

    /**
     * list all reports
     *
     * @return list of path
     * @throws IOException
     */
    public List<Path> listReportDirectory() throws IOException {
        return listDirectory(performanceReportDirectory);
    }

    public boolean sipExist(String sipPath) {
        return Files.exists(sipDirectory.resolve(sipPath));
    }

    /**
     * return an InputStream for a reportName
     *
     * @param reportName path of the report
     * @return InputStream
     * @throws IOException
     */
    public InputStream readReport(String reportName) throws IOException {
        return Files.newInputStream(performanceReportDirectory.resolve(reportName));
    }

    private List<Path> listDirectory(Path directory) throws IOException {
        return Files.list(directory)
            .collect(Collectors.toList());
    }

    private static String getAppSessionId() {
        return "MyApplicationId-ChangeIt";
    }
}
