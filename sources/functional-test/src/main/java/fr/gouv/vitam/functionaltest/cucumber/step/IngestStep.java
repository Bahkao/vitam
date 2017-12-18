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
package fr.gouv.vitam.functionaltest.cucumber.step;

import static fr.gouv.vitam.common.GlobalDataRest.X_REQUEST_ID;
import static fr.gouv.vitam.logbook.common.parameters.Contexts.DEFAULT_WORKFLOW;
import static fr.gouv.vitam.logbook.common.parameters.Contexts.FILING_SCHEME;
import static fr.gouv.vitam.logbook.common.parameters.Contexts.HOLDING_SCHEME;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import fr.gouv.vitam.access.external.client.VitamPoolingClient;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.client.VitamContext;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.ingest.external.api.exception.IngestExternalException;
import fr.gouv.vitam.tools.SipTool;

public class IngestStep {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(IngestStep.class);

    private Path sip;

    private World world;
    private static boolean deleteSip = false;
    private static boolean attachMode = false;

    public IngestStep(World world) {
        this.world = world;
    }

    /**
     * define a sip
     *
     * @param fileName name of a sip
     */
    @Given("^un fichier SIP nommé (.*)$")
    public void a_sip_named(String fileName) {
        this.sip = Paths.get(world.getBaseDirectory(), fileName);
    }

    /**
     * call vitam to upload the SIP
     *
     * @throws IOException
     * @throws IngestExternalException
     */
    @When("^je télécharge le SIP")
    public void upload_this_sip() throws IOException, VitamException, IOException {
        try (InputStream inputStream = Files.newInputStream(sip, StandardOpenOption.READ)) {
            RequestResponse response = world.getIngestClient()
                .ingest(
                    new VitamContext(world.getTenantId()).setApplicationSessionId(world.getApplicationSessionId()),
                    inputStream, DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.name());
            final String operationId = response.getHeaderString(GlobalDataRest.X_REQUEST_ID);
            world.setOperationId(operationId);
            final VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(world.getAdminClient());
            boolean process_timeout = vitamPoolingClient
                .wait(world.getTenantId(), operationId, ProcessState.COMPLETED, 1800, 1_000L, TimeUnit.MILLISECONDS);
            if (!process_timeout) {
                fail("Sip processing not finished. Timeout exeedeed.");
            }
            assertThat(operationId).as(format("%s not found for request", X_REQUEST_ID)).isNotNull();
        }
    }

    /**
     * call vitam to upload the plan
     *
     * @throws IOException
     * @throws IngestExternalException
     */
    @When("^je télécharge le plan")
    public void upload_this_plan() throws IOException, VitamException {
        try (InputStream inputStream = Files.newInputStream(sip, StandardOpenOption.READ)) {

            RequestResponse<Void> response = world.getIngestClient()
                .ingest(
                    new VitamContext(world.getTenantId()).setApplicationSessionId(world.getApplicationSessionId()),
                    inputStream, FILING_SCHEME.name(), ProcessAction.RESUME.name());

            final String operationId = response.getHeaderString(GlobalDataRest.X_REQUEST_ID);

            world.setOperationId(operationId);
            final VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(world.getAdminClient());
            boolean process_timeout = vitamPoolingClient
                .wait(world.getTenantId(), operationId, ProcessState.COMPLETED, 200, 1_000L, TimeUnit.MILLISECONDS);
            if (!process_timeout) {
                fail("Sip processing not finished. Timeout exeedeed.");
            }
            assertThat(operationId).as(format("%s not found for request", X_REQUEST_ID)).isNotNull();
        }
        if (attachMode) {
            deleteSip = true;
        }
    }

    /**
     * call vitam to upload the tree
     *
     * @throws IOException
     * @throws IngestExternalException
     */
    @When("^je télécharge l'arbre")
    public void upload_this_tree() throws IOException, VitamException {
        try (InputStream inputStream = Files.newInputStream(sip, StandardOpenOption.READ)) {
            RequestResponse response = world.getIngestClient()
                .ingest(
                    new VitamContext(world.getTenantId()).setApplicationSessionId(world.getApplicationSessionId()),
                    inputStream, HOLDING_SCHEME.name(), ProcessAction.RESUME.name());

            final String operationId = response.getHeaderString(GlobalDataRest.X_REQUEST_ID);

            world.setOperationId(operationId);
            final VitamPoolingClient vitamPoolingClient = new VitamPoolingClient(world.getAdminClient());
            boolean process_timeout = vitamPoolingClient
                .wait(world.getTenantId(), operationId, ProcessState.COMPLETED, 100, 1_000L, TimeUnit.MILLISECONDS);
            if (!process_timeout) {
                fail("Sip processing not finished. Timeout exeedeed.");
            }
            assertThat(operationId).as(format("%s not found for request", X_REQUEST_ID)).isNotNull();
        }
        if (attachMode) {
            deleteSip = true;
        }
    }


    @When("je construit le sip de rattachement avec le template")
    public void build_the_attachenment() throws IOException {
        this.sip = SipTool.copyAndModifyManifestInZip(sip, SipTool.REPLACEMENT_STRING, world.getUnitId());
        attachMode = true;
    }

    /**
     * Execute an ingest OK and saves the operationId in test set map.
     * 
     * @param fileName name of a sip
     */
    @Given("^les données du jeu de test du SIP nommé (.*)")
    public void use_test_set_from_sip(String fileName) {
        if (!StringUtils.isNotBlank(World.getOperationId(fileName))) {
            try {
                a_sip_named(fileName);
                upload_this_sip();
                world.getLogbookService().checkFinalStatusLogbook(world.getAccessClient(), world.getTenantId(),
                    world.getContractId(), world.getApplicationSessionId(), world.getOperationId(), "OK");
                World.setOperationId(fileName, world.getOperationId());
            } catch (VitamException | IOException e) {
                fail("Could not load test set : ingest failure.", e);
            }
        } else {
            this.world.setOperationId(World.getOperationId(fileName));
        }
    }

    @After
    public void afterScenario() throws IOException {

        if (this.sip != null && deleteSip) {
            Files.delete(this.sip);
            deleteSip = false;
            attachMode = false;
        }
    }
}
