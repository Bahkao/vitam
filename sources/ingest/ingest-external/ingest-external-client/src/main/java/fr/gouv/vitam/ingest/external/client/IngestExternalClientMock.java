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
package fr.gouv.vitam.ingest.external.client;

import static fr.gouv.vitam.common.GlobalDataRest.X_REQUEST_ID;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.external.client.AbstractMockClient;
import fr.gouv.vitam.common.external.client.ClientMockResultHelper;
import fr.gouv.vitam.common.external.client.IngestCollection;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.ingest.external.api.exception.IngestExternalException;

/**
 * Mock client implementation for IngestExternal
 */
class IngestExternalClientMock extends AbstractMockClient implements IngestExternalClient {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(IngestExternalClientMock.class);
    private static final String FAKE_X_REQUEST_ID = "aedqaaaaacaam7mxaaaamakvhiv4rsiaaa0";
    public static final String MOCK_INGEST_EXTERNAL_RESPONSE_STREAM = "VITAM-Ingest External Client Mock Response";
    final int TENANT_ID = 0;
    public static final String ID = "identifier1";
    protected StatusCode globalStatus;

    @Override
    public RequestResponse<Void> upload(VitamContext vitamContext, InputStream stream,
        String contextId,
        String action)
        throws IngestExternalException {
        if (stream == null) {
            throw new IngestExternalException("stream is null");
        }
        StreamUtils.closeSilently(stream);

        RequestResponseOK r = new RequestResponseOK<>();
        r.setHttpCode(Status.ACCEPTED.getStatusCode());
        r.addHeader(FAKE_X_REQUEST_ID, X_REQUEST_ID);

        return r;
    }

    @Override
    public Response downloadObjectAsync(VitamContext vitamContext, String objectId,
        IngestCollection type)
        throws VitamClientException {
        return ClientMockResultHelper.getObjectStream();
    }
}
