package eu.ec.doris.kohesio.services;

import eu.ec.doris.kohesio.payload.SimilarWord;

import java.util.ArrayList;

public class ExpandedQuery {
    String expandedQuery;
    ArrayList<SimilarWord> keywords;

    public ExpandedQuery setExpandedQuery(String expandedQuery) {
        this.expandedQuery = expandedQuery;
        return this;
    }

    public ExpandedQuery setKeywords(ArrayList<SimilarWord> keywords) {
        this.keywords = keywords;
        return this;
    }

    public ArrayList<SimilarWord> getKeywords() {
        return keywords;
    }

    public String getExpandedQuery() {
        return expandedQuery;
    }
}
