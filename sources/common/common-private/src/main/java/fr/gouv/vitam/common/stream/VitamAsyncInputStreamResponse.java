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
package fr.gouv.vitam.common.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Link.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import fr.gouv.vitam.common.client.DefaultClient;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

/**
 * This class implements a fake InputStream from a Response such that the response will be closed once the stream is
 * closed itself. It should replace all the AsyncInputStreamHelper<br>
 * <br>
 * Example of usages:<br>
 * <br>
 * When using specific headers:<br><br>
 * <code>
 *          Map<String, String> headers = new HashMap<>();<br>
 *          headers.put(HttpHeaders.CONTENT_TYPE, response.getMediaType().toString());<br>
 *          headers.put(HttpHeaders.CONTENT_DISPOSITION, response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION));<br>
 *          headers.put(GlobalDataRest.X_QUALIFIER, xQualifier);<br>
 *          headers.put(GlobalDataRest.X_VERSION, xVersion);<br>
 *          return new VitamAsyncInputStreamResponse(response, Status.OK, headers);<br>
 * </code>
 * <br>
 * When using standard headers:<br><br>
 * <code>
 *          return new VitamAsyncInputStreamResponse(response, Status.OK, MediaType.APPLICATION_OCTET_STREAM_TYPE);<br>
 * </code>
 * <br>
 * When using the native response as source:<br><br>
 * <code>
 *          return new VitamAsyncInputStreamResponse(response);<br>
 * </code>
 */
public class VitamAsyncInputStreamResponse extends Response {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(VitamAsyncInputStreamResponse.class);
    private final Response response;
    private final InputStream inputStream;
    private final Status status;
    private final MediaType mediaType;

    /**
     * @param response
     */
    public VitamAsyncInputStreamResponse(Response response) {
        inputStream = new VitamAsyncInputStream(response);
        status = (Status) response.getStatusInfo();
        MediaType rmediaType = response.getMediaType();
        if (rmediaType == null) {
            this.mediaType = MediaType.APPLICATION_OCTET_STREAM_TYPE;
        } else {
            this.mediaType = rmediaType;
        }
        this.response = Response.status(status).type(mediaType).entity(inputStream).build();
    }

    /**
     * @param response
     * @param status
     * @param mediaType
     */
    public VitamAsyncInputStreamResponse(Response response, Status status, MediaType mediaType) {
        inputStream = new VitamAsyncInputStream(response);
        this.status = status;
        if (mediaType == null) {
            this.mediaType = MediaType.APPLICATION_OCTET_STREAM_TYPE;
        } else {
            this.mediaType = mediaType;
        }
        this.response = Response.status(status).type(mediaType).entity(inputStream).build();
    }

    /**
     * @param response
     * @param status
     * @param headers
     */
    public VitamAsyncInputStreamResponse(Response response, Status status, Map<String, String> headers) {
        inputStream = new VitamAsyncInputStream(response);
        this.status = status;
        String mediaTypeString = headers.get(HttpHeaders.CONTENT_TYPE);
        if (mediaTypeString == null || mediaTypeString.isEmpty()) {
            mediaTypeString = MediaType.APPLICATION_OCTET_STREAM;
        }
        this.mediaType = MediaType.valueOf(mediaTypeString);
        ResponseBuilder responseBuilder = Response.status(status).type(mediaType);
        for (Entry<String, String> header : headers.entrySet()) {
            responseBuilder.header(header.getKey(), header.getValue());
        }
        this.response = responseBuilder.entity(inputStream).build();
    }

    /**
     * @param inputStream
     * @param status
     * @param headers
     */
    public VitamAsyncInputStreamResponse(InputStream inputStream, Status status, Map<String, String> headers) {
        this.inputStream = inputStream;
        this.status = status;
        String mediaTypeString = headers.get(HttpHeaders.CONTENT_TYPE);
        if (mediaTypeString == null || mediaTypeString.isEmpty()) {
            mediaTypeString = MediaType.APPLICATION_OCTET_STREAM;
        }
        this.mediaType = MediaType.valueOf(mediaTypeString);
        ResponseBuilder responseBuilder = Response.status(status).type(mediaType);
        for (Entry<String, String> header : headers.entrySet()) {
            responseBuilder.header(header.getKey(), header.getValue());
        }
        this.response = responseBuilder.entity(inputStream).build();
    }

