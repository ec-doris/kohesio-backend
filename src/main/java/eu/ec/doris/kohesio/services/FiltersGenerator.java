package eu.ec.doris.kohesio.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;

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


            search +=
                    "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                            + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                            + keywords.replace("\"", "\\\"")
                            + "\"; " +
//                            "<http://www.openrdf.org/contrib/lucenesail#snippet> ?description"+
                            "] .";

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
    public HashMap<String,String> getCountriesCodeMapping(){
        HashMap<String, String> mapping = new HashMap<>();
//        EU_LANGUAGES = ['bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'ga', 'hr',
//                'hu', 'it', 'lt', 'lv', 'mt', 'nl', 'pl', 'pt', 'ro', 'sk', 'sl', 'sv']

        mapping.put("<https://linkedopendata.eu/entity/Q14>","lt"); // lithuania
        mapping.put("<https://linkedopendata.eu/entity/Q10>"," fi"); // finland
        mapping.put("<https://linkedopendata.eu/entity/Q27>","sl"); // slovenia
        mapping.put("<https://linkedopendata.eu/entity/Q18>","pt"); // portugal
        mapping.put("<https://linkedopendata.eu/entity/Q23>","et"); // estonia
        mapping.put("<https://linkedopendata.eu/entity/Q9>","fr"); // luxembourg
        mapping.put("<https://linkedopendata.eu/entity/Q29>","bg"); // bulgaria
        mapping.put("<https://linkedopendata.eu/entity/Q28>","ro"); // romania
        mapping.put("<https://linkedopendata.eu/entity/Q3>","hu"); // hungary
        mapping.put("<https://linkedopendata.eu/entity/Q19>","nl"); // netherlands
        mapping.put("<https://linkedopendata.eu/entity/Q26>","sk"); // slovakia
        mapping.put("<https://linkedopendata.eu/entity/Q16>","de"); // austria
        mapping.put("<https://linkedopendata.eu/entity/Q24>","lv"); // latvia
        mapping.put("<https://linkedopendata.eu/entity/Q12>","da"); // denmark
        mapping.put("<https://linkedopendata.eu/entity/Q30>","hr"); // croatia
        mapping.put("<https://linkedopendata.eu/entity/Q11>","sv"); // sweden
        mapping.put("<https://linkedopendata.eu/entity/Q7>","es"); // spain
        mapping.put("<https://linkedopendata.eu/entity/Q22>","de"); // germany
        mapping.put("<https://linkedopendata.eu/entity/Q31>","el"); // cyprus
        mapping.put("<https://linkedopendata.eu/entity/Q17>","el"); // greece
        mapping.put("<https://linkedopendata.eu/entity/Q25>","cs"); // czech republic
        mapping.put("<https://linkedopendata.eu/entity/Q13>","pl"); // poland
        mapping.put("<https://linkedopendata.eu/entity/Q15>","it"); // italy
        mapping.put("<https://linkedopendata.eu/entity/Q20>","fr"); // france
        mapping.put("<https://linkedopendata.eu/entity/Q2>","en"); // ireland
        mapping.put("<https://linkedopendata.eu/entity/Q32>","en"); // malta

        return mapping;
    }
}
