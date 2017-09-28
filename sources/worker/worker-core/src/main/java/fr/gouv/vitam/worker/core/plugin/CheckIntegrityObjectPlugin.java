package fr.gouv.vitam.worker.core.plugin;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageClientException;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.handler.ActionHandler;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;

public class CheckIntegrityObjectPlugin extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(CheckIntegrityObjectPlugin.class);

    private static final String CHECK_INTEGRITY_ID = "AUDIT_FILE_INTEGRITY";
    public static final String QUALIFIERS = "#qualifiers";
    private static final int OG_NODE_RANK = 0;

    /**
     * Empty constructor CheckIntegrityObjectPlugin
     */
    public CheckIntegrityObjectPlugin() {
        // Empty
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler)
        throws ProcessingException, ContentAddressableStorageServerException {
        LOGGER.debug(CHECK_INTEGRITY_ID + " in execute");
        ObjectNode evDetData = JsonHandler.createObjectNode();

        final ItemStatus itemStatus = new ItemStatus(CHECK_INTEGRITY_ID);
        int nbObjectOK = 0;
        int nbObjectKO = 0;
        try (final StorageClient storageClient = StorageClientFactory.getInstance().getClient()) {
            JsonNode ogNode = (JsonNode) handler.getInput(OG_NODE_RANK);
            JsonNode qualifiersList = ogNode.get(QUALIFIERS);
            evDetData.set("OriginatingAgency", ogNode.get("#originating_agency"));

            for (JsonNode qualifier : qualifiersList) {
                if (qualifier.get("qualifier").asText().equals("PhysicalMaster")) {
                    continue;
                }
                JsonNode versions = qualifier.get("versions");
                ArrayNode errors = JsonHandler.createArrayNode();
                for (JsonNode version : versions) {
                    boolean checkDigest = true;
                    String messageDigest = null;
                    if (version.has("MessageDigest")) {
                        messageDigest = version.get("MessageDigest").asText();
                    } else {
                        nbObjectKO += 1;
                        continue;
                    }                

                    JsonNode storageInformation = version.get("_storage");
                    final String strategy = storageInformation.get("strategyId").textValue();
                    final List<String> offerIds = new ArrayList<>();
                    for (JsonNode offerId : storageInformation.get("offerIds")) {
                        offerIds.add(offerId.textValue());
                    }
                    
                    JsonNode offerToMetadata = storageClient.getObjectInformation(strategy, version.get("_id").asText(), offerIds);
                    for (String offerId : offerIds) {
                        String digest = null;
                        JsonNode metadata = offerToMetadata.findValue(offerId);
                        if (metadata != null){
                            digest = metadata.get("digest").asText();
                        } else {
                            checkDigest = false;
                            continue;
                        }
                        
                        if (messageDigest.equals(digest)) {
                            checkDigest = true;
                        } else {
                            checkDigest = false;
                        }
                    }
                    
                    if (checkDigest) {
                        nbObjectOK += 1;
                    } else {
                        nbObjectKO += 1;
                    }
                    
                    ObjectNode objectError = JsonHandler.createObjectNode();
                    
                    objectError.put("IdObj", version.get("_id").textValue());
                    objectError.put("Usage", version.get("DataObjectVersion").textValue());
                    errors.add(objectError);
                }
                evDetData.set("errors", errors);
            }
        } catch (StorageClientException e) {
            LOGGER.error("Storage server errors : ", e);
            itemStatus.increment(StatusCode.FATAL);
            return new ItemStatus(CHECK_INTEGRITY_ID).setItemsStatus(CHECK_INTEGRITY_ID, itemStatus);
        }
        
        if (nbObjectKO > 0) {
            itemStatus.increment(StatusCode.KO);
        }

        if (itemStatus.getGlobalStatus().equals(StatusCode.UNKNOWN)) {
            itemStatus.increment(StatusCode.OK);
        }

        itemStatus.setData("Detail", "Detail = OK : "+ nbObjectOK + " KO : " + nbObjectKO);
        try {
            evDetData.set("nbKO", JsonHandler.getFromString(String.valueOf(nbObjectKO)));
            itemStatus.setEvDetailData( JsonHandler.unprettyPrint( evDetData ) );
        } catch (InvalidParseOperationException e) {
            LOGGER.error(e);
        }
        
        return new ItemStatus(CHECK_INTEGRITY_ID).setItemsStatus(CHECK_INTEGRITY_ID, itemStatus);
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // TODO Auto-generated method stub

    }
    
    /**
     * @return CHECK_INTEGRITY_ID
     */
    public static final String getId() {
        return CHECK_INTEGRITY_ID;
    }

}
