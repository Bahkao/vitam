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
package fr.gouv.vitam.worker.core.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import fr.gouv.vitam.common.FileUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.DefaultClient;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.common.model.processing.IOParameter;
import fr.gouv.vitam.common.model.processing.ProcessingUri;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCyclesClientHelper;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.WorkspaceAction;
import fr.gouv.vitam.processing.common.model.WorkspaceQueue;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.exception.WorkerspaceQueueException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import fr.gouv.vitam.workspace.common.CompressInformation;

/**
 * Handler input and output parameter
 */
public class HandlerIOImpl implements HandlerIO, VitamAutoCloseable {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(HandlerIOImpl.class);

    /**
     * Not Conform Param
     */
    public static final String NOT_CONFORM_PARAM = "Input/Output io parameter is not correct";
    private static final String HANDLER_INPUT_NOT_FOUND = "Handler input not found exception: ";

    private final ListMultimap<String, Object> input = ArrayListMultimap.create();
    private final List<ProcessingUri> output = new ArrayList<>();
    private final String containerName;
    private final String workerId;
    private final File localDirectory;
    private final Map<String, Object> memoryMap = new HashMap<>();
    private final WorkspaceClient workspaceCient;
    private final LogbookLifeCyclesClient lifecyclesClient;
    private final LogbookLifeCyclesClientHelper helper;

    private AsyncWorkspaceTransfer asyncWorkspaceTransfer;
    private String currentObjectId;
    private List<String> objectIds;

    private boolean needRefresh = false;

    /**
     * Constructor with local root path
     *
     * @param containerName the container name
     * @param workerId      the worker id
     * @param objectIds
     */
    public HandlerIOImpl(String containerName, String workerId, List<String> objectIds) {
        this(WorkspaceClientFactory.getInstance().getClient(), containerName, workerId, objectIds);
    }

    /**
     * Constructor with workspaceClient, local root path he is used for test purpose
     *
     * @param workspaceClient
     * @param containerName   the container name
     * @param workerId        the worker id
     * @param objectIds
     */
    @VisibleForTesting
    public HandlerIOImpl(WorkspaceClient workspaceClient, String containerName, String workerId,
        List<String> objectIds) {
        this.containerName = containerName;
        this.workerId = workerId;
        localDirectory = PropertiesUtils.fileFromTmpFolder(containerName + "_" + workerId);
        localDirectory.mkdirs();
        workspaceCient = workspaceClient;
        lifecyclesClient = LogbookLifeCyclesClientFactory.getInstance().getClient();
        helper = new LogbookLifeCyclesClientHelper();
        this.objectIds = objectIds;

        this.asyncWorkspaceTransfer = new AsyncWorkspaceTransfer(this);
    }



    @Override
    public LogbookLifeCyclesClient getLifecyclesClient() {
        return lifecyclesClient;
    }

    @Override
    public LogbookLifeCyclesClientHelper getHelper() {
        return helper;
    }

    @Override
    public void addInIOParameters(List<IOParameter> list) {
        for (final IOParameter in : list) {
            switch (in.getUri().getPrefix()) {
                case WORKSPACE:
                    try {
                        // TODO P1 : remove optional when lazy file loading is implemented
                        for (String objectId : objectIds) {
                            input.put(objectId, findFileFromWorkspace(in.getUri().getPath(),
                                in.getOptional()));
                        }

                        break;
                    } catch (final FileNotFoundException e) {
                        throw new IllegalArgumentException(HANDLER_INPUT_NOT_FOUND + in.getUri().getPath(), e);
                    }
                case MEMORY:
                    for (String objectId : objectIds) {
                        input.put(objectId, memoryMap.get(String.format("%s.%s", in.getUri().getPath(), objectId)));
                    }
                    break;
                case VALUE:
                    for (String objectId : objectIds) {
                        input.put(objectId, in.getUri().getPath());
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                        HANDLER_INPUT_NOT_FOUND + in.getUri().getPrefix() + ":" + in.getUri().getPath());
            }
        }
    }

    @Override
    public void addOutIOParameters(List<IOParameter> list) {
        for (final IOParameter out : list) {
            switch (out.getUri().getPrefix()) {
                case WORKSPACE:
                case MEMORY:
                    output.add(out.getUri());
                    break;
                default:
                    throw new IllegalArgumentException("Handler Output not conform: " + out.getUri());
            }
        }
    }

