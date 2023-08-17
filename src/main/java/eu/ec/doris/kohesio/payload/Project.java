package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;

public class Project {
    String link;
    String item;
    ArrayList<String> snippet;
    ArrayList<String> labels;
    ArrayList<String> originalLabels;
    ArrayList<String> descriptions;
    ArrayList<String> orignalDescriptions;
    ArrayList<String> startTimes;
    ArrayList<String> endTimes;
    ArrayList<String> euBudgets;
    ArrayList<String> totalBudgets;
    ArrayList<String> images;
    ArrayList<String> copyrightImages;
    ArrayList<String> coordinates;
    ArrayList<String> objectiveIds;
    ArrayList<String> countrycode;

    public ArrayList<String> getOrignalDescriptions() {
        return orignalDescriptions;
    }

    public void setOrignalDescriptions(ArrayList<String> orignalDescriptions) {
        this.orignalDescriptions = orignalDescriptions;
    }

    public ArrayList<String> getOriginalLabels() {
        return originalLabels;
    }

    public void setOriginalLabels(ArrayList<String> originalLabels) {
        this.originalLabels = originalLabels;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public ArrayList<String> getSnippet() {
        return snippet;
    }

    public void setSnippet(ArrayList<String> snippet) {
        this.snippet = snippet;
    }

    public ArrayList<String> getLabels() {
        return labels;
    }

    public void setLabels(ArrayList<String> labels) {
        this.labels = labels;
    }

    public ArrayList<String> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(ArrayList<String> descriptions) {
        this.descriptions = descriptions;
    }

    public ArrayList<String> getStartTimes() {
        return startTimes;
    }

    public void setStartTimes(ArrayList<String> startTimes) {
        this.startTimes = startTimes;
    }

    public ArrayList<String> getEndTimes() {
        return endTimes;
    }

    public void setEndTimes(ArrayList<String> endTimes) {
        this.endTimes = endTimes;
    }

    public ArrayList<String> getEuBudgets() {
        return euBudgets;
    }

    public void setEuBudgets(ArrayList<String> euBudgets) {
        this.euBudgets = euBudgets;
    }

    public ArrayList<String> getTotalBudgets() {
        return totalBudgets;
    }

    public void setTotalBudgets(ArrayList<String> totalBudgets) {
        this.totalBudgets = totalBudgets;
    }

    public ArrayList<String> getImages() {
        return images;
    }

    public void setImages(ArrayList<String> images) {
        this.images = images;
    }

    public ArrayList<String> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(ArrayList<String> coordinates) {
        this.coordinates = coordinates;
    }

    public ArrayList<String> getObjectiveIds() {
        return objectiveIds;
    }

    public void setObjectiveIds(ArrayList<String> objectiveIds) {
        this.objectiveIds = objectiveIds;
    }

    public ArrayList<String> getCountrycode() {
        return countrycode;
    }

    public void setCountrycode(ArrayList<String> countrycode) {
        this.countrycode = countrycode;
    }

    public ArrayList<String> getCopyrightImages() {
        return copyrightImages;
    }

    public void setCopyrightImages(ArrayList<String> copyrightImages) {
        this.copyrightImages = copyrightImages;
    }
}