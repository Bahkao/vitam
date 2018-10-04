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
package fr.gouv.vitam.worker.core.plugin.elimination.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.gouv.vitam.worker.core.plugin.elimination.model.EliminationActionObjectGroupStatus;

import java.util.Set;

public class EliminationActionObjectGroupReportExportEntry {

    @JsonProperty("id")
    private String objectGroupId;
    @JsonProperty("originatingAgency")
    private String originatingAgency;
    @JsonProperty("opi")
    private String initialOperation;
    @JsonProperty("deletedParentUnitIds")
    private Set<String> deletedParentUnitIds;
    @JsonProperty("objectIds")
    private Set<String> objectIds;
    @JsonProperty("status")
    private EliminationActionObjectGroupStatus status;

    public EliminationActionObjectGroupReportExportEntry() {
        // Empty constructor for deserialization
    }

    public EliminationActionObjectGroupReportExportEntry(String objectGroupId, String originatingAgency,
        String initialOperation, Set<String> deletedParentUnitIds,
        Set<String> objectIds, EliminationActionObjectGroupStatus status) {
        this.objectGroupId = objectGroupId;
        this.originatingAgency = originatingAgency;
        this.initialOperation = initialOperation;
        this.deletedParentUnitIds = deletedParentUnitIds;
        this.objectIds = objectIds;
        this.status = status;
    }

    public String getObjectGroupId() {
        return objectGroupId;
    }

    public void setObjectGroupId(String objectGroupId) {
        this.objectGroupId = objectGroupId;
    }

    public String getOriginatingAgency() {
        return originatingAgency;
    }

    public void setOriginatingAgency(String originatingAgency) {
        this.originatingAgency = originatingAgency;
    }

    public String getInitialOperation() {
        return initialOperation;
    }

    public void setInitialOperation(String initialOperation) {
        this.initialOperation = initialOperation;
    }

    public Set<String> getDeletedParentUnitIds() {
        return deletedParentUnitIds;
    }

    public void setDeletedParentUnitIds(Set<String> deletedParentUnitIds) {
        this.deletedParentUnitIds = deletedParentUnitIds;
    }

    public Set<String> getObjectIds() {
        return objectIds;
    }

    public void setObjectIds(Set<String> objectIds) {
        this.objectIds = objectIds;
    }

    public EliminationActionObjectGroupStatus getStatus() {
        return status;
    }

    public void setStatus(EliminationActionObjectGroupStatus status) {
        this.status = status;
    }
}
