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
package fr.gouv.vitam.metadata.core.database.collections;

import com.mongodb.DBObject;
import com.mongodb.client.MongoCursor;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.GlobalDatas;
import fr.gouv.vitam.common.database.collections.VitamCollection;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchAccess;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchFacetResultHelper;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchUtil;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.metadata.core.database.configuration.GlobalDatasDb;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.sort.SortBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * ElasticSearch model with MongoDB as main database
 */
public class ElasticsearchAccessMetadata extends ElasticsearchAccess {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ElasticsearchAccessMetadata.class);

    public static final String MAPPING_UNIT_FILE = "/unit-es-mapping.json";
    public static final String MAPPING_OBJECT_GROUP_FILE = "/og-es-mapping.json";

    private static final String OFFSET_LIMIT_INCORRECT =
        "Offset and limit are incorrect, the request could not be executed";

    /**
     * @param clusterName cluster name
     * @param nodes list of elasticsearch node
     * @throws VitamException if nodes list is empty
     */
    public ElasticsearchAccessMetadata(final String clusterName, List<ElasticsearchNode> nodes)
        throws VitamException, IOException {
        super(clusterName, nodes);
    }

    /**
     * Delete one index
     *
     * @param collection the working metadata collection
     * @param tenantId the tenant for operation
     * @return True if ok
     */
    public final boolean deleteIndex(final MetadataCollections collection, Integer tenantId) {
        try {
            if (client.admin().indices().prepareExists(getAliasName(collection, tenantId)).get().isExists()) {
                if (!client.admin().indices().prepareDelete(getAliasName(collection, tenantId)).get()
                    .isAcknowledged()) {
                    LOGGER.error("Error on index delete");
                }
            }
            refreshIndex(collection, tenantId);
            return true;
        } catch (final Exception e) {
            LOGGER.error("Error while deleting index", e);
            return true;
        }
    }

    /**
     * Add a type to an index
     *
     * @param collection the working metadata collection
     * @param tenantId the tenant for operation
     * @return True if ok
     */
    public final boolean addIndex(final MetadataCollections collection, Integer tenantId) {
        try {
            super.createIndexAndAliasIfAliasNotExists(collection.getName().toLowerCase(), getMapping(collection),
                VitamCollection.getTypeunique(), tenantId);
        } catch (final Exception e) {
            LOGGER.error("Error while set Mapping", e);
            return false;
        }
        return true;
    }

    /**
     * refresh an index
     *
     * @param collection the workking metadata collection
     * @param tenantId the tenant for operation
     */
    public final void refreshIndex(final MetadataCollections collection, Integer tenantId) {
        String allIndexes = getAliasName(collection, tenantId);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("refreshIndex: " + allIndexes);
        }
        RefreshResponse response =
            client.admin().indices().refresh(new RefreshRequest(allIndexes)).actionGet();
        LOGGER.debug("Refresh request executed with {} successfull shards", response.getSuccessfulShards());
    }

    /**
     * Add an entry in the ElasticSearch index
     *
     * @param collection
     * @param tenantId
     * @param id
     * @param json
     * @return True if ok
     */
    final boolean addEntryIndex(final MetadataCollections collection, final Integer tenantId, final String id,
        final String json) {
        final String type = VitamCollection.getTypeunique();
        return client.prepareIndex(getAliasName(collection, tenantId), type, id).setSource(json)
            .setOpType(OpType.INDEX)
            .get().getVersion() > 0;
    }

    /**
     * Add a set of entries in the ElasticSearch index. <br>
     * Used in reload from scratch.
     *
     * @param collection
     * @param mapIdJson
     * @return the listener on bulk insert
     */

    final ListenableActionFuture<BulkResponse> addEntryIndexes(final MetadataCollections collection,
        final Integer tenantId, final Map<String, String> mapIdJson) {
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        // either use client#prepare, or use Requests# to directly build
        // index/delete requests
        final String type = VitamCollection.getTypeunique();
        for (final Entry<String, String> val : mapIdJson.entrySet()) {
            bulkRequest.add(client.prepareIndex(getAliasName(collection, tenantId), type, val.getKey())
                .setSource(val.getValue(), XContentType.JSON));
        }
        return bulkRequest.execute(); // new thread
    }

    /**
     * Add a set of entries in the ElasticSearch index in blocking mode. <br>
     * Used in reload from scratch.
     *
     * @param collection
     * @param mapIdJson
     * @return True if ok
     */
    final boolean addEntryIndexesBlocking(final MetadataCollections collection, final Integer tenantId,
        final Map<String, String> mapIdJson) {
        final BulkResponse bulkResponse = addEntryIndexes(collection, tenantId, mapIdJson).actionGet();
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES previous insert in error: " + bulkResponse.buildFailureMessage());
        }
        return !bulkResponse.hasFailures();
        // Should process failures by iterating through each bulk response item
    }

    /**
     * Method to filter what should never be indexed
     *
     * @param unit
     * @return the new Unit without the unwanted fields
     */
    private static final Unit getFiltered(final Unit unit) {
        final Unit eunit = new Unit(unit);
        eunit.remove(VitamDocument.SCORE);
        return eunit;
    }

    /**
     * Add one VitamDocument to indexation immediately
     *
     * @param document the {@link MetadataDocument} for indexing
     * @param tenantId the tenant for operation
     * @return True if inserted in ES
     */
    public final boolean addEntryIndex(final MetadataDocument<?> document, Integer tenantId) {
        MetadataDocument<?> newdoc = document;
        MetadataCollections collection = MetadataCollections.OBJECTGROUP;
        if (newdoc instanceof Unit) {
            collection = MetadataCollections.UNIT;
            newdoc = getFiltered((Unit) newdoc);
        }
        final String id = newdoc.getId();
        newdoc.remove(VitamDocument.ID);
        final String mongoJson = newdoc.toJson(new JsonWriterSettings(JsonMode.STRICT));
        newdoc.clear();
        // TODO P1 test bson4jackson
        // ( https://github.com/michel-kraemer/bson4jackson)
        final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
        final String esJson = dbObject.toString();
        return addEntryIndex(collection, tenantId, id, esJson);
    }

    /**
     * Used for iterative reload in restore operation (using bulk).
     *
     * @param indexes set of operation index
     * @param tenantId the tenant for operation
     * @param document the {@link MetadataDocument} for indexing
     * @return the number of Unit incorporated (0 if none)
     */
    public final int addBulkEntryIndex(final Map<String, String> indexes, final Integer tenantId,
        final MetadataDocument<?> document) {
        MetadataDocument<?> newdoc = document;
        MetadataCollections collection = MetadataCollections.OBJECTGROUP;
        if (newdoc instanceof Unit) {
            collection = MetadataCollections.UNIT;
            newdoc = getFiltered((Unit) newdoc);
        }
        final String id = newdoc.getId();
        newdoc.remove(VitamDocument.ID);

        final String mongoJson = newdoc.toJson(new JsonWriterSettings(JsonMode.STRICT));
        final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
        indexes.put(id, dbObject.toString());
        int nb = 0;
        if (indexes.size() > GlobalDatasDb.LIMIT_ES_NEW_INDEX) {
            nb = indexes.size();
            addEntryIndexes(collection, tenantId, indexes);
            indexes.clear();
        }
        newdoc.clear();
        return nb;
    }

    /**
     * Update an entry in the ElasticSearch index
     *
     * @param collection
     * @param id
     * @param json
     * @return True if ok
     * @throws Exception
     */
    final boolean updateEntryIndex(final MetadataCollections collection, final Integer tenantId, final String id,
        final String json)
        throws Exception {
        final String type = VitamCollection.getTypeunique();
        return client.prepareUpdate(getAliasName(collection, tenantId), type, id)
            .setDoc(json, XContentType.JSON)
            .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
            .execute().actionGet().getVersion() > 1;
    }

    /**
     * Insert in bulk mode from cursor<br>
     * <br>
     * Insert a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all Units to be indexed
     * @throws MetaDataExecutionException if the bulk insert failed
     */
    final void insertBulkUnitsEntriesIndexes(MongoCursor<Unit> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES insert in error since no results to insert");
            throw new MetaDataExecutionException("No result to insert");
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int max = VitamConfiguration.getMaxElasticsearchBulk();
        while (cursor.hasNext()) {
            max--;
            final Unit unit = getFiltered(cursor.next());
            final String id = unit.getId();
            unit.remove(VitamDocument.ID);
            LOGGER.debug("DEBUG insert {}", unit);

            final String mongoJson = unit.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toInsert = dbObject.toString().trim();
            if (toInsert.isEmpty()) {
                LOGGER.error("ES insert in error since result to insert is empty");
                throw new MetaDataExecutionException("Result to insert is empty");
            }
            bulkRequest.add(client
                .prepareIndex(getAliasName(MetadataCollections.UNIT, tenantId), VitamCollection.getTypeunique(), id)
                .setSource(toInsert, XContentType.JSON));
            if (max == 0) {
                max = VitamConfiguration.getMaxElasticsearchBulk();
                final BulkResponse bulkResponse =
                    bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new thread

                if (bulkResponse.hasFailures()) {
                    int duplicates = 0;
                    for (final BulkItemResponse bulkItemResponse : bulkResponse) {
                        if (bulkItemResponse.getVersion() > 1) {
                            duplicates++;
                        }
                    }
                    LOGGER.error("ES insert in error with possible duplicates {}: {}", duplicates,
                        bulkResponse.buildFailureMessage());
                    throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
                }
                bulkRequest = client.prepareBulk();
            }
        }
        if (bulkRequest.numberOfActions() > 0) {
            final BulkResponse bulkResponse =
                bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
            if (bulkResponse.hasFailures()) {
                int duplicates = 0;
                for (final BulkItemResponse bulkItemResponse : bulkResponse) {
                    if (bulkItemResponse.getVersion() > 1) {
                        duplicates++;
                    }
                }
                LOGGER.error("ES insert in error with possible duplicates {}: {}", duplicates,
                    bulkResponse.buildFailureMessage());
                throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
            }
        }
    }

    /**
     * updateBulkUnitsEntriesIndexes
     * <p>
     * Update a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all Units to be indexed
     * @throws MetaDataExecutionException if the bulk update failed
     */
    final void updateBulkUnitsEntriesIndexes(MongoCursor<Unit> cursor, Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES update in error since no results to update");
            throw new MetaDataExecutionException("No result to update");
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int max = VitamConfiguration.getMaxElasticsearchBulk();
        while (cursor.hasNext()) {
            max--;
            final Unit unit = getFiltered(cursor.next());
            final String id = unit.getId();
            unit.remove(VitamDocument.ID);

            final String mongoJson = unit.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toUpdate = dbObject.toString().trim();
            if (toUpdate.isEmpty()) {
                LOGGER.error("ES update in error since result to update is empty");
                throw new MetaDataExecutionException("Result to update is empty");
            }

            bulkRequest
                .add(client
                    .prepareUpdate(getAliasName(MetadataCollections.UNIT, tenantId), VitamCollection.getTypeunique(),
                        id)
                    .setDoc(toUpdate, XContentType.JSON));
            // .setDoc(toUpdate));
            if (max == 0) {
                max = VitamConfiguration.getMaxElasticsearchBulk();
                final BulkResponse bulkResponse =
                    bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
                if (bulkResponse.hasFailures()) {
                    LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
                    throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
                }
                bulkRequest = client.prepareBulk();
            }
        }
        if (bulkRequest.numberOfActions() > 0) {
            final BulkResponse bulkResponse =
                bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
            if (bulkResponse.hasFailures()) {
                LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
                throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
            }
        }
    }

    /**
     * @param collection
     * @param tenantId
     * @param type
     * @param query as in DSL mode "{ "fieldname" : "value" }" "{ "match" : { "fieldname" : "value" } }" "{ "ids" : { "
     * values" : [list of id] } }"
     * @param sorts the list of sort
     * @param facets the list of facet
     * @return a structure as ResultInterface
     * @throws MetaDataExecutionException
     * @throws BadRequestException
     */
    protected final Result search(final MetadataCollections collection, final Integer tenantId, final String type,
        final QueryBuilder query, final List<SortBuilder> sorts, int offset, Integer limit,
        final List<AggregationBuilder> facets, final String scrollId, final Integer scrollTimeout)
        throws MetaDataExecutionException, BadRequestException {

        final SearchResponse response;
        final SearchRequestBuilder request;
        final boolean isUnit = collection == MetadataCollections.UNIT;
        final Result<?> resultRequest =
            isUnit ? MongoDbMetadataHelper.createOneResult(FILTERARGS.UNITS)
                : MongoDbMetadataHelper.createOneResult(FILTERARGS.OBJECTGROUPS);
        if (scrollId != null && !scrollId.isEmpty()) {
            int limitES = (limit != null && limit > 0) ? limit : GlobalDatasDb.DEFAULT_LIMIT_SCROLL;
            int scrollTimeoutES =
                (scrollTimeout != null && scrollTimeout > 0) ? scrollTimeout : GlobalDatasDb.DEFAULT_SCROLL_TIMEOUT;
            request = client.prepareSearch(getAliasName(collection, tenantId))
                .setScroll(new TimeValue(scrollTimeoutES))
                .setQuery(query)
                .setSize(limitES);
            if (sorts != null) {
                sorts.forEach(request::addSort);
            }
            if (scrollId.equals(GlobalDatasDb.SCROLL_ACTIVATE_KEYWORD)) {
                response = request.get();
            } else {
                response = client.prepareSearchScroll(scrollId).setScroll(new TimeValue(scrollTimeoutES)).execute()
                    .actionGet();
            }
            resultRequest.setScrollId(response.getScrollId());
        } else {
            // Note: Could change the code to allow multiple indexes and multiple
            // types
            request = client.prepareSearch(getAliasName(collection, tenantId))
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(type).setExplain(false)
                .setSize(GlobalDatas.LIMIT_LOAD).setFetchSource(MetadataDocument.ES_PROJECTION, null);
            if (offset != -1) {
                request.setFrom(offset);
            }
            if (limit != -1) {
                request.setSize(limit);
            }
            if (sorts != null) {
                sorts.forEach(request::addSort);
            }
            if (facets != null) {
                facets.forEach(request::addAggregation);
            }

            request.setQuery(query);

            if (GlobalDatasDb.PRINT_REQUEST) {
                LOGGER.warn("ESReq: {}", request);
            } else {
                LOGGER.debug("ESReq: {}", request);
            }
            try {
                response = request.get();
            } catch (final Exception e) {
                LOGGER.debug(e.getMessage(), e);
                if (e instanceof SearchPhaseExecutionException &&
                    (offset + limit > VitamConfiguration.getMaxResultWindow())) {
                    throw new BadRequestException(OFFSET_LIMIT_INCORRECT);
                }
                throw new MetaDataExecutionException(e.getMessage(), e);
            }
        }

        if (response.status() != RestStatus.OK) {
            LOGGER.debug("Error " + response.status() + " from : " + request + ":" + query);
            throw new MetaDataExecutionException("Error " + response.status());
        }
        final SearchHits hits = response.getHits();
        if (hits.getHits().length > GlobalDatas.LIMIT_LOAD) {
            LOGGER.warn("Warning, more than " + GlobalDatas.LIMIT_LOAD + " hits: " + hits.getTotalHits());
        }
        if (hits.getTotalHits() == 0) {
            LOGGER.debug("No result from : " + request);
            return isUnit ? MongoDbMetadataHelper.createOneResult(FILTERARGS.UNITS)
                : MongoDbMetadataHelper.createOneResult(FILTERARGS.OBJECTGROUPS);
        }

        for (SearchHit hit : hits) {
            final String id = hit.getId();
            resultRequest.addId(id, hit.getScore());
        }
        if (GlobalDatasDb.PRINT_REQUEST) {
            LOGGER.debug("FinalEsResult: {} : {}", resultRequest.getCurrentIds(), resultRequest.getNbResult());
        }
        resultRequest.setTotal(hits.getTotalHits());

        // facets
        Aggregations aggregations = response.getAggregations();
        if (aggregations != null) {
            for (Aggregation aggregation : aggregations) {
                resultRequest
                    .addFacetResult(ElasticsearchFacetResultHelper.transformFromEsAggregation(aggregation));
            }
        }

        return resultRequest;
    }

    /**
     * Makes a search request on elasticsearch on a collection with aggregations and a query
     *
     * @param collection on which the request is made
     * @param tenantId on which the request is made
     * @param aggregations elasticsearch
     * @param query elasticsearch
     * @return the elasticsearch SearchResponse
     */
    public SearchResponse basicSearch(MetadataCollections collection, Integer tenantId,
        List<AggregationBuilder> aggregations, QueryBuilder query) {
        SearchRequestBuilder request = client.prepareSearch(getAliasName(collection, tenantId)).setQuery(query);
        aggregations.forEach(request::addAggregation);
        return request
            .execute()
            .actionGet();
    }

    /**
     * @param collections the working collection
     * @param tenantId the tenant for operation
     * @param type the type of document to delete
     * @param id the id of document to delete
     * @throws MetaDataExecutionException if query operation exception occurred
     * @throws MetaDataNotFoundException if item not found when deleting
     */
    public final void deleteEntryIndex(final MetadataCollections collections, Integer tenantId, final String type,
        final String id)
        throws MetaDataExecutionException, MetaDataNotFoundException {
        final DeleteRequestBuilder builder = client.prepareDelete(getAliasName(collections, tenantId), type, id);
        final DeleteResponse response;
        try {
            response = builder.setRefreshPolicy(RefreshPolicy.IMMEDIATE).get();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            throw new MetaDataExecutionException(e.getMessage(), e);
        }
        if (response.status() == RestStatus.NOT_FOUND) {
            throw new MetaDataNotFoundException("Item not found when trying to delete");
        }
    }

    /**
     * updateBulkOGEntriesIndexes
     * <p>
     * Update a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all OG to be indexed
     * @throws MetaDataExecutionException if the bulk update failed
     */
    final boolean updateBulkOGEntriesIndexes(MongoCursor<ObjectGroup> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES update in error since no results to update");
            throw new MetaDataExecutionException("No result to update");
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int max = VitamConfiguration.getMaxElasticsearchBulk();
        while (cursor.hasNext()) {
            max--;
            final ObjectGroup og = cursor.next();
            final String id = og.getId();
            og.remove(VitamDocument.ID);
            og.remove(VitamDocument.SCORE);
            final String mongoJson = og.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toUpdate = dbObject.toString().trim();
            if (toUpdate.isEmpty()) {
                LOGGER.error("ES update in error since result to update is empty");
                throw new MetaDataExecutionException("Result to update is empty");
            }

            bulkRequest.add(client.prepareUpdate(getAliasName(MetadataCollections.OBJECTGROUP, tenantId),
                VitamCollection.getTypeunique(), id).setDoc(toUpdate));
            if (max == 0) {
                max = VitamConfiguration.getMaxElasticsearchBulk();
                final BulkResponse bulkResponse =
                    bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
                if (bulkResponse.hasFailures()) {
                    LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
                    throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
                }
                bulkRequest = client.prepareBulk();
            }
        }
        if (bulkRequest.numberOfActions() > 0) {
            final BulkResponse bulkResponse =
                bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
            if (bulkResponse.hasFailures()) {
                LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
                throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
            }
        }
        return true;
    }

    /**
     * Insert one element
     *
     * @param collection
     * @param tenantId
     * @param id
     * @param doc full document to insert
     * @return True if updated
     */
    public void insertFullDocument(MetadataCollections collection, Integer tenantId, String id, MetadataDocument doc)
        throws MetaDataExecutionException {
        try {
            final String mongoJson = doc.toJson(new JsonWriterSettings(JsonMode.STRICT));
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            dbObject.removeField(VitamDocument.ID);
            final String document = dbObject.toString().trim();
            IndexResponse indexResponse = client.prepareIndex(getAliasName(collection, tenantId),
                VitamCollection.getTypeunique(), id)
                .setSource(document, XContentType.JSON)
                .setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet();
            if (indexResponse.status() != RestStatus.CREATED && indexResponse.status() != RestStatus.OK) {
                throw new MetaDataExecutionException(String
                    .format("Could not index document on ES. Id=%s, collection=%s, status=%s", id, collection,
                        indexResponse.status()));
            }
        } finally {
            doc.put(VitamDocument.ID, id);
        }
    }

    public void insertFullDocuments(MetadataCollections collection, Integer tenantId,
        List<? extends MetadataDocument> documents)
        throws MetaDataExecutionException {
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();

        documents.forEach(document -> {
            String id = (String) document.remove("_id");
            String source = document.toJson();
            bulkRequestBuilder
                .add(client.prepareIndex(getAliasName(collection, tenantId), VitamCollection.TYPEUNIQUE, id)
                    .setSource(source, XContentType.JSON));
        });

        BulkResponse bulkRes = bulkRequestBuilder.setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet();

        LOGGER.info("{}", bulkRes.getItems().length);
        if (bulkRes.hasFailures()) {
            LOGGER.error("##### Bulk Request failure with error: " + bulkRes.buildFailureMessage());
            throw new MetaDataExecutionException(String
                .format("Could not index document on ES. collection=%s, status=%s", collection,
                    bulkRes.status()));

        }
    }


    /**
     * Update one element fully
     *
     * @param collection
     * @param tenantId
     * @param id
     * @param doc full document to update
     */
    public void updateFullDocument(MetadataCollections collection, Integer tenantId, String id, MetadataDocument doc)
        throws MetaDataExecutionException {
        try {
            final String mongoJson = doc.toJson(new JsonWriterSettings(JsonMode.STRICT));
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            dbObject.removeField(VitamDocument.ID);
            final String toUpdate = dbObject.toString().trim();
            UpdateResponse response = client.prepareUpdate(getAliasName(collection, tenantId),
                VitamCollection.getTypeunique(), id)
                .setDoc(toUpdate, XContentType.JSON)
                .setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet();
            if (response.status() != RestStatus.OK) {
                throw new MetaDataExecutionException(String
                    .format("Could not update document on ES. Id=%s, collection=%s, status=%s", id, collection,
                        response.status()));
            }
        } finally {
            doc.put(VitamDocument.ID, id);
        }
    }

    /**
     * deleteBulkOGEntriesIndexes
     * <p>
     * Bulk to delete entry indexes
     *
     * @param ids list of ids of OG
     * @param tenantId the tenant for operation
     * @return boolean true if delete ok
     * @throws MetaDataExecutionException when delete index exception occurred
     */
    public boolean deleteBulkOGEntriesIndexes(List<String> ids, final Integer tenantId)
        throws MetaDataExecutionException {
        if (ids.isEmpty()) {
            LOGGER.error("ES delete in error since no results to delete");
            throw new MetaDataExecutionException("No result to delete");
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int max = VitamConfiguration.getMaxElasticsearchBulk();
        for (String id : ids) {
            max--;
            bulkRequest.add(client.prepareDelete(getAliasName(MetadataCollections.OBJECTGROUP, tenantId),
                VitamCollection.getTypeunique(), id));
            if (max == 0) {
                max = VitamConfiguration.getMaxElasticsearchBulk();
                final BulkResponse bulkResponse =
                    bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
                if (bulkResponse.hasFailures()) {
                    LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
                    throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
                }
                bulkRequest = client.prepareBulk();
            }
        }
        if (bulkRequest.numberOfActions() > 0) {
            final BulkResponse bulkResponse =
                bulkRequest.setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet(); // new thread
            if (bulkResponse.hasFailures()) {
                LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
                throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
            }
        }
        return true;
    }

    /**
     * deleteBulkUnitEntriesIndexes
     * <p>
     * Bulk to delete entry indexes
     *
     * @param ids containing all Unit to be delete
     * @param tenantId the tenant of operation
     * @throws MetaDataExecutionException when delete exception occurred
     */
    public void deleteBulkUnitsEntriesIndexes(List<String> ids, final Integer tenantId)
        throws MetaDataExecutionException {
        if (ids.isEmpty()) {
            LOGGER.error("ES delete in error since no results to delete");
            throw new MetaDataExecutionException("No result to delete");
        }
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int max = VitamConfiguration.getMaxElasticsearchBulk();
        for (String id : ids) {
            max--;
            bulkRequest
                .add(client.prepareDelete(getAliasName(MetadataCollections.UNIT, tenantId),
                    VitamCollection.getTypeunique(), id));
            if (max == 0) {
                max = VitamConfiguration.getMaxElasticsearchBulk();
                final BulkResponse bulkResponse =
                    bulkRequest.setRefreshPolicy(RefreshPolicy.NONE).execute().actionGet(); // new
                if (bulkResponse.hasFailures()) {
                    LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
                    throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
                }
                bulkRequest = client.prepareBulk();
            }
        }
        if (bulkRequest.numberOfActions() > 0) {
            final BulkResponse bulkResponse =
                bulkRequest.setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet(); // new thread
            if (bulkResponse.hasFailures()) {
                LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
                throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
            }
        }
    }

    private String getAliasName(final MetadataCollections collection, Integer tenantId) {
        return collection.getName().toLowerCase() + "_" + tenantId.toString();
    }

    private String getMapping(MetadataCollections collection) throws IOException {
        if (collection == MetadataCollections.UNIT) {
            return ElasticsearchUtil.transferJsonToMapping(Unit.class.getResourceAsStream(MAPPING_UNIT_FILE));
        } else if (collection == MetadataCollections.OBJECTGROUP) {
            return ElasticsearchUtil
                .transferJsonToMapping(ObjectGroup.class.getResourceAsStream(MAPPING_OBJECT_GROUP_FILE));
        }
        return "";
    }
}
