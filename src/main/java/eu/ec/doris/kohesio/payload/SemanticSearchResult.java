package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;

public class SemanticSearchResult {
    private ArrayList<String> projectsURIs;
    private int numberOfResults;
    private ArrayList<String> similarWords;
    public ArrayList<String> getProjectsURIs() {
        return projectsURIs;
    }

    public void setProjectsURIs(ArrayList<String> projectsURIs) {
        this.projectsURIs = projectsURIs;
    }

    public int getNumberOfResults() {
        return numberOfResults;
    }

    public void setNumberOfResults(int numberOfResults) {
        this.numberOfResults = numberOfResults;
    }

    public ArrayList<String> getSimilarWords() {
        return similarWords;
    }

    public void setSimilarWords(ArrayList<String> similarWords) {
        this.similarWords = similarWords;
    }
}