    @Override
    public void reset() {
        input.clear();
        output.clear();
        helper.clear();
    }

    @Override
    public void close() {
        try {
            this.enableAsync(false);
        } catch (WorkerspaceQueueException e) {
            throw new RuntimeException(e);
        } finally {
            workspaceCient.close();
            lifecyclesClient.close();
            partialClose();
        }
    }

    /**
     * Close the HandlerIO, including temporary files and directories at the end of the step Workflow execution, but do
     * not close the WorkspaceClient
     */
    public void partialClose() {
        reset();
        memoryMap.clear();
        if (!FileUtil.deleteRecursive(localDirectory)) {
            LOGGER.warn("Cannot clear the temporary directory: " + localDirectory);
        }
    }

    @Override
    public List<Object> getInput() {
        return input.get(currentObjectId);
    }

    @Override
    public Object getInput(int rank) {
        return input.get(currentObjectId).get(rank);
    }

    @Override
    public List<ProcessingUri> getOutput() {
        return output;
    }

    @Override
    public ProcessingUri getOutput(int rank) {
        return output.get(rank);
    }

    @Override
    public HandlerIO addOutputResult(int rank, Object object, boolean asyncIO) throws ProcessingException {
        return addOutputResult(rank, object, false, asyncIO);
    }

    @Override
    public HandlerIO addOutputResult(int rank, Object object, boolean deleteLocal, boolean asyncIO)
        throws ProcessingException {
        final ProcessingUri uri = output.get(rank);
        if (uri == null) {
            throw new IllegalArgumentException(HANDLER_INPUT_NOT_FOUND + rank);
        }
        switch (uri.getPrefix()) {
            case MEMORY:
                memoryMap.put(String.format("%s.%s", uri.getPath(), currentObjectId), object);
                break;
            case VALUE:
                // Ignore
                break;
            case WORKSPACE:
                if (!(object instanceof File)) {
                    throw new ProcessingException("Not a File but WORKSPACE out parameter: " + uri);
                }
                transferFileToWorkspace(uri.getPath(), (File) object, deleteLocal, asyncIO);
                break;
            default:
                throw new IllegalArgumentException(HANDLER_INPUT_NOT_FOUND + uri);

        }
        return this;
    }

    @Override
    public String getContainerName() {
        return containerName;
    }

    @Override
    public String getWorkerId() {
        return workerId;
    }

    @Override
    public File getLocalPathRoot() {
        return localDirectory;
    }

    @Override
    public File getNewLocalFile(String name) {
        final File file = new File(localDirectory.getAbsolutePath() + "/" + name);
        file.getParentFile().mkdirs();
        return file;
    }

    @Override
    public boolean checkHandlerIO(int outputNumber, List<Class<?>> clasz) {
        if (getInput().size() != clasz.size() || getOutput().size() != outputNumber) {
            LOGGER.error("InputSize shoul be {} but is {} OR OutputSize should be {} but is {}",
                clasz.size(), getInput().size(), outputNumber, getOutput().size());
            return false;
        }
        for (int i = 0; i < getInput().size(); i++) {
            final Object object = getInput(i);
            if (object == null || !clasz.get(i).isInstance(object)) {
                LOGGER.error("Input class should be {} but is {}",
                    clasz.get(i).getName(), object != null ? object.getClass().getName() : "Null object");
                return false;
            }
        }
        return true;
    }

    @Override
    public void transferFileToWorkspace(String workspacePath, File sourceFile, boolean toDelete, boolean asyncIO)
        throws ProcessingException {
        try {
            ParametersChecker.checkParameter("Workspace path is a mandatory parameter", workspacePath);
            ParametersChecker.checkParameter("Source file is a mandatory parameter", sourceFile);
        } catch (final IllegalArgumentException e) {
            throw new ProcessingException(e);
        }
        if (!sourceFile.canRead()) {
            throw new ProcessingException("Cannot found source file: " + sourceFile);
        }

        try {
            transferInputStreamToWorkspace(workspacePath, Files.newInputStream(sourceFile.toPath()),
                toDelete, Paths.get(sourceFile.toURI()), asyncIO);
        } catch (final IOException e) {
            throw new ProcessingException("Cannot found or read source file: " + sourceFile, e);
        }
    }