    /**
     * Return the default Map of headers from Response
     * @param response
     * @return the default map
     */
    public static final Map<String, String> getDefaultMapFromResponse(Response response) {
        Map<String, String> headers = new HashMap<>();
        MediaType mediaType = response.getMediaType();
        String mediaTypeString = MediaType.APPLICATION_OCTET_STREAM;
        if (mediaType != null) {
            mediaTypeString = mediaType.toString();
        }
        headers.put(HttpHeaders.CONTENT_TYPE, mediaTypeString);
        headers.put(HttpHeaders.CONTENT_DISPOSITION, response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION));
        return headers;
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public StatusType getStatusInfo() {
        return response.getStatusInfo();
    }

    @Override
    public MediaType getMediaType() {
        return response.getMediaType();
    }

    @Override
    public Object getEntity() {
        return inputStream;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(Class<T> entityType) {
        return (T) inputStream;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(GenericType<T> entityType) {
        return (T) inputStream;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
        return (T) inputStream;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
        return (T) inputStream;
    }

    @Override
    public void close() {
        StreamUtils.closeSilently(inputStream);
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() {
        return response.getHeaders();
    }

    @Override
    public boolean hasEntity() {
        return response.hasEntity();
    }

    @Override
    public boolean bufferEntity() {
        return response.bufferEntity();
    }

    @Override
    public Locale getLanguage() {
        return response.getLanguage();
    }

    @Override
    public int getLength() {
        return response.getLength();
    }

    @Override
    public Set<String> getAllowedMethods() {
        return response.getAllowedMethods();
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return response.getCookies();
    }

    @Override
    public EntityTag getEntityTag() {
        return response.getEntityTag();
    }

    @Override
    public Date getDate() {
        return response.getDate();
    }

    @Override
    public Date getLastModified() {
        return response.getLastModified();
    }

    @Override
    public URI getLocation() {
        return response.getLocation();
    }

    @Override
    public Set<Link> getLinks() {
        return response.getLinks();
    }

    @Override
    public boolean hasLink(String relation) {
        return response.hasLink(relation);
    }

    @Override
    public Link getLink(String relation) {
        return response.getLink(relation);
    }

    @Override
    public Builder getLinkBuilder(String relation) {
        return response.getLinkBuilder(relation);
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return response.getMetadata();
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        return response.getStringHeaders();
    }

    @Override
    public String getHeaderString(String name) {
        return response.getHeaderString(name);
    }

    private static class VitamAsyncInputStream extends InputStream {
        private final Response response;
        private InputStream inputStream;

        /**
         * Constructor using one response containing itself a stream
         *
         * @param response the original received response to forward
         */
        public VitamAsyncInputStream(Response response) {
            this.response = response;
            try {
                this.inputStream = response.readEntity(InputStream.class);
            } catch (IllegalStateException e) {
                // Not an InputStream
                Object object = response.getEntity();
                if (object == null) {
                    this.inputStream = new ByteArrayInputStream(new byte[0]);
                } else {
                    try {
                        this.inputStream = JsonHandler.writeToInpustream(response.getEntity());
                    } catch (InvalidParseOperationException e1) {
                        LOGGER.error(e.getMessage(), e1);
                        throw e;
                    }
                }
            }
        }

        @Override
        public int available() throws IOException {
            return inputStream.available();
        }

        @Override
        public void close() {
            DefaultClient.staticConsumeAnyEntityAndClose(response);
            StreamUtils.closeSilently(inputStream);
        }

        @Override
        public synchronized void mark(int readlimit) {
            inputStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return inputStream.markSupported();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inputStream.read(b);
        }

        @Override
        public synchronized void reset() throws IOException {
            inputStream.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return inputStream.skip(n);
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

    }
}
