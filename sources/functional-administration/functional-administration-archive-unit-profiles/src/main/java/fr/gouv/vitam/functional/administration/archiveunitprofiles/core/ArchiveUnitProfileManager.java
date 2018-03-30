/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019) <p> contact.vitam@culture.gouv.fr <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently. <p> This software is governed by the CeCILL 2.1 license under French law and
 * abiding by the rules of distribution of free software. You can use, modify and/ or redistribute the software under
 * the terms of the CeCILL 2.1 license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". <p> As a counterpart to the access to the source code and rights to copy, modify and
 * redistribute granted by the license, users are provided only with a limited warranty and the software's author, the
 * holder of the economic rights, and the successive licensors have only limited liability. <p> In this respect, the
 * user's attention is drawn to the risks associated with loading, using, modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software, that may mean that it is complicated to
 * manipulate, and that also therefore means that it is reserved for developers and experienced professionals having
 * in-depth computer knowledge. Users are therefore encouraged to load and test the software's suitability as regards
 * their requirements in conditions enabling the security of their systems and/or data to be ensured and, more
 * generally, to use and operate it in the same conditions as regards security. <p> The fact that you are presently
 * reading this means that you have had knowledge of the CeCILL 2.1 license and that you accept its terms.
 */

package fr.gouv.vitam.functional.administration.archiveunitprofiles.core;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.error.VitamCode;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamDBException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.i18n.VitamLogbookMessages;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.json.SchemaValidationUtils;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileModel;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileStatus;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.functional.administration.archiveunitprofiles.core.ArchiveUnitProfileValidator.RejectionCause;
import fr.gouv.vitam.functional.administration.common.ArchiveUnitProfile;
import fr.gouv.vitam.functional.administration.common.server.FunctionalAdminCollections;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.metadata.api.exception.MetaDataClientServerException;
import fr.gouv.vitam.metadata.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import org.bson.conversions.Bson;

/**
 * This class manage validation and log operation of profile service
 */
public class ArchiveUnitProfileManager {

    private static final String ARCHIVE_UNIT_PROFILE_SERVICE_ERROR = "Archive unit profile service Error";
    private static final String FUNCTIONAL_MODULE_ARCHIVE_UNIT_PROFILE = "FunctionalModule-ArchiveUnitProfile";
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ArchiveUnitProfileManager.class);

    private List<ArchiveUnitProfileValidator> validators;

    private final GUID eip;

    private LogbookOperationsClient logbookClient;
    private final MetaDataClient metaDataClient;

    public ArchiveUnitProfileManager(LogbookOperationsClient logbookClient, MetaDataClient metaDataClient, GUID eip) {
        this.logbookClient = logbookClient;
        this.metaDataClient = metaDataClient;
        this.eip = eip;
        validators = Arrays.asList(
            createMandatoryParamsValidator(),
            createWrongFieldFormatValidator(),
            createCheckDuplicateInDatabaseValidator());
    }

    public boolean validateArchiveUnitProfile(ArchiveUnitProfileModel profile,
        VitamError error) {

        for (ArchiveUnitProfileValidator validator : validators) {
            Optional<RejectionCause> result = validator.validate(profile);
            if (result.isPresent()) {
                // there is a validation error on this profile
                /* profile is valid, add it to the list to persist */
                error.addToErrors(getVitamError(result.get().getReason()));
                // once a validation error is detected on a profile, jump to next profile
                return false;
            }
        }
        return true;
    }


    private VitamError getVitamError(String error) {
        return new VitamError(VitamCode.ARCHIVE_UNIT_PROFILE_VALIDATION_ERROR.getItem())
            .setMessage(ARCHIVE_UNIT_PROFILE_SERVICE_ERROR)
            .setState("ko").setContext(FUNCTIONAL_MODULE_ARCHIVE_UNIT_PROFILE).setDescription(error);
    }

    /**
     * Log validation error (business error)
     *
     * @param errorsDetails
     */
    public void logValidationError(String eventType, String objectId, String errorsDetails) throws VitamException {
        LOGGER.error("There validation errors on the input file {}", errorsDetails);
        final GUID eipId = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
        final LogbookOperationParameters logbookParameters = LogbookParametersFactory
            .newLogbookOperationParameters(eipId, eventType, eip, LogbookTypeProcess.MASTERDATA,
                StatusCode.KO,
                VitamLogbookMessages.getCodeOp(eventType, StatusCode.KO), eip);
        logbookMessageError(objectId, errorsDetails, logbookParameters);

        logbookClient.update(logbookParameters);
    }

    private void logbookMessageError(String objectId, String errorsDetails,
        LogbookOperationParameters logbookParameters) {
        if (null != errorsDetails && !errorsDetails.isEmpty()) {
            try {
                final ObjectNode object = JsonHandler.createObjectNode();
                object.put("archiveUnitProfileCheck", errorsDetails);

                final String wellFormedJson = SanityChecker.sanitizeJson(object);
                logbookParameters.putParameterValue(LogbookParameterName.eventDetailData, wellFormedJson);
            } catch (InvalidParseOperationException e) {
                // Do nothing
            }
        }
        if (null != objectId && !objectId.isEmpty()) {
            logbookParameters.putParameterValue(LogbookParameterName.objectIdentifier, objectId);
        }
    }

    /**
     * log fatal error (system or technical error)
     *
     * @param errorsDetails
     * @throws VitamException
     */
    public void logFatalError(String eventType, String objectId, String errorsDetails) throws VitamException {
        LOGGER.error("There validation errors on the input file {}", errorsDetails);
        final GUID eipId = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
        final LogbookOperationParameters logbookParameters = LogbookParametersFactory
            .newLogbookOperationParameters(eipId, eventType, eip, LogbookTypeProcess.MASTERDATA,
                StatusCode.FATAL,
                VitamLogbookMessages.getCodeOp(eventType, StatusCode.FATAL), eip);

        logbookMessageError(objectId, errorsDetails, logbookParameters);

        logbookClient.update(logbookParameters);
    }

    /**
     * log start process
     *
     * @throws VitamException
     */
    public void logStarted(String eventType, String objectId) throws VitamException {
        final LogbookOperationParameters logbookParameters = LogbookParametersFactory
            .newLogbookOperationParameters(eip, eventType, eip, LogbookTypeProcess.MASTERDATA,
                StatusCode.STARTED,
                VitamLogbookMessages.getCodeOp(eventType, StatusCode.STARTED), eip);

        logbookMessageError(objectId, null, logbookParameters);
        logbookClient.create(logbookParameters);

    }

    /**
     * log end success process
     *
     * @throws VitamException
     */
    public void logSuccess(String eventType, String objectId, String message) throws VitamException {
        final GUID eipId = GUIDFactory.newOperationLogbookGUID(ParameterHelper.getTenantParameter());
        final LogbookOperationParameters logbookParameters = LogbookParametersFactory
            .newLogbookOperationParameters(eipId, eventType, eip, LogbookTypeProcess.MASTERDATA,
                StatusCode.OK,
                VitamLogbookMessages.getCodeOp(eventType, StatusCode.OK), eip);

        if (null != objectId && !objectId.isEmpty()) {
            logbookParameters.putParameterValue(LogbookParameterName.objectIdentifier, objectId);
        }

        if (null != message && !message.isEmpty()) {
            logbookParameters.putParameterValue(LogbookParameterName.eventDetailData, message);
        }

        logbookClient.update(logbookParameters);
    }

    /**
     * Validate that profile have not a missing mandatory parameter
     *
     * @return
     */
    public ArchiveUnitProfileValidator createMandatoryParamsValidator() {
        return (profile) -> {
            RejectionCause rejection = null;
            if (profile.getName() == null || profile.getName().length() == 0) {
                rejection = RejectionCause.rejectMandatoryMissing(ArchiveUnitProfile.NAME);
            }
            return (rejection == null) ? Optional.empty() : Optional.of(rejection);
        };
    }

    /**
     * Set a default value if null and check for wrong data type/format/value for fields
     *
     * @return the validator with thrown errors
     */
    public ArchiveUnitProfileValidator createWrongFieldFormatValidator() {
        return (archiveUnitProfile) -> {
            RejectionCause rejection = null;

            String now = LocalDateUtil.getFormattedDateForMongo(LocalDateUtil.now());
            if (archiveUnitProfile.getStatus() == null) {
                archiveUnitProfile.setStatus(ArchiveUnitProfileStatus.INACTIVE);
            }

            if (!archiveUnitProfile.getStatus().equals(ArchiveUnitProfileStatus.ACTIVE) &&
                !archiveUnitProfile.getStatus().equals(ArchiveUnitProfileStatus.INACTIVE)) {
                LOGGER.error("Error archive unit profile status not valide (must be ACTIVE or INACTIVE");
                rejection =
                    RejectionCause.rejectMandatoryMissing("Status " + archiveUnitProfile.getStatus() +
                        " not valide must be ACTIVE or INACTIVE");
            }


            try {
                if (archiveUnitProfile.getCreationdate() == null ||
                    archiveUnitProfile.getCreationdate().trim().isEmpty()) {
                    archiveUnitProfile.setCreationdate(now);
                } else {
                    archiveUnitProfile
                        .setCreationdate(LocalDateUtil.getFormattedDateForMongo(archiveUnitProfile.getCreationdate()));
                }

            } catch (Exception e) {
                LOGGER.error("Error profile parse dates", e);
                rejection = RejectionCause.rejectMandatoryMissing("CreationDate");
            }
            try {
                if (archiveUnitProfile.getActivationdate() == null ||
                    archiveUnitProfile.getActivationdate().trim().isEmpty()) {
                    archiveUnitProfile.setActivationdate(now);
                } else {
                    archiveUnitProfile.setActivationdate(
                        LocalDateUtil.getFormattedDateForMongo(archiveUnitProfile.getActivationdate()));

                }
            } catch (Exception e) {
                LOGGER.error("Error profile parse dates", e);
                rejection = RejectionCause.rejectMandatoryMissing("ActivationDate");
            }
            try {

                if (archiveUnitProfile.getDeactivationdate() == null ||
                    archiveUnitProfile.getDeactivationdate().trim().isEmpty()) {
                    archiveUnitProfile.setDeactivationdate(now);
                } else {

                    archiveUnitProfile.setDeactivationdate(
                        LocalDateUtil.getFormattedDateForMongo(archiveUnitProfile.getDeactivationdate()));
                }
            } catch (Exception e) {
                LOGGER.error("Error profile parse dates", e);
                rejection = RejectionCause.rejectMandatoryMissing("DeactivationDate");
            }

            archiveUnitProfile.setLastupdate(now);

            return (rejection == null) ? Optional.empty() : Optional.of(rejection);
        };
    }

    /**
     * Check if the Id of the archive unit profile already exists in database
     *
     * @return
     */
    public ArchiveUnitProfileValidator checkDuplicateInIdentifierSlaveModeValidator() {
        return (archiveUnitProfile) -> {
            if (archiveUnitProfile.getIdentifier() == null || archiveUnitProfile.getIdentifier().isEmpty()) {
                return Optional.of(ArchiveUnitProfileValidator.RejectionCause.rejectMandatoryMissing(
                    ArchiveUnitProfile.IDENTIFIER));
            }
            RejectionCause rejection = null;
            final int tenant = ParameterHelper.getTenantParameter();
            final Bson clause =
                and(eq(VitamDocument.TENANT_ID, tenant),
                    eq(ArchiveUnitProfile.IDENTIFIER, archiveUnitProfile.getIdentifier()));
            final boolean exist = FunctionalAdminCollections.ARCHIVE_UNIT_PROFILE.getCollection().count(clause) > 0;
            if (exist) {
                rejection = ArchiveUnitProfileValidator.RejectionCause
                    .rejectDuplicatedInDatabase(archiveUnitProfile.getIdentifier());
            }
            return rejection == null ? Optional.empty() : Optional.of(rejection);
        };
    }

    /**
     * Check if the profile identifier already exists in database
     *
     * @return
     */
    public ArchiveUnitProfileValidator createCheckDuplicateInDatabaseValidator() {
        return (profile) -> {
            if (ParametersChecker.isNotEmpty(profile.getIdentifier())) {
                int tenant = ParameterHelper.getTenantParameter();
                Bson clause = and(eq(VitamDocument.TENANT_ID, tenant),
                    eq(ArchiveUnitProfile.IDENTIFIER, profile.getIdentifier()));
                boolean exist = FunctionalAdminCollections.ARCHIVE_UNIT_PROFILE.getCollection().count(clause) > 0;
                if (exist) {
                    return Optional.of(RejectionCause.rejectDuplicatedInDatabase(profile.getName()));
                }
            }
            return Optional.empty();

        };
    }

    /**
     * Check if the archive unit profile name already exists in database
     *
     * @return
     */
    public ArchiveUnitProfileValidator createCheckDuplicateNamesInDatabaseValidator() {
        return (profile) -> {
            if (ParametersChecker.isNotEmpty(profile.getName())) {
                int tenant = ParameterHelper.getTenantParameter();
                Bson clause = and(eq(VitamDocument.TENANT_ID, tenant), eq(ArchiveUnitProfile.NAME, profile.getName()));
                boolean exist = FunctionalAdminCollections.ARCHIVE_UNIT_PROFILE.getCollection().count(clause) > 0;
                if (exist) {
                    return Optional.of(RejectionCause.rejectDuplicatedInDatabase(profile.getName()));
                }
            }
            return Optional.empty();

        };
    }


    /**
     * Check if the archive unit ControlSchema property is a valid jon schema .
     *
     * @return
     */
    public ArchiveUnitProfileValidator createJsonSchemaValidator() {
        return (profile) -> {
            if (profile.getControlSchema() != null) {
                try {
                    new SchemaValidationUtils(profile.getControlSchema(), true);
                    return Optional.empty();
                } catch (ProcessingException | FileNotFoundException | InvalidParseOperationException e) {
                    return Optional.of(RejectionCause.rejectJsonShema(ArchiveUnitProfile.CONTROLSCHEMA));
                }
            } else {
                return Optional.of(RejectionCause.rejectMandatoryMissing(ArchiveUnitProfile.CONTROLSCHEMA));
            }

        };
    }

    /**
     * Check if the ArchiveUnitProfile is used in a ArchiveUnit
     * @return
     */
    public ArchiveUnitProfileValidator createCheckUsedJsonSchema() {
        return (profile) -> {
            if (ParametersChecker.isNotEmpty(profile.getIdentifier())) {

                final SelectMultiQuery selectMultiple = new SelectMultiQuery();
                try {
                    selectMultiple
                        .setQuery(QueryHelper.eq("ArchiveUnitProfile", profile.getIdentifier()));
                    JsonNode jsonNode = metaDataClient.selectUnits(selectMultiple.getFinalSelect());
                    if (jsonNode != null && jsonNode.get("$results").size() > 0) {
                        return Optional.of(RejectionCause.rejectJsonSchemaModificationIfInUse(profile.getName()));
                    }
                } catch (InvalidCreateOperationException | VitamDBException | InvalidParseOperationException | MetaDataExecutionException |
                    MetaDataDocumentSizeException | MetaDataClientServerException e) {
                    return Optional.of(RejectionCause.rejectJsonSchemaModificationIfInUse(profile.getName()));
                }

            }
            return Optional.empty();

        };
    }


}

