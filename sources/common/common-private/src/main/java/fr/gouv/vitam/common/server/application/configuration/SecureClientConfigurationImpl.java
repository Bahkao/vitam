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
package fr.gouv.vitam.common.server.application.configuration;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.model.SSLConfiguration;

/**
 * SecureClientConfiguration interface's implementation
 */
public class SecureClientConfigurationImpl implements SecureClientConfiguration {

    private static final String CONFIGURATION_PARAMETERS = "SecureClientConfiguration parameters";
    private String serverHost;
    private int serverPort;
    private boolean secure = true;
    private SSLConfiguration sslConfiguration = new SSLConfiguration();
    private boolean hostnameVerification = true;


    /**
     * Empty SecureClientConfiguration constructor for YAMLFactory
     */
    public SecureClientConfigurationImpl() {
        // nothing
    }

    /**
     * ClientConfiguration constructor
     *
     * @param serverHost server IP address
     * @param serverPort server port
     * @throws IllegalArgumentException if serverHost is null or empty or serverPort <= 0
     */
    public SecureClientConfigurationImpl(String serverHost, int serverPort) {
        this(serverHost, serverPort, true, new SSLConfiguration(),true);
   
    }
    
    /**
     * ClientConfiguration constructor
     *
     * @param serverHost server IP address
     * @param serverPort server port
     * @param secure  HTTP/HTTPS
     * @throws IllegalArgumentException if configuration param is null or empty or serverPort <= 0
     */
    public SecureClientConfigurationImpl(String serverHost, int serverPort, boolean secure) {
        this(serverHost, serverPort, secure, new SSLConfiguration(), true);
   
    }
    
    
    /**
     * ClientConfiguration constructor
     *
     * @param serverHost server IP address
     * @param serverPort server port
     * @param secure  HTTP/HTTPS
     * @param SSLConfiguration
     * @throws IllegalArgumentException if configuration param is null or empty or serverPort <= 0
     */
    public SecureClientConfigurationImpl(String serverHost, int serverPort, boolean secure, SSLConfiguration sslConfiguration) {
        this(serverHost, serverPort, secure, sslConfiguration, true);
   
    }
    
    
    /**
     * ClientConfiguration constructor
     *
     * @param serverHost server IP address
     * @param serverPort server port
     * @throws IllegalArgumentException if any configuration param is null or empty or serverPort <= 0
     */
    public SecureClientConfigurationImpl(String serverHost, int serverPort, boolean secure, SSLConfiguration sslConfiguration, boolean hostnameVerification) {
        ParametersChecker.checkParameter(CONFIGURATION_PARAMETERS, serverHost, secure, 
            sslConfiguration, hostnameVerification);
        if (serverPort <= 0) {
            throw new IllegalArgumentException("Port most be positive");
        }
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.secure=secure;
        this.hostnameVerification=hostnameVerification;
        this.sslConfiguration=sslConfiguration;
    }

    @Override
    public String getServerHost() {
        return serverHost;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }
    
    @Override
    public boolean isSecure() {
        return secure;
    }


    @Override
    public boolean isHostnameVerification() {
        return hostnameVerification;
    }
    
    @Override
    public SSLConfiguration getSslConfiguration() {
        return sslConfiguration;
    }

    /**
     * @param serverHost the server host
     * @return this
     * @throws IllegalArgumentException if serverHost is null or empty
     */
    public SecureClientConfigurationImpl setServerHost(String serverHost) {
        ParametersChecker.checkParameter(CONFIGURATION_PARAMETERS, serverHost);
        this.serverHost = serverHost;
        return this;
    }

    /**
     * @param serverPort the server port
     * @return this
     * @throws IllegalArgumentException if serverPort <= 0
     */
    public SecureClientConfigurationImpl setServerPort(int serverPort) {
        if (serverPort <= 0) {
            throw new IllegalArgumentException("Port most be positive");
        }
        this.serverPort = serverPort;
        return this;
    }
    
    /**
     * @param hostnameVerification the hostnameVerification to set
     * @return this
     * @throws IllegalArgumentException if hostnameVerification is null or empty
     */
    public SecureClientConfigurationImpl setHostnameVerification(boolean hostnameVerification) {
        ParametersChecker.checkParameter(CONFIGURATION_PARAMETERS, hostnameVerification);
        this.hostnameVerification = hostnameVerification;
        return this;
    }
    

    /**
     * @param secure the secure to set
     *
     * @return this
     */
    public SecureClientConfigurationImpl setSecure(boolean secure) {
        ParametersChecker.checkParameter(CONFIGURATION_PARAMETERS, secure);
        this.secure = secure;
        return this;
    }

    /**
     * @param sslConfiguration the sslConfiguration to set
     *
     * @return this
     */
    public SecureClientConfigurationImpl setSslConfiguration(SSLConfiguration sslConfiguration) {
        ParametersChecker.checkParameter(CONFIGURATION_PARAMETERS, sslConfiguration);
        this.sslConfiguration = sslConfiguration;
        return this;
    }
    
    
    
}