    private void transferInputStreamToWorkspace(String workspacePath, InputStream inputStream, boolean toDelete,
        Path filePath,
        boolean asyncIO) throws ProcessingException {
        if (!asyncIO) {
            try {
                workspaceCient.putObject(containerName, workspacePath, inputStream);
            } catch (final ContentAddressableStorageServerException e) {
                throw new ProcessingException("Cannot write to workspace: " + containerName + "/" + workspacePath, e);
            } finally {
                try {
                    if (filePath != null && toDelete) {
                        Files.delete(filePath);
                    }
                } catch (IOException e) {
                    LOGGER.warn("File could not be deleted: " + filePath.toAbsolutePath());
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOGGER.warn("InputStream close failed :", e);
                    }
                }
            }
        } else {
            try {
                this.asyncWorkspaceTransfer
                    .transfer(new WorkspaceQueue(workspacePath, inputStream).setFilePath(filePath));
            } catch (WorkerspaceQueueException e) {
                throw new ProcessingException(e);
            }
        }
    }

    @Override
    public void transferInputStreamToWorkspace(String workspacePath, InputStream inputStream, Path filePath,
        boolean asyncIO)
        throws ProcessingException {
        transferInputStreamToWorkspace(workspacePath, inputStream, true, filePath, asyncIO);
    }

    /**
     * Get the File associated with this filename, trying in this order: as fullpath, as in Vitam Config Folder, as
     * Resources file
     *
     * @param objectName object name
     * @param optional   if file is optional
     * @return file if found, if not found, null if optional
     * @throws FileNotFoundException if file is not found and not optional
     */
    private final File findFileFromWorkspace(String objectName, boolean optional) throws FileNotFoundException {
        // First try as full path
        File file = null;
        // TODO P1 : this optional situation would be treated later when lazy file loading is implemented
        if (optional) {
            try {
                file = getFileFromWorkspace(objectName);
            } catch (final ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException |
                IOException e) {
                LOGGER.debug(e);
                file = null;
            }
            if (file != null && !file.exists()) {
                file = null;
            }
        } else {
            try {
                file = getFileFromWorkspace(objectName);
            } catch (final ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException |
                IOException e) {
                // need to rewrite the exception
                LOGGER.error(e);
                throw new FileNotFoundException("File not found: " + objectName);
            }
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + objectName);
            }
        }
        return file;
    }

    @Override
    public File getFileFromWorkspace(String objectName)
        throws IOException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException {
        final File file = getNewLocalFile(objectName);
        if (!file.exists()) {
            Response response = null;
            try {
                response = workspaceCient.getObject(containerName, objectName);
                if (response != null) {
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        StreamUtils.copy((InputStream) response.getEntity(), fileOutputStream);
                    }
                }
            } finally {
                workspaceCient.consumeAnyEntityAndClose(response);
            }
        }
        return file;
    }

    @Override
    public InputStream getInputStreamFromWorkspace(String objectName)
        throws IOException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException {
        return new FileInputStream(getFileFromWorkspace(objectName));
    }

    @Override
    public Response getInputStreamNoCachedFromWorkspace(String objectName)
        throws ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException {
        return workspaceCient.getObject(containerName, objectName);
    }

    @Override
    public void consumeAnyEntityAndClose(Response response) {
        workspaceCient.consumeAnyEntityAndClose(response);
    }

    @Override
    public JsonNode getJsonFromWorkspace(String jsonFilePath) throws ProcessingException {
        Response response = null;
        InputStream is = null;
        try {
            final File file = getNewLocalFile(jsonFilePath);
            if (!file.exists()) {
                response = workspaceCient.getObject(containerName, jsonFilePath);
                is = (InputStream) response.getEntity();
                if (is != null) {
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        StreamUtils.copy(is, fileOutputStream);
                    }
                } else {
                    LOGGER.error("Json not found");
                    throw new ProcessingException("Json not found");
                }
            }
            return JsonHandler.getFromFile(file);
        } catch (final InvalidParseOperationException e) {
            LOGGER.debug("Json wrong format", e);
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.debug("Workspace Server Error", e);
            throw new ProcessingException(e);
        } catch (IOException e) {
            LOGGER.debug("Local Worker Storage Error", e);
            throw new ProcessingException(e);
        } finally {
            DefaultClient.staticConsumeAnyEntityAndClose(response);
        }
    }

    @Override
    public boolean deleteLocalFile(String objectName) {
        final File file = getNewLocalFile(objectName);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    @Override
    public List<URI> getUriList(String containerName, String folderName) throws ProcessingException {
        try {
            return JsonHandler
                .getFromStringAsTypeRefence(workspaceCient.getListUriDigitalObjectFromFolder(containerName, folderName)
                    .toJsonNode().get("$results").get(0).toString(), new TypeReference<List<URI>>() {
                });
        } catch (ContentAddressableStorageServerException | InvalidParseOperationException | InvalidFormatException e) {
            LOGGER.debug("Workspace Server Error", e);
            throw new ProcessingException(e);
        }
    }

    @Override
    public void transferJsonToWorkspace(String collectionName, String objectName, JsonNode jsonNode,
        boolean toDelete, boolean asyncIO)
        throws ProcessingException {
        String path = collectionName + File.separator + objectName;
        try {
            if (toDelete) {
                InputStream inputStream = JsonHandler.writeToInpustream(jsonNode);
                transferInputStreamToWorkspace(path, inputStream, null, asyncIO);
            } else {
                File file = getNewLocalFile(path);
                JsonHandler.writeAsFile(jsonNode, file);
                transferFileToWorkspace(path, file, toDelete, asyncIO);
            }
        } catch (final InvalidParseOperationException e) {
            throw new ProcessingException("Invalid parse Exception: " + path, e);
        }

    }

    @Override
    public void unzipInputStreamOnWorkspace(String container, final String folderName,
        final String archiveMimeType, final InputStream uploadedInputStream, boolean asyncIO)
        throws ContentAddressableStorageException {

        if (container == null) {
            container = this.containerName;
        }
        LOGGER.debug("Try to push stream to workspace...");

        if (!asyncIO) {
            // call workspace
            if (!workspaceCient.isExistingContainer(container)) {
                workspaceCient.createContainer(container);
                workspaceCient.uncompressObject(container, folderName, archiveMimeType, uploadedInputStream);
            } else {
                LOGGER.error(container + "already exist");
                throw new ContentAddressableStorageAlreadyExistException(container + "already exist");
            }

            LOGGER.debug(" -> push compressed file to workspace finished");
        } else {
            try {
                this.asyncWorkspaceTransfer.transfer(
                    new WorkspaceQueue(container, uploadedInputStream, WorkspaceAction.UNZIP)
                        .setMediaType(archiveMimeType).setFolderName(folderName));
            } catch (WorkerspaceQueueException e) {
                new ContentAddressableStorageException(e);
            }
        }
    }

    @Override
    public void zipWorkspace(String outputFile, String... inputFiles)
        throws ContentAddressableStorageException {

        LOGGER.debug("Try to push stream to workspace...");

        // call workspace
        if (workspaceCient.isExistingContainer(containerName)) {
            CompressInformation compressInformation = new CompressInformation();
            Collections.addAll(compressInformation.getFiles(), inputFiles);
            compressInformation.setOutputFile(outputFile);
            workspaceCient.compress(containerName, compressInformation);
        } else {
            LOGGER.error(containerName + "already exist");
            throw new ContentAddressableStorageAlreadyExistException(containerName + "already exist");
        }

    }

    @Override
    public void enableAsync(boolean async) throws WorkerspaceQueueException {
        if (async) {
            this.asyncWorkspaceTransfer.startTransfer(VitamConfiguration.getAsyncWorkspaceQueueSize());
        } else {
            this.asyncWorkspaceTransfer.waitEndOfTransfer();
        }
    }

    @Override
    public boolean removeFolder(String folderName) throws ContentAddressableStorageException {
        if (!isFolderExist(folderName)) {
            return false;
        }
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            client.deleteFolder(this.containerName, folderName);
            return true;
        } catch (ContentAddressableStorageServerException | ContentAddressableStorageNotFoundException exc) {
            throw new ContentAddressableStorageException(exc);
        }
    }

    @Override
    public void setCurrentObjectId(String currentObjectId) {
        this.currentObjectId = currentObjectId;
    }

    private boolean isFolderExist(String folderName) throws ContentAddressableStorageException {
        try (WorkspaceClient client = WorkspaceClientFactory.getInstance().getClient()) {
            return client.isExistingFolder(this.containerName, folderName);
        } catch (ContentAddressableStorageServerException exc) {
            throw new ContentAddressableStorageException(exc);
        }
    }

}
