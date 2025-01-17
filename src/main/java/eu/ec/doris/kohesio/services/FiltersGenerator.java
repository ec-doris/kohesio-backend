package eu.ec.doris.kohesio.services;

import eu.ec.doris.kohesio.payload.BoundingBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FiltersGenerator {
    @Autowired
    SimilarityService similarityService;

    public String filterProject(
            String keywords,
            String language,
            String country,
            String theme,
            String fund,
            String program,
            List<String> categoryOfIntervention,
            String policyObjective,
            Long budgetBiggerThen,
            Long budgetSmallerThen,
            Long budgetEUBiggerThen,
            Long budgetEUSmallerThen,
            String startDateBefore,
            String startDateAfter,
            String endDateBefore,
            String endDateAfter,
            String latitude,
            String longitude,
            Long radius,
            String region,
            String granularityRegion,
            Boolean interreg,
            Boolean highlighted,
            List<String> ccis,
            String kohesioCategory,
            List<String> projectTypes,
            String priorityAxis,
            BoundingBox boundingBox,
            Integer limit,
            Integer offset,
            boolean flagFilter
    ) {
        String search = "";

        search += " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";
        if (keywords != null) {
            search += "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                    + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                    + keywords.replace("\"", "\\\"").replace("!", "\\\\!")
                    + "\"; "
                    + " <http://www.openrdf.org/contrib/lucenesail#indexid> <http://the-qa-company.com/modelcustom/Proj_" + language + "> "
                    + "] .";

        }
        if (country != null && region == null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }

        if (priorityAxis != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P574247> <" + priorityAxis + "> . ";
        }

        if (fund != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1584> <" + fund + "> . ";
        }

        if (program != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1368> <" + program + "> . ";
        }

        if (ccis != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P1368> ?program . "
                    + " ?program <https://linkedopendata.eu/prop/direct/P1367> ?cci . "
                    + "FILTER(?cci IN (";
            for (String cci : ccis) {
                search += " \"" + cci + "\",";
            }
            search = search.substring(0, search.length() - 1);
            search += "))";
        }

        if (categoryOfIntervention != null) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P888> ?categoryOfIntervention . ";
            search += "VALUES ?categoryOfIntervention {";
            for (String category : categoryOfIntervention) {
                search += "<" + category + "> ";
            }
            search += "}";
        }

        if (interreg != null && interreg) {
            search += "?s0 <https://linkedopendata.eu/prop/direct/P562941> ?keepId . ";
        }
        if (interreg != null && !interreg) {
            search +=
                    "OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P562941> ?keepId . } FILTER(!BOUND(?keepId))";
        }
        if (highlighted != null && highlighted) {
            search +=
                    "?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . ";
        }
        if (highlighted != null && !highlighted) {
            search +=
                    "FILTER(NOT EXISTS { ?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . } )";
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
                search += "FILTER( ?endDate <= \""
                        + endDateBefore
                        + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
            if (endDateAfter != null) {
                search += "FILTER( ?endDate >= \""
                        + endDateAfter
                        + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
            }
        }


        if (granularityRegion != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + granularityRegion + "> . ";
        } else if (region != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . ";
        }
        boolean isCoordinateSearch = (latitude != null && longitude != null);
        boolean isBoundingBoxSearch = (boundingBox != null);

        if (isCoordinateSearch || isBoundingBoxSearch) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . ";
            if (isBoundingBoxSearch) {
//                BoundingBox bbox = BoundingBox;
                if (keywords !=null) {
                    search += " FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?coordinates, \"" + boundingBox.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>)) ";
                }
