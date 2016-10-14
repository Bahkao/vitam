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
package fr.gouv.vitam.processing.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * ActionDefinition class This class is used to define an action It has for the moment 2 fields actionKey and actionType
 *
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class ActionDefinition {

    @JsonProperty("actionKey")
    private String actionKey;

    @JsonProperty("behavior")
    private ProcessBehavior behavior;

    @JsonProperty("in")
    private List<IOParameter> in;
    @JsonProperty("out")
    private List<IOParameter> out;

    /**
     *
     * @return actionKey the action key
     */
    public String getActionKey() {
        if (actionKey == null) {
            return "";
        }
        return actionKey;
    }

    /**
     * set action key
     *
     * @param actionKey the key of the action
     * @return the updated ActionDefinition object
     */
    public ActionDefinition setActionKey(String actionKey) {
        this.actionKey = actionKey;
        return this;
    }

    /**
     *
     * @return ({ProcessBehavior}) Type of action object or bean
     */
    public ProcessBehavior getBehavior() {
        return behavior;
    }

    /**
     * set action type
     *
     * @param actionType the type of the action
     * @return the updated ActionDefinition object
     */
    public ActionDefinition setBehavior(ProcessBehavior behavior) {
        this.behavior = behavior;
        return this;
    }


    /**
     * @return the in
     */
    public List<IOParameter> getIn() {
        return in;
    }

    /**
     * @param in the in to set
     *
     * @return this Action
     */
    public ActionDefinition setIn(List<IOParameter> in) {
        this.in = in;
        return this;
    }

    /**
     * @return the out
     */
    public List<IOParameter> getOut() {
        return out;
    }

    /**
     * @param out the out to set
     *
     * @return this Action
     */
    public ActionDefinition setOut(List<IOParameter> out) {
        this.out = out;
        return this;
    }



}
