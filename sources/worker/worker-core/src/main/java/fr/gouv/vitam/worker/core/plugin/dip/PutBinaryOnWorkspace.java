/**
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
package fr.gouv.vitam.worker.core.plugin.dip;

import static java.lang.String.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.ws.rs.core.Response;

import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.accesslog.AccessLogUtils;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.accesslog.AccessLogInfoModel;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.exception.StorageNotFoundException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

/**
 * move a binary file from storage to workspace
 */
public class PutBinaryOnWorkspace extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(PutBinaryOnWorkspace.class);

    private static final String PUT_BINARY_ON_WORKSPACE = "PUT_BINARY_ON_WORKSPACE";
    private static final String DEFAULT_STORAGE_STRATEGY = "default";

    static final int GUID_TO_INFO_RANK = 0;
    public static final int NUMBER_OF_RETRY = 3;

    /**
     * factory of a storage client
     */
    private StorageClientFactory storageClientFactory;

    /**
     * empty constructor
     */
    public PutBinaryOnWorkspace() {
        this(StorageClientFactory.getInstance());
    }

    @VisibleForTesting PutBinaryOnWorkspace(StorageClientFactory storageClientFactory) {
        this.storageClientFactory = storageClientFactory;
    }

    /**
     * @param param   {@link WorkerParameters}
     * @param handler
     * @return
     * @throws ProcessingException
     * @throws ContentAddressableStorageServerException
     */
    @Override
    public ItemStatus execute(WorkerParameters param, HandlerIO handler)
        throws ProcessingException, ContentAddressableStorageServerException {

        final ItemStatus itemStatus = new ItemStatus(PUT_BINARY_ON_WORKSPACE);

        Map<String, Object> guidToPath;
        try (InputStream inputStream = new FileInputStream((File) handler.getInput(GUID_TO_INFO_RANK))) {

            guidToPath = JsonHandler.getMapFromInputStream(inputStream);

        } catch (IOException | InvalidParseOperationException e) {
            itemStatus.increment(StatusCode.FATAL);
            return new ItemStatus(PUT_BINARY_ON_WORKSPACE).setItemsStatus(PUT_BINARY_ON_WORKSPACE, itemStatus);
        }

        for (int i = 0; i < NUMBER_OF_RETRY; i++) {
            try {
                transferFile(param, handler, guidToPath);

                itemStatus.increment(StatusCode.OK);
                return new ItemStatus(PUT_BINARY_ON_WORKSPACE).setItemsStatus(PUT_BINARY_ON_WORKSPACE, itemStatus);
            } catch (StorageNotFoundException | StorageServerClientException | ProcessingException e) {
                LOGGER.error(format("unable to transfer file from offer to workspace, retry: %d", i), e);
            }
        }

        itemStatus.increment(StatusCode.FATAL);
        return new ItemStatus(PUT_BINARY_ON_WORKSPACE).setItemsStatus(PUT_BINARY_ON_WORKSPACE, itemStatus);
    }

    private void transferFile(WorkerParameters param, HandlerIO handler, Map<String, Object> guidToInfo)
        throws ProcessingException, StorageNotFoundException, StorageServerClientException {
        try (StorageClient storageClient = storageClientFactory.getClient()) {

            Map objectInfo = (Map) guidToInfo.get(param.getObjectName());

            Boolean mustLog = Boolean.valueOf(param.getMapParameters().get(WorkerParameterName.mustLogAccessOnObject));
            AccessLogInfoModel logInfo = AccessLogUtils.getInfoFromWorkerInfo(objectInfo, VitamThreadUtils.getVitamSession(), mustLog);

            Response response = storageClient
                .getContainerAsync(DEFAULT_STORAGE_STRATEGY, param.getObjectName(), DataCategory.OBJECT, logInfo);

            handler.transferInputStreamToWorkspace((String) objectInfo.get("FILE_NAME"),
                (InputStream) response.getEntity(), null, false);
        }
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {

    }
}
