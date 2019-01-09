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
package fr.gouv.vitam.functional.administration.griffin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.builder.request.single.Update;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.exception.BadRequestException;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.SchemaValidationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.GriffinModel;
import fr.gouv.vitam.common.server.HeaderIdHelper;
import fr.gouv.vitam.functional.administration.common.FunctionalBackupService;
import fr.gouv.vitam.functional.administration.common.Griffin;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessReferential;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.gouv.vitam.common.LocalDateUtil.getFormattedDateForMongo;
import static fr.gouv.vitam.common.LocalDateUtil.now;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper.id;
import static fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper.tenant;
import static fr.gouv.vitam.common.database.builder.query.action.UpdateActionHelper.set;
import static fr.gouv.vitam.common.guid.GUIDReader.getGUID;
import static fr.gouv.vitam.common.json.JsonHandler.toJsonNode;
import static fr.gouv.vitam.common.thread.VitamThreadUtils.getVitamSession;
import static fr.gouv.vitam.functional.administration.common.Griffin.IDENTIFIER;
import static fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections.GRIFFIN;
import static fr.gouv.vitam.functional.administration.griffin.LogbookHelper.createLogbook;
import static fr.gouv.vitam.functional.administration.griffin.LogbookHelper.createLogbookEventKo;
import static fr.gouv.vitam.functional.administration.griffin.LogbookHelper.createLogbookEventSuccess;
import static fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory.getInstance;
import static java.util.stream.Collectors.toSet;

public class GriffinService {
    private static final String GRIFFIN_BACKUP_EVENT = "STP_BACKUP_GRIFFIN";
    private static final String GRIFFIN_IMPORT_EVENT = "IMPORT_GRIFFIN";

    private MongoDbAccessReferential mongoDbAccess;
    private FunctionalBackupService functionalBackupService;
    private LogbookOperationsClientFactory logbookOperationsClientFactory;

    @VisibleForTesting GriffinService(MongoDbAccessReferential mongoDbAccess,
        FunctionalBackupService functionalBackupService,
        LogbookOperationsClientFactory logbookOperationsClientFactory) {
        this.mongoDbAccess = mongoDbAccess;
        this.functionalBackupService = functionalBackupService;
        this.logbookOperationsClientFactory = logbookOperationsClientFactory;
    }

    public GriffinService(MongoDbAccessAdminImpl mongoAccess, FunctionalBackupService functionalBackupService) {
        this(mongoAccess, functionalBackupService, getInstance());
    }

    public RequestResponse<GriffinModel> importGriffin(@NotNull List<GriffinModel> listToImport)
        throws VitamException, InvalidCreateOperationException {

        String operationId = getVitamSession().getRequestId();
        GUID guid = getGUID(operationId);

        createLogbook(logbookOperationsClientFactory, guid, GRIFFIN_IMPORT_EVENT);

        try {
            final List<String> listIdsToDelete = new ArrayList<>();
            final List<GriffinModel> listToUpdate = new ArrayList<>();
            final List<GriffinModel> listToInsert = new ArrayList<>();

            classifyDataInInsertUpdateOrDeleteLists(listToImport, listToInsert, listToUpdate, listIdsToDelete);

            insertGriffins(listToInsert);

            updateGriffins(listToUpdate);

            deleteGriffins(listIdsToDelete);

            functionalBackupService.saveCollectionAndSequence(guid, GRIFFIN_BACKUP_EVENT, GRIFFIN, operationId);

        } catch (InvalidCreateOperationException | VitamException e) {
            createLogbookEventKo(logbookOperationsClientFactory, guid, GRIFFIN_BACKUP_EVENT, e.getMessage());
            throw e;
        }

        createLogbookEventSuccess(logbookOperationsClientFactory, guid, GRIFFIN_IMPORT_EVENT);

        return new RequestResponseOK<GriffinModel>().addAllResults(listToImport)
            .setHttpCode(Response.Status.CREATED.getStatusCode());
    }

