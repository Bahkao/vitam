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
package fr.gouv.vitam.common.model.logbook;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Logbook Common event model
 */
public class LogbookEvent {
    @JsonProperty("evId")
    private String evId;

    @JsonProperty("evType")
    private String evType;

    @JsonProperty("evDateTime")
    private String evDateTime;

    @JsonProperty("evIdProc")
    private String evIdProc;

    @JsonProperty("evTypeProc")
    private String evTypeProc;

    @JsonProperty("outcome")
    private String outcome;

    @JsonProperty("outDetail")
    private String outDetail;

    @JsonProperty("outMessg")
    private String outMessg;

    @JsonProperty("agId")
    private String agId;

    @JsonProperty("obId")
    private String obId;

    @JsonProperty("evDetData")
    private String evDetData;

    /**
     * @return the evId
     */
    public String getEvId() {
        return evId;
    }

    /**
     * @param evId the evId to set
     *
     * @return this
     */
    public void setEvId(String evId) {
        this.evId = evId;
    }

    /**
     * @return the evType
     */
    public String getEvType() {
        return evType;
    }

    /**
     * @param evType the evType to set
     *
     * @return this
     */
    public void setEvType(String evType) {
        this.evType = evType;
    }

    /**
     * @return the evDateTime
     */
    public String getEvDateTime() {
        return evDateTime;
    }

    /**
     * @param evDateTime the evDateTime to set
     *
     * @return this
     */
    public void setEvDateTime(String evDateTime) {
        this.evDateTime = evDateTime;
    }

    /**
     * @return the evIdProc
     */
    public String getEvIdProc() {
        return evIdProc;
    }

    /**
     * @param evIdProc the evIdProc to set
     *
     * @return this
     */
    public void setEvIdProc(String evIdProc) {
        this.evIdProc = evIdProc;
    }

    /**
     * @return the evTypeProc
     */
    public String getEvTypeProc() {
        return evTypeProc;
    }

    /**
     * @param evTypeProc the evTypeProc to set
     *
     * @return this
     */
    public void setEvTypeProc(String evTypeProc) {
        this.evTypeProc = evTypeProc;
    }

    /**
     * @return the outcome
     */
    public String getOutcome() {
        return outcome;
    }

    /**
     * @param outcome the outcome to set
     *
     * @return this
     */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /**
     * @return the outDetail
     */
    public String getOutDetail() {
        return outDetail;
    }

    /**
     * @param outDetail the outDetail to set
     *
     * @return this
     */
    public void setOutDetail(String outDetail) {
        this.outDetail = outDetail;
    }

    /**
     * @return the outMessg
     */
    public String getOutMessg() {
        return outMessg;
    }

    /**
     * @param outMessg the outMessg to set
     *
     * @return this
     */
    public void setOutMessg(String outMessg) {
        this.outMessg = outMessg;
    }

    /**
     * @return the agId
     */
    public String getAgId() {
        return agId;
    }

    /**
     * @param agId the agId to set
     *
     * @return this
     */
    public void setAgId(String agId) {
        this.agId = agId;
    }

    /**
     * @return the obId
     */
    public String getObId() {
        return obId;
    }

    /**
     * @param obId the obId to set
     *
     * @return this
     */
    public void setObId(String obId) {
        this.obId = obId;
    }

    /**
     * @return the evDetData
     */
    public String getEvDetData() {
        return evDetData;
    }

    /**
     * @param evDetData the evDetData to set
     *
     * @return this
     */
    public void setEvDetData(String evDetData) {
        this.evDetData = evDetData;
    }


}
