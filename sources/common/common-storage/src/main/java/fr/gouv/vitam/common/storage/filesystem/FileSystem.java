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
package fr.gouv.vitam.common.storage.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.filesystem.reference.FilesystemConstants;
import org.jclouds.providers.ProviderMetadata;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.MetadatasObject;
import fr.gouv.vitam.common.storage.cas.container.api.ContentAddressableStorageJcloudsAbstract;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.common.storage.cas.container.api.MetadatasStorageObject;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.common.storage.ContainerInformation;

/**
 * FileSystemMock implements a Content Addressable Storage that stores objects
 * on the file system.
 */
public class FileSystem extends ContentAddressableStorageJcloudsAbstract {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(FileSystem.class);

    /**
     * @param configuration
     *            to associate with the FileSystem
     */
    public FileSystem(StorageConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void closeContext() {
        context.close();
    }

    @Override
    public ContainerInformation getContainerInformation(String containerName) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter("Container name may not be null", containerName);
        final File baseDirFile = getBaseDir(containerName);
        final long usableSpace = baseDirFile.getUsableSpace();
        final long usedSpace = getFolderUsedSize(baseDirFile);
        final ContainerInformation containerInformation = new ContainerInformation();
        containerInformation.setUsableSpace(usableSpace);
        containerInformation.setUsedSpace(usedSpace);
        return containerInformation;
    }

    // TODO： manage the cycle in filesystem (ex: symlink)
    private long getFolderUsedSize(File directory) {
        long usedSpace = 0;
        for (final File file : directory.listFiles()) {
            if (file.isFile()) {
                usedSpace += file.length();
            } else if (file.isDirectory()) {
                usedSpace += getFolderUsedSize(file);
            }
        }
        return usedSpace;
    }

    private File getBaseDir(String containerName) throws ContentAddressableStorageNotFoundException {
        try {
            final ProviderMetadata providerMetadata = context.unwrap().getProviderMetadata();
            final Properties properties = providerMetadata.getDefaultProperties();
            final String baseDir = properties.getProperty(FilesystemConstants.PROPERTY_BASEDIR);
            File baseDirFile;
            if (containerName != null) {
                baseDirFile = new File(baseDir, containerName);
            } else {
                baseDirFile = new File(baseDir);
            }
            if (!baseDirFile.exists()) {
                LOGGER.error("container not found: " + containerName + "(BaseDir File: " + baseDirFile + ")");
                throw new ContentAddressableStorageNotFoundException("Storage not found");
            }
            return baseDirFile;
        } finally {
            closeContext();
        }
    }

    @Override
    public BlobStoreContext getContext(StorageConfiguration configuration) {
        final Properties props = new Properties();
        props.setProperty(FilesystemConstants.PROPERTY_BASEDIR, configuration.getStoragePath());
        LOGGER.debug("Get File System Context");
        return ContextBuilder.newBuilder("filesystem").overrides(props).buildView(BlobStoreContext.class);
    }

    private File getFileFromJClouds(String containerName, String objectId) throws ContentAddressableStorageNotFoundException {
        final ProviderMetadata providerMetadata = context.unwrap().getProviderMetadata();
        final Properties properties = providerMetadata.getDefaultProperties();
        final String baseDir = properties.getProperty(FilesystemConstants.PROPERTY_BASEDIR);
        File file;
        if (containerName != null) {
            if (objectId != null) {
                file = new File(baseDir, containerName + "/" + objectId);
            } else {
                file = new File(baseDir, containerName);
            }
        } else {
            file = new File(baseDir);
        }
        if (!file.exists()) {
            LOGGER.error("container not found: " + containerName + "(BaseDir File: " + file + ")");
            throw new ContentAddressableStorageNotFoundException("Storage not found");
        }
        return file;
    }

    private BasicFileAttributes getFileAttributes(File file) throws IOException {
        Path path = Paths.get(file.getPath());
        BasicFileAttributeView basicView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
        BasicFileAttributes basicAttribs = basicView.readAttributes();
        return basicAttribs;
    }

    @Override
    public MetadatasObject getObjectMetadatas(String containerName, String objectId)
            throws IOException, ContentAddressableStorageException {
        MetadatasStorageObject result = new MetadatasStorageObject();
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectId);
        try {
            File file = getFileFromJClouds(containerName, objectId);
            BasicFileAttributes basicAttribs = getFileAttributes(file);
            long size = Files.size(Paths.get(file.getPath()));
            if (null != file) {
                if (objectId != null) {
                    result.setObjectName(objectId);
                    // TODO To be reviewed with the X-DIGEST-ALGORITHM parameter
                    result.setDigest(computeObjectDigest(containerName, objectId, VitamConfiguration.getDefaultDigestType()));
                    result.setFileSize(size);
                } else {
                    result.setObjectName(containerName);
                    // TODO calculer l'empreint de répertoire
                    result.setDigest(null);
                    result.setFileSize(getFolderUsedSize(file));
                }
                // TODO store vitam metadatas
                if (containerName != null) {
                    result.setType(containerName.split("_")[1]);
                    result.setFileOwner("Vitam_" + containerName.split("_")[0]);
                }
                result.setLastAccessDate(basicAttribs.lastAccessTime().toString());
                result.setLastModifiedDate(basicAttribs.lastModifiedTime().toString());
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } catch (ContentAddressableStorageException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } finally {
            closeContext();
        }
        return result;
    }

    @Override
    public void close() {
        closeContext();
    }
}
