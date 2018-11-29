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
package fr.gouv.vitam.common.storage.cas.container.api;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.storage.ContainerInformation;
import fr.gouv.vitam.common.storage.StorageConfiguration;
import fr.gouv.vitam.common.storage.cas.container.jcloud.VitamJcloudsPageSetImpl;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.ContainerNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.ListContainerOptions;

import java.io.InputStream;

/**
 * Abstract class of CAS that contains common methods for a Jclouds backend
 */
public abstract class ContentAddressableStorageJcloudsAbstract extends ContentAddressableStorageAbstract {

    private static final VitamLogger LOGGER =
            VitamLoggerFactory.getInstance(ContentAddressableStorageJcloudsAbstract.class);

    // FIXME P1: the BlobStoreContext should be build for each call, since it is
    // as a HttpClient. For now (Filesystem),
    // that's fine.
    protected final BlobStoreContext context;

    /**
     * creates a new ContentAddressableStorageImpl with a storage configuration
     * param
     *
     * @param configuration {@link StorageConfiguration}
     */
    public ContentAddressableStorageJcloudsAbstract(StorageConfiguration configuration) {
        super(configuration);
        context = getContext(configuration);
    }

    /**
     * enables the connection to a storage service with the param provided
     *
     * @param configuration the storage configuration
     * @return BlobStoreContext
     */
    public abstract BlobStoreContext getContext(StorageConfiguration configuration);

    /**
     * Close context according to implementation (http client not closed)
     */
    public abstract void closeContext();

    @Override
    public void createContainer(String containerName) throws ContentAddressableStorageAlreadyExistException {
        LOGGER.info(" create container : " + containerName);
        ParametersChecker
                .checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        // TODO: is it thread safe ?
        try {
            if (!context.getBlobStore().createContainerInLocation(null, containerName)) {
                LOGGER.info(ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName);
                throw new ContentAddressableStorageAlreadyExistException(
                        ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName);
            }
        } finally {
            closeContext();
        }

    }


    @Override
    public boolean isExistingContainer(String containerName) {
        try {
            if (super.isExistingContainerInCache(containerName)) {
                return true;
            }
            boolean exists = context.getBlobStore().containerExists(containerName);
            cacheExistsContainer(containerName, exists);
            return exists;
        } finally {
            closeContext();
        }
    }

    @Override
    public String putObject(String containerName, String objectName, InputStream stream, DigestType digestType,
                            Long size, boolean recomputeDigest)
            throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        final BlobStore blobStore = context.getBlobStore();
        try {

            Digest digest = new Digest(digestType);
            InputStream digestInputStream = digest.getDigestInputStream(stream);

            final Blob blob = blobStore.blobBuilder(objectName).payload(digestInputStream).build();

            blob.getMetadata().getContentMetadata().setContentLength(size);
            blobStore.putBlob(containerName, blob);

            String streamDigest = digest.digestHex();

            if (recomputeDigest) {
                String computedDigest = computeObjectDigest(containerName, objectName, digestType);
                if (!streamDigest.equals(computedDigest)) {
                    throw new ContentAddressableStorageException("Illegal state. Stream digest " + streamDigest + " is not equal to computed digest " + computedDigest);
                }
            }

            return streamDigest;

        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final Exception e) {
            LOGGER.error("Rollback", e.getMessage());
            blobStore.removeBlob(containerName, objectName);
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
            StreamUtils.closeSilently(stream);
        }
    }

    @Override
    public String getObjectDigest(String containerName, String objectName, DigestType digestType, boolean noCache)
            throws
            ContentAddressableStorageException {
        return computeObjectDigest(containerName, objectName, digestType);
    }

    @Override
    public ObjectContent getObject(String containerName, String objectName) throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingObject(containerName, objectName)) {
                LOGGER.error(
                        ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName + " in container '" + containerName + "'");
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }

            final Blob blob = blobStore.getBlob(containerName, objectName);
            if (null == blob) {
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }
            long size = blob.getMetadata().getSize();
            InputStream is = blob.getPayload().openStream();
            return new ObjectContent(is, size);
        } catch (final ContainerNotFoundException e) {
            throw new ContentAddressableStorageNotFoundException(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName, e);
        } catch (final ContentAddressableStorageNotFoundException e) {
            throw e;
        } catch (final Exception e) {
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
        }
    }

    @Override
    public void deleteObject(String containerName, String objectName)
            throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingContainer(containerName) || !isExistingObject(containerName, objectName)) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }

            blobStore.removeBlob(containerName, objectName);
        } finally {
            closeContext();
        }
    }

    @Override
    public boolean isExistingObject(String containerName, String objectName) {
        try {
            boolean isExists = false;
            final BlobStore blobStore = context.getBlobStore();
            try {
                isExists = blobStore.blobExists(containerName, objectName);
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
            return isExists;
        } finally {
            closeContext();
        }
    }

    @Override
    public abstract ContainerInformation getContainerInformation(String containerName)
            throws ContentAddressableStorageNotFoundException;

    @Override
    public VitamPageSet<? extends VitamStorageMetadata> listContainer(String containerName)
            throws ContentAddressableStorageNotFoundException {
        ParametersChecker
                .checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);

        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }

            ListContainerOptions options = new ListContainerOptions();
            options.maxResults(LISTING_MAX_RESULTS);

            return VitamJcloudsPageSetImpl.wrap(blobStore.list(containerName, options));

        } finally {
            closeContext();
        }

    }

    @Override
    public VitamPageSet<? extends VitamStorageMetadata> listContainerNext(String containerName,
                                                                          String nextMarker)
            throws ContentAddressableStorageNotFoundException {
        ParametersChecker
                .checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);

        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }

            ListContainerOptions options = new ListContainerOptions();
            options.maxResults(LISTING_MAX_RESULTS);
            options.afterMarker(nextMarker);
            return VitamJcloudsPageSetImpl.wrap(blobStore.list(containerName, options));
        } finally {
            closeContext();
        }
    }
}
