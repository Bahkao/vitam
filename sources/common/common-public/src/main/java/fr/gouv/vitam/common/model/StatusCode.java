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
package fr.gouv.vitam.common.model;

import javax.ws.rs.core.Response.Status;

/**
 * Enum StatusCode
 *
 * different constants status code for workflow , action handler and process
 *
 */
public enum StatusCode {

    /**
     * UNKNOWN : indicates that the workflow or the action handler or the process is in unknown status!
     */
    UNKNOWN,

    /**
     * STARTED : indicates that the workflow or the action handler or the process has been started
     */
    STARTED,

    /**
     * OK : indicates the successful without warning
     */
    OK,

    /**
     * WARNING : indicates successful with a general warning. Warning are often useful in preventing future Action
     * problems
     */
    WARNING,

    /**
     * KO : indicates the failed execution of the action
     */
    KO,

    /**
     * FATAL : indicates a critical error such as technical Exception ( runtime exception, illegal argument exception,
     * null pointer exception ...)
     */
    FATAL;

    /**
     * @return Status Level
     */
    public int getStatusLevel() {
        return ordinal();
    }

    /**
     *
     * @return True if the status is greater or equal to OK
     */
    public boolean isGreaterOrEqualToKo() {
        return compareTo(KO) >= 0;
    }

    /**
     *
     * @return True if the status is greater or equal to FATAL
     */
    public boolean isGreaterOrEqualToFatal() {
        return compareTo(FATAL) >= 0;
    }

    /**
     *
     * @return the HTTP Status equivalent to this StatusCode for OK, KO and WARNING status
     */
    public Status getEquivalentHttpStatus() {
        Status status = Status.OK;
        if (!StatusCode.OK.equals(this)) {
            if (StatusCode.WARNING.equals(this)) {
                status = Status.PARTIAL_CONTENT;
            } else {
                status = Status.BAD_REQUEST;
            }
        }

        return status;
    }

    /**
     * @param status
     * @return returns the StatusCode equivalent to the given HTTP status
     */
    public static StatusCode parseFromHttpStatus(int status) {
        StatusCode statusCode = StatusCode.OK;
        if (Status.OK.getStatusCode() != status) {
            if (Status.PARTIAL_CONTENT.getStatusCode() == status) {
                statusCode = StatusCode.WARNING;
            } else if (Status.INTERNAL_SERVER_ERROR.getStatusCode() == status) {
                statusCode = StatusCode.FATAL;
            } else {
                statusCode = StatusCode.KO;
            }
        }

        return statusCode;
    }
}
