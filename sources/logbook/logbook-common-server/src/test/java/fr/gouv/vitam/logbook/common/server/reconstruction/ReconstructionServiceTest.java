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
package fr.gouv.vitam.logbook.common.server.reconstruction;

import static fr.gouv.vitam.logbook.common.server.reconstruction.ReconstructionService.LOGBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import fr.gouv.vitam.common.database.api.VitamRepositoryFactory;
import fr.gouv.vitam.common.database.api.VitamRepositoryProvider;
import fr.gouv.vitam.common.database.offset.OffsetRepository;
import org.bson.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import fr.gouv.vitam.common.database.api.impl.VitamElasticsearchRepository;
import fr.gouv.vitam.common.database.api.impl.VitamMongoRepository;
import fr.gouv.vitam.common.exception.DatabaseException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.administration.AccessionRegisterDetailModel;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AccessionRegisterException;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.DatabaseConflictException;
import fr.gouv.vitam.logbook.common.model.reconstruction.ReconstructionRequestItem;
import fr.gouv.vitam.logbook.common.model.reconstruction.ReconstructionResponseItem;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookTransformData;
import fr.gouv.vitam.logbook.common.server.exception.LogbookException;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;

/**
 * ReconstructionService tests.
 */
public class ReconstructionServiceTest {

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    private VitamRepositoryProvider vitamRepositoryProvider;
    private VitamMongoRepository mongoRepository;
    private VitamElasticsearchRepository esRepository;
    private RestoreBackupService restoreBackupService;
    private AdminManagementClientFactory adminManagementClientFactory;
    private AdminManagementClient adminManagementClient;

    private ReconstructionRequestItem requestItem;

    private OffsetRepository offsetRepository;

    @Before
    public void setup() {
        vitamRepositoryProvider = mock(VitamRepositoryFactory.class);
        mongoRepository = mock(VitamMongoRepository.class);
        esRepository = mock(VitamElasticsearchRepository.class);
        when(vitamRepositoryProvider.getVitamMongoRepository(Mockito.any())).thenReturn(mongoRepository);
        when(vitamRepositoryProvider.getVitamESRepository(Mockito.any())).thenReturn(esRepository);

        restoreBackupService = mock(RestoreBackupService.class);
        adminManagementClientFactory = mock(AdminManagementClientFactory.class);
        adminManagementClient = mock(AdminManagementClient.class);
        when(adminManagementClientFactory.getClient()).thenReturn(adminManagementClient);

        requestItem = new ReconstructionRequestItem();
        requestItem.setTenant(10).setLimit(100);

        offsetRepository = mock(OffsetRepository.class);
    }

