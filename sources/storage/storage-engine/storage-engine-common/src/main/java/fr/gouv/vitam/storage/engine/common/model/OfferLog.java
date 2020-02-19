/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
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
package fr.gouv.vitam.storage.engine.common.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.gouv.vitam.common.LocalDateUtil;

/**
 * OfferLog.
 */
public class OfferLog {

    /**
     * Sequence.
     */
    @JsonProperty("Sequence")
    private long sequence;

    /**
     * Time.
     */
    @JsonProperty("Time")
    private LocalDateTime time;

    /**
     * Container name
     */
    @JsonProperty("Container")
    private String container;

    /**
     * Filename
     */
    @JsonProperty("FileName")
    private String fileName;

    /**
     * Action
     */
    @JsonProperty("Action")
    private OfferLogAction action;

    /**
     * Format version (default version assumed = V1)
     */
    @JsonProperty("_FormatVersion")
    private OfferLogFormatVersion formatVersion = OfferLogFormatVersion.V1;

    /**
     * Constructor,jackson usage only
     */
    public OfferLog() {}

    /**
     * Constructor
     * 
     * @param container container
     * @param fileName fileName
     * @param action action
     */
    public OfferLog(String container, String fileName, OfferLogAction action) {
        this.time = LocalDateUtil.now();
        this.container = container;
        this.fileName = fileName;
        this.action = action;
        this.formatVersion = OfferLogFormatVersion.V2;
    }

    public OfferLog(long sequence, LocalDateTime time, String container, String fileName,
        OfferLogAction action) {
        this.sequence = sequence;
        this.time = time;
        this.container = container;
        this.fileName = fileName;
        this.action = action;
        this.formatVersion = OfferLogFormatVersion.V2;
    }

    /**
     * @return the sequence
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * @param sequence the sequence to set
     *
     * @return this
     */
    public OfferLog setSequence(long sequence) {
        this.sequence = sequence;
        return this;
    }

    /**
     * @return the time
     */
    public LocalDateTime getTime() {
        return time;
    }

    /**
     * @param time the time to set
     *
     * @return this
     */
    public OfferLog setTime(LocalDateTime time) {
        this.time = time;
        return this;
    }

    /**
     * @return the container
     */
    public String getContainer() {
        return container;
    }

    /**
     * @param container the container to set
     *
     * @return this
     */
    public OfferLog setContainer(String container) {
        this.container = container;
        return this;
    }

    /**
     * @return the fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @param fileName the fileName to set
     *
     * @return this
     */
    public OfferLog setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    /**
     * @return the action
     */
    public OfferLogAction getAction() {
        return action;
    }

    /**
     * @param action the action to set
     *
     * @return this
     */
    public OfferLog setAction(OfferLogAction action) {
        this.action = action;
        return this;
    }

    public OfferLogFormatVersion getFormatVersion() {
        return formatVersion;
    }

    public OfferLog setFormatVersion(OfferLogFormatVersion formatVersion) {
        this.formatVersion = formatVersion;
        return this;
    }
}
