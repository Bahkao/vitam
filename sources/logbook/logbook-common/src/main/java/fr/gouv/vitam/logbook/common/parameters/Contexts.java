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

package fr.gouv.vitam.logbook.common.parameters;

/**
 * TODO: to review, hack for release 4 and demo Note US #2774: Moving Contexts enum from ingestExternal and little
 * refactoring
 */
public enum Contexts {

    BLANK_TEST(LogbookTypeProcess.INGEST_TEST, "PROCESS_SIP_UNITARY"),
    HOLDING_SCHEME(LogbookTypeProcess.MASTERDATA, "HOLDINGSCHEME"),
    FILING_SCHEME(LogbookTypeProcess.INGEST, "FILINGSCHEME"),
    DEFAULT_WORKFLOW(LogbookTypeProcess.INGEST, "PROCESS_SIP_UNITARY"),
    UPDATE_RULES_ARCHIVE_UNITS(LogbookTypeProcess.UPDATE, "UPDATE_RULES_ARCHIVE_UNITS"),
    CHECK(LogbookTypeProcess.CHECK, "CHECK_LOGBOOK_OP_SECURISATION"),
    AUDIT_WORKFLOW(LogbookTypeProcess.AUDIT, "PROCESS_AUDIT"),
    UNIT_LFC_TRACEABILITY(LogbookTypeProcess.TRACEABILITY, "LOGBOOK_UNIT_LFC_TRACEABILITY"),
    OBJECTGROUP_LFC_TRACEABILITY(LogbookTypeProcess.TRACEABILITY, "LOGBOOK_OBJECTGROUP_LFC_TRACEABILITY"),
    EXPORT_DIP(LogbookTypeProcess.EXPORT_DIP, "EXPORT_DIP"),
    EVIDENCE_AUDIT(LogbookTypeProcess.AUDIT, "EVIDENCE_AUDIT"),
    DATA_MIGRATION(LogbookTypeProcess.DATA_MIGRATION, "DATA_MIGRATION"),
    RECLASSIFICATION(LogbookTypeProcess.RECLASSIFICATION, "RECLASSIFICATION");

    private LogbookTypeProcess logbookTypeProcess;
    private String eventType;

    Contexts(LogbookTypeProcess logbookTypeProcess, String eventType) {
        this.logbookTypeProcess = logbookTypeProcess;
        this.eventType = eventType;
    }

    /**
     * Get logbook type process (evTypeProc)
     *
     * @return the logbook type process
     */
    public LogbookTypeProcess getLogbookTypeProcess() {
        return logbookTypeProcess;
    }

    /**
     * Get eventType value
     *
     * @return the event type
     */
    public String getEventType() {
        return eventType;
    }
}
