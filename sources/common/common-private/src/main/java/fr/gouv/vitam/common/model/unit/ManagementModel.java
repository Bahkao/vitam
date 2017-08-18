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
package fr.gouv.vitam.common.model.unit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.gouv.culture.archivesdefrance.seda.v2.UpdateOperationType;
import fr.gouv.vitam.common.SedaConstants;

@JsonIgnoreProperties({"OriginatingAgency"})
public class ManagementModel {

    @JsonProperty("StorageRule")
    private RuleCategoryModel storage;

    @JsonProperty("AppraisalRule")
    private RuleCategoryModel appraisal;

    @JsonProperty("AccessRule")
    private RuleCategoryModel access;

    @JsonProperty("DisseminationRule")
    private RuleCategoryModel dissemination;

    @JsonProperty("ReuseRule")
    private RuleCategoryModel reuse;

    @JsonProperty("ClassificationRule")
    private RuleCategoryModel classification;

    private Boolean needAuthorization;

    private UpdateOperationType updateOperationType;

    public void setStorage(RuleCategoryModel storage) {
        this.storage = storage;
    }

    public RuleCategoryModel getStorage() {
        return storage;
    }

    public RuleCategoryModel getAppraisal() {
        return appraisal;
    }

    public void setAppraisal(RuleCategoryModel appraisal) {
        this.appraisal = appraisal;
    }

    public void setAccess(RuleCategoryModel access) {
        this.access = access;
    }

    public RuleCategoryModel getAccess() {
        return access;
    }

    public RuleCategoryModel getDissemination() {
        return dissemination;
    }

    public void setDissemination(RuleCategoryModel dissemination) {
        this.dissemination = dissemination;
    }

    public RuleCategoryModel getReuse() {
        return reuse;
    }

    public void setReuse(RuleCategoryModel reuse) {
        this.reuse = reuse;
    }

    public RuleCategoryModel getClassification() {
        return classification;
    }

    public void setClassification(RuleCategoryModel classification) {
        this.classification = classification;
    }

    public Boolean isNeedAuthorization() {
        return needAuthorization;
    }

    public UpdateOperationType getUpdateOperationType() {
        return updateOperationType;
    }

    public void setUpdateOperationType(UpdateOperationType updateOperationType) {
        this.updateOperationType = updateOperationType;
    }

    @JsonIgnore
    public void setRuleCategoryModel(RuleCategoryModel ruleCategoryModel, String type) {
        switch (type) {
            case SedaConstants.TAG_RULE_STORAGE:
                setStorage(ruleCategoryModel);
                break;
            case SedaConstants.TAG_RULE_APPRAISAL:
                setAppraisal(ruleCategoryModel);
                break;
            case SedaConstants.TAG_RULE_ACCESS:
                setAccess(ruleCategoryModel);
                break;
            case SedaConstants.TAG_RULE_CLASSIFICATION:
                setClassification(ruleCategoryModel);
                break;
            case SedaConstants.TAG_RULE_DISSEMINATION:
                setDissemination(ruleCategoryModel);
                break;
            case SedaConstants.TAG_RULE_REUSE:
                setReuse(ruleCategoryModel);
                break;
            default:
                throw new IllegalArgumentException("Type cannot be " + type);
        }
    }

    @JsonIgnore
    public RuleCategoryModel getRuleCategoryModel(String type) {
        switch (type) {
            case SedaConstants.TAG_RULE_STORAGE:
                return getStorage();
            case SedaConstants.TAG_RULE_APPRAISAL:
                return getAppraisal();
            case SedaConstants.TAG_RULE_ACCESS:
                return getAccess();
            case SedaConstants.TAG_RULE_CLASSIFICATION:
                return getClassification();
            case SedaConstants.TAG_RULE_DISSEMINATION:
                return getDissemination();
            case SedaConstants.TAG_RULE_REUSE:
                return getReuse();
            default:
                throw new IllegalArgumentException("Type cannot be " + type);
        }
    }
}
