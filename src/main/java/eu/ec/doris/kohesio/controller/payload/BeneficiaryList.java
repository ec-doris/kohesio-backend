package eu.ec.doris.kohesio.controller.payload;

import java.util.ArrayList;

public class BeneficiaryList {
    ArrayList<Beneficiary> list = new ArrayList<Beneficiary>();
    int numberResults;

    public ArrayList<Beneficiary> getList() {
        return list;
    }

    public void setList(ArrayList<Beneficiary> list) {
        this.list = list;
    }

    public int getNumberResults() {
        return numberResults;
    }

    public void setNumberResults(int numberResults) {
        this.numberResults = numberResults;
    }
}