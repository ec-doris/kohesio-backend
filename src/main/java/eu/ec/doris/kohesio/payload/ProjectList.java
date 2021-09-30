package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;

public class ProjectList {
    ArrayList<Project> list = new ArrayList<Project>();
    int numberResults;

    ArrayList<String> similarWords;

    public ArrayList<String> getSimilarWords() {
        return similarWords;
    }

    public void setSimilarWords(ArrayList<String> similarWords) {
        this.similarWords = similarWords;
    }

    public ArrayList<Project> getList() {
        return list;
    }

    public void setList(ArrayList<Project> list) {
        this.list = list;
    }

    public int getNumberResults() {
        return numberResults;
    }

    public void setNumberResults(int numberResults) {
        this.numberResults = numberResults;
    }
}