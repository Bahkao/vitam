package fr.gouv.vitam.functional.administration.rest;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.database.server.DbRequestResult;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamClientException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.counter.VitamCounterService;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminImpl;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * EvidenceResource Test
 */
public class EvidenceResourceTest {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(EvidenceResourceTest.class);

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());
    @Mock ProcessingManagementClientFactory processingManagementClientFactory;
    @Mock ProcessingManagementClient processingManagementClient;
    @Mock WorkspaceClientFactory workspaceClientFactory;
    @Mock WorkspaceClient workspaceClient;
    @Mock LogbookOperationsClientFactory logbookOperationsClientFactory;
    @Mock LogbookOperationsClient logbookOperationsClient;
    @Mock MongoDbAccessAdminImpl mongoDbAccess;
    @Mock DbRequestResult dbRequestResult;
    @Mock VitamCounterService vitamCounterService;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private static final int TENANT_ID = 0;
    private EvidenceResource evidenceResource;

    @Before
    public void setUp() throws InvalidParseOperationException, ReferentialException {
        when(processingManagementClientFactory.getClient()).thenReturn(processingManagementClient);
        when(workspaceClientFactory.getClient()).thenReturn(workspaceClient);
        when(logbookOperationsClientFactory.getClient()).thenReturn(logbookOperationsClient);

        AccessContractModel accessContract = new AccessContractModel().setEveryOriginatingAgency(true)
                .setEveryDataObjectVersion(true);
        accessContract.setIdentifier("fakeContract");
        when(dbRequestResult.getDocuments(any(), any())).thenReturn(Arrays.asList(accessContract));
        when(mongoDbAccess.findDocuments(any(), any())).thenReturn(dbRequestResult);

        VitamThreadUtils.getVitamSession().setContractId("fakeContract");
        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        evidenceResource =
            new EvidenceResource(processingManagementClientFactory, logbookOperationsClientFactory,
                workspaceClientFactory, mongoDbAccess, vitamCounterService);
        GUID guid = GUIDFactory.newEventGUID(TENANT_ID);
        VitamThreadUtils.getVitamSession().setRequestId(guid);
        VitamThreadUtils.getVitamSession().setContract(new AccessContractModel().setEveryOriginatingAgency(true));
    }

    @Test
    @RunWithCustomExecutor
    public void given_empty_query_when_audit_then_return_forbidden_request() throws Exception {

        Response audit = evidenceResource.audit(new Select().getFinalSelect());
        assertThat(audit.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void given_good_query_then_return_ok_status() throws Exception {

        // given
        SelectMultiQuery selectMultiQuery = new SelectMultiQuery();

        selectMultiQuery.setQuery(QueryHelper.eq("title", "test"));

        when(processingManagementClient
            .executeOperationProcess(anyString(), eq("EVIDENCE_AUDIT"), anyString(), anyString()))
            .thenReturn(new RequestResponseOK<JsonNode>(new Select().getFinalSelect()).setHttpCode(200));
        Response audit = evidenceResource.audit(selectMultiQuery.getFinalSelect());

        //then
        assertThat(audit.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }

    @Test
    @RunWithCustomExecutor
    public void fail_when_a_related_server_is_unavailable() throws Exception {
        SelectMultiQuery selectMultiQuery = new SelectMultiQuery();

        selectMultiQuery.setQuery(QueryHelper.eq("title", "test"));


        willThrow(VitamClientException.class).given(workspaceClient).putObject(anyString(), any(), any());
        Response audit = evidenceResource.audit(selectMultiQuery.getFinalSelect());
        assertThat(audit.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());


        willThrow(LogbookClientAlreadyExistsException.class).given(logbookOperationsClient).create(any());
        audit = evidenceResource.audit(selectMultiQuery.getFinalSelect());
        assertThat(audit.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());


    }

}
