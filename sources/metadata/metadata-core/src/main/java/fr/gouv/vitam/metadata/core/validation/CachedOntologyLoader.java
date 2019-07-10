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
package fr.gouv.vitam.metadata.core.validation;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.model.MetadataType;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.OntologyModel;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CachedOntologyLoader implements OntologyLoader {

    private final AdminManagementClientFactory adminManagementClientFactory;
    private final LoadingCache<String, List<OntologyModel>> ongologyCache;
    private MetadataType metadataType;

    public CachedOntologyLoader(
        AdminManagementClientFactory adminManagementClientFactory, int maxEntriesInCache, int cacheTimeoutInSeconds,
        MetadataType metadataType) {

        this.adminManagementClientFactory = adminManagementClientFactory;
        this.metadataType = metadataType;

        CacheBuilder<Object, Object> objectObjectCacheBuilder = CacheBuilder.newBuilder();
        // Max entries in cache
        objectObjectCacheBuilder.maximumSize(maxEntriesInCache);
        // Access timeout
        objectObjectCacheBuilder.expireAfterAccess(cacheTimeoutInSeconds, TimeUnit.SECONDS);
        // Okay to GC
        objectObjectCacheBuilder.weakValues();
        this.ongologyCache = objectObjectCacheBuilder
            .build(new CacheLoader<String, List<OntologyModel>>() {
                @Override
                public List<OntologyModel> load(String key) {
                    return loadOntologiesFromAdminManagement();
                }
            });
    }

    public List<OntologyModel> loadOntologies() {
        String requestId = VitamThreadUtils.getVitamSession().getRequestId();
        return this.ongologyCache.getUnchecked(requestId);
    }

    private List<OntologyModel> loadOntologiesFromAdminManagement() {
        try (AdminManagementClient adminClient = adminManagementClientFactory.getClient()) {
            Select selectOntologies = new Select();
            selectOntologies.setQuery(
                QueryHelper.in(OntologyModel.TAG_COLLECTIONS, metadataType.getName())
            );
            selectOntologies.addUsedProjection(OntologyModel.TAG_IDENTIFIER);
            selectOntologies.addUsedProjection(OntologyModel.TAG_TYPE);

            RequestResponse<OntologyModel> responseOntologies =
                adminClient.findOntologies(selectOntologies.getFinalSelect());
            if (!responseOntologies.isOk()) {
                throw new VitamRuntimeException("Could not load ontologies");
            }

            return ((RequestResponseOK<OntologyModel>) responseOntologies).getResults();
        } catch (InvalidParseOperationException | AdminManagementClientServerException | InvalidCreateOperationException e) {
            throw new VitamRuntimeException("Could not load ontologies", e);
        }
    }
}