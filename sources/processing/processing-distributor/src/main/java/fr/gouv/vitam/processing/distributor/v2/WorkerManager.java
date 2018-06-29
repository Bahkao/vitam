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

package fr.gouv.vitam.processing.distributor.v2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingBadRequestException;
import fr.gouv.vitam.processing.common.exception.WorkerAlreadyExistsException;
import fr.gouv.vitam.processing.common.exception.WorkerFamilyNotFoundException;
import fr.gouv.vitam.processing.common.model.WorkerBean;
import fr.gouv.vitam.processing.distributor.api.IWorkerManager;

/**
 * WorkerManager class contains methods to manage workers
 */
public class WorkerManager implements IWorkerManager {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(WorkerManager.class);

    /**
     * Default queue size
     */
    public static final int QUEUE_SIZE = 15;
    private ConcurrentMap<String, WorkerFamilyManager> workersFamily;


    /**
     * Constructor
     */
    public WorkerManager() {
        workersFamily = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized void marshallToDB() {
        if (!WORKER_DB_FILE.exists()) {
            try {
                if (Files.notExists(Paths.get(VitamConfiguration.getVitamDataFolder()))) {
                    Files.createDirectories(Paths.get(VitamConfiguration.getVitamDataFolder()));
                }
                Files.createFile(WORKER_DB_FILE.toPath());

            } catch (IOException e) {
                LOGGER.warn("Cannot create worker list serialization file : " + WORKER_DB_FILE.getName(), e);
            }
        }
        List<WorkerBean> registeredWorkers = new ArrayList<>();

        for (Map.Entry<String, WorkerFamilyManager> family : workersFamily.entrySet()) {
            for (Map.Entry<String, WorkerExecutor> worker : family.getValue().getWorkers().entrySet()) {
                WorkerBean workerBean = worker.getValue().getWorkerBean();
                registeredWorkers.add(workerBean);
            }
        }

        try {
            JsonHandler.writeAsFile(registeredWorkers, WORKER_DB_FILE);
        } catch (InvalidParseOperationException e) {
            LOGGER.error("Cannot update database worker", e);
        }
    }

    @Override
    public void registerWorker(String familyId, String workerId, String workerInformation)
        throws ProcessingBadRequestException {

        ParametersChecker.checkParameter("familyId is a mandatory argument", familyId);
        ParametersChecker.checkParameter("workerId is a mandatory argument", workerId);
        ParametersChecker.checkParameter("workerInformation is a mandatory argument", workerInformation);
        WorkerBean worker = null;
        try {
            worker = JsonHandler.getFromString(workerInformation, WorkerBean.class);
            if (!worker.getFamily().equals(familyId)) {
                throw new ProcessingBadRequestException("Cannot register a worker of another family!");
            } else {
                worker.setWorkerId(workerId);
            }
            registerWorker(worker);

        } catch (final InvalidParseOperationException e) {
            LOGGER.error("Worker Information incorrect", e);
            throw new ProcessingBadRequestException("Worker description is incorrect");
        }

    }

    @Override
    public void registerWorker(WorkerBean workerBean) {
        workersFamily.putIfAbsent(workerBean.getFamily(), new WorkerFamilyManager(QUEUE_SIZE));
        workersFamily.compute(workerBean.getFamily(), (key, workerManager) -> {
            workerManager.registerWorker(workerBean);
            return workerManager;
        });
        marshallToDB();
    }

    @Override
    public void unregisterWorker(String workerFamily, String worker) throws WorkerFamilyNotFoundException {
        final WorkerFamilyManager workerManager = workersFamily.get(workerFamily);
        if (workerManager == null) {
            throw new WorkerFamilyNotFoundException("Worker : " + worker + " not found in the family :" + workerFamily);
        }
        workerManager.unregisterWorker(worker);

        marshallToDB();
    }

    @Override
    public WorkerFamilyManager findWorkerBy(String workerFamily) {
        return workersFamily.get(workerFamily);
    }
}
