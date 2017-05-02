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

package fr.gouv.vitam.processing.data.core.management;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ProcessExecutionStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.processing.common.exception.ProcessingStorageWorkspaceException;
import fr.gouv.vitam.processing.common.model.ProcessWorkflow;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

/**
 * Workspace implemenation for workflows datas management
 */
public class WorkspaceProcessDataManagement implements ProcessDataManagement {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(WorkspaceProcessDataManagement.class);

    private static final ProcessDataManagement INSTANCE = new WorkspaceProcessDataManagement();

    private WorkspaceProcessDataManagement() {
        // Nothing
    }

    /**
     * Get the current instance
     *
     * @return the current instance
     */
    public static ProcessDataManagement getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean createProcessContainer() throws ProcessingStorageWorkspaceException {
        if (isProcessContainerExist()) {
            return false;
        }
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            client.createContainer(PROCESS_CONTAINER);
            return true;
        } catch (ContentAddressableStorageAlreadyExistException | ContentAddressableStorageServerException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public boolean isProcessContainerExist() throws ProcessingStorageWorkspaceException {
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            return client.isExistingContainer(PROCESS_CONTAINER);
        } catch (ContentAddressableStorageServerException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public boolean createFolder(String folderName) throws ProcessingStorageWorkspaceException {
        if (isFolderExist(folderName)) {
            return false;
        }
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            client.createFolder(PROCESS_CONTAINER, folderName);
            return true;
        } catch (ContentAddressableStorageAlreadyExistException | ContentAddressableStorageServerException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public boolean isFolderExist(String folderName) throws ProcessingStorageWorkspaceException {
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            return client.isExistingFolder(PROCESS_CONTAINER, folderName);
        } catch (ContentAddressableStorageServerException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public boolean removeFolder(String folderName) throws ProcessingStorageWorkspaceException {
        if (!isFolderExist(folderName)) {
            return false;
        }
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            client.deleteFolder(PROCESS_CONTAINER, folderName);
            return true;
        } catch (ContentAddressableStorageServerException | ContentAddressableStorageNotFoundException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public void persistProcessWorkflow(String folderName, String asyncId, ProcessWorkflow processWorkflow)
        throws ProcessingStorageWorkspaceException, InvalidParseOperationException {
        LOGGER.debug("[PERSIST] workflow process with execution status : <{}>", processWorkflow.getExecutionStatus());
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            // XXX: ugly way to do this (bytearray) ?
            client.putObject(PROCESS_CONTAINER, getPathToObjectFromFolder(folderName, asyncId),
                new ByteArrayInputStream(JsonHandler.writeAsString(processWorkflow).getBytes()));
        } catch (ContentAddressableStorageServerException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public ProcessWorkflow getProcessWorkflow(String folderName, String asyncId)
        throws ProcessingStorageWorkspaceException, InvalidParseOperationException {
        Response response = null;
        InputStream is = null;
        // Not using try with resources because in this case, response.close() throw an exception :
        // MultiException stack 1 of 1
        // java.lang.IllegalStateException: ServiceLocatorImpl(__HK2_Generated_30,31,1929030508) has been shut down
        WorkspaceClient client = null;
        try {
            client = WorkspaceClientFactory.getInstance().getClient();
            response = client.getObject(PROCESS_CONTAINER, getPathToObjectFromFolder(folderName, asyncId));
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                is = (InputStream) response.getEntity();
                ProcessWorkflow processWorkflow = JsonHandler.getFromInputStream(is, ProcessWorkflow.class);
                return processWorkflow;
            } else {
                client.consumeAnyEntityAndClose(response);
                throw new ProcessingStorageWorkspaceException("Workspace error: " + response.getStatusInfo()
                    .getReasonPhrase());
            }
        } catch (ContentAddressableStorageServerException | ContentAddressableStorageNotFoundException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        } finally {
            if (client != null) {
                client.consumeAnyEntityAndClose(response);
                client.close();
            }
        }

    }

    @Override
    public void removeProcessWorkflow(String folderName, String asyncId) throws ProcessingStorageWorkspaceException {
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            client.deleteObject(PROCESS_CONTAINER, getPathToObjectFromFolder(folderName, asyncId));
        } catch (ContentAddressableStorageServerException | ContentAddressableStorageNotFoundException exc) {
            throw new ProcessingStorageWorkspaceException(exc);
        }
    }

    @Override
    public Map<String, ProcessWorkflow> getProcessWorkflowFor(Integer tenantId, String folderName)
        throws ProcessingStorageWorkspaceException {
        Map<String, ProcessWorkflow> result = new ConcurrentHashMap<>();
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            List<URI> uris =
                JsonHandler.getFromStringAsTypeRefence(client.getListUriDigitalObjectFromFolder(PROCESS_CONTAINER, folderName)
                    .toJsonNode().get("$results").get(0).toString(), new TypeReference<List<URI>>() {});
            for (URI uri : uris) {
                try {
                    // TODO: review this ugly split
                    ProcessWorkflow processWorkflow = getProcessWorkflow(folderName, uri.getPath().split("\\.")[0]);
                    if (!ProcessExecutionStatus.PAUSE.equals(processWorkflow.getExecutionStatus())) {
                        processWorkflow.setExecutionStatus(ProcessExecutionStatus.CANCELLED);
                        processWorkflow.setGlobalStatusCode(StatusCode.UNKNOWN);
                    }
                    if (tenantId == null || processWorkflow.getTenantId().equals(tenantId)) {
                        result.put(processWorkflow.getOperationId(), processWorkflow);
                    }
                } catch (InvalidParseOperationException e) {
                    // TODO: is blocking ?
                    LOGGER.error("Error on loading old workflow {} -> cannot be resume", uri.getPath(), e);
                }
            }
        } catch (ContentAddressableStorageServerException e) {
            throw new ProcessingStorageWorkspaceException(e);
        } catch (InvalidParseOperationException e) {
            throw new ProcessingStorageWorkspaceException(e);
        }
        return result;
    }

    /**
     * Get object name with json extension
     *
     * @param asyncId the async ID
     * @return asyncId.json
     */
    private String getObjectName(String asyncId) {
        return asyncId + ".json";
    }

    /**
     * Get the path to object name (with json extension) from a folder
     *
     * @param folderName the folder
     * @param object the object
     * @return the path to the object from folder like folderName/asyncId.json
     */
    private String getPathToObjectFromFolder(String folderName, String object) {
        return folderName + "/" + getObjectName(object);
    }
}
