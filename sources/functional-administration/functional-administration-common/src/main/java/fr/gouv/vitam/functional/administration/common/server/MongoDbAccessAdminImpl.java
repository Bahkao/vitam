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
package fr.gouv.vitam.functional.administration.common.server;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.annotations.VisibleForTesting;
import com.mongodb.MongoClient;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Delete;
import fr.gouv.vitam.common.database.builder.request.single.Insert;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.database.server.DbRequestSingle;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.SchemaValidationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import org.bson.Document;

/**
 * MongoDbAccess Implement for Admin
 */
public class MongoDbAccessAdminImpl extends MongoDbAccess implements MongoDbAccessReferential {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(MongoDbAccessAdminImpl.class);


    /**
     * @param mongoClient client of mongo
     * @param dbname name of database
     * @param recreate true if recreate type
     */
    protected MongoDbAccessAdminImpl(MongoClient mongoClient, String dbname, boolean recreate) {
        super(mongoClient, dbname, recreate);
        for (final FunctionalAdminCollections collection : FunctionalAdminCollections.values()) {
            collection.initialize(super.getMongoDatabase(), recreate);
        }
    }

    @Override
    public DbRequestResult insertDocuments(ArrayNode arrayNode, FunctionalAdminCollections collection)
        throws ReferentialException, SchemaValidationException {
        return insertDocuments(arrayNode, collection, 0);
    }

    @Override
    public DbRequestResult insertDocuments(ArrayNode arrayNode, FunctionalAdminCollections collection, Integer version)
        throws ReferentialException, SchemaValidationException {
        try {
            final DbRequestSingle dbrequest = new DbRequestSingle(collection.getVitamCollection());
            final Insert insertquery = new Insert();
            insertquery.setData(arrayNode);
            return dbrequest.execute(insertquery, version);
        } catch (InvalidParseOperationException | BadRequestException | DatabaseException |
            InvalidCreateOperationException | VitamDBException e) {
            LOGGER.error("Insert Documents Exception", e);
            throw new ReferentialException(e);
        }
    }

    // Not check, test feature !
    @Override
    public DbRequestResult deleteCollection(FunctionalAdminCollections collection, Delete delete)
        throws DatabaseException, ReferentialException {
        long count = 0;
        if (collection.isMultitenant()) {
            final Document filter =
                new Document().append(VitamDocument.TENANT_ID, ParameterHelper.getTenantParameter());
            count = collection.getCollection().count(filter);
        } else {
            count = collection.getCollection().count();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(collection.getName() + " count before: " + count);
        }
        if (count > 0) {

            final DbRequestSingle dbrequest = new DbRequestSingle(collection.getVitamCollection());
            try (DbRequestResult result = dbrequest.execute(delete)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(collection.getName() + " result.result.getDeletedCount(): " + result.getCount());
                }

                return result;
            } catch (InvalidParseOperationException | BadRequestException | InvalidCreateOperationException |
                VitamDBException | SchemaValidationException e) {
                throw new DatabaseException("Delete document exception");
            }
        }
        return new DbRequestResult();
    }

    @Override
    public DbRequestResult deleteCollection(FunctionalAdminCollections collection)
        throws DatabaseException, ReferentialException, SchemaValidationException {

        long count = 0;
        if (collection.isMultitenant()) {
            final Document filter =
                new Document().append(VitamDocument.TENANT_ID, ParameterHelper.getTenantParameter());
            count = collection.getCollection().count(filter);
        } else {
            count = collection.getCollection().count();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(collection.getName() + " count before: " + count);
        }
        if (count > 0) {
            Delete delete = new Delete();
            final DbRequestSingle dbrequest = new DbRequestSingle(collection.getVitamCollection());
            try (DbRequestResult result = dbrequest.execute(delete)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(collection.getName() + " result.result.getDeletedCount(): " + result.getCount());
                }
                if (result.getCount() != count) {
                    throw new DatabaseException(
                        String.format("%s: Delete %s from %s elements", collection.getName(),
                            result.getCount(), count));
                }
                return result;
            } catch (InvalidParseOperationException | BadRequestException | InvalidCreateOperationException |
                VitamDBException e) {
                throw new DatabaseException("Delete document exception");
            }
        }
        return new DbRequestResult();
    }

    @VisibleForTesting
    @Override
    public VitamDocument<?> getDocumentById(String id, FunctionalAdminCollections collection)
        throws ReferentialException {
        return (VitamDocument<?>) collection.getCollection().find(eq(VitamDocument.ID, id)).first();
    }

    @Override
    public VitamDocument<?> getDocumentByUniqueId(String id, FunctionalAdminCollections collection, String field)
        throws ReferentialException {
        if (collection.isMultitenant()) {
            Integer tenantId = ParameterHelper.getTenantParameter();
            return (VitamDocument<?>) collection.getCollection().find(and(eq(field, id),
                eq(VitamDocument.TENANT_ID, tenantId))).first();
        }
        return (VitamDocument<?>) collection.getCollection().find(eq(field, id)).first();
    }

    @Override
    public DbRequestResult findDocuments(JsonNode select, FunctionalAdminCollections collection)
        throws ReferentialException {
        try {
            final SelectParserSingle parser = new SelectParserSingle(collection.getVarNameAdapater());
            parser.parse(select);
            final DbRequestSingle dbrequest = new DbRequestSingle(collection.getVitamCollection());
            return dbrequest.execute(parser.getRequest());
        } catch (final DatabaseException | BadRequestException | InvalidParseOperationException |
            InvalidCreateOperationException | VitamDBException | SchemaValidationException e) {
            LOGGER.error("find Document Exception", e);
            throw new ReferentialException(e);
        }
    }

    @Override
    public DbRequestResult updateData(JsonNode update, FunctionalAdminCollections collection, Integer version)
        throws ReferentialException, SchemaValidationException, BadRequestException {
        try {
            final UpdateParserSingle parser = new UpdateParserSingle(collection.getVarNameAdapater());
            parser.parse(update);
            final DbRequestSingle dbrequest = new DbRequestSingle(collection.getVitamCollection());
            final DbRequestResult result = dbrequest.execute(parser.getRequest(), version);
            if (result.getDiffs().size() == 0) {
                throw new BadRequestException("Document was not updated as there is no changes");
            }
            return result;
        } catch (final InvalidParseOperationException | InvalidCreateOperationException e) {
            throw new BadRequestException(e);
        } catch (final DatabaseException | VitamDBException e) {
            LOGGER.error("find Document Exception", e);
            throw new ReferentialException(e);
        }
    }

    @Override
    public DbRequestResult updateData(JsonNode update, FunctionalAdminCollections collection)
        throws ReferentialException, SchemaValidationException, BadRequestException {
        return updateData(update, collection, 0);
    }

    @Override
    public DbRequestResult insertDocument(JsonNode json, FunctionalAdminCollections collection)
        throws ReferentialException, SchemaValidationException {
        return insertDocuments(JsonHandler.createArrayNode().add(json), collection);
    }

}