    @RunWithCustomExecutor
    @Test
    public void should_return_new_offset_when_item_unit_is_ok()
        throws DatabaseException, LogbookException, InvalidParseOperationException,
        AccessionRegisterException, AdminManagementClientServerException, DatabaseConflictException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);

        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList(Arrays.asList(getOfferLog(100), getOfferLog(101))));
        when(restoreBackupService.loadData("default", "100", 100L))
            .thenReturn(getLogbokBackupModel("100", 100L));
        when(restoreBackupService.loadData("default", "101", 101L))
            .thenReturn(getLogbokBackupModel("101", 101L));
        when(adminManagementClient.createorUpdateAccessionRegister(Mockito.any()))
            .thenReturn(
                new RequestResponseOK<AccessionRegisterDetailModel>().setHttpCode(Status.CREATED.getStatusCode()));

        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 101L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.OK);
    }


    @RunWithCustomExecutor
    @Test
    public void should_return_request_offset_when_item_limit_zero() throws DatabaseException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);

        requestItem.setLimit(0);
        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList());

        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 100L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.OK);
    }

    @RunWithCustomExecutor
    @Test
    public void should_throw_IllegalArgumentException_when_item_is_negative() {
        // given
        requestItem.setLimit(-5);
        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when + then
        assertThatCode(() -> reconstructionService.reconstruct(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @RunWithCustomExecutor
    @Test
    public void should_throw_IllegalArgumentException_when_item_is_null() {
        // given
        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when + then
        assertThatCode(() -> reconstructionService.reconstruct(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @RunWithCustomExecutor
    @Test
    public void should_throw_IllegalArgumentException_when_item_tenant_is_null() {
        // given
        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when + then
        assertThatCode(() -> reconstructionService.reconstruct(requestItem.setTenant(null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @RunWithCustomExecutor
    @Test
    public void should_return_request_offset_when_mongo_exception()
        throws DatabaseException, LogbookException, InvalidParseOperationException,
        AccessionRegisterException, AdminManagementClientServerException, DatabaseConflictException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);

        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList(Arrays.asList(getOfferLog(100), getOfferLog(101))));
        when(restoreBackupService.loadData("default", "100", 100L))
            .thenReturn(getLogbokBackupModel("100", 100L));
        when(restoreBackupService.loadData("default", "101", 101L))
            .thenReturn(getLogbokBackupModel("101", 101L));
        Mockito.doThrow(new DatabaseException("mongo error")).when(mongoRepository).save(Mockito.any(List.class));
        when(adminManagementClient.createorUpdateAccessionRegister(Mockito.any())).thenReturn(
            new RequestResponseOK<AccessionRegisterDetailModel>().setHttpCode(Status.CREATED.getStatusCode()));


        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 101L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.OK);
    }

    @RunWithCustomExecutor
    @Test
    public void should_return_request_offset_when_es_exception()
        throws DatabaseException, LogbookException, InvalidParseOperationException,
        AccessionRegisterException, AdminManagementClientServerException, DatabaseConflictException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);
        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList(Arrays.asList(getOfferLog(100), getOfferLog(101))));
        when(restoreBackupService.loadData("default", "100", 100L))
            .thenReturn(getLogbokBackupModel("100", 100L));
        when(restoreBackupService.loadData("default", "101", 101L))
            .thenReturn(getLogbokBackupModel("101", 101L));
        Mockito.doThrow(new DatabaseException("mongo error")).when(esRepository).save(Mockito.any(List.class));
        when(adminManagementClient.createorUpdateAccessionRegister(Mockito.any()))
            .thenReturn(new RequestResponseOK<AccessionRegisterDetailModel>());

        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 100L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.KO);
    }


    @RunWithCustomExecutor
    @Test
    public void should_return_request_offset_when_logbook_null()
        throws DatabaseException, LogbookException, InvalidParseOperationException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);
        LogbookBackupModel logbookBackupModel100 = getLogbokBackupModel("100", 100L);
        logbookBackupModel100.setLogbookOperation(null);
        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList(Arrays.asList(getOfferLog(100), getOfferLog(101))));
        when(restoreBackupService.loadData("default", "100", 100L))
            .thenReturn(logbookBackupModel100);
        when(restoreBackupService.loadData("default", "101", 101L))
            .thenReturn(getLogbokBackupModel("101", 101L));

        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 100L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.KO);
    }

    @RunWithCustomExecutor
    @Test
    public void should_return_request_offset_when_loading_data_return_null()
        throws DatabaseException, InvalidParseOperationException {
        // given
        when(offsetRepository.findOffsetBy(10, LOGBOOK)).thenReturn(100L);

        when(restoreBackupService.getListing("default", 100L,
            requestItem.getLimit())).thenReturn(Arrays.asList(Arrays.asList(getOfferLog(100), getOfferLog(101))));
        when(restoreBackupService.loadData("default", "100", 100L))
            .thenReturn(getLogbokBackupModel("100", 100L));
        when(restoreBackupService.loadData("default", "101", 101L)).thenReturn(null);

        ReconstructionService reconstructionService =
            new ReconstructionService(vitamRepositoryProvider, restoreBackupService, adminManagementClientFactory,
                new LogbookTransformData(), offsetRepository);
        // when
        ReconstructionResponseItem realResponseItem = reconstructionService.reconstruct(requestItem);
        // then
        assertThat(realResponseItem).isNotNull();
        verify(offsetRepository).createOrUpdateOffset(10, LOGBOOK, 100L);
        assertThat(realResponseItem.getTenant()).isEqualTo(10);
        assertThat(realResponseItem.getStatus()).isEqualTo(StatusCode.KO);
    }

    private LogbookBackupModel getLogbokBackupModel(String id, Long offset) throws InvalidParseOperationException {
        LogbookBackupModel model = new LogbookBackupModel();
        model.setLogbookOperation(new Document("_id", id));
        model.getAccessionRegisters()
            .add(JsonHandler
                .toJsonNode(new AccessionRegisterDetailModel()
                    .setIdentifier("Identifier")
                    .setOperationGroup("OP_GROUP").setId("aehaaaaaaagvm7jmaalysalbwplb56aaaaaq")
                    .setOriginatingAgency("OriginatingAgency").addOperationsId(id)));
        model.setOffset(offset);
        return model;
    }

    private OfferLog getOfferLog(long sequence) {
        OfferLog offerLog = new OfferLog("container", "" + sequence, "write");
        offerLog.setSequence(sequence);
        return offerLog;
    }

}
