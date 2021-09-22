package eu.ec.doris.kohesio.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class FiltersGenerator {
    @Autowired
    SimilarityService similarityService;

        public String filterProject(String keywords,
                                     String country,
                                     String theme,
                                     String fund,
                                     String program,
                                     String categoryOfIntervention,
                                 String policyObjective,
                                     Integer budgetBiggerThen,
                                     Integer budgetSmallerThen,
                                     Integer budgetEUBiggerThen,
                                     Integer budgetEUSmallerThen,
                                     String startDateBefore,
                                     String startDateAfter,
                                 String endDateBefore,
                                 String endDateAfter,
                                 String latitude,
                                 String longitude,
                                 String region,
                                 String granularityRegion,
                                 Integer limit,
                                 Integer offset) throws IOException {
        String search = "";
        if (keywords != null) {
//            if (!keywords.contains("AND") && !keywords.contains("OR") && !keywords.contains("NOT")) {
//                String[] words = keywords.split(" ");
//                StringBuilder keywordsBuilder = new StringBuilder();
//                for (int i = 0; i < words.length - 1; i++) {
//                    keywordsBuilder.append(words[i]).append(" AND ");
//                }
//                keywordsBuilder.append(words[words.length - 1]);
//                keywords = keywordsBuilder.toString();
//            }

            String expanedQuery = similarityService.expandQuery(keywords);
            search +=
                    "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                            + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                            + expanedQuery.replace("\"", "\\\"")
                            + "\" ] .";

        }
//        if(keywords != null) {
//            SemanticSearchResult semanticSearchResult = getProjectsURIsfromSemanticSearch(keywords, false, 0, 5000);
//            ArrayList<String> projectsURIs = semanticSearchResult.getProjectsURIs();
//            if (projectsURIs.size() > 0) {
//                //search = "";
//                search += "VALUES ?s0 {";
//                for (String uri : projectsURIs) {
//                    String uriStr = "<" + uri + ">";
//                    search += uriStr + " ";
//                }
//                search += "}";
//                //numResults = semanticSearchResult.getNumberOfResults();
//            } else {
//                System.out.println("Semantic search API returned empty result!!");
//            }
//        }
        if (country != null && region == null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }

        if (theme != null) {
            search +=
                    "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
                            + "?category <https://linkedopendata.eu/prop/direct/P1848> <"
                            + theme
                            + "> . ";
        }

        if (policyObjective != null) {
            search +=
                    "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
                            + "?category <https://linkedopendata.eu/prop/direct/P1849> <"
                            + policyObjective
                            + "> . ";
        }

        if (fund != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1584> <" + fund + "> . ";
        }

        if (program != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1368> <" + program + "> . ";
        }

        if (categoryOfIntervention != null) {
            search +=
                    "?s0 <https://linkedopendata.eu/prop/direct/P888> <" + categoryOfIntervention + "> . ";
        }

        if (budgetBiggerThen != null) {
            search +=
                    " ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget . "
                            + "FILTER( ?budget > "
                            + budgetBiggerThen
                            + ")";
        }

        if (budgetSmallerThen != null || budgetBiggerThen != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget . ";
            if (budgetBiggerThen != null) {
                search += "FILTER( ?budget > " + budgetBiggerThen + ")";
            }
            if (budgetSmallerThen != null) {
                search += "FILTER( ?budget < " + budgetSmallerThen + ")";
            }
        }

        if (budgetEUBiggerThen != null || budgetEUSmallerThen != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P835> ?budgetEU . ";
            if (budgetEUBiggerThen != null) {
                search += "FILTER( ?budgetEU > " + budgetEUBiggerThen + ")";
            }
            if (budgetEUSmallerThen != null) {
                search += "FILTER( ?budgetEU < " + budgetEUSmallerThen + ")";
            }
        }

        if (startDateBefore != null || startDateAfter != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startDate . ";
            if (startDateBefore != null) {
                search +=
                        "FILTER( ?startDate <= \""
                                + startDateBefore
                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
            if (startDateAfter != null) {
                search +=
                        "FILTER( ?startDate >= \""
                                + startDateAfter
                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
        }

        if (endDateBefore != null || endDateAfter != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endDate . ";
            if (endDateBefore != null) {
                search +=
                        "FILTER( ?endDate <= \""
                                + endDateBefore
                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
            if (endDateAfter != null) {
                search +=
                        "FILTER( ?endDate >= \""
                                + endDateAfter
                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
        }

        if (region != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . ";
        }
        if (granularityRegion != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + granularityRegion + "> . ";
        }
        if (latitude != null && longitude != null) {
            search +=
                    "?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                            + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
                            + longitude
                            + " "
                            + latitude
                            + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)< 100000) . ";
        }

        search +=
                "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";
        return search;
    }
}
