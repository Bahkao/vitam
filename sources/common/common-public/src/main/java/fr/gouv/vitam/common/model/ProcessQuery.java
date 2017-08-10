package fr.gouv.vitam.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Query model to search in process.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessQuery {

    @JsonProperty("id")
    private String id;
    @JsonProperty("states")
    private List<String> states;
    @JsonProperty("statuses")
    private List<String> statuses;
    @JsonProperty("workflows")
    private List<String> workflows;
    @JsonProperty("listSteps")
    private List<String> listSteps;
    @JsonProperty("listProcessTypes")
    private List<String> listProcessTypes;
    // TODO: use LocaDate instead of String here, but need serializer / deserializer (see JavaTimeModule)
    @JsonProperty("startDateMin")
    private String startDateMin;
    // TODO: use LocaDate instead of String here, but need serializer / deserializer (see JavaTimeModule)
    @JsonProperty("startDateMax")
    private String startDateMax;


    /**
     * Constructor without fields use for jackson
     */
    public ProcessQuery() {
    }

    /**
     * @param id
     * @param states
     * @param statuses
     * @param workflows
     * @param listSteps
     * @param listProcessTypes 
     * @param startDateMin
     * @param startDateMax
     */
    public ProcessQuery(String id, List<String> states, List<String> statuses, List<String> workflows,
        List<String> listSteps, List<String> listProcessTypes, String startDateMin, String startDateMax) {
        this.id = id;
        this.states = states;
        this.statuses = statuses;
        this.workflows = workflows;
        this.listSteps = listSteps;
        this.listProcessTypes = listProcessTypes;
        this.startDateMin = startDateMin;
        this.startDateMax = startDateMax;
    }

    /**
     * Gets the id
     * 
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the states
     * 
     * @return the states
     */
    public List<String> getStates() {
        return states;
    }

    /**
     * Gets the statuses
     * 
     * @return the statuses
     */
    public List<String> getStatuses() {
        return statuses;
    }

    /**
     * Gets the workflows
     * 
     * @return the workflows
     */
    public List<String> getWorkflows() {
        return workflows;
    }

    /**
     * Gets steps list
     * 
     * @return the steps
     */
    public List<String> getListSteps() {
        return listSteps;
    }
    
    /**
     * Gets process types list
     * 
     * @return the listProcessTypes
     */
    public List<String> getListProcessTypes() {
        return listProcessTypes;
    }

	/**
     * Gets the startDateMin
     * 
     * @return the startDateMin
     */
    public String getStartDateMin() {
        return startDateMin;
    }

    /**
     * Gets the startDateMax
     * 
     * @return the startDateMax
     */
    public String getStartDateMax() {
        return startDateMax;
    }

    /**
     * Sets the id
     * 
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the states
     * 
     * @param states the states to set
     */
    public void setStates(List<String> states) {
        this.states = states;
    }

    /**
     * Sets the statuses
     * 
     * @param statuses the statuses to set
     */
    public void setStatuses(List<String> statuses) {
        this.statuses = statuses;
    }

    /**
     * Sets the workflows
     * 
     * @param workflows the workflows to set
     */
    public void setWorkflows(List<String> workflows) {
        this.workflows = workflows;
    }

    /**
     * Sets the steps
     * 
     * @param listSteps the steps to set
     */
    public void setListSteps(List<String> listSteps) {
        this.listSteps = listSteps;
	}
    
    /**
     * Sets the process types
     * 
     * @param listProcessTypes the process types to set
     */
    public void setListProcessTypes(List<String> listProcessTypes) {
        this.listProcessTypes = listProcessTypes;
    }

    /**
     * Sets the startDateMin
     * 
     * @param startDateMin the startDateMin to set
     */
    public void setStartDateMin(String startDateMin) {
        this.startDateMin = startDateMin;
    }

    /**
     * Sets the startDateMax
     * 
     * @param startDateMax the startDateMax to set
     */
    public void setStartDateMax(String startDateMax) {
        this.startDateMax = startDateMax;
    }

    @Override
    public String toString() {
        return "{id:" + id + ", states: " + states + ", statuses: " + statuses + ", workflows: " + workflows
            + ", steps: " + listSteps + ", min: " + startDateMin + ", max: " + startDateMax + "}";
    }

}
