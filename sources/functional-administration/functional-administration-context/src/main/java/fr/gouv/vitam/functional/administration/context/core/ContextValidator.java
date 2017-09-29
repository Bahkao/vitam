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
package fr.gouv.vitam.functional.administration.context.core;

import java.util.Optional;

import fr.gouv.vitam.common.model.administration.ContextModel;

public interface ContextValidator {
    /**
     * Validate a context object
     *
     * @param context to validate
     * @return empty optional if OK, Else return the rejection cause
     */
    Optional<ContextRejectionCause> validate(ContextModel context);

    public class ContextRejectionCause {
        public static String ERR_ID_NOT_ALLOWED_IN_CREATE = "Id must be null when creating context (%s)";
        public static String ERR_DUPLICATE_CONTEXT_ENTRY =
            "One or many contexts in the imported list have the same name : %s";
        public static String ERR_MANDATORY_FIELD = "The field %s is mandatory";
        public static String ERR_DUPLICATE_CONTEXT = "The context %s already exists in database";
        public static String ERR_NO_EXISTANCE_INGEST = "The ingest contract %s does not exist";
        public static String ERR_NO_EXISTANCE_ACCESS = "The access contract %s does not exist";
        public static String ERR_INVALID_SECURITY_PROFILE = "The security profile %s does not exist";

        private String reason;

        public ContextRejectionCause(String error) {
            setReason(error);
        }

        /**
         * Reject if id exisit and the action is creation. If id exists, it should be an update instead of create
         *
         * @param contextIdentifier
         * @return ContextRejectionCause
         */
        public static ContextRejectionCause rejectIdNotAllowedInCreate(String contextIdentifier) {
            return new ContextRejectionCause(String.format(ERR_ID_NOT_ALLOWED_IN_CREATE, contextIdentifier));
        }

        /**
         * Reject if multiple context have the same name in the same request before persist into database. The context
         * identifier must be unique
         *
         * @param contextIdentifier
         * @return ContextRejectionCause
         */
        public static ContextRejectionCause rejectDuplicatedEntry(String contextIdentifier) {
            return new ContextRejectionCause(String.format(ERR_DUPLICATE_CONTEXT_ENTRY, contextIdentifier));
        }

        /**
         * Reject if one of multiple mandatory parameter are null
         * 
         * @param fieldName
         * @return ContextRejectionCause
         */
        public static ContextRejectionCause rejectMandatoryMissing(String fieldName) {
            return new ContextRejectionCause(String.format(ERR_MANDATORY_FIELD, fieldName));
        }

        /**
         * Verify for each context if already exists one in database that have the same name. The database my manage
         * this kind of constraint (by creating an unique index on the field or column)
         * 
         * @param contextName
         * @return ContextRejectionCause
         */
        public static ContextRejectionCause rejectDuplicatedInDatabase(String contextName) {
            return new ContextRejectionCause(String.format(ERR_DUPLICATE_CONTEXT, contextName));
        }

        public static ContextRejectionCause rejectNoExistanceOfIngestContract(String contextName) {
            return new ContextRejectionCause(String.format(ERR_NO_EXISTANCE_INGEST, contextName));
        }

        public static ContextRejectionCause rejectNoExistanceOfAccessContract(String contextName) {
            return new ContextRejectionCause(String.format(ERR_NO_EXISTANCE_ACCESS, contextName));
        }

        public static ContextRejectionCause invalidSecurityProfile(String securityProfileIdentifier) {
            return new ContextRejectionCause(String.format(ERR_INVALID_SECURITY_PROFILE, securityProfileIdentifier));
        }

        public String getReason() {
            return reason;
        }

        private void setReason(String reason) {
            this.reason = reason;
        }

    }

}
