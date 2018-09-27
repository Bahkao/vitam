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
package fr.gouv.vitam.functional.administration.common.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.api.VitamRepositoryProvider;
import fr.gouv.vitam.common.database.api.impl.VitamElasticsearchRepository;
import fr.gouv.vitam.common.database.api.impl.VitamMongoRepository;
import fr.gouv.vitam.common.database.offset.OffsetRepository;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.*;
import fr.gouv.vitam.functional.administration.common.api.ReconstructionService;
import fr.gouv.vitam.functional.administration.common.api.RestoreBackupService;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.storage.engine.common.exception.StorageException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;
import org.bson.Document;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Filters.eq;
import static fr.gouv.vitam.common.database.server.mongodb.VitamDocument.ID;
import static fr.gouv.vitam.common.database.server.mongodb.VitamDocument.TENANT_ID;

/**
 * Reconstrution of Vitam Collections.<br>
 */
public class ReconstructionServiceImpl implements ReconstructionService {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ReconstructionServiceImpl.class);

    private static final String STRATEGY_ID = "default";
    private static final int ADMIN_TENANT = VitamConfiguration.getAdminTenant();

    private RestoreBackupService recoverBuckupService;

    private VitamRepositoryProvider vitamRepositoryProvider;

    private static final String RECONSTRUCTION_ITEM_MONDATORY_MSG = "the item defining reconstruction is mandatory.";
    private static final String RECONSTRUCTION_COLLECTION_MONDATORY_MSG = "the collection to reconstruct is mondatory.";
    private static final String RECONSTRUCTION_TENANT_MONDATORY_MSG = "the tenant to reconstruct is mondatory.";
    private static final String RECONSTRUCTION_LIMIT_POSITIVE_MSG = "the limit to reconstruct is should at least 0.";

    private static final String TOTAL_OBJECT_GROUPS_INGESTED = "TotalObjectGroups_ingested";
    private static final String TOTAL_OBJECT_GROUPS_DELETED = "TotalObjectGroups_deleted";
    private static final String TOTAL_OBJECT_GROUPS_REMAINED = "TotalObjectGroups_remained";
    private static final String TOTAL_UNITS_INGESTED = "TotalUnits_ingested";
    private static final String TOTAL_UNITS_DELETED = "TotalUnits_deleted";
    private static final String TOTAL_UNITS_REMAINED = "TotalUnits_remained";
    private static final String TOTAL_OBJECT_INGEST = "TotalObjects_ingested";
    private static final String TOTAL_OBJECT_DELETED = "TotalObjects_deleted";
    private static final String TOTAL_OBJECT_REMAINED = "TotalObjects_remained";
    private static final String OBJECT_SIZE_INGESTED = "ObjectSize_ingested";
    private static final String OBJECT_SIZE_DELETED = "ObjectSize_deleted";
    private static final String OBJECT_SIZE_REMAINED = "ObjectSize_remained";

    private OffsetRepository offsetRepository;

    public ReconstructionServiceImpl(VitamRepositoryProvider vitamRepositoryProvider) {
        this(vitamRepositoryProvider, new RestoreBackupServiceImpl());
    }

    public ReconstructionServiceImpl(VitamRepositoryProvider vitamRepositoryProvider, RestoreBackupService recoverBuckupService, OffsetRepository offsetRepository) {
        this.vitamRepositoryProvider = vitamRepositoryProvider;
        this.recoverBuckupService = recoverBuckupService;
        this.offsetRepository = offsetRepository;
    }

    @VisibleForTesting
    public ReconstructionServiceImpl(VitamRepositoryProvider vitamRepositoryProvider, RestoreBackupService recoverBuckupService) {
        this.vitamRepositoryProvider = vitamRepositoryProvider;
        this.recoverBuckupService = recoverBuckupService;
    }

    /**
     * purge collection content and reconstruct the content.
     *
     * @param collection the collection to reconstruct.
     * @param tenants    the given tenant.
     */
    @Override
    public void reconstruct(FunctionalAdminCollections collection, Integer... tenants) throws DatabaseException {

        ParametersChecker.checkParameter("All parameters [%s, %s] are required.", collection, tenants);
        LOGGER.debug(String
            .format("Start reconstruction of the %s collection on the Vitam tenant %s.", collection.getType(),
                tenants));

        Integer originalTenant = VitamThreadUtils.getVitamSession().getTenantId();

        final VitamMongoRepository mongoRepository = vitamRepositoryProvider.getVitamMongoRepository(collection.getVitamCollection());
        final VitamElasticsearchRepository elasticsearchRepository =
            vitamRepositoryProvider.getVitamESRepository(collection.getVitamCollection());

        final VitamMongoRepository sequenceRepository =
            vitamRepositoryProvider.getVitamMongoRepository(FunctionalAdminCollections.VITAM_SEQUENCE.getVitamCollection());

        switch (collection) {
            case CONTEXT:
            case FORMATS:
            case SECURITY_PROFILE:
            case VITAM_SEQUENCE:
                // TODO: 1/3/18 admin tenant must be request from configuration
                tenants = new Integer[] {ADMIN_TENANT};
                break;
        }
        try {
            for (Integer tenant : tenants) {
                // This is a hak, we must set manually the tenant is the VitamSession (used and transmitted in the headers)
                VitamThreadUtils.getVitamSession().setTenantId(tenant);

                // get the last version of the backup copies.
                Optional<CollectionBackupModel> collectionBackup =
                    recoverBuckupService.readLatestSavedFile(STRATEGY_ID, collection);

                // reconstruct Vitam collection from the backup copy.
                if (collectionBackup.isPresent()) {
                    LOGGER.debug(String.format("Last backup copy version : %s", collectionBackup));

                    // purge collection content
                    if (collection.isMultitenant()) {
                        mongoRepository.purge(tenant);
                        elasticsearchRepository.purge(tenant);
                    } else {
                        mongoRepository.purge();
                        elasticsearchRepository.purge();
                    }

                    // saving the sequence & backup sequence in mongoDB
                    restoreSequence(sequenceRepository, collectionBackup.get().getSequence());
                    restoreSequence(sequenceRepository, collectionBackup.get().getBackupSequence());

                    // saving the backup collection in mongoDB and elasticSearch.
                    mongoRepository.save(collectionBackup.get().getDocuments());
                    elasticsearchRepository.save(collectionBackup.get().getDocuments());

                    // log the recontruction of Vitam collection.
                    LOGGER.debug(String
                        .format(
                            "[Reconstruction]: the collection {%s} has been reconstructed on the tenants {%s} at %s",
                            collectionBackup, tenants, LocalDateUtil.now()));
                }
            }
        } finally {
            VitamThreadUtils.getVitamSession().setTenantId(originalTenant);
        }

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

        if(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL.getName().toLowerCase().equals(reconstructionItem.getCollection().toLowerCase())) {
            return reconstructCollection(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL,
                    reconstructionItem.getTenant(), reconstructionItem.getLimit());
        } else if(FunctionalAdminCollections.ACCESSION_REGISTER_SYMBOLIC.getName().toLowerCase().equals(reconstructionItem.getCollection().toLowerCase())) {
            return reconstructCollection(FunctionalAdminCollections.ACCESSION_REGISTER_SYMBOLIC,
                    reconstructionItem.getTenant(), reconstructionItem.getLimit());
        } else {
            throw new IllegalArgumentException(String.format("ERROR: Invalid collection {%s}", reconstructionItem.getCollection()));
        }
    }

    /**
     * Reconstruct collection.
     *
     * @param collection collection
     * @param tenant     tenant
     * @param limit      number of data to reconstruct
     * @return response of reconstruction
     */
    private ReconstructionResponseItem reconstructCollection(FunctionalAdminCollections collection, int tenant, int limit) {

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
                case ACCESSION_REGISTER_DETAIL:
                    type = DataCategory.ACCESSION_REGISTER_DETAIL;
                    break;
                case ACCESSION_REGISTER_SYMBOLIC:
                    type = DataCategory.ACCESSION_REGISTER_SYMBOLIC;
                    break;
                default:
                    throw new IllegalArgumentException(String.format("ERROR: Invalid collection {%s}", collection));
            }

            // get the list of datas to backup.
            List<OfferLog> listing = recoverBuckupService.getListing(STRATEGY_ID, type, offset, limit, Order.ASC);
            List<List<OfferLog>> partition = Lists.partition(listing, VitamConfiguration.getRestoreBulkSize());
            Set<String> originatingAgencies = new HashSet<String>();

            for (List<OfferLog> listingBulk : partition) {

                List<AccessionRegisterBackupModel> dataFromOffer = new ArrayList<>();
                for (OfferLog offerLog : listingBulk) {
                    AccessionRegisterBackupModel model = recoverBuckupService
                            .loadData(STRATEGY_ID, collection, offerLog.getFileName(), offerLog.getSequence());
                    if (model != null && model.getAccessionRegister() != null && model.getOffset() != null) {
                        originatingAgencies.add((model.getAccessionRegister().getString("OriginatingAgency")));
                        dataFromOffer.add(model);
                    } else {
                        throw new StorageException(String.format(
                                "[Reconstruction]: Data is not present in file {%s} for the collection {%s} on the tenant {%s}",
                                offerLog.getFileName(), collection, tenant));
                    }
                }

                // reconstruct Vitam collection from the backup datas.
                if (dataFromOffer.isEmpty()) {
                    continue;
                }


                reconstructCollectionAccessionRegister(collection, dataFromOffer,
                        VitamConfiguration.getOptimisticLockRetryNumber());
                AccessionRegisterBackupModel last = Iterables.getLast(dataFromOffer);
                newOffset = last.getOffset();


                // log the recontruction of Vitam collection.
                LOGGER.info(String.format(
                        "[Reconstruction]: the collection {%s} has been reconstructed on the tenant {%s} from {offset:%s} at %s",
                        collection.name(), tenant, offset, LocalDateUtil.now()));
            }

            if(collection.equals(FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL)) {
                this.computeAccessionRegisterSummary(originatingAgencies, tenant);
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
        } finally {
            offsetRepository.createOrUpdateOffset(tenant, collection.getName(), newOffset);
            VitamThreadUtils.getVitamSession().setTenantId(originalTenant);
        }
        return response;
    }

    /**
     * Reconstruct metadatas in databases
     *
     * @param collection    the concerning collection
     * @param dataFromOffer list of items to back up
     * @throws DatabaseException databaseException
     */
    private void reconstructCollectionAccessionRegister(FunctionalAdminCollections collection, List<AccessionRegisterBackupModel> dataFromOffer,
                                                Integer nbRetry)
            throws DatabaseException {
        if (nbRetry < 0) {
            throw new DatabaseException("Optimistic lock number of retry reached");
        }
        LOGGER.info("[Reconstruction]: Back up of Accession Register bulk");

        // Create bulk of ReplaceOneModel
        List<WriteModel<Document>> metadatas =
                dataFromOffer.stream().map(AccessionRegisterBackupModel::getAccessionRegister).map(this::createReplaceOneModel)
                        .collect(Collectors.toList());
        try {
            this.bulkMongo(collection, metadatas);
        } catch (DatabaseException e) {
            if (e.getCause() instanceof MongoBulkWriteException) {
                LOGGER.warn("[Reconstruction]: [Optimistic_Lock]: optimistic lock occurs while reconstruct Accession Register");

                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(VitamConfiguration.getOptimisticLockSleepTime()));
                } catch (InterruptedException e1) {
                    SysErrLogger.FAKE_LOGGER.ignoreLog(e1);
                    throw new DatabaseException(e1);
                }

                nbRetry--; // Retry after optimistic lock problem
                reconstructCollectionAccessionRegister(collection, dataFromOffer, nbRetry);
                return;
            } else {
                throw e;
            }

        }

        List<Document> documents =
                dataFromOffer.stream().map(AccessionRegisterBackupModel::getAccessionRegister).collect(Collectors.toList());
        bulkElasticsearch(collection, documents);
    }

    private void restoreSequence(VitamMongoRepository sequenceRepository,
        VitamSequence sequenceCollection) throws DatabaseException {
        sequenceRepository
            .removeByNameAndTenant(sequenceCollection.getName(), sequenceCollection.getTenantId());
        sequenceRepository.save(sequenceCollection);
    }

    @Override
    public void reconstruct(FunctionalAdminCollections collection) throws DatabaseException {

        ParametersChecker.checkParameter("The collection parameter is required.", collection);
        LOGGER.debug(String
            .format("Start reconstruction of the %s collection on all of the Vitam tenants.",
                collection.getType()));

        // get the list of vitam tenants from the configuration.
        List<Integer> tenants = VitamConfiguration.getTenants();

        // reconstruct all the Vitam tenants from the backup copy.
        if (null != tenants && !tenants.isEmpty()) {
            LOGGER.debug(String.format("Reconstruction of %s Vitam tenants", tenants.size()));
            // reconstruction of the list of tenants
            reconstruct(collection, tenants.stream().toArray(Integer[]::new));
        }
    }

    /**
     * @param document
     * @return
     * @throws InvalidParseOperationException
     */
    private WriteModel<Document> createReplaceOneModel(Document document) {
        Bson filter = eq(ID, document.get(ID));
        return new ReplaceOneModel<>(filter, document, new UpdateOptions().upsert(true));
    }

    /**
     * Bulk write in mongodb
     *
     * @param faCollection
     * @param collection
     * @throws DatabaseException
     */
    private void bulkMongo(FunctionalAdminCollections faCollection, List<WriteModel<Document>> collection)
            throws DatabaseException {
        this.vitamRepositoryProvider.getVitamMongoRepository(faCollection.getVitamCollection()).update(collection);
    }

    /**
     * Bulk save in elasticsearch
     *
     * @param faCollection
     * @param collection       of documents
     * @throws DatabaseException
     */
    private void bulkElasticsearch(FunctionalAdminCollections faCollection, List<Document> collection)
            throws DatabaseException {
        this.vitamRepositoryProvider.getVitamESRepository(faCollection.getVitamCollection()).save(collection);
    }

    public void computeAccessionRegisterSummary(String originatingAgency, Integer tenant) {
        computeAccessionRegisterSummary(Sets.newHashSet(originatingAgency), tenant);
    }

    public void computeAccessionRegisterSummary(Set<String> originatingAgencies, Integer tenant) {

        ParametersChecker.checkParameter("All params are required", originatingAgencies, tenant);
        int originalTenant = VitamThreadUtils.getVitamSession().getTenantId();

        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenant);
            Bson query = Filters.and(Filters.in(AccessionRegisterDetail.ORIGINATING_AGENCY, originatingAgencies), Filters.eq(AccessionRegisterDetail.TENANT_ID, tenant));

            AggregateIterable<Document> aggregate =
                    FunctionalAdminCollections.ACCESSION_REGISTER_DETAIL.getCollection().aggregate(Arrays.asList(
                            Aggregates.match(query),
                            //new Document("$match", query),
                            new Document("$group",
                                    new Document(ID, new Document(AccessionRegisterDetail.ORIGINATING_AGENCY, "$" + AccessionRegisterDetail.ORIGINATING_AGENCY))
                                            .append(TOTAL_OBJECT_GROUPS_INGESTED, new Document("$sum", "$TotalObjectGroups.ingested"))
                                            .append(TOTAL_OBJECT_GROUPS_DELETED, new Document("$sum", "$TotalObjectGroups.deleted"))
                                            .append(TOTAL_OBJECT_GROUPS_REMAINED, new Document("$sum", "$TotalObjectGroups.remained"))

                                            .append(TOTAL_UNITS_INGESTED, new Document("$sum", "$TotalUnits.ingested"))
                                            .append(TOTAL_UNITS_DELETED, new Document("$sum", "$TotalUnits.deleted"))
                                            .append(TOTAL_UNITS_REMAINED, new Document("$sum", "$TotalUnits.remained"))

                                            .append(TOTAL_OBJECT_INGEST, new Document("$sum", "$TotalObjects.ingested"))
                                            .append(TOTAL_OBJECT_DELETED, new Document("$sum", "$TotalObjects.deleted"))
                                            .append(TOTAL_OBJECT_REMAINED, new Document("$sum", "$TotalObjects.remained"))

                                            .append(OBJECT_SIZE_INGESTED, new Document("$sum", "$ObjectSize.ingested"))
                                            .append(OBJECT_SIZE_DELETED, new Document("$sum", "$ObjectSize.deleted"))
                                            .append(OBJECT_SIZE_REMAINED, new Document("$sum", "$ObjectSize.remained"))
                            ),
                            new Document("$project",
                                    new Document(AccessionRegisterDetail.ORIGINATING_AGENCY, 1)
                                            .append(TOTAL_OBJECT_GROUPS_INGESTED, 1)
                                            .append(TOTAL_OBJECT_GROUPS_DELETED, 1)
                                            .append(TOTAL_OBJECT_GROUPS_REMAINED, 1)

                                            .append(TOTAL_UNITS_INGESTED, 1)
                                            .append(TOTAL_UNITS_DELETED, 1)
                                            .append(TOTAL_UNITS_REMAINED, 1)

                                            .append(TOTAL_OBJECT_INGEST, 1)
                                            .append(TOTAL_OBJECT_DELETED, 1)
                                            .append(TOTAL_OBJECT_REMAINED, 1)

                                            .append(OBJECT_SIZE_INGESTED, 1)
                                            .append(OBJECT_SIZE_DELETED, 1)
                                            .append(OBJECT_SIZE_REMAINED, 1)
                            )
                    ), Document.class);

            List<Document> documents = Lists.newArrayList(aggregate.iterator());
            Set<Document> accessionRegisterSummary = new HashSet<>();
            for(Document doc : documents) {
                Document registerSummaryDoc = new Document()
                        .append(AccessionRegisterSummary.ORIGINATING_AGENCY, ((Document)doc.get(ID)).get(AccessionRegisterDetail.ORIGINATING_AGENCY))

                                .append(AccessionRegisterSummary.TOTAL_OBJECTGROUPS, new Document()
                                        .append(AccessionRegisterSummary.INGESTED, doc.get(TOTAL_OBJECT_GROUPS_INGESTED))
                                        .append(AccessionRegisterSummary.DELETED, doc.get(TOTAL_OBJECT_GROUPS_DELETED))
                                        .append(AccessionRegisterSummary.REMAINED, doc.get(TOTAL_OBJECT_GROUPS_REMAINED))
                                )

                                .append(AccessionRegisterSummary.TOTAL_UNITS, new Document()
                                        .append(AccessionRegisterSummary.INGESTED, doc.get(TOTAL_UNITS_INGESTED))
                                        .append(AccessionRegisterSummary.DELETED, doc.get(TOTAL_UNITS_DELETED))
                                        .append(AccessionRegisterSummary.REMAINED, doc.get(TOTAL_UNITS_REMAINED))
                                )

                                .append(AccessionRegisterSummary.TOTAL_OBJECTS, new Document()
                                        .append(AccessionRegisterSummary.INGESTED, doc.get(TOTAL_OBJECT_INGEST))
                                        .append(AccessionRegisterSummary.DELETED, doc.get(TOTAL_OBJECT_DELETED))
                                        .append(AccessionRegisterSummary.REMAINED, doc.get(TOTAL_OBJECT_REMAINED))
                                )

                                .append(AccessionRegisterSummary.OBJECT_SIZE, new Document()
                                        .append(AccessionRegisterSummary.INGESTED, doc.get(OBJECT_SIZE_INGESTED))
                                        .append(AccessionRegisterSummary.DELETED, doc.get(OBJECT_SIZE_DELETED))
                                        .append(AccessionRegisterSummary.REMAINED, doc.get(OBJECT_SIZE_REMAINED))
                                )

                                .append(TENANT_ID, tenant)

                                .append(AccessionRegisterSummary.CREATION_DATE, LocalDateUtil.getFormattedDateForMongo(
                                        LocalDateTime.now()));

                BasicDBObject searchQuery = new BasicDBObject();
                searchQuery.put(AccessionRegisterSummary.ORIGINATING_AGENCY, registerSummaryDoc.getString(AccessionRegisterSummary.ORIGINATING_AGENCY));
                MongoCursor registerSummaryIt = (FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY.getCollection().find(searchQuery)).iterator();
                if(registerSummaryIt.hasNext()) {
                    registerSummaryDoc.append(ID, ((AccessionRegisterSummary)registerSummaryIt.next()).get(ID));
                } else {
                    registerSummaryDoc.append(ID, GUIDFactory.newAccessionRegisterSummaryGUID(ParameterHelper.getTenantParameter()).getId());
                }
                accessionRegisterSummary.add(registerSummaryDoc);
            }

            // Create bulk of ReplaceOneModel
            List<WriteModel<Document>> collectionWM =
                    accessionRegisterSummary.stream().map(this::createReplaceOneModel)
                            .collect(Collectors.toList());
            try {
                this.bulkMongo(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, collectionWM);
                this.bulkElasticsearch(FunctionalAdminCollections.ACCESSION_REGISTER_SUMMARY, Lists.newArrayList(accessionRegisterSummary));
            } catch (DatabaseException e) {
                if (e.getCause() instanceof MongoBulkWriteException) {
                    LOGGER.warn("[Reconstruction]: [Optimistic_Lock]: optimistic lock occurs while reconstruct Accession Register");
                } else {
                    LOGGER.error(e);
                }

            }

        } finally {
            VitamThreadUtils.getVitamSession().setTenantId(originalTenant);
        }

    }
}
