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
package fr.gouv.vitam.storage.engine.server.offersynchronization;

import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.storage.engine.common.exception.StorageException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.OfferLog;
import fr.gouv.vitam.storage.engine.common.model.Order;
import fr.gouv.vitam.storage.engine.server.distribution.StorageDistribution;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RestoreOfferBackupService tests
 */
@RunWith(MockitoJUnitRunner.class)
public class RestoreOfferBackupServiceTest {

    /**
     * Vitam Logger.
     */
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(RestoreOfferBackupServiceTest.class);

    private static final String STRATEGY_ID = "default";
    private static final String OFFER_ID = "default";
    private static final Integer TENANT_ID_0 = 0;
    private static final String FILE_NAME = "fileName_";
    private static final String WRITE = "write";
    private static final String UPDATE = "update";
    public static final String OBJECT_NAME_1 = "object_name_1";

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    private StorageDistribution distribution;

    @InjectMocks
    private RestoreOfferBackupService restoreOfferBackupService;

    @RunWithCustomExecutor
    @Test
    public void getLatestOffsetByContainerTest() throws Exception {

        OfferLog offerLogInstance = new OfferLog(DataCategory.UNIT.getFolder(), OBJECT_NAME_1, WRITE);
        RequestResponseOK<OfferLog> offerLogRequestResponseOK = new RequestResponseOK<>();
        offerLogRequestResponseOK.setHttpCode(Response.Status.OK.getStatusCode());
        offerLogRequestResponseOK.addResult(offerLogInstance);

        // given
        when(distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.UNIT, null, 1, Order.DESC))
            .thenReturn(offerLogRequestResponseOK);

        // when
        Optional<OfferLog> offerLog =
            restoreOfferBackupService.getLatestOffsetByContainer(STRATEGY_ID, OFFER_ID, DataCategory.UNIT, null, 1);