    void classifyDataInInsertUpdateOrDeleteLists(@NotNull List<GriffinModel> listToImport,
        @NotNull List<GriffinModel> listToInsert,
        @NotNull List<GriffinModel> listToUpdate,
        @NotNull List<String> listToDelete)
        throws ReferentialException, BadRequestException, InvalidParseOperationException {

        final ObjectNode finalSelect = new Select().getFinalSelect();
        DbRequestResult result = mongoDbAccess.findDocuments(finalSelect, GRIFFIN);
        final List<GriffinModel> allGriffinInDatabase = result.getDocuments(Griffin.class, GriffinModel.class);

        Set<String> dataBaseIds = allGriffinInDatabase.stream().map(GriffinModel::getIdentifier).collect(toSet());
        final HashSet<String> updateIds = new HashSet<>(dataBaseIds);

        final Set<String> importIds = listToImport.stream().map(GriffinModel::getIdentifier).collect(toSet());
        updateIds.retainAll(importIds);

        final HashSet<String> removeIds = new HashSet<>(dataBaseIds);
        removeIds.removeAll(updateIds);

        listToDelete.addAll(removeIds);

        for (GriffinModel griffinModel : listToImport) {

            classifyModelToImportIntoInsertOrUpdateList(griffinModel, dataBaseIds, listToInsert, listToUpdate);
        }
    }

    private void classifyModelToImportIntoInsertOrUpdateList(@NotNull GriffinModel griffinModel,
        @NotNull Set<String> dataBaseIds,
        @NotNull List<GriffinModel> listToInsert,
        @NotNull List<GriffinModel> listToUpdate) {

        if (dataBaseIds.contains(griffinModel.getIdentifier())) {

            listToUpdate.add(griffinModel);
            return;
        }
        listToInsert.add(griffinModel);
    }

    private void insertGriffins(@NotNull List<GriffinModel> listToInsert)
        throws InvalidParseOperationException, ReferentialException, SchemaValidationException {

        if (listToInsert.isEmpty()) {
            return;
        }
        ArrayNode griffinToInsert = JsonHandler.createArrayNode();

        for (GriffinModel griffinModel : listToInsert) {
            griffinModel.setTenant(HeaderIdHelper.getTenantId());

            formatDateForMongo(griffinModel);
            griffinToInsert.add(toJson(griffinModel));
        }

        mongoDbAccess.insertDocuments(griffinToInsert, GRIFFIN);
    }

    private JsonNode toJson(@NotNull GriffinModel model) throws InvalidParseOperationException {

        ObjectNode modelNode = (ObjectNode) toJsonNode(model);

        JsonNode hashTenant = modelNode.remove(tenant());
        if (hashTenant != null) {
            modelNode.set("_tenant", hashTenant);
        }

        JsonNode jsonNode = modelNode.remove(id());
        if (jsonNode != null) {
            modelNode.set("_id", jsonNode);
        }
        return modelNode;
    }

    private void deleteGriffins(@NotNull List<String> listIdsToDelete)
        throws ReferentialException, BadRequestException, SchemaValidationException,
        InvalidCreateOperationException {

        for (String identifier : listIdsToDelete) {
            final Select select = new Select();
            select.setQuery(eq(IDENTIFIER, identifier));
            mongoDbAccess.deleteDocument(select.getFinalSelect(), GRIFFIN);
        }
    }

    private void updateGriffins(@NotNull List<GriffinModel> listToUpdate)
        throws ReferentialException, SchemaValidationException, BadRequestException, InvalidCreateOperationException,
        InvalidParseOperationException, DatabaseException {

        for (GriffinModel griffinModel : listToUpdate) {

            formatDateForMongo(griffinModel);

            mongoDbAccess.replaceDocument(JsonHandler.toJsonNode(griffinModel), griffinModel.getIdentifier(), IDENTIFIER,
                FunctionalAdminCollections.GRIFFIN);
        }
    }

    private void formatDateForMongo(GriffinModel griffinModel) {

        String lastUpdate = getFormattedDateForMongo(now());
        griffinModel.setLastUpdate(lastUpdate);

        String creationDate = griffinModel.getCreationDate();

        if (creationDate == null) {
            creationDate = now().toString();
        }
        creationDate = getFormattedDateForMongo(creationDate);
        griffinModel.setCreationDate(creationDate);
    }

    public RequestResponse<GriffinModel> findGriffin(JsonNode queryDsl)
        throws ReferentialException, BadRequestException, InvalidParseOperationException {

        DbRequestResult documents = mongoDbAccess.findDocuments(queryDsl, GRIFFIN);

        return documents.getRequestResponseOK(queryDsl, Griffin.class, GriffinModel.class);
    }
}
