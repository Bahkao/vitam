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
package fr.gouv.vitam.metadata.core.reconstruction;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Projections.include;
import static fr.gouv.vitam.common.database.server.mongodb.VitamDocument.ID;
import static fr.gouv.vitam.common.database.utils.MetadataDocumentHelper.getComputedGraphObjectGroupFields;
import static fr.gouv.vitam.common.database.utils.MetadataDocumentHelper.getComputedGraphUnitFields;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.offset.OffsetRepository;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamFatalRuntimeException;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.storage.compress.VitamArchiveStreamFactory;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.metadata.core.database.collections.MetadataCollections;
import fr.gouv.vitam.metadata.core.database.collections.Unit;
import fr.gouv.vitam.metadata.core.database.collections.VitamRepositoryProvider;
import fr.gouv.vitam.metadata.core.model.ReconstructionRequestItem;
import fr.gouv.vitam.metadata.core.model.ReconstructionResponseItem;
import fr.gouv.vitam.storage.engine.common.exception.StorageException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Reconstruction of Vitam Metadata Collections.<br>
 */
public class ReconstructionService {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ReconstructionService.class);
    private static final String RECONSTRUCTION_ITEM_MONDATORY_MSG = "the item defining reconstruction is mandatory.";
    private static final String RECONSTRUCTION_COLLECTION_MONDATORY_MSG = "the collection to reconstruct is mondatory.";
    private static final String RECONSTRUCTION_TENANT_MONDATORY_MSG = "the tenant to reconstruct is mondatory.";
    private static final String RECONSTRUCTION_LIMIT_POSITIVE_MSG = "the limit to reconstruct is should at least 0.";

    private static final String STRATEGY_ID = "default";
    public static final String $_SET = "$set";
    public static final String GRAPH_LAST_PERSISTED_DTE = "_glpd";
    public static final String GRAPH_LAST_PERSISTED_DATE = GRAPH_LAST_PERSISTED_DTE;

    private RestoreBackupService restoreBackupService;

    private VitamRepositoryProvider vitamRepositoryProvider;

    private LogbookLifeCyclesClientFactory logbookLifeCyclesClientFactory;

    private OffsetRepository offsetRepository;

    /**
     * Constructor
     *
     * @param vitamRepositoryProvider vitamRepositoryProvider
     * @param offsetRepository        offsetRepository
     */
    public ReconstructionService(VitamRepositoryProvider vitamRepositoryProvider,
        OffsetRepository offsetRepository) {
        this(vitamRepositoryProvider, new RestoreBackupService(), LogbookLifeCyclesClientFactory.getInstance(),
            offsetRepository);
    }

    /**
     * Constructor for tests
     *
     * @param vitamRepositoryProvider       vitamRepositoryProvider
     * @param recoverBackupService          recoverBackupService
     * @param logbookLifecycleClientFactory logbookLifecycleClientFactory
     * @param offsetRepository
     */
    @VisibleForTesting
    public ReconstructionService(VitamRepositoryProvider vitamRepositoryProvider,
        RestoreBackupService recoverBackupService, LogbookLifeCyclesClientFactory logbookLifecycleClientFactory,
        OffsetRepository offsetRepository) {
        this.vitamRepositoryProvider = vitamRepositoryProvider;
        this.restoreBackupService = recoverBackupService;
        this.logbookLifeCyclesClientFactory = logbookLifecycleClientFactory;
        this.offsetRepository = offsetRepository;
    }

    /**
     * Reconstruct a collection
     *
     * @param reconstructionItem request for reconstruction
     * @return response of reconstruction
     * @throws DatabaseException        database exception
     * @throws IllegalArgumentException invalid input
     */
    public ReconstructionResponseItem reconstruct(ReconstructionRequestItem reconstructionItem) {
        ParametersChecker.checkParameter(RECONSTRUCTION_ITEM_MONDATORY_MSG, reconstructionItem);
        ParametersChecker.checkParameter(RECONSTRUCTION_COLLECTION_MONDATORY_MSG, reconstructionItem.getCollection());
        ParametersChecker.checkParameter(RECONSTRUCTION_TENANT_MONDATORY_MSG, reconstructionItem.getTenant());
        if (reconstructionItem.getLimit() < 0) {
            throw new IllegalArgumentException(RECONSTRUCTION_LIMIT_POSITIVE_MSG);
        }
        LOGGER
            .info(String.format(
                "[Reconstruction]: Reconstruction of {%s} Collection on {%s} Vitam tenant",
                reconstructionItem.getCollection(), reconstructionItem.getTenant()));

        switch (DataCategory.valueOf(reconstructionItem.getCollection().toUpperCase())) {
            case UNIT_GRAPH:
            case OBJECTGROUP_GRAPH:
                return reconstructGraphFromZipStream(reconstructionItem.getCollection(), reconstructionItem.getLimit());
            case UNIT:
            case OBJECTGROUP:
                return reconstructCollection(MetadataCollections.getFromValue(reconstructionItem.getCollection()),
                    reconstructionItem.getTenant(), reconstructionItem.getLimit());
            default:
                return new ReconstructionResponseItem(reconstructionItem, StatusCode.KO);
        }
    }

    private ReconstructionResponseItem reconstructGraphFromZipStream(String collectionName, int limit) {

        Integer tenant = VitamConfiguration.getAdminTenant();
        VitamThreadUtils.getVitamSession().setTenantId(tenant);


        final long offset = offsetRepository.findOffsetBy(tenant, collectionName);
        ParametersChecker.checkParameter("Parameter collection is required.", collectionName);
        LOGGER.info(String
            .format(
                "[Reconstruction]: Start reconstruction of the {%s} collection on the Vitam tenant {%s} for %s elements starting from {%s}.",
                collectionName, tenant, limit, offset));
        ReconstructionResponseItem response =
            new ReconstructionResponseItem().setCollection(collectionName).setTenant(tenant);
        MetadataCollections metaDaCollection;
        DataCategory dataCategory = DataCategory.valueOf(collectionName);
        switch (dataCategory) {
            case UNIT_GRAPH:
                metaDaCollection = MetadataCollections.UNIT;
                break;
            case OBJECTGROUP_GRAPH:
                metaDaCollection = MetadataCollections.OBJECTGROUP;
                break;
            default:
                throw new IllegalArgumentException(String.format("ERROR: Invalid collection {%s}", collectionName));
        }

        long newOffset = offset;


        try {
            // get the list of datas to backup.
            List<OfferLog> listing =
                restoreBackupService.getListing(STRATEGY_ID, dataCategory, offset, limit, Order.ASC);

            for (OfferLog offerLog : listing) {
                String guid = GUIDFactory.newGUID().getId();

                Path filePath = Files.createTempFile(guid + "_", offerLog.getFileName());
                try {
                    // Read zip file from offer
                    InputStream zipFileAsStream =
                        restoreBackupService.loadData(STRATEGY_ID, dataCategory, offerLog.getFileName());

                    // Copy file to local tmp to prevent risk of broken stream
                    Files.copy(zipFileAsStream, filePath, StandardCopyOption.REPLACE_EXISTING);

                    // Close the stream
                    IOUtils.closeQuietly(zipFileAsStream);

                    // Handle a reconstruction from a copied zip file
                    try (InputStream zipInputStream = Files.newInputStream(filePath)) {
                        reconstructGraphFromZipStream(metaDaCollection, zipInputStream);
                    }
                } finally {
                    // Remove file
                    Files.deleteIfExists(filePath);
                }
                newOffset = offerLog.getSequence();
                // log the recontruction of Vitam collection.
                LOGGER.info(String.format(
                    "[Reconstruction]: the collection {%s} has been reconstructed on the tenant {%s} from {offset:%s} at %s",
                    collectionName, tenant, offset, LocalDateUtil.now()));

            }

            response.setStatus(StatusCode.OK);
        } catch (ReconstructionException | IOException de) {
            LOGGER.error(String.format(
                "[Reconstruction]: Exception has been thrown when reconstructing Vitam collection {%s} metadatas on the tenant {%s} from {offset:%s}",
                collectionName, tenant, offset), de);
            newOffset = offset;
            response.setStatus(StatusCode.KO);

        } finally {
            offsetRepository.createOrUpdateOffset(tenant, collectionName, newOffset);
        }
        return response;
    }

    /**
     * Reconstruct collection.
     *
     * @param collection collection
     * @param tenant     tenant
     * @param limit      number of data to reconstruct
     * @return response of reconstruction
     * @throws DatabaseException        database exception
     * @throws IllegalArgumentException invalid input
     * @throws VitamRuntimeException    storage error
     */
    private ReconstructionResponseItem reconstructCollection(MetadataCollections collection, int tenant, int limit) {

        final long offset = offsetRepository.findOffsetBy(tenant, collection.getName());
        ParametersChecker.checkParameter("Parameter collection is required.", collection);
        LOGGER.info(String
            .format(
                "[Reconstruction]: Start reconstruction of the {%s} collection on the Vitam tenant {%s} for %s elements starting from {%s}.",
                collection.name(), tenant, limit, offset));
        ReconstructionResponseItem response =
            new ReconstructionResponseItem().setCollection(collection.name()).setTenant(tenant);
        Integer originalTenant = VitamThreadUtils.getVitamSession().getTenantId();

        long newOffset = offset;

        try {
            // This is a hack, we must set manually the tenant is the VitamSession (used and transmitted in the
            // headers)
            VitamThreadUtils.getVitamSession().setTenantId(tenant);
            DataCategory type;
            switch (collection) {
                case UNIT:
                    type = DataCategory.UNIT;
                    break;
                case OBJECTGROUP:
                    type = DataCategory.OBJECTGROUP;
                    break;
                default:
                    throw new IllegalArgumentException(String.format("ERROR: Invalid collection {%s}", collection));
            }

            // get the list of datas to backup.
            List<OfferLog> listing = restoreBackupService.getListing(STRATEGY_ID, type, offset, limit, Order.ASC);
            List<List<OfferLog>> partition = Lists.partition(listing, VitamConfiguration.getRestoreBulkSize());

            for (List<OfferLog> listingBulk : partition) {

                List<MetadataBackupModel> dataFromOffer = new ArrayList<>();
                for (OfferLog offerLog : listingBulk) {
                    MetadataBackupModel model = restoreBackupService
                        .loadData(STRATEGY_ID, collection, offerLog.getFileName(), offerLog.getSequence());
                    if (model != null && model.getMetadatas() != null && model.getLifecycle() != null &&
                        model.getOffset() != null) {
                        dataFromOffer.add(model);
                    } else {
                        throw new StorageException(String.format(
                            "[Reconstruction]: Metadatas or Logbooklifecycle is not present in file {%s} for the collection {%s} on the tenant {%s}",
                            offerLog.getFileName(), collection, tenant));
                    }
                }

                // reconstruct Vitam collection from the backup datas.
                if (dataFromOffer.isEmpty()) {
                    continue;
                }


                reconstructCollectionMetadatas(collection, dataFromOffer,
                    VitamConfiguration.getOptimisticLockRetryNumber());
                reconstructCollectionLifecycles(collection, dataFromOffer);
                MetadataBackupModel last = Iterables.getLast(dataFromOffer);
                newOffset = last.getOffset();


                // log the recontruction of Vitam collection.
                LOGGER.info(String.format(
                    "[Reconstruction]: the collection {%s} has been reconstructed on the tenant {%s} from {offset:%s} at %s",
                    collection.name(), tenant, offset, LocalDateUtil.now()));
            }
            response.setStatus(StatusCode.OK);
        } catch (DatabaseException de) {
            LOGGER.error(String.format(
                "[Reconstruction]: Exception has been thrown when reconstructing Vitam collection {%s} metadatas on the tenant {%s} from {offset:%s}",
                collection, tenant, offset), de);
            newOffset = offset;
            response.setStatus(StatusCode.KO);
        } catch (StorageException se) {
            LOGGER.error(se.getMessage());
            newOffset = offset;
            response.setStatus(StatusCode.KO);
        } catch (LogbookClientException | InvalidParseOperationException exc) {
            LOGGER.error(String.format(
                "[Reconstruction]: Exception has been thrown when reconstructing Vitam collection {%s} lifecycles on the tenant {%s} from {offset:%s}",
                collection, tenant, offset), exc);
            newOffset = offset;
            response.setStatus(StatusCode.KO);
        } finally {
            offsetRepository.createOrUpdateOffset(tenant, collection.getName(), newOffset);
            VitamThreadUtils.getVitamSession().setTenantId(originalTenant);
        }
        return response;
    }

    /**
     * If an already existing document, have a graph data. Do not erase graph data
     *
     * @param collection
     * @param dataFromOffer
     */
    private void preventAlreadyExistingGraphData(MetadataCollections collection,
        List<MetadataBackupModel> dataFromOffer) {

        List<MetadataBackupModel> toRemove = new ArrayList<>();
        Map<String, MetadataBackupModel> dataMap = new HashMap<>();

        for (MetadataBackupModel mbm : dataFromOffer) {
            Document document = mbm.getMetadatas();
            String id = document.getString(ID);
            MetadataBackupModel alreadyExists = dataMap.remove(id);
            if (null != alreadyExists) {
                toRemove.add(alreadyExists);
            }

            dataMap.put(id, mbm);
        }

        // Remove duplicate document and keep only the latest one (as they have the latest version in the storage)
        dataFromOffer.removeAll(toRemove);


        final Bson projection;

        switch (collection) {
            case UNIT:
                projection = include(getComputedGraphUnitFields());
                break;
            case OBJECTGROUP:
                projection = include(getComputedGraphObjectGroupFields());
                break;
            default:
                throw new IllegalStateException("Unsupported metadata type " + collection);
        }


        try (MongoCursor<Document> iterator = this.vitamRepositoryProvider.getVitamMongoRepository(collection)
            .findDocuments(dataMap.keySet(), projection)
            .iterator()) {

            while (iterator.hasNext()) {

                // SourceDocument document from mongo contains only graph data and _id
                // TargetDocument document from offer (do no contains graph data

                Document sourceDocument = iterator.next();
                String id = sourceDocument.getString(ID);
                Document targetDocument = dataMap.get(id).getMetadatas();

                targetDocument.putAll(sourceDocument);
            }
        }

    }

    /**
     * Reconstruct lifecycles in logbook
     *
     * @param collection collection
     * @param bulk       list of items to back up
     * @throws LogbookClientException         error from logbook
     * @throws InvalidParseOperationException error parsing logbook response
     */
    private void reconstructCollectionLifecycles(MetadataCollections collection, List<MetadataBackupModel> bulk)
        throws LogbookClientException, InvalidParseOperationException {

        LOGGER.info("[Reconstruction]: Back up of lifecycles bulk");

        try (LogbookLifeCyclesClient logbookLifecycleClient = logbookLifeCyclesClientFactory.getClient()) {
            List<JsonNode> lifecycles =
                bulk.stream()
                    .map(model -> {
                        try {
                            if (model.getLifecycle() != null) {
                                return JsonHandler
                                    .getFromString(
                                        model.getLifecycle().toJson(new JsonWriterSettings(JsonMode.STRICT)));
                            } else {
                                throw new VitamRuntimeException("lifecycle should not be null");
                            }
                        } catch (InvalidParseOperationException e) {
                            throw new VitamRuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
            switch (collection) {
                case UNIT:
                    logbookLifecycleClient.createRawbulkUnitlifecycles(lifecycles);
                    break;
                case OBJECTGROUP:
                    logbookLifecycleClient.createRawbulkObjectgrouplifecycles(lifecycles);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid collection");
            }

        } catch (VitamRuntimeException lifecycleParsingException) {
            throw new InvalidParseOperationException(lifecycleParsingException);
        }
    }

    /**
     * Reconstruct metadatas in databases
     *
     * @param collection    the concerning collection
     * @param dataFromOffer list of items to back up
     * @throws DatabaseException databaseException
     */
    private void reconstructCollectionMetadatas(MetadataCollections collection, List<MetadataBackupModel> dataFromOffer,
        Integer nbRetry)
        throws DatabaseException {
        if (nbRetry < 0) {
            throw new DatabaseException("Optimistic lock number of retry reached");
        }
        LOGGER.info("[Reconstruction]: Back up of metadatas bulk");

        // Do not erase graph data
        preventAlreadyExistingGraphData(collection, dataFromOffer);

        // Create bulk of ReplaceOneModel
        List<WriteModel<Document>> metadatas =
            dataFromOffer.stream().map(MetadataBackupModel::getMetadatas).map(this::createReplaceOneModel)
                .collect(Collectors.toList());
        try {
            this.bulkMongo(collection, metadatas);
        } catch (DatabaseException e) {
            if (e.getCause() instanceof MongoBulkWriteException) {
                LOGGER.warn("[Reconstruction]: [Optimistic_Lock]: optimistic lock occurs while reconstruct AU/GOT");

                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(VitamConfiguration.getOptimisticLockSleepTime()));
                } catch (InterruptedException e1) {
                    SysErrLogger.FAKE_LOGGER.ignoreLog(e1);
                    throw new DatabaseException(e1);
                }

                nbRetry--; // Retry after optimistic lock problem
                reconstructCollectionMetadatas(collection, dataFromOffer, nbRetry);

            } else {
                throw e;
            }

        }

        List<Document> documents =
            dataFromOffer.stream().map(MetadataBackupModel::getMetadatas).collect(Collectors.toList());
        bulkElasticsearch(collection, documents);
    }

    /**
     * @param metaDaCollection
     * @param zipStream        The zip inputStream
     * @throws DatabaseException
     */
    private void reconstructGraphFromZipStream(MetadataCollections metaDaCollection, InputStream zipStream)
        throws ReconstructionException {
        LOGGER.info("[Reconstruction]: Back up of metadatas bulk");

        try (final ArchiveInputStream archiveInputStream = new VitamArchiveStreamFactory()
            .createArchiveInputStream(CommonMediaType.valueOf(CommonMediaType.ZIP), zipStream)) {
            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                if (archiveInputStream.canReadEntryData(entry)) {
                    if (!entry.isDirectory()) {
                        ArrayNode arrayNode = (ArrayNode) JsonHandler.getFromInputStream(archiveInputStream);
                        treatBulkGraph(metaDaCollection, arrayNode);
                    }
                }
            }
        } catch (InvalidParseOperationException | IOException | ArchiveException | DatabaseException e) {
            throw new ReconstructionException(e);
        } finally {
            removeGraphOnlyReconstructedOlderDocuments(metaDaCollection);
        }

    }


    /**
     * Find all older (AU/GOT) where only graph data are reconstructed
     * As Documents with only graph data are not indexed in elasticsearch=> we have not to implement deletion from Elastcisearch
     */
    private void removeGraphOnlyReconstructedOlderDocuments(MetadataCollections metaDaCollection) {

        try {

            String dateDeleteLimit = LocalDateUtil.getFormattedDateForMongo(
                LocalDateTime
                    .now()
                    .minus(VitamConfiguration.getDeleteIncompleteReconstructedUnitDelay(), ChronoUnit.SECONDS));
            Bson query = and(
                exists(Unit.TENANT_ID, false),
                lte(Unit.GRAPH_LAST_PERSISTED_DATE, dateDeleteLimit));

            this.vitamRepositoryProvider.getVitamMongoRepository(metaDaCollection).remove(query);
        } catch (DatabaseException e) {
            LOGGER.error("[Reconstruction]: Error while remove older documents having only graph data", e);
        }
    }

    /**
     * @param metadataCollections
     * @param arrayNode
     * @throws DatabaseException
     */
    private void treatBulkGraph(MetadataCollections metadataCollections, ArrayNode arrayNode) throws DatabaseException {
        List<WriteModel<Document>> collection = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        arrayNode.forEach(o -> {
            //Create UpdateOneModel
            try {
                collection.add(createUpdateOneModel(o));
                /**
                 * Take only documents having graph data and business data to be indexed in elasticsearch
                 * Skip all documents with only graph data
                 */
                if (null != o.get(Unit.TENANT_ID)) {
                    ids.add(o.get(Unit.ID).asText());
                }
            } catch (InvalidParseOperationException e) {
                throw new VitamFatalRuntimeException(e);
            }
        });

        // Save in MongoDB
        bulkMongo(metadataCollections, collection);

        // Save in Elasticsearch
        bulkElasticsearch(metadataCollections, ids);
    }

    /**
     * Bulk write in mongodb
     *
     * @param metaDaCollection
     * @param collection
     * @throws DatabaseException
     */
    private void bulkMongo(MetadataCollections metaDaCollection, List<WriteModel<Document>> collection)
        throws DatabaseException {
        this.vitamRepositoryProvider.getVitamMongoRepository(metaDaCollection).update(collection);
    }


    /**
     * Bulk save in elasticsearch
     *
     * @param metaDaCollection
     * @param collection       of id of documents
     * @throws DatabaseException
     */
    private void bulkElasticsearch(MetadataCollections metaDaCollection, Set<String> collection)
        throws DatabaseException {

        if (collection.isEmpty()) {
            return;
        }

        FindIterable<Document> fit =
            this.vitamRepositoryProvider.getVitamMongoRepository(metaDaCollection).findDocuments(collection, null);
        MongoCursor<Document> it = fit.iterator();
        List<Document> documents = new ArrayList<>();
        while (it.hasNext()) {
            documents.add(it.next());
        }

        bulkElasticsearch(metaDaCollection, documents);
    }

    /**
     * Bulk save in elasticsearch
     *
     * @param metaDaCollection
     * @param collection       of documents
     * @throws DatabaseException
     */
    private void bulkElasticsearch(MetadataCollections metaDaCollection, List<Document> collection)
        throws DatabaseException {
        this.vitamRepositoryProvider.getVitamESRepository(metaDaCollection).save(collection);
    }

    /**
     * @param graphData
     * @return
     * @throws InvalidParseOperationException
     */
    private UpdateOneModel<Document> createUpdateOneModel(JsonNode graphData) throws InvalidParseOperationException {
        JsonNode id = ((ObjectNode) graphData).remove(Unit.ID);
        final Document data = new Document($_SET, Document.parse(JsonHandler.writeAsString(graphData)));
        return new UpdateOneModel<>(eq(Unit.ID, id.asText()), data, new UpdateOptions().upsert(true));
    }


    /**
     * @param document
     * @return
     * @throws InvalidParseOperationException
     */
    private WriteModel<Document> createReplaceOneModel(Document document) {
        final Object glpd = document.get(Unit.GRAPH_LAST_PERSISTED_DATE);

        Bson filter;
        if (null == glpd) {
            // Document not yet in mongodb or in mongodb with but without graph data
            filter = and(
                eq(ID, document.get(ID)),
                exists(Unit.GRAPH, false)
            );
        } else {
            // Document already exists in mongodb and already have graph data
            filter = and(
                eq(ID, document.get(ID)),
                eq(Unit.GRAPH_LAST_PERSISTED_DATE, glpd.toString())
            );
        }
        return new ReplaceOneModel<>(filter, document, new UpdateOptions().upsert(true));
    }
}
