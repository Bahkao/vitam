/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
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
 */
package fr.gouv.vitam.batch.report.rest.repository;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.model.ExtractedMetadata;
import org.bson.Document;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ExtractedMetadataRepositoryTest {
    private static final int TENANT = 0;
    private static final String PROCESS_ID = "processId";

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @InjectMocks
    private ExtractedMetadataRepository extractedMetadataRepository;

    @Mock
    private MongoCollection<Document> extractedMetadataForAuCollection;

    @Captor
    private ArgumentCaptor<List<UpdateOneModel<Document>>> captor;

    @Test
    public void should_add_a_list_of_extracted_metadata_into_database() {
        // Given
        ExtractedMetadata extractedMetadata = new ExtractedMetadata(
            "BATMAN",
            PROCESS_ID,
            TENANT,
            Collections.singletonList("unitId"),
            Collections.singletonMap("MetadataKey", Collections.singletonList("MetadataValue"))
        );

        List<ExtractedMetadata> extractedMetadatas = Collections.singletonList(extractedMetadata);

        BulkWriteResult result = Mockito.mock(BulkWriteResult.class);
        given(result.getUpserts()).willReturn(Collections.singletonList(Mockito.mock(BulkWriteUpsert.class)));

        given(extractedMetadataForAuCollection.bulkWrite(captor.capture(), any())).willReturn(result);

        // When
        extractedMetadataRepository.addExtractedMetadataForAu(extractedMetadatas);

        // Then
        assertThat(captor.getValue()).extracting(UpdateOneModel::getUpdate).extracting(i -> ((Document) ((Document) i).get("$set")).get("processId")).contains(PROCESS_ID);
    }

    @Test
    public void should_get_extracted_metadata_by_process() {
        // Given
        FindIterable findResultMock = Mockito.mock(FindIterable.class);
        MongoCursor expectedCursorResult = Mockito.mock(MongoCursor.class);

        given(extractedMetadataForAuCollection.find(and(eq("processId", PROCESS_ID), eq("tenant", TENANT)))).willReturn(findResultMock);
        given(findResultMock.map(any())).willReturn(findResultMock);
        given(findResultMock.cursor()).willReturn(expectedCursorResult);

        // When
        MongoCursor<ExtractedMetadata> metadataMongoCursorResult = extractedMetadataRepository.getExtractedMetadataByProcessId(PROCESS_ID, TENANT);

        // Then
        assertThat(metadataMongoCursorResult).isEqualTo(expectedCursorResult);
    }

    @Test
    public void should_delete_extracted_metadata_by_process() {
        // Given / When
        extractedMetadataRepository.deleteExtractedMetadataByProcessId(PROCESS_ID, TENANT);
        // Then
        verify(extractedMetadataForAuCollection).deleteMany(and(eq("processId", PROCESS_ID), eq("tenant", TENANT)));
    }
}