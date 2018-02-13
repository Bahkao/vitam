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

package fr.gouv.vitam.storage.offers.common.database;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;

import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageDatabaseException;

/**
 * Database service for access to OfferSequence collection.
 */
public class OfferSequenceDatabaseService {

    public static final String OFFER_SEQUENCE_COLLECTION = "OfferSequence";
    public static final String BACKUP_LOG_SEQUENCE_ID = "Backup_Log_Sequence";

    private MongoCollection<Document> mongoCollection;

    /**
     * Constructor
     * 
     * @param mongoDatabase mongoDatabase
     */
    public OfferSequenceDatabaseService(MongoDatabase mongoDatabase) {
        this.mongoCollection = mongoDatabase.getCollection(OFFER_SEQUENCE_COLLECTION);
    }

    /**
     * Init offerSequence collection in database if not exists
     */
    public void initSequences() {
        if (mongoCollection.count(new BasicDBObject(OfferSequence.ID_FIELD, BACKUP_LOG_SEQUENCE_ID)) == 0) {
            OfferSequence offerSequence = new OfferSequence(BACKUP_LOG_SEQUENCE_ID);
            mongoCollection.insertOne(offerSequence.toDocument());
        }
    }

    /**
     * Increments the sequence and retrieve the next sequence value
     * 
     * @param sequenceId sequence identifier
     * @return next sequence value
     * @throws ContentAddressableStorageDatabaseException database error
     */
    public long getNextSequence(String sequenceId) throws ContentAddressableStorageDatabaseException {
        try {
            final BasicDBObject incQuery = new BasicDBObject();
            incQuery.append("$inc", new BasicDBObject(OfferSequence.COUNTER_FIELD, 1));
            Bson query = eq(OfferSequence.ID_FIELD, sequenceId);
            FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions();
            findOneAndUpdateOptions.returnDocument(ReturnDocument.AFTER);

            Document sequence = mongoCollection.findOneAndUpdate(query, incQuery, findOneAndUpdateOptions);
            if (sequence != null) {
                return sequence.getLong(OfferSequence.COUNTER_FIELD);
            } else {
                throw new ContentAddressableStorageDatabaseException(
                    String.format("Database Error sequence %s not found", sequenceId));
            }

        } catch (MongoException e) {
            throw new ContentAddressableStorageDatabaseException(String.format(
                "Database Error while getting next sequence value for %s", sequenceId), e);
        }
    }
}
