/*
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
 */

package fr.gouv.vitam.storage.engine.common.referential.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a storage strategy (that may be mapped from Json but not
 * exclusively)
 */
public class StorageStrategy {
    @JsonProperty("id")
    private String id;
    @JsonProperty("offers")
    private List<OfferReference> offers = new ArrayList<>();

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id of host storage
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * @return the list of offer references
     */
    public List<OfferReference> getOffers() {
        return offers;
    }

    /**
     * @param offers list of {@link OfferReference}
     */
    public void setOffers(List<OfferReference> offers) {
        this.offers = offers;
    }
    
    public Integer getCopy() {
        return getOffers().size();
    }

    /**
     * remove (after init) inactive offerReferences
     */
    public void postInit() {
        setOffers(
            Collections.unmodifiableList(getOffers().stream()
                .filter(offerReference -> offerReference.isEnabled())
                .collect(Collectors.toList())));
    }

    /**
     * check whether storage offer is enabled (not present means disabled because {@link #postInit} method can filter it)
     *
     * @param offerId storageOfferId to check
     * @return
     */
    public boolean isStorageOfferEnabled(String offerId) {
        Optional<OfferReference>
            offerReference = getOffers().stream()
            .filter(offerRef -> offerRef.getId().equals(offerId)).findFirst();

        return offerReference.isPresent() && offerReference.get().isEnabled();
    }

    public boolean isStorageOfferReferent(String offerId) {
        Optional<OfferReference>
            offerReference = getOffers().stream()
            .filter(offerRef -> offerRef.getId().equals(offerId)).findFirst();

        return offerReference.isPresent() && offerReference.get().isReferent();
    }

}
