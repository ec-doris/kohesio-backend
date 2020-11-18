package eu.ec.doris.kohesio.payload;

import org.apache.commons.math3.util.Precision;

public class Beneficiary {
    String id;
    String label;
    String euBudget;
    String budget;
    String cofinancingRate;
    int numberProjects;
    String country;
    String countryCode;
    String link;

    public Beneficiary(){}

    public void setId(String id) {
        this.id = id;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setEuBudget(String euBudget) {
        this.euBudget = euBudget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public void setCofinancingRate(String cofinancingRate) {
        this.cofinancingRate = cofinancingRate;
    }

    public void setNumberProjects(int numberProjects) {
        this.numberProjects = numberProjects;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getEuBudget() {
        return euBudget;
    }

    public String getBudget() {
        return budget;
    }

    public String getCofinancingRate() {
        return cofinancingRate;
    }

    public int getNumberProjects() {
        return numberProjects;
    }

    public String getCountry() {
        return country;
    }

    public String getLink() {
        return link;
    }

    public void computeCofinancingRate(){
        if (budget!= null && euBudget != null){
            cofinancingRate = Double.toString(Precision.round(Double.parseDouble(euBudget) / Double.parseDouble(budget) * 100,2));
        }
    }
}
