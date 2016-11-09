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
package fr.gouv.vitam.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.stream.XMLStreamException;

import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;

/**
 * File Utility class
 *
 */
public final class FileUtil {
    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(FileUtil.class);

    private FileUtil() {
        // Unused
    }

    /**
     * @param filename
     * @return the content of the file
     * @throws IOException
     */
    public static final String readFile(final String filename) throws IOException {
        final File file = new File(filename);
        return readFile(file);
    }

    /**
     * @param file
     * @return the content of the file
     * @throws IOException
     */
    public static final String readFile(final File file) throws IOException {
        String result = "";

        if (file != null && file.canRead()) {
            try (final FileInputStream inputStream = new FileInputStream(file)) {
                result = readInputStreamLimited(inputStream, Integer.MAX_VALUE);
            } catch (final IOException e) {
                LOGGER.error(e);
                throw e;
            }
        }

        return result;
    }

    /**
     * @param file
     * @param limit the limit in bytes to read
     * @return the content of the file
     * @throws IOException
     */
    public static final String readPartialFile(final File file, int limit)
        throws IOException {
        String result = "";

        if (file != null && file.canRead() && limit > 0) {
            try {
                try (final FileInputStream inputStream = new FileInputStream(file)) {
                    result = readInputStreamLimited(inputStream, limit);
                }
            } catch (final IOException e) {
                LOGGER.error(e);
                throw e;
            }
        }

        return result;
    }

    private static final boolean delereRecursiveInternal(File dir) {
        boolean status = true;
        for (final File file : dir.listFiles()) {
            if (file.isDirectory()) {
                status &= delereRecursiveInternal(file);
            }
            if (!file.delete()) {
                LOGGER.warn("File could not be deleted: " + file);
                status = false;
            }
        }
        return status;
    }

    /**
     * CARE: delete all files and directories from this file or directory, this one included
     *
     * @param file
     * @return True if all files were deleted
     */
    public static final boolean deleteRecursive(File file) {
        if (file == null) {
            return true;
        }
        if (!file.exists()) {
            return true;
        }
        if (!file.isDirectory()) {
            if (!file.delete()) {
                LOGGER.warn("File could not be deleted: " + file);
                return false;
            }
            return true;
        }
        boolean status = delereRecursiveInternal(file);
        if (!file.delete()) {
            LOGGER.warn("File could not be deleted: " + file);
            status = false;
        }
        return status;
    }

    /**
     * @param input to read
     * @return String
     * @throws XMLStreamException
     * @throws IOException
     */
    private static final String readInputStreamLimited(InputStream input, int limit) throws IOException {
        final StringBuilder builder = new StringBuilder();
        try (final InputStreamReader reader = new InputStreamReader(input)) {
            try (final BufferedReader buffered = new BufferedReader(reader)) {
                String line;
                while ((line = buffered.readLine()) != null) {
                    builder.append(line).append('\n');
                    if (builder.length() >= limit) {
                        break;
                    }
                }
            }
        }
        return builder.toString();
    }

    /**
     * @param input to read
     * @return String
     * @throws XMLStreamException
     * @throws IOException
     */
    public static final String readInputStream(InputStream input) throws IOException {
        return readInputStreamLimited(input, Integer.MAX_VALUE);
    }
}
