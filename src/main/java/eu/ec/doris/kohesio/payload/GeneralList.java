package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;

public class GeneralList {
    ArrayList<General> list;
    int numberResults;

    public ArrayList<General> getList() {
        return list;
    }

    public void setList(ArrayList<General> list) {
        this.list = list;
    }

    public int getNumberResults() {
        return numberResults;
    }

    public void setNumberResults(int numberResults) {
        this.numberResults = numberResults;
    }
}