        //then
        verify(distribution, times(1))
            .getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.UNIT, null, 1, Order.DESC);

        assertThat(offerLog).isNotEmpty();
        assertThat(offerLog.get().getContainer()).isEqualTo(DataCategory.UNIT.getFolder());
        assertThat(offerLog.get().getFileName()).isEqualTo(OBJECT_NAME_1);
        assertThat(offerLog.get().getAction()).isEqualTo(WRITE);

        // given
        offerLogInstance = new OfferLog(DataCategory.LOGBOOK.getFolder(), OBJECT_NAME_1, UPDATE);
        offerLogRequestResponseOK = new RequestResponseOK<>();
        offerLogRequestResponseOK.setHttpCode(Response.Status.OK.getStatusCode());
        offerLogRequestResponseOK.addResult(offerLogInstance);

        when(distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.LOGBOOK, null, 1, Order.DESC))
            .thenReturn(offerLogRequestResponseOK);

        // when
        offerLog =
            restoreOfferBackupService.getLatestOffsetByContainer(STRATEGY_ID, OFFER_ID, DataCategory.LOGBOOK, null, 1);

        // then
        verify(distribution, times(1))
            .getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.LOGBOOK, null, 1, Order.DESC);

        assertThat(offerLog).isNotEmpty();
        assertThat(offerLog.get().getContainer()).isEqualTo(DataCategory.LOGBOOK.getFolder());
        assertThat(offerLog.get().getFileName()).isEqualTo(OBJECT_NAME_1);
        assertThat(offerLog.get().getAction()).isEqualTo(UPDATE);

    }

    @RunWithCustomExecutor
    @Test
    public void should_get_listing_when_listing_objects_returns_response_ok() throws StorageException {

        // given
        String containerName = String.format("%s_%s", TENANT_ID_0, DataCategory.BACKUP_OPERATION.getFolder());

        when(
            distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.BACKUP_OPERATION, 10L, 2, Order.ASC))
            .thenReturn(getOfferLogsListing(containerName, 10L, 2L));

        // when
        List<List<OfferLog>> offerLogListing =
            restoreOfferBackupService.getListing(STRATEGY_ID, OFFER_ID, DataCategory.BACKUP_OPERATION, 10L, 2, Order.ASC);

        // then
        assertThat(offerLogListing).isNotNull().isNotEmpty();
        assertThat(offerLogListing.size()).isEqualTo(1);
        assertThat(offerLogListing.get(0)).isNotNull().isNotEmpty();
        assertThat(offerLogListing.get(0).size()).isEqualTo(2);
        assertThat(offerLogListing.get(0).get(0).getContainer()).isEqualTo(containerName);
        assertThat(offerLogListing.get(0).get(0).getFileName()).isEqualTo(FILE_NAME + "10");
        assertThat(offerLogListing.get(0).get(1).getFileName()).isEqualTo(FILE_NAME + "11");

        // given
        containerName = String.format("%s_%s", TENANT_ID_0, DataCategory.OBJECTGROUP.getFolder());

        when(
            distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.OBJECTGROUP, 100L, 3, Order.DESC))
            .thenReturn(getOfferLogsListing(containerName, 100L, 3L));

        // when
        offerLogListing =
            restoreOfferBackupService.getListing(STRATEGY_ID, OFFER_ID, DataCategory.OBJECTGROUP, 100L,3,  Order.DESC);

        // then
        assertThat(offerLogListing).isNotNull().isNotEmpty();
        assertThat(offerLogListing.size()).isEqualTo(1);
        assertThat(offerLogListing.get(0)).isNotNull().isNotEmpty();
        assertThat(offerLogListing.get(0).size()).isEqualTo(3);
        assertThat(offerLogListing.get(0).get(0).getContainer()).isEqualTo(containerName);
        assertThat(offerLogListing.get(0).get(0).getFileName()).isEqualTo(FILE_NAME + "100");
        assertThat(offerLogListing.get(0).get(1).getFileName()).isEqualTo(FILE_NAME + "101");
        assertThat(offerLogListing.get(0).get(2).getFileName()).isEqualTo(FILE_NAME + "102");
    }

    @RunWithCustomExecutor
    @Test
    public void should_get_empty_listing_when_listing_objects_returns_empty_response_ok() throws StorageException {

        when(
            distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.OBJECT, 10L, 1, Order.ASC))
            .thenReturn(getOfferLogsListing(DataCategory.OBJECT.getFolder(), 10L, -1L));

        // when
        List<List<OfferLog>> offerLogListing =
            restoreOfferBackupService.getListing(STRATEGY_ID, OFFER_ID, DataCategory.OBJECT, 10L, 1,  Order.ASC);

        // then
        assertThat(offerLogListing).isNotNull().isEmpty();
    }

    @RunWithCustomExecutor
    @Test
    public void should_throw_VitamRuntimeException_when_listing_and_storage_throws_StorageServerClientException()
        throws StorageException {

        // given
        when(
            distribution.getOfferLogsByOfferId(STRATEGY_ID, OFFER_ID, DataCategory.OBJECT, 10L, 1, Order.DESC))
            .thenThrow(new StorageException("ERROR: Storage exception has been thrown."));

        assertThatCode(
            () -> restoreOfferBackupService.getListing(STRATEGY_ID, OFFER_ID, DataCategory.OBJECT, 10L,  1, Order.DESC))
            .isInstanceOf(VitamRuntimeException.class);
    }

    /**
     * Prepare listing offerLogs for tests.
     *
     * @param offset
     * @param limit
     * @return
     */
    private RequestResponseOK<OfferLog> getOfferLogsListing(String container, long offset, long limit) {
        RequestResponseOK<OfferLog> listing = new RequestResponseOK<>();
        listing.setHttpCode(Response.Status.OK.getStatusCode());
        LongStream.range(offset, offset + limit).forEach(i -> {
            OfferLog offerLog = new OfferLog(container, FILE_NAME + String.valueOf(i), "write");
            listing.addResult(offerLog);
        });
        return listing;
    }

}
