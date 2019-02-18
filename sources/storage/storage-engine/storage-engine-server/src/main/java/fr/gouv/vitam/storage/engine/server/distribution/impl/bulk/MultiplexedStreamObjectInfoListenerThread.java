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
package fr.gouv.vitam.storage.engine.server.distribution.impl.bulk;

import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.stream.ExactSizeInputStream;
import fr.gouv.vitam.common.stream.MultiplexedStreamReader;
import org.apache.commons.collections4.ListUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class MultiplexedStreamObjectInfoListenerThread implements Callable<List<ObjectInfo>> {

    private final InputStream inputStream;
    private final DigestType digestType;
    private final List<String> objectIds;

    public MultiplexedStreamObjectInfoListenerThread(InputStream inputStream, DigestType digestType,
        List<String> objectIds) {
        this.inputStream = inputStream;
        this.digestType = digestType;
        this.objectIds = objectIds;
    }

    @Override
    public List<ObjectInfo> call() throws IOException, InvalidParseOperationException {

        try (MultiplexedStreamReader multiplexedStreamReader = new MultiplexedStreamReader(inputStream)) {

            Optional<ExactSizeInputStream> headerEntry = multiplexedStreamReader.readNextEntry();
            if (!headerEntry.isPresent()) {
                throw new IllegalStateException("Header entry not found");
            }
            List<String> streamObjectIds = JsonHandler.getFromInputStream(headerEntry.get(), List.class);
            if (!ListUtils.isEqualList(objectIds, streamObjectIds)) {
                throw new IllegalStateException("Invalid object ids");
            }

            List<ObjectInfo> objectInfo = new ArrayList<>();
            for (String objectId : objectIds) {
                Optional<ExactSizeInputStream> entryInputStream = multiplexedStreamReader.readNextEntry();
                if (!entryInputStream.isPresent()) {
                    throw new IllegalStateException("No entry not found for object id " + objectId);
                }

                Digest digest = new Digest(digestType);
                digest.update(entryInputStream.get());
                objectInfo.add(new ObjectInfo(objectId, digest.digestHex(), entryInputStream.get().getSize()));
            }

            if (multiplexedStreamReader.readNextEntry().isPresent()) {
                throw new IllegalStateException("No more entries expected");
            }

            return objectInfo;
        }
    }
}
