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
package fr.gouv.vitam.functional.administration.common;

import org.bson.Document;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.parameter.ParameterHelper;

/**
 * Defines an Ingest contract model for SIP transfer control. </BR>
 * It's an implementation of the SEDA specification and NF Z44022 MEDONA concerning the communication between a
 * TransferringAgency and an ArchivalAgency.
 * 
 */
public class IngestContract extends VitamDocument<IngestContract> {

    /**
     * the serial version uid
     */
    private static final long serialVersionUID = -3547871388720359674L;
    /**
     * the contract name
     */
    public static final String NAME = "Name";
    /**
     * the contract description
     */
    public static final String DESCRIPTION = "Description";
    /**
     * the contract status
     */
    public static final String STATUS = "Status";
    /**
     * the creatation date of contract 
     */
    public static final String CREATIONDATE = "CreationDate";
    /**
     * the last update of contract 
     */
    public static final String LAST_UPDATE = "LastUpdate";
    /**
     * the activation date of contract 
     */
    public static final String ACTIVATIONDATE = "ActivationDate";
    /**
     * the desactication date of contract 
     */
    public static final String DEACTIVATIONDATE = "DeactivationDate";


    /**
     * Empty Constructor
     */
    public IngestContract() {
        // Empty
        append(TENANT_ID, ParameterHelper.getTenantParameter());
    }

    /**
     * Constructor
     *
     * @param document data in format Document to create contact
     */
    public IngestContract(Document document) {
        super(document);
        append(TENANT_ID, ParameterHelper.getTenantParameter());
    }

    /**
     * @param content in format JsonNode to create contract
     */
    public IngestContract(JsonNode content) {
        super(content);
        append(TENANT_ID, ParameterHelper.getTenantParameter());
    }

    /**
     * @param content in format String to create contract
     */
    public IngestContract(String content) {
        super(content);
        append(TENANT_ID, ParameterHelper.getTenantParameter());
    }

    /**
     * 
     * @param tenantId the working tenant
     */
    public IngestContract(Integer tenantId) {
        append(TENANT_ID, tenantId);
    }

    /**
     * @param id the id of ingest contract
     * @return AccessionRegisterDetail
     */
    public IngestContract setId(String id) {
        append(VitamDocument.ID, id);
        return this;
    }

    /**
     * Name of the contract
     * @return name of contract
     */
    public String getName() {
        return getString(NAME);
    }

    /**
     * Set or change the contract name
     * @param name to set 
     * @return this
     */
    public IngestContract setName(String name) {
        append(NAME, name);
        return this;
    }

    /**
     * Get the contract description
     * @return this
     */
    public String getDescription() {
        return getString(DESCRIPTION);
    }

    /**
     * Set or change the contract description
     * @param description to set to contact
     * @return this
     */
    public IngestContract setDescription(String description) {
        append(DESCRIPTION, description);
        return this;
    }

    
    /**
     * Get the contract status
     * @return status of ingest contact
     */
    public IngestContractStatus getStatus() {
        String status = getString(STATUS);
        if (status == null) {
            return null;
        }
        try {
            return IngestContractStatus.valueOf(status);
        } catch (IllegalArgumentException exp) {
            // no value corresponds to this status => corrupted state
            return null;
        }
    }

    /**
     * Set or change the contract status
     * @param status to set 
     * @return this
     */
    public IngestContract setStatus(IngestContractStatus status) {
        append(STATUS, status.name());
        return this;
    }

    /**
     * @return creation date of contract
     */
    public String getCreationdate() {
        return getString(CREATIONDATE);
    }

    /**
     * @param creationdate to set
     * @return this
     */
    public IngestContract setCreationdate(String creationdate) {
        append(CREATIONDATE, creationdate);
        return this;
    }

    /**
     * @return last update of contract
     */
    public String getLastupdate() {
        return getString(LAST_UPDATE);
    }

    /**
     * @param lastupdate to set 
     * @return this
     */
    public IngestContract setLastupdate(String lastupdate) {
        append(LAST_UPDATE, lastupdate);
        return this;
    }

    /**
     * @return activation date of contract
     */
    public String getActivationdate() {
        return getString(ACTIVATIONDATE);
    }

    /**
     * @param activationdate to set
     * @return this
     */
    public IngestContract setActivationdate(String activationdate) {
        append(ACTIVATIONDATE, activationdate);
        return this;
    }

    /**
     * @return desactivation date of contract
     */
    public String getDeactivationdate() {
        return getString(DEACTIVATIONDATE);
    }

    /**
     * @param deactivationdate to set
     * @return this
     */
    public IngestContract setDeactivationdate(String deactivationdate) {
        append(DEACTIVATIONDATE, deactivationdate);
        return this;
    }


}
