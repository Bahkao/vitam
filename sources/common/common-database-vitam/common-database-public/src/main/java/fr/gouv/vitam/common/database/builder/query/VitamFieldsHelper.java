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
/**
 *
 */
package fr.gouv.vitam.common.database.builder.query;

import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS.CACHE;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS.NOCACHE;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS.OBJECTGROUPS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS.UNITS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.ALL;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.ALLUNITUPS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.DUA;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.FORMAT;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.ID;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.MANAGEMENT;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.MAX;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.MIN;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.NBOBJECTS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.NBUNITS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.OBJECT;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.OPERATIONS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.ORIGINATING_AGENCIES;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.ORIGINATING_AGENCY;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.QUALIFIERS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.SIZE;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.TENANT;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.TYPE;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.UNITTYPE;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.UNITUPS;
import static fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS.VERSION;


/**
 * Vitam Field Helper to facilitate the usage of field names
 *
 */
public class VitamFieldsHelper {

    private VitamFieldsHelper() {
        // Empty
    }

    /**
     * @return #id
     */
    public static final String id() {
        return ID.exactToken();
    }

    /**
     * @return #nbunits
     */
    public static final String nbunits() {
        return NBUNITS.exactToken();
    }

    /**
     * @return #nbobjects
     */
    public static final String nbobjects() {
        return NBOBJECTS.exactToken();
    }

    /**
     * @return #all
     */
    public static final String all() {
        return ALL.exactToken();
    }

    /**
     * @return #size
     */
    // FIXME P2 not valid
    static final String size() {
        return SIZE.exactToken();
    }

    /**
     * @return #format
     */
    // FIXME P2 not valid
    static final String format() {
        return FORMAT.exactToken();
    }

    /**
     * @return #type
     */
    public static final String type() {
        return TYPE.exactToken();
    }

    /**
     * @return #tenant
     */
    public static final String tenant() {
        return TENANT.exactToken();
    }

    /**
     * @return #dua
     */
    public static final String dua() {
        return DUA.exactToken();
    }

    /**
     * @return #units
     */
    public static final String units() {
        return UNITS.exactToken();
    }

    /**
     * @return #objectgroups
     */
    public static final String objectgroups() {
        return OBJECTGROUPS.exactToken();
    }

    /**
     * @return #cache
     */
    public static final String cache() {
        return CACHE.exactToken();
    }

    /**
     * @return #nocache
     */
    public static final String nocache() {
        return NOCACHE.exactToken();
    }

    /**
     * @return #qualifiers
     */
    public static final String qualifiers() {
        return QUALIFIERS.exactToken();
    }

    /**
     * @return #object
     */
    public static final String object() {
        return OBJECT.exactToken();
    }

    /**
     * @return #unitups
     */
    public static final String unitups() {
        return UNITUPS.exactToken();
    }

    /**
     * @return #allunitups
     */
    public static final String allunitups() {
        return ALLUNITUPS.exactToken();
    }

    /**
     * @return #min
     */
    public static final String min() {
        return MIN.exactToken();
    }

    /**
     * @return #max
     */
    public static final String max() {
        return MAX.exactToken();
    }

    /**
     *
     * @return #management
     */
    public static final String management() {
        return MANAGEMENT.exactToken();
    }

    /**
     * @return #operations
     */
    public static final String operations() {
        return OPERATIONS.exactToken();
    }

    /**
     * @return #unittype
     */
    public static final String unitType() {
        return UNITTYPE.exactToken();
    }
    /**
     * @return #originating_agency
     */
    public static final String originatingAgency() {
        return ORIGINATING_AGENCY.exactToken();
    }
    /**
     * @return #originating_agencies
     */
    public static final String originatingAgencies() {
        return ORIGINATING_AGENCIES.exactToken();
    }

    /**
     * @return #version
     */
    public static final String version() {
        return VERSION.exactToken();
    }
}