//                } else {
//                    search += " FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + boundingBox.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates)) ";
//                }
            }
            if (isCoordinateSearch) {
                if (radius == null) {
                    radius = 100L;
                }
//            search += "?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
//                    + "FILTER ( "
//                    + "<http://www.opengis.net/def/function/geosparql/distance>(\"POINT(" + longitude + " " + latitude + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)"
//                    + "< 100000) . ";
                if (keywords == null) {
                    search += " FILTER(<http://www.opengis.net/def/function/geosparql/distance>(\"POINT(" + longitude + " " + latitude + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>) < " + (radius * 1000) + ")";
                } else {
                    search += " FILTER(<http://www.opengis.net/def/function/geosparql/distance>(?coordinates,\"POINT(" + longitude + " " + latitude + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,<http://www.opengis.net/def/uom/OGC/1.0/metre>) < " + (radius * 1000) + ")";
                }
            }
        }

        if (kohesioCategory != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P888> ?category."
                    + " ?category <https://linkedopendata.eu/prop/direct/P579321> <" + kohesioCategory + "> . "
            ;
        }

        if (projectTypes != null) {
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P35> ";
            search += String.join(
                    ",",
                    projectTypes.stream().map((projectType) -> "<" + projectType + ">").collect(Collectors.toList())
            );
            search += ". ";
        }

        if (theme != null || policyObjective != null) {
            search += "{";
            if (theme != null) {
                search += " ?s0 <https://linkedopendata.eu/prop/direct/P1848> <" + theme + "> .  ";
            }
            if (policyObjective != null) {
                search += " ?s0 <https://linkedopendata.eu/prop/direct/P1849> <" + policyObjective + "> . ";
            }
            search += " } UNION { ";
            search += " ?s0 <https://linkedopendata.eu/prop/direct/P888> ?category . ";
            if (policyObjective != null) {
                if (flagFilter) {
                    search += " FILTER EXISTS {";
                }
                search += " ?category <https://linkedopendata.eu/prop/direct/P1849> <" + policyObjective + "> . ";
                if (flagFilter) {
                    search += "} ";
                }
            }
            if (theme != null) {
                if (flagFilter) {
                    search += " FILTER EXISTS {";
                }
                search += " ?category <https://linkedopendata.eu/prop/direct/P1848> <" + theme + "> .  ";
                if (flagFilter) {
                    search += "} ";
                }
            }
            search += " } ";
        }
        return search;
    }

    public HashMap<String, List<String>> getCountriesCodeMapping() {
        HashMap<String, List<String>> mapping = new HashMap<>();
//        EU_LANGUAGES = ['bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'ga', 'hr',
//                'hu', 'it', 'lt', 'lv', 'mt', 'nl', 'pl', 'pt', 'ro', 'sk', 'sl', 'sv']

        mapping.put("<https://linkedopendata.eu/entity/Q14>", Arrays.asList("lt")); // lithuania
        mapping.put("<https://linkedopendata.eu/entity/Q10>", Arrays.asList("fi")); // finland
        mapping.put("<https://linkedopendata.eu/entity/Q27>", Arrays.asList("sl")); // slovenia
        mapping.put("<https://linkedopendata.eu/entity/Q18>", Arrays.asList("pt")); // portugal
        mapping.put("<https://linkedopendata.eu/entity/Q23>", Arrays.asList("et")); // estonia
        mapping.put("<https://linkedopendata.eu/entity/Q9>", Arrays.asList("fr")); // luxembourg
        mapping.put("<https://linkedopendata.eu/entity/Q29>", Arrays.asList("bg")); // bulgaria
        mapping.put("<https://linkedopendata.eu/entity/Q28>", Arrays.asList("ro")); // romania
        mapping.put("<https://linkedopendata.eu/entity/Q3>", Arrays.asList("hu")); // hungary
        mapping.put("<https://linkedopendata.eu/entity/Q19>", Arrays.asList("nl")); // netherlands
        mapping.put("<https://linkedopendata.eu/entity/Q26>", Arrays.asList("sk")); // slovakia
        mapping.put("<https://linkedopendata.eu/entity/Q16>", Arrays.asList("de")); // austria
        mapping.put("<https://linkedopendata.eu/entity/Q24>", Arrays.asList("lv")); // latvia
        mapping.put("<https://linkedopendata.eu/entity/Q12>", Arrays.asList("da")); // denmark
        mapping.put("<https://linkedopendata.eu/entity/Q30>", Arrays.asList("hr")); // croatia
        mapping.put("<https://linkedopendata.eu/entity/Q11>", Arrays.asList("sv")); // sweden
        mapping.put("<https://linkedopendata.eu/entity/Q7>", Arrays.asList("es")); // spain
        mapping.put("<https://linkedopendata.eu/entity/Q22>", Arrays.asList("de")); // germany
        mapping.put("<https://linkedopendata.eu/entity/Q31>", Arrays.asList("el", "en")); // cyprus
        mapping.put("<https://linkedopendata.eu/entity/Q17>", Arrays.asList("el")); // greece
        mapping.put("<https://linkedopendata.eu/entity/Q25>", Arrays.asList("cs")); // czech republic
        mapping.put("<https://linkedopendata.eu/entity/Q13>", Arrays.asList("pl")); // poland
        mapping.put("<https://linkedopendata.eu/entity/Q15>", Arrays.asList("it")); // italy
        mapping.put("<https://linkedopendata.eu/entity/Q20>", Arrays.asList("fr")); // france
        mapping.put("<https://linkedopendata.eu/entity/Q2>", Arrays.asList("en")); // ireland
        mapping.put("<https://linkedopendata.eu/entity/Q32>", Arrays.asList("en")); // malta
        mapping.put("<https://linkedopendata.eu/entity/Q8>", Arrays.asList("fr", "nl", "de", "en")); // belgium

        return mapping;
    }
}