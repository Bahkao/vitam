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
package fr.gouv.vitam.functional.administration.agencies.api;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static fr.gouv.vitam.common.database.builder.query.QueryHelper.in;
import static fr.gouv.vitam.functional.administration.common.Agencies.DESCRIPTION;
import static fr.gouv.vitam.functional.administration.common.Agencies.IDENTIFIER;
import static fr.gouv.vitam.functional.administration.common.Agencies.NAME;
import static fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections.AGENCIES;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.database.builder.query.VitamFieldsHelper;
import fr.gouv.vitam.common.database.builder.query.action.SetAction;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.builder.request.single.Update;
import fr.gouv.vitam.common.database.parser.request.adapter.SingleVarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.adapter.VarNameAdapter;
import fr.gouv.vitam.common.database.parser.request.single.SelectParserSingle;
import fr.gouv.vitam.common.database.parser.request.single.UpdateParserSingle;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.i18n.VitamErrorMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.VitamAutoCloseable;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.AgenciesModel;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.functional.administration.ContractsFinder;
import fr.gouv.vitam.functional.administration.common.AccessContract;
import fr.gouv.vitam.functional.administration.common.Agencies;
import fr.gouv.vitam.functional.administration.common.AgenciesParser;
import fr.gouv.vitam.functional.administration.common.ErrorReportAgencies;
import fr.gouv.vitam.functional.administration.common.FileAgenciesErrorCode;
import fr.gouv.vitam.functional.administration.common.FilesSecurisator;
import fr.gouv.vitam.functional.administration.common.ReportConstants;
import fr.gouv.vitam.functional.administration.common.exception.AgencyImportDeletionException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.functional.administration.counter.SequenceType;
import fr.gouv.vitam.functional.administration.counter.VitamCounterService;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.common.exception.StorageException;
import fr.gouv.vitam.storage.engine.common.model.StorageCollectionType;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * AgenciesService class allowing multiple operation on AgenciesService collection
 */
public class AgenciesService implements VitamAutoCloseable {

    public static final String AGENCIES_IMPORT_EVENT = "STP_IMPORT_AGENCIES";
    public static final String AGENCIES_REPORT_EVENT = "STP_AGENCIES_REPORT";

    public static final String AGENCIES_IMPORT_AU_USAGE = AGENCIES_IMPORT_EVENT + ".USED_AU";
    private static final String AGENCIES_IMPORT_CONTRACT_USAGE = AGENCIES_IMPORT_EVENT + ".USED_CONTRACT";


    public static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AgenciesService.class);

    private static final String TMP = "tmpAgencies";
    private static final String CSV = "csv";
    private static final String JSON = "json";

    private static final String INVALID_CSV_FILE = "Invalid CSV File";

    private static final String MESSAGE_ERROR = "Import agency error > ";
    private static final String _ID = "_id";
    private static final String _TENANT = "_tenant";

    private final MongoDbAccessAdminImpl mongoAccess;
    private final LogbookOperationsClient logBookclient;
    private final VitamCounterService vitamCounterService;
    private final LogbookOperationsClientFactory logbookOperationsClientFactory;

    private AgenciesManager manager;
    private Map<Integer, List<ErrorReportAgencies>> errorsMap;
    private List<AgenciesModel> usedAgenciesByContracts;
    private List<AgenciesModel> usedAgenciesByAU;
    private List<AgenciesModel> agenciesToInsert;
    private List<AgenciesModel> agenciesToUpdate;
    private List<AgenciesModel> agenciesToDelete;
    private List<AgenciesModel> agenciesToImport = new ArrayList<>();
    private List<AgenciesModel> agenciesInDb;
    private final FilesSecurisator securisator;

    private final String file_name = "AGENCIES";
    private GUID eip;
    private ContractsFinder finder;

    /**
     * Constructor
     *
     * @param mongoAccess MongoDB client
     * @param vitamCounterService the vitam counter service
     * @param securisator the FilesSecurisator
     */
    public AgenciesService(MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService, FilesSecurisator securisator) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.securisator = securisator;
        logbookOperationsClientFactory = LogbookOperationsClientFactory.getInstance();
        logBookclient = LogbookOperationsClientFactory.getInstance().getClient();
        errorsMap = new HashMap<>();
        usedAgenciesByContracts = new ArrayList<>();
        usedAgenciesByAU = new ArrayList<>();
        agenciesToInsert = new ArrayList<>();
        agenciesToUpdate = new ArrayList<>();
        agenciesToDelete = new ArrayList<>();
        agenciesToImport = new ArrayList<>();
        agenciesInDb = new ArrayList<>();
        finder = new ContractsFinder(mongoAccess, vitamCounterService);
    }

    /**
     * 
     * @param mongoAccess MongoDB client
     * @param vitamCounterService the vitam counter service
     * @param securisator the FilesSecurisator
     * @param logbookOperationsClientFactory the logbook operaction client factory
     * @param storageClientFactory the storage client factory
     * @param workspaceClientFactory the workspace client factory
     */
    @VisibleForTesting
    public AgenciesService(MongoDbAccessAdminImpl mongoAccess,
        VitamCounterService vitamCounterService, FilesSecurisator securisator,
        LogbookOperationsClientFactory logbookOperationsClientFactory, StorageClientFactory storageClientFactory,
        WorkspaceClientFactory workspaceClientFactory) {
        this.mongoAccess = mongoAccess;
        this.vitamCounterService = vitamCounterService;
        this.securisator = securisator;
        this.logbookOperationsClientFactory = logbookOperationsClientFactory;
        logBookclient = this.logbookOperationsClientFactory.getClient();
        errorsMap = new HashMap<>();
        usedAgenciesByContracts = new ArrayList<>();
        usedAgenciesByAU = new ArrayList<>();
        agenciesToInsert = new ArrayList<>();
        agenciesToUpdate = new ArrayList<>();
        agenciesToDelete = new ArrayList<>();
        agenciesInDb = new ArrayList<>();
        finder = new ContractsFinder(mongoAccess, vitamCounterService);

    }

    /**
     * @param id the document id
     * @return the vitamDocument as an Agencies object
     * @throws ReferentialException thrown if the agency is not found or if the an error is encountered
     */
    public VitamDocument<Agencies> findDocumentById(String id) throws ReferentialException {
        try {
            SanityChecker.checkParameter(id);

            final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
            parser.parse(parser.getRequest().getFinalSelect());
            parser.addCondition(eq(AgenciesModel.TAG_IDENTIFIER, id));
            DbRequestResult result =
                mongoAccess.findDocuments(parser.getRequest().getFinalSelect(), AGENCIES);
            parser.parse(new Select().getFinalSelect());

            final List<Agencies> list = result.getDocuments(Agencies.class);
            if (list.isEmpty()) {
                throw new ReferentialException("Agency not found");
            }
            return list.get(0);
        } catch (InvalidParseOperationException | InvalidCreateOperationException e) {
            LOGGER.error("ReferentialException", e);
        }
        throw new ReferentialException("Agency not found");
    }

    /**
     * 
     * @param select the query as a json
     * @return list of response as a RequestResponseOK object
     * @throws ReferentialException thrown if an error is encountered
     */
    public RequestResponseOK findDocuments(JsonNode select)
        throws ReferentialException {
        return findAgencies(select).getRequestResponseOK(select, Agencies.class);
    }



    /**
     * Construct query DSL for find all Agencies (referential)
     *
     * @return list of FileAgencies in database
     */
    public List<Agencies> findAllAgencies() {
        final Select select = new Select();
        List<Agencies> agenciesModels = new ArrayList<>();
        try {
            RequestResponseOK<Agencies> response = findDocuments(select.getFinalSelect());
            if (response != null) {
                return response.getResults();
            }
        } catch (ReferentialException e) {
            LOGGER.error("ReferentialException", e);
        }
        return agenciesModels;
    }


    /**
     * Construct query DSL for find all Agencies (referential)
     * 
     * @throws VitamException thrown if query could not be executed
     */
    public void findAllAgenciesUsedByUnits() throws VitamException {

        for (AgenciesModel agency : agenciesToUpdate) {
            final SelectMultiQuery selectMultiple = new SelectMultiQuery();
            try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient()) {

                // FIXME Add limit when Dbrequest is Fix and when distinct is implement in DbRequest:
                ObjectNode objectNode = JsonHandler.createObjectNode();
                objectNode.put(VitamFieldsHelper.id(), 1);
                ArrayNode arrayNode = JsonHandler.createArrayNode();
                VitamFieldsHelper.management();
                selectMultiple
                    .setQuery(eq(VitamFieldsHelper.management() + ".OriginatingAgency", agency.getIdentifier()));
                selectMultiple.addRoots(arrayNode);
                selectMultiple.addProjection(JsonHandler.createObjectNode().set("$fields", objectNode));

                final JsonNode unitsResultNode = metaDataClient.selectUnits(selectMultiple.getFinalSelect());

                if (unitsResultNode != null && unitsResultNode.get("$results").size() > 0) {
                    usedAgenciesByAU.add(agency);
                }
            } catch (InvalidCreateOperationException | InvalidParseOperationException | MetaDataExecutionException |
                MetaDataDocumentSizeException | MetaDataClientServerException e) {
                LOGGER.error("Query construction not valid ", e);
            }
        }

        if (usedAgenciesByAU.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_AU_USAGE);
            return;
        }
        final ArrayNode usedAgenciesAUNode = JsonHandler.createArrayNode();
        usedAgenciesByAU.forEach(agency -> usedAgenciesAUNode.add(agency.getIdentifier()));

        final ObjectNode data = JsonHandler.createObjectNode();
        data.set(ReportConstants.ADDITIONAL_INFORMATION, usedAgenciesAUNode);

        manager.setEvDetData(data);

        manager.logEventWarning(AGENCIES_IMPORT_AU_USAGE);
    }

    /**
     * Find all agencies used by access contracts
     * 
     * @throws InvalidCreateOperationException thrown if the query could not be created
     * @throws VitamException thrown if an error is encountered
     */
    public void findAllAgenciesUsedByAccessContrats() throws InvalidCreateOperationException, VitamException {

        for (AgenciesModel agency : agenciesToUpdate) {

            final Select select = new Select();
            select.setQuery(in(AccessContract.ORIGINATINGAGENCIES, agency.getIdentifier()));
            final JsonNode queryDsl = select.getFinalSelect();

            RequestResponseOK<AccessContractModel> result = finder.findAccessContrats(queryDsl);

            if (!result.getResults().isEmpty()) {

                usedAgenciesByContracts.add(agency);

            }
        }

        if (agenciesToUpdate.isEmpty()) {
            manager.logEventSuccess(AGENCIES_IMPORT_CONTRACT_USAGE);
            return;
        }

        final ArrayNode usedAgenciesContractNode = JsonHandler.createArrayNode();

        usedAgenciesByContracts.forEach(agency -> usedAgenciesContractNode.add(agency.getIdentifier()));

        final ObjectNode data = JsonHandler.createObjectNode();
        data.set(ReportConstants.ADDITIONAL_INFORMATION, usedAgenciesContractNode);

        manager.setEvDetData(data);

        manager.logEventWarning(AGENCIES_IMPORT_CONTRACT_USAGE);
    }

    /**
     * Convert a given input stream to a file
     *
     * @param agenciesStream the agencies stream
     * @param extension the file extension 
     * @return a file
     * @throws IOException thrown if the file could not be created
     */
    private File convertInputStreamToFile(InputStream agenciesStream, String extension) throws IOException {
        try {
            final File csvFile = File.createTempFile(TMP, extension, new File(VitamConfiguration.getVitamTmpFolder()));
            Files.copy(agenciesStream, csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return csvFile;
        } finally {
            StreamUtils.closeSilently(agenciesStream);
        }
    }

    /**
     * Check file integrity
     * 
     * @param stream the stream to be checked
     * @throws ReferentialException thrown if the file is not correct
     * @throws IOException thrown if the file could be read
     */
    public void checkFile(InputStream stream)
        throws ReferentialException,
        IOException {

        int lineNumber = 1;
        File csvFileReader = convertInputStreamToFile(stream, CSV);

        try (FileReader reader = new FileReader(csvFileReader)) {
            final CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader().withTrim());
            final HashSet<String> idsset = new HashSet<>();
            try {
                for (final CSVRecord record : parser) {
                    List<ErrorReportAgencies> errors = new ArrayList<>();
                    lineNumber++;
                    if (checkRecords(record)) {
                        final String identifier = record.get(AgenciesModel.TAG_IDENTIFIER);
                        final String name = record.get(AgenciesModel.TAG_NAME);
                        final String description = record.get(AgenciesModel.TAG_DESCRIPTION);

                        final AgenciesModel agenciesModel = new AgenciesModel(identifier, name, description);

                        checkParametersNotEmpty(identifier, name, description, errors, lineNumber);

                        if (idsset.contains(identifier)) {
                            errors
                                .add(new ErrorReportAgencies(FileAgenciesErrorCode.STP_IMPORT_AGENCIES_ID_DUPLICATION,
                                    lineNumber, agenciesModel));
                        }

                        idsset.add(identifier);

                        if (errors.size() > 0) {
                            errorsMap.put(lineNumber, errors);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message.contains("Name not found")) {
                    message = ReportConstants.FILE_INVALID + "Name";
                }
                if (message.contains("Identifier not found")) {
                    message = ReportConstants.FILE_INVALID + "Identifier";
                }
                if (message.contains("Description not found")) {
                    message = ReportConstants.FILE_INVALID + "Description";
                }
                throw new ReferentialException(message);
            } catch (Exception e) {
                throw new ReferentialException(e);
            }
            if (csvFileReader != null) {

                agenciesToImport = AgenciesParser.readFromCsv(new FileInputStream(csvFileReader));

                if (errorsMap.size() > 0) {
                    throw new ReferentialException(INVALID_CSV_FILE);
                }

                csvFileReader.delete();
            }
        }
    }

    /**
     * Check agencies in database
     * 
     */
    public void checkAgenciesInDb() {

        List<Agencies> tempAgencies = findAllAgencies();
        tempAgencies.forEach(a -> agenciesInDb.add(a.wrap()));

    }

    /**
     * Check Referential To Import for create agency to delete, update, insert
     */
    private void createInsertUpdateDeleteList() {

        agenciesToInsert.addAll(agenciesToImport);

        agenciesToDelete.addAll(agenciesInDb);

        for (AgenciesModel agencyToImport : agenciesToImport) {
            for (AgenciesModel agencyInDb : agenciesInDb) {

                if (agencyInDb.getIdentifier().equals(agencyToImport.getIdentifier()) &&
                    (!agencyInDb.getName().equals(agencyToImport.getName()) ||
                        !agencyInDb.getDescription().equals(agencyToImport.getDescription()))) {

                    agenciesToUpdate.add(agencyToImport);
                }

                if (agencyToImport.getIdentifier().equals(agencyInDb.getIdentifier())) {

                    agenciesToInsert.remove(agencyToImport);
                }

                if (agencyInDb.getIdentifier().equals(agencyToImport.getIdentifier())) {
                    agenciesToDelete.remove(agencyInDb);
                }
            }
        }
    }

    private void checkParametersNotEmpty(String identifier, String name, String description,
        List<ErrorReportAgencies> errors, int line) {
        List<String> missingParam = new ArrayList<>();
        if (identifier.isEmpty()) {
            missingParam.add(AgenciesModel.TAG_IDENTIFIER);
        }
        if (name == null || name.isEmpty()) {
            missingParam.add(AgenciesModel.TAG_NAME);
        }
        if (missingParam.size() > 0) {
            errors.add(new ErrorReportAgencies(FileAgenciesErrorCode.STP_IMPORT_AGENCIES_MISSING_INFORMATIONS, line,
                missingParam.stream().collect(Collectors.joining())));
        }
    }

    /**
     * @param record
     * @return
     */
    private boolean checkRecords(CSVRecord record) {
        return record.get(IDENTIFIER) != null && record.get(NAME) != null && record.get(DESCRIPTION) != null;
    }



    /**
     * Import an input stream into agencies collection
     * 
     * @param stream the stream to be imported
     * @return a response as a RequestResponse<AgenciesModel> object
     * @throws VitamException thrown if logbook could not be initialized
     * @throws IOException thrown in case or error with stream
     * @throws InvalidCreateOperationException thrown if the error report could not be stored
     */
    public RequestResponse<AgenciesModel> importAgencies(InputStream stream)
        throws VitamException, IOException, InvalidCreateOperationException {

        eip = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
        manager = new AgenciesManager(logBookclient, eip);


        manager.logStarted(AGENCIES_IMPORT_EVENT);
        InputStream report;
        try {

            File file = convertInputStreamToFile(stream, CSV);

            try (FileInputStream inputStream = new FileInputStream(file)) {
                checkFile(inputStream);
            }

            checkAgenciesInDb();

            createInsertUpdateDeleteList();

            checkAgenciesDeletion();

            findAllAgenciesUsedByAccessContrats();

            findAllAgenciesUsedByUnits();

            commitAgencies();

            report = generateReportOK();

            storeReport(report);

            storeCSV(file);

            storeJson();

            manager.logFinish();
        } catch (final AgencyImportDeletionException e) {

            LOGGER.error(MESSAGE_ERROR, e);
            InputStream errorStream = generateErrorReport();
            storeReport(errorStream);
            errorStream.close();
            ObjectNode erorrMessage = JsonHandler.createObjectNode();
            erorrMessage.put("ErrorMessage", MESSAGE_ERROR + e.getMessage());

            String listAgencies = agenciesToDelete.stream().map(agenciesModel -> agenciesModel.getIdentifier())
                .collect(Collectors.joining(","));
            erorrMessage.put("Agencies ", listAgencies);


            return generateVitamBadRequestError(erorrMessage.toString());

        } catch (final Exception e) {
            LOGGER.error(MESSAGE_ERROR, e);
            InputStream errorStream = generateErrorReport();
            storeReport(errorStream);
            errorStream.close();
            return generateVitamError(MESSAGE_ERROR + e.getMessage());
        } finally {
            // StreamUtils.closeSilently(stream);
        }

        return new RequestResponseOK<AgenciesModel>().setHttpCode(Response.Status.CREATED.getStatusCode());

    }

    private void checkAgenciesDeletion() throws AgencyImportDeletionException {
        if (agenciesToDelete.size() > 0) {
            throw new AgencyImportDeletionException("used Agencies want to be deleted");
        }
    }



    private VitamError generateVitamBadRequestError(String err) throws VitamException {
        manager.logError(err);
        return new VitamError(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode())
            .setCode(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setDescription(err)
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    private VitamError generateVitamError(String err) throws VitamException {
        manager.logError(err);
        return new VitamError(VitamCode.AGENCIES_VALIDATION_ERROR.getItem())
            .setHttpCode(Response.Status.BAD_REQUEST.getStatusCode())
            .setCode(VitamCode.GLOBAL_INTERNAL_SERVER_ERROR.getItem())
            .setDescription(err)
            .setHttpCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private void insertDocuments(List<AgenciesModel> agenciesToInsert, Integer sequence)
        throws InvalidParseOperationException, ReferentialException {

        ArrayNode agenciesNodeToPersist = JsonHandler.createArrayNode();

        for (final AgenciesModel agency : agenciesToInsert) {
            agency.setId(GUIDFactory.newGUID().getId());
            agency.setTenant(ParameterHelper.getTenantParameter());
            ObjectNode agencyNode = (ObjectNode) JsonHandler.toJsonNode(agency);
            JsonNode jsonNode = agencyNode.remove(VitamFieldsHelper.id());
            if (jsonNode != null) {
                agencyNode.set(_ID, jsonNode);
            }
            JsonNode hashTenant = agencyNode.remove(VitamFieldsHelper.tenant());
            if (hashTenant != null) {
                agencyNode.set(_TENANT, hashTenant);
            }
            agenciesNodeToPersist.add(agencyNode);
        }

        if (!agenciesToInsert.isEmpty()) {
            mongoAccess.insertDocuments(agenciesNodeToPersist, AGENCIES, sequence).close();
        }

    }

    private void commitAgencies()
        throws InvalidParseOperationException, ReferentialException, InvalidCreateOperationException {

        Integer sequence = vitamCounterService
            .getNextSequence(ParameterHelper.getTenantParameter(), SequenceType.AGENCIES_SEQUENCE);

        for (AgenciesModel agency : agenciesToUpdate) {
            updateAgency(agency, sequence);
        }

        if (!agenciesToInsert.isEmpty()) {
            insertDocuments(agenciesToInsert, sequence);
        }
    }


    /**
     * Create QueryDsl for update the given Agencies
     *
     * @param fileAgenciesModel Agencies to update
     * @param sequence
     * @throws InvalidCreateOperationException
     * @throws ReferentialException
     * @throws InvalidParseOperationException
     */
    private void updateAgency(AgenciesModel fileAgenciesModel, Integer sequence)
        throws InvalidCreateOperationException,
        ReferentialException,
        InvalidParseOperationException {

        final UpdateParserSingle updateParser = new UpdateParserSingle(new VarNameAdapter());
        final Update updateFileAgencies = new Update();
        List<SetAction> actions = new ArrayList<>();
        SetAction setAgencyValue = new SetAction(AgenciesModel.TAG_NAME, fileAgenciesModel.getName());
        SetAction setAgencyDescription =
            new SetAction(AgenciesModel.TAG_DESCRIPTION, fileAgenciesModel.getDescription());

        actions.add(setAgencyValue);
        actions.add(setAgencyDescription);
        updateFileAgencies.setQuery(eq(AgenciesModel.TAG_IDENTIFIER, fileAgenciesModel.getIdentifier()));
        updateFileAgencies.addActions(actions.toArray(new SetAction[actions.size()]));

        updateParser.parse(updateFileAgencies.getFinalUpdate());

        mongoAccess.updateData(updateParser.getRequest().getFinalUpdate(), AGENCIES, sequence);
    }

    /**
     * @param queryDsl the query to be executed
     * @return a DbRequestResult containing agencies
     * @throws ReferentialException thrown if the query could not be executed
     */
    public DbRequestResult findAgencies(JsonNode queryDsl)
        throws ReferentialException {
        return mongoAccess.findDocuments(queryDsl, AGENCIES);
    }

    /**
     * @param id the agency Id
     * @return the agency as an AgenciesModel object
     * @throws ReferentialException thrown if the query could not be executed
     * @throws InvalidParseOperationException thrown if the query could not be created
     */
    public AgenciesModel findOneAgencyById(String id) throws ReferentialException, InvalidParseOperationException {
        SanityChecker.checkParameter(id);
        final SelectParserSingle parser = new SelectParserSingle(new SingleVarNameAdapter());
        parser.parse(new Select().getFinalSelect());
        try {
            parser.addCondition(eq(AgenciesModel.TAG_IDENTIFIER, id));
        } catch (InvalidCreateOperationException e) {
            throw new ReferentialException(e);
        }
        try (DbRequestResult result =
            mongoAccess.findDocuments(parser.getRequest().getFinalSelect(), AGENCIES)) {
            final List<AgenciesModel> list = result.getDocuments(Agencies.class, AgenciesModel.class);
            if (list.isEmpty()) {
                throw new ReferentialException("Agency not found");
            }
            return list.get(0);
        }
    }


    /**
     * generate Error Report
     *
     * @return the error report inputStream
     * @throws ReferentialException
     */
    private InputStream generateReportOK()
        throws ReferentialException {
        ObjectNode reportFinal = generateReport();

        return new ByteArrayInputStream(JsonHandler.unprettyPrint(reportFinal).getBytes(StandardCharsets.UTF_8));
    }

    private ObjectNode generateReport() {

        final ObjectNode reportFinal = JsonHandler.createObjectNode();
        final ObjectNode guidmasterNode = JsonHandler.createObjectNode();
        final ArrayNode insertAgenciesNode = JsonHandler.createArrayNode();
        final ArrayNode updateAgenciesNode = JsonHandler.createArrayNode();
        final ArrayNode usedAgenciesContractNode = JsonHandler.createArrayNode();
        final ArrayNode usedAgenciesAUNode = JsonHandler.createArrayNode();
        final ArrayNode agenciesTodelete = JsonHandler.createArrayNode();
        final ArrayNode allAgencies = JsonHandler.createArrayNode();

        guidmasterNode.put(ReportConstants.EV_TYPE, AGENCIES_IMPORT_EVENT);
        guidmasterNode.put(ReportConstants.EV_DATE_TIME, LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now()));
        guidmasterNode.put(ReportConstants.EV_ID, eip.toString());

        agenciesToInsert.forEach(agency -> insertAgenciesNode.add(agency.getIdentifier()));

        agenciesToUpdate.forEach(agency -> updateAgenciesNode.add(agency.getIdentifier()));

        usedAgenciesByContracts.forEach(agency -> usedAgenciesContractNode.add(agency.getIdentifier()));

        usedAgenciesByAU.forEach(agency -> usedAgenciesAUNode.add(agency.getIdentifier()));

        agenciesToDelete.forEach(agency -> agenciesTodelete.add(agency.getIdentifier()));

        agenciesToImport.forEach(agency -> allAgencies.add(agency.getIdentifier()));

        reportFinal.set(ReportConstants.JDO_DISPLAY, guidmasterNode);
        reportFinal.set("AgenciesToImport", allAgencies);
        reportFinal.set("InsertAgencies", insertAgenciesNode);
        reportFinal.set("UpdatedAgencies", updateAgenciesNode);
        reportFinal.set("UsedAgencies By Contrat", usedAgenciesContractNode);
        reportFinal.set("UsedAgencies By AU", usedAgenciesAUNode);
        reportFinal.set("UsedAgencies to Delete", agenciesTodelete);

        return reportFinal;
    }



    /**
     * Generate an error report
     * 
     * @return an input stream containing the report
     */
    public InputStream generateErrorReport() {

        final ObjectNode reportFinal = generateReport();

        final ArrayNode messagesArrayNode = JsonHandler.createArrayNode();
        final ObjectNode lineNode = JsonHandler.createObjectNode();

        for (Integer line : errorsMap.keySet()) {
            List<ErrorReportAgencies> errorsReports = errorsMap.get(line);
            for (ErrorReportAgencies error : errorsReports) {
                final ObjectNode errorNode = JsonHandler.createObjectNode();
                errorNode.put(ReportConstants.CODE, error.getCode().name() + ".KO");
                errorNode.put(ReportConstants.MESSAGE, VitamErrorMessages.getFromKey(error.getCode().name()));
                switch (error.getCode()) {
                    case STP_IMPORT_AGENCIES_MISSING_INFORMATIONS:
                        errorNode.put(ReportConstants.ADDITIONAL_INFORMATION,
                            error.getMissingInformations());
                        break;
                    case STP_IMPORT_AGENCIES_ID_DUPLICATION:
                        errorNode.put(ReportConstants.ADDITIONAL_INFORMATION,
                            error.getFileAgenciesModel().getId());
                        break;
                    case STP_IMPORT_AGENCIES_NOT_CSV_FORMAT:
                    case STP_IMPORT_AGENCIES_DELETE_USED_AGENCIES:
                    case STP_IMPORT_AGENCIES_UPDATED_AGENCIES:
                    default:
                        break;
                }
                messagesArrayNode.add(errorNode);
            }
            lineNode.set(String.format("line %s", line), messagesArrayNode);
        }
        reportFinal.set(ReportConstants.ERROR, lineNode);
        return new ByteArrayInputStream(JsonHandler.unprettyPrint(reportFinal).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get Errors as a map
     * 
     * @return errors as a map
     */
    @VisibleForTesting
    public Map<Integer, List<ErrorReportAgencies>> getErrorsMap() {
        return errorsMap;
    }

    private void store(InputStream stream, String digest, String extension)
        throws ReferentialException, InvalidParseOperationException, InvalidCreateOperationException,
        LogbookClientServerException, StorageException, LogbookClientBadRequestException,
        LogbookClientAlreadyExistsException {

        Integer sequence = vitamCounterService
            .getSequence(ParameterHelper.getTenantParameter(), SequenceType.AGENCIES_SEQUENCE);

        securisator.secureFiles(sequence, stream, extension, eip, digest, LogbookTypeProcess.STORAGE_AGENCIES,
            StorageCollectionType.AGENCIES, AGENCIES_IMPORT_EVENT, file_name);
    }

    private void storeReport(InputStream stream)
        throws ReferentialException, InvalidParseOperationException, InvalidCreateOperationException,
        LogbookClientServerException, StorageException, LogbookClientBadRequestException,
        LogbookClientAlreadyExistsException {
        final String fileName = eip + ".json";

        securisator
            .secureFiles(stream, eip, AGENCIES_REPORT_EVENT, LogbookTypeProcess.STORAGE_AGENCIES,
                StorageCollectionType.REPORTS, file_name, fileName);
    }

    private void storeCSV(File file)
        throws IOException, ReferentialException, StorageException, LogbookClientBadRequestException,
        InvalidCreateOperationException, LogbookClientAlreadyExistsException, LogbookClientServerException,
        InvalidParseOperationException {
        final DigestType digestType = VitamConfiguration.getDefaultTimestampDigestType();
        final Digest digest = new Digest(digestType);
        digest.update(new FileInputStream(file));

        store(new FileInputStream(file), digest.toString(), CSV);

    }

    private void storeJson()
        throws ReferentialException, InvalidParseOperationException, InvalidCreateOperationException,
        LogbookClientServerException, StorageException, LogbookClientBadRequestException,
        LogbookClientAlreadyExistsException {

        final DigestType digestType = VitamConfiguration.getDefaultTimestampDigestType();
        final Digest digest = new Digest(digestType);
        final SelectParserSingle parser = new SelectParserSingle(new VarNameAdapter());
        parser.parse(new Select().getFinalSelect());

        final RequestResponseOK documents = findDocuments(parser.getRequest().getFinalSelect());
        String json = JsonHandler.toJsonNode(documents.getResults()).toString();

        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        digest.update(json.getBytes(StandardCharsets.UTF_8));

        store(stream, digest.toString(), JSON);
    }

    @Override
    public void close() {
        logBookclient.close();
    }
}
