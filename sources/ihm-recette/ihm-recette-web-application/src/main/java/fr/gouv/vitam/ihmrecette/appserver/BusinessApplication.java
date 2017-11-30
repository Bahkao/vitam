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
package fr.gouv.vitam.ihmrecette.appserver;

import static fr.gouv.vitam.common.serverv2.application.ApplicationParameter.CONFIGURATION_FILE_APPLICATION;
import static java.lang.String.format;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import org.apache.shiro.authz.annotation.RequiresPermissions;

import com.google.common.base.Throwables;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.serverv2.application.CommonBusinessApplication;
import fr.gouv.vitam.ihmdemo.common.utils.PermissionReader;
import fr.gouv.vitam.ihmrecette.appserver.applicativetest.ApplicativeTestResource;
import fr.gouv.vitam.ihmrecette.appserver.applicativetest.ApplicativeTestService;
import fr.gouv.vitam.ihmrecette.appserver.performance.PerformanceResource;
import fr.gouv.vitam.ihmrecette.appserver.performance.PerformanceService;

public class BusinessApplication extends Application {

    private final CommonBusinessApplication commonBusinessApplication;

    private Set<Object> singletons;

    /**
     * BusinessApplication Constructor 
     * 
     * @param servletConfig
     */
    public BusinessApplication(@Context ServletConfig servletConfig) {
        String configurationFile = servletConfig.getInitParameter(CONFIGURATION_FILE_APPLICATION);

        try (final InputStream yamlIS = PropertiesUtils.getConfigAsStream(configurationFile)) {
            final WebApplicationConfig configuration =
                PropertiesUtils.readYaml(yamlIS, WebApplicationConfig.class);
            
            commonBusinessApplication = new CommonBusinessApplication();
            singletons = new HashSet<>();
            singletons.addAll(commonBusinessApplication.getResources());
            
            final WebApplicationResourceDelete deleteResource = new WebApplicationResourceDelete(configuration);
            final WebApplicationResource resource = new WebApplicationResource(configuration.getTenants(), configuration.getSecureMode());
            singletons.add(deleteResource);
            singletons.add(resource);
            
            Path sipDirectory = Paths.get(configuration.getSipDirectory());
            Path reportDirectory = Paths.get(configuration.getPerformanceReportDirectory());

            if (!Files.exists(sipDirectory)) {
                Exception sipNotFound =
                    new FileNotFoundException(String.format("directory %s does not exist", sipDirectory));
                throw Throwables.propagate(sipNotFound);
            }

            if (!Files.exists(reportDirectory)) {
                Exception reportNotFound =
                    new FileNotFoundException(format("directory %s does not exist", reportDirectory));
                throw Throwables.propagate(reportNotFound);
            }

            PerformanceService performanceService = new PerformanceService(sipDirectory, reportDirectory);
            singletons.add(new PerformanceResource(performanceService));
            
            String testSystemSipDirectory = configuration.getTestSystemSipDirectory();
            String testSystemReportDirectory = configuration.getTestSystemReportDirectory();
            ApplicativeTestService applicativeTestService =
                new ApplicativeTestService(Paths.get(testSystemReportDirectory));

            singletons.add(new ApplicativeTestResource(applicativeTestService,
                testSystemSipDirectory));
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Set<Class<?>> getClasses() {
        return commonBusinessApplication.getClasses();
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}