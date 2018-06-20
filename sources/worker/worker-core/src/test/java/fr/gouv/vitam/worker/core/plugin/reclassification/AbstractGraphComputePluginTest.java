package fr.gouv.vitam.worker.core.plugin.reclassification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import fr.gouv.vitam.common.client.VitamClientFactoryInterface;
import fr.gouv.vitam.common.model.GraphComputeResponse;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.plugin.reclassification.AbstractGraphComputePlugin;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AbstractGraphComputePluginTest {

    @Spy
    private AbstractGraphComputePlugin abstractGraphComputePlugin;

    @Before
    public void setUp() throws Exception {
        MetaDataClientFactory metaDataClientFactory = MetaDataClientFactory.getInstance();
        metaDataClientFactory.setVitamClientType(VitamClientFactoryInterface.VitamClientType.MOCK);
        abstractGraphComputePlugin.setMetaDataClientFactory(metaDataClientFactory);

        when(abstractGraphComputePlugin.getGraphComputeAction())
            .thenReturn(GraphComputeResponse.GraphComputeAction.UNIT);
    }


    @Test(expected = ProcessingException.class)
    public void executeShouldThrowException() throws ProcessingException {
        HandlerIO handlerIO = mock(HandlerIO.class);
        abstractGraphComputePlugin.execute(null, handlerIO);
    }

    @Test
    public void whenExecuteListThenOK() {
        HandlerIO handlerIO = mock(HandlerIO.class);
        WorkerParameters workerParameters = WorkerParametersFactory.newWorkerParameters();
        workerParameters.setObjectNameList(Lists.newArrayList("a", "b", "c"));
        List<ItemStatus> itemStatuses = abstractGraphComputePlugin.executeList(workerParameters, handlerIO);
        assertThat(itemStatuses).hasSize(1);
        ItemStatus itemStatuse = itemStatuses.iterator().next();
        assertThat(itemStatuse.getGlobalStatus()).isEqualTo(StatusCode.OK);
        List<Integer> statusMeter = itemStatuse.getStatusMeter();
        assertThat(statusMeter.get(3)).isEqualTo(3);
    }

    @Test
    public void whenExecuteListThenFATAL() {
        HandlerIO handlerIO = mock(HandlerIO.class);
        WorkerParameters workerParameters = WorkerParametersFactory.newWorkerParameters();
        workerParameters.setObjectNameList(Lists.newArrayList("a", "b", "c", "d"));
        List<ItemStatus> itemStatuses = abstractGraphComputePlugin.executeList(workerParameters, handlerIO);
        assertThat(itemStatuses).hasSize(1);
        ItemStatus itemStatuse = itemStatuses.iterator().next();
        assertThat(itemStatuse.getGlobalStatus()).isEqualTo(StatusCode.FATAL);
        List<Integer> statusMeter = itemStatuse.getStatusMeter();
        assertThat(statusMeter.get(3)).isEqualTo(3);
        assertThat(statusMeter.get(6)).isEqualTo(1);
    }

}
