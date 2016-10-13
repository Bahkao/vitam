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
package fr.gouv.vitam.common.client;

import fr.gouv.vitam.common.server.application.configuration.ClientConfigurationImpl;

/**
 * Configuration for client that may use SSL
 */
public class SSLClientConfiguration extends ClientConfigurationImpl {
    private boolean useSSL = false;
    private String serverContextPath;

    /**
     * Empty constructor used by YAMLFactory to instantiate the object
     */
    public SSLClientConfiguration() {
        // Emtpy constructor
    }

    /**
     * Construct a configuration with all parameters at once
     * 
     * @param serverHost the server hostname
     * @param serverPort the server port
     * @param useSSL whether to use https or not
     * @param serverContextPath context
     */
    public SSLClientConfiguration(String serverHost, int serverPort, boolean useSSL, String serverContextPath) {
        super(serverHost, serverPort);
        this.useSSL = useSSL;
        this.serverContextPath = serverContextPath;
    }

    /**
     * Get the useSSL property
     * 
     * @return the value of the useSSL property
     */
    public boolean getUseSSL() {
        return useSSL;
    }

    /**
     * Set the useSSL property
     * 
     * @param useSSL the new value
     */
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

    /**
     * Get the serverContextPath property
     * 
     * @return the value of the serverContextPath property
     */
    public String getServerContextPath() {
        return serverContextPath;
    }

    /**
     * Set the serverContextPath property
     * 
     * @param serverContextPath the new value
     */
    public void setServerContextPath(String serverContextPath) {
        this.serverContextPath = serverContextPath;
    }
}
