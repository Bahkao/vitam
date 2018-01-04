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
 *  In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */

package fr.gouv.vitam.common.storage.swift;

import java.util.LinkedHashSet;
import java.util.List;

import org.openstack4j.model.storage.object.SwiftObject;

import fr.gouv.vitam.common.storage.cas.container.api.VitamPageSet;
import fr.gouv.vitam.common.storage.cas.container.api.VitamStorageMetadata;
import fr.gouv.vitam.common.storage.filesystem.v2.metadata.VitamStorageMetadataImpl;

/**
 * This class wrap Openstack swift list to vitam pageSet
 */
public class OpenstackPageSetImpl extends LinkedHashSet<VitamStorageMetadata>
    implements VitamPageSet<VitamStorageMetadata> {

    protected String marker;

    public static VitamPageSet<VitamStorageMetadata> wrap(List<? extends SwiftObject> list) {
        OpenstackPageSetImpl openstackPageSet = new OpenstackPageSetImpl();
        if (list == null) {
            return null;
        }
        list.forEach(o -> openstackPageSet.add(new VitamStorageMetadataImpl(null, null, o.getName(), null, null,
            null, o.getETag(), null, o.getLastModified(), o.getSizeInBytes())));
        openstackPageSet.marker = list.get(list.size() - 1).getName();
        return openstackPageSet;
    }

    @Override
    public String getNextMarker() {
        return this.marker;
    }
}
