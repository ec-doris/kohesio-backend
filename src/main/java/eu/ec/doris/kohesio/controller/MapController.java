package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import eu.ec.doris.kohesio.geoIp.GeoIp;
import eu.ec.doris.kohesio.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.payload.BoundingBox;
import eu.ec.doris.kohesio.payload.Nut;
import eu.ec.doris.kohesio.payload.NutsRegion;
import eu.ec.doris.kohesio.services.*;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mapdb.Atomic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wikibase")

public class MapController {

    private static final Logger logger = LoggerFactory.getLogger(MapController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Autowired
    SimilarityService similarityService;

    @Autowired
    FiltersGenerator filtersGenerator;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @Value("${kohesio.sparqlEndpointNuts}")
    String getSparqlEndpointNuts;

    @Autowired
    HttpReqRespUtils httpReqRespUtils;

    @Autowired
    GeoIp geoIp;

    @Autowired
    FacetController facetController;

    @Autowired
    NominatimService nominatimService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/search/project/map", produces = "application/json")
    public ResponseEntity euSearchProjectMap(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "keywords", required = false) String keywords, //
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "categoryOfIntervention", required = false) List<String> categoryOfIntervention,
            @RequestParam(value = "policyObjective", required = false) String policyObjective,
            @RequestParam(value = "budgetBiggerThan", required = false) Long budgetBiggerThen,
            @RequestParam(value = "budgetSmallerThan", required = false) Long budgetSmallerThen,
            @RequestParam(value = "budgetEUBiggerThan", required = false) Long budgetEUBiggerThen,
            @RequestParam(value = "budgetEUSmallerThan", required = false) Long budgetEUSmallerThen,
            @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
            @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
            @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
            @RequestParam(value = "endDateAfter", required = false) String endDateAfter,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "granularityRegion", required = false) String granularityRegion,
            @RequestParam(value = "nuts3", required = false) String nuts3,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "town", required = false) String town,
            @RequestParam(value = "radius", required = false) Long radius,
            @RequestParam(value = "interreg", required = false) Boolean interreg,
            @RequestParam(value = "highlighted", required = false) Boolean highlighted,
            @RequestParam(value = "cci", required = false) List<String> cci,
            @RequestParam(value = "kohesioCategory", required = false) String kohesioCategory,
            @RequestParam(value = "projectTypes", required = false) List<String> projectTypes,
            @RequestParam(value = "priority_axis", required = false) String priorityAxis,
            @RequestParam(value = "boundingBox", required = false) String boundingBoxString,
            Integer timeout,
            Principal principal)
            throws Exception {
        logger.info("Search Projects on map: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} region {} limit {} offset {} granularityRegion {}, lat {} long {} timeout {} interreg {} boundingBox {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, region, limit, offset, granularityRegion, latitude, longitude, timeout, interreg, boundingBoxString);
        facetController.initialize(language);
        if (timeout == null) {
            timeout = 300;
        }
        BoundingBox boundingBox = null;
        if (boundingBoxString != null) {
            boundingBox = objectMapper.readValue(boundingBoxString, BoundingBox.class);
        }

        //simplify the query
        String c = country;

        if (nuts3 != null && facetController.nutsRegion.containsKey(nuts3) && facetController.nutsRegion.get(nuts3).type.contains("nuts3")) {
            granularityRegion = nuts3;
        }

        if (granularityRegion != null) {
            c = null;
        }
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords, language);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }
        if (town != null) {
            NominatimService.Coordinates tmpCoordinates = nominatimService.getCoordinatesFromTown(town);
            if (tmpCoordinates != null) {
                latitude = tmpCoordinates.getLatitude();
                longitude = tmpCoordinates.getLongitude();

            }
        }
        String search = filtersGenerator.filterProject(
                expandedQueryText, language, c, theme, fund, program, categoryOfIntervention,
                policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen,
                budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore,
                endDateAfter, latitude, longitude, null, region, granularityRegion,
                interreg, highlighted, cci, kohesioCategory, projectTypes, priorityAxis, boundingBox, limit, offset
        );
        //computing the number of results
        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + search + "} ";
        int numResults = 0;
        if (limit == null || limit > 2000) {
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map");

            if (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
            }
        }

        logger.debug("Number of results {}", numResults);
        boolean hasCoordinates = latitude != null && longitude != null;
        Nut nut = facetController.nutsRegion.get(granularityRegion);
        boolean isInSweden = nut != null && "https://linkedopendata.eu/entity/Q11".equals(nut.country);
        boolean hasLowerGranularity = nut != null && !nut.narrower.isEmpty() || granularityRegion == null;
        boolean isGreekNuts2 = nut != null && "https://linkedopendata.eu/entity/Q17".equals(nut.country) && "nuts2".equals(nut.granularity);
//        logger.debug(
//                "hasCoordinates {}, isInSweden {}, hasLowerGranularity {}, numResults {}, granularityRegion {}",
//                hasCoordinates, isInSweden, hasLowerGranularity, numResults, granularityRegion
//        );
        if (!hasLowerGranularity || numResults <= 2000 || hasCoordinates || isGreekNuts2) {
            return mapReturnCoordinates(
                    language,
                    search,
                    country,
                    region,
                    granularityRegion,
                    latitude,
                    longitude,
                    cci,
                    limit,
                    offset,
                    timeout
            );
        } else {
            if (granularityRegion == null) {
                granularityRegion = "https://linkedopendata.eu/entity/Q1";
                query = "SELECT ?region (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                        + " { SELECT DISTINCT ?region { "
                        + " <https://linkedopendata.eu/entity/Q1> <https://linkedopendata.eu/prop/direct/P104> ?region . "
                        + " }"
                        + " }"
                        + " OPTIONAL {"
                        + search
                        + " ?s0 <https://linkedopendata.eu/prop/direct/P32> ?region ."
                        + " }"
                        + " } GROUP BY ?region "
                ;
                TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map");

                HashMap<String, JSONObject> subRegions2 = new HashMap<>();
                while (resultSet.hasNext()) {
                    BindingSet querySolution = resultSet.next();
                    JSONObject element = new JSONObject();
                    String uri = querySolution.getBinding("region").getValue().stringValue();
                    Nut n = facetController.nutsRegion.get(uri);
                    String regionLabel = n.name.get(language);
                    if (regionLabel == null) {
                        regionLabel = n.name.get("en");
                    }
                    element.put("regionLabel", regionLabel);
                    element.put("region", uri);
                    element.put("geoJson", n.geoJson);
                    element.put("count", ((Literal) querySolution.getBinding("c").getValue()).intValue());
                    subRegions2.put(uri, element);
                }

                JSONArray resultList = new JSONArray();
                for (JSONObject o : subRegions2.values()) {
                    resultList.add(o);
                }

                JSONObject result = new JSONObject();

                result.put("region", granularityRegion);
                result.put("upperRegions", findUpperRegions(granularityRegion, language));
                result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));
                result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
                result.put("subregions", resultList);

                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                HashMap<String, JSONObject> r = findLowerRegionCount(granularityRegion, search, language, timeout);
                JSONArray resultList = new JSONArray();
                for (JSONObject o : r.values()) {
                    resultList.add(o);
                }
                JSONObject result = new JSONObject();

                result.put("region", granularityRegion);
                result.put("upperRegions", findUpperRegions(granularityRegion, language));
                result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));
                result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
                result.put("subregions", resultList);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
        }
    }

    private HashMap<String, JSONObject> findLowerRegionCount(String region, String search, String language, int timeout) throws Exception {
        String query = "SELECT ?region (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                + " { SELECT DISTINCT ?region { "
                + " { "
                + " ?region <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407315> . "
                + " ?region <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . "
                + " } UNION { "
                + " ?region <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407316> . "
                + " ?region <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . "
                + " } UNION { "
                + " ?region <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407317> . "
                + " ?region <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . "
                + " }"
                + " }"
                + " }"
                + " OPTIONAL {"
                + search.replaceAll("[?]s0 +<https://linkedopendata.eu/prop/direct/P1845> +<[^>]+> *. *", "")
                + " ?s0 <https://linkedopendata.eu/prop/direct/P1845> ?region ."
                + " }"
                + " } GROUP BY ?region ";

        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map");
        HashMap<String, JSONObject> subRegions = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            String uri = querySolution.getBinding("region").getValue().stringValue();
            if (uri.equals(region)) {
                continue;
            }
            boolean isStat = sparqlQueryService.executeBooleanQuery(
                    sparqlEndpoint,
                    "ASK { <" + uri + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2727537> . }",
                    20
            );
            if (isStat) {
                subRegions.putAll(findLowerRegionCount(uri, search, language, timeout));
            } else {
                Nut n = facetController.nutsRegion.get(uri);
                String regionLabel = n.name.get(language);
                if (regionLabel == null) {
                    regionLabel = n.name.get("en");
                }
                element.put("regionLabel", regionLabel);
                element.put("region", uri);
                element.put("geoJson", n.geoJson);
                element.put("count", ((Literal) querySolution.getBinding("c").getValue()).intValue());
                subRegions.put(uri, element);
            }
        }
        return subRegions;
    }

    ResponseEntity<JSONObject> mapReturnCoordinates(String language, String search, String country, String region, String granularityRegion, String latitude, String longitude, List<String> cci, Integer limit, Integer offset, int timeout) throws Exception {
        logger.debug("granularityRegion {}, limit {}, cci {}", granularityRegion, limit, cci);
        String optional = " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. ";
        // not performing
        if (granularityRegion != null) {
            if ("country".equals(facetController.nutsRegion.get(granularityRegion).granularity)) {
                optional += " OPTIONAL {SELECT DISTINCT ?o WHERE { ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry20M> ?o . }} ";

            } else {
                optional += " OPTIONAL {SELECT DISTINCT ?o WHERE { ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry> ?o . }} ";
            }
            //check if granularity region is a country, if yes the filter is not needed
            boolean isCountry = false;
            for (Object jsonObject : facetController.facetEuCountries("en", null)) {
                JSONObject o = (JSONObject) jsonObject;
                if (granularityRegion.equals(o.get("instance"))) {
                    isCountry = true;
                }
            }
            // this is a hack to show brittany
            if ((latitude == null || longitude == null)) {
                optional += "FILTER (<http://www.opengis.net/def/function/geosparql/sfWithin>(?coordinates, ?o)) . ";
            }
        }
        // add info regio optional, to flag on the map
        optional += "OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . }";

        if (limit == null) {
            limit = 1000;
        }
        String query = null;
        if (latitude != null && longitude != null) {
            query =
                    "SELECT DISTINCT ?coordinates ?infoRegioID WHERE { "
                            + " { SELECT ?s0 ((<http://www.opengis.net/def/function/geosparql/distance>(\"POINT(" + longitude + " " + latitude + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)) AS ?distance) WHERE { "
                            + search
                            + " } ORDER BY ?distance LIMIT "
                            + limit
                            + " OFFSET "
                            + offset
                            + " } "
                            + optional
                            + " FILTER((<http://www.opengis.net/def/function/geosparql/distance>(\"POINT(" + longitude + " " + latitude + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>, ?coordinates, <http://www.opengis.net/def/uom/OGC/1.0/metre>)) < 100000 )"
                            + "} ";
        } else {
            query =
                    "SELECT DISTINCT ?coordinates ?infoRegioID WHERE { "
                            + " { SELECT DISTINCT ?s0 WHERE { "
                            + search
                            + " } "/*LIMIT "
                            + limit
                            + " OFFSET "
                            + offset*/
                            + " } "
                            + optional
                            + "} ";
        }
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "point");
//        JSONArray resultList = new JSONArray();
//        while (resultSet.hasNext()) {
//            BindingSet querySolution = resultSet.next();
//            resultList.add(((Literal) querySolution.getBinding("coordinates").getValue())
//                    .getLabel()
//                    .replace("Point(", "")
//                    .replace(")", "")
//                    .replace(" ", ","));
//        }
//        JSONObject result = new JSONObject();
//        result.put("list", resultList);
//        if (granularityRegion != null) {
//            result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
//        } else if (country != null && region == null) {
//            result.put("geoJson", facetController.nutsRegion.get(country).geoJson);
//        } else if (country != null && region != null) {
//            result.put("geoJson", facetController.nutsRegion.get(region).geoJson);
//        } else {
//            result.put("geoJson", "");
//        }
        HashMap<String, Boolean> unique_highlighted = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();

            String coordinates = ((Literal) querySolution.getBinding("coordinates").getValue())
                    .getLabel()
                    .replace("Point(", "")
                    .replace(")", "")
                    .replace(" ", ",");
            if (querySolution.getBinding("infoRegioID") != null)
                unique_highlighted.put(coordinates, true);
            else if (!unique_highlighted.containsKey(coordinates))
                unique_highlighted.put(coordinates, false);
        }
        JSONArray resultList = new JSONArray();
        for (String coordinates : unique_highlighted.keySet()) {
            JSONObject point = new JSONObject();
            point.put("coordinates", coordinates);
            point.put("isHighlighted", unique_highlighted.get(coordinates));
            resultList.add(point);
        }
        if (cci != null) {
            String queryProgramNuts = "SELECT DISTINCT ?country ?nuts WHERE { "
                    + " ?prg <https://linkedopendata.eu/prop/direct/P1367>  ?cci. "
                    + " FILTER(?cci IN ( ";
            for (String c : cci) {
                queryProgramNuts += "\"" + c + "\",";
            }
            queryProgramNuts = queryProgramNuts.substring(0, queryProgramNuts.length() - 1);
            queryProgramNuts += ")).";
            queryProgramNuts += " ?prg <https://linkedopendata.eu/prop/direct/P32> ?country."
                    + " ?prg <https://linkedopendata.eu/prop/direct/P2316> ?nuts."
                    + "}";

            TupleQueryResult resultSetProgramNuts = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, queryProgramNuts, timeout, "point");
            List<String> programCountry = new ArrayList<>();
            List<String> programNuts = new ArrayList<>();
            while (resultSetProgramNuts.hasNext()) {
                BindingSet querySolution = resultSetProgramNuts.next();
                if (!programCountry.contains(querySolution.getBinding("country").getValue().stringValue())) {
                    programCountry.add(querySolution.getBinding("country").getValue().stringValue());
                }
                if (!programNuts.contains(querySolution.getBinding("nuts").getValue().stringValue())) {
                    programNuts.add(querySolution.getBinding("nuts").getValue().stringValue());
                }
            }
            if (programNuts.size() == 1) {
                granularityRegion = programNuts.get(0);
            } else if (programCountry.size() == 1) {
                granularityRegion = programCountry.get(0);
            } else {
                granularityRegion = "https://linkedopendata.eu/entity/Q1";
            }
//            granularityRegion = getSmallestCommonNuts(programNuts);
//            System.err.println(granularityRegion);
//            System.err.println(programNuts);
//            System.err.println(programCountry);
//            System.err.println(findUpperRegions(granularityRegion, language));
        }
        if (granularityRegion == null) {
            granularityRegion = "https://linkedopendata.eu/entity/Q1";
        }
        JSONObject result = new JSONObject();
        result.put("list", resultList);
        result.put("upperRegions", findUpperRegions(granularityRegion, language));
        result.put("region", granularityRegion);

        if (facetController.nutsRegion.get(granularityRegion).name.containsKey(language)) {
            result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));
        } else {
            result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get("en"));
        }
        if (granularityRegion != null) {
            result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
        } else if (country != null && region == null) {
            result.put("geoJson", facetController.nutsRegion.get(country).geoJson);
        } else if (country != null && region != null) {
            result.put("geoJson", facetController.nutsRegion.get(region).geoJson);
        } else {
            result.put("geoJson", "");
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private String getSmallestCommonNuts(List<String> nuts) {
        if (nuts.size() == 0) {
            return null;
        }
        if (nuts.size() == 1) {
            return nuts.get(0);
        }
        HashMap<String, List<String>> upperNuts = new HashMap<>();
        nuts.forEach(s1 -> {
            upperNuts.put(s1, new ArrayList<>());
        });
        facetController.nutsRegion.forEach((s, nut) -> {
            nuts.forEach(s1 -> {
                if (nut.narrower.contains(s1)) {
                    if (!upperNuts.get(s1).contains(s)) {
                        upperNuts.get(s1).add(s);
                    }
                }
            });
        });
        boolean found = true;
        List<String> newNuts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : upperNuts.entrySet()) {
            entry.getValue().forEach(s -> {
                if (!newNuts.contains(s)) {
                    newNuts.add(s);
                }
            });

//            found &= entry.getValue().size() == 1;
        }
        return getSmallestCommonNuts(newNuts);
    }

    @GetMapping(value = "/facet/eu/search/project/map/point", produces = "application/json")
    public ResponseEntity euSearchProjectMapPoint(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "keywords", required = false) String keywords, //
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "categoryOfIntervention", required = false) List<String> categoryOfIntervention,
            @RequestParam(value = "policyObjective", required = false) String policyObjective,
            @RequestParam(value = "budgetBiggerThan", required = false) Long budgetBiggerThen,
            @RequestParam(value = "budgetSmallerThan", required = false) Long budgetSmallerThen,
            @RequestParam(value = "budgetEUBiggerThan", required = false) Long budgetEUBiggerThen,
            @RequestParam(value = "budgetEUSmallerThan", required = false) Long budgetEUSmallerThen,
            @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
            @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
            @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
            @RequestParam(value = "endDateAfter", required = false) String endDateAfter,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "granularityRegion", required = false) String granularityRegion,
            @RequestParam(value = "limit", required = false, defaultValue = "1000") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "coordinate", required = true) String coordinate,
            @RequestParam(value = "interreg", required = false) Boolean interreg,
            @RequestParam(value = "highlighted", required = false) Boolean highlighted,
            @RequestParam(value = "cci", required = false) List<String> cci,
            @RequestParam(value = "kohesioCategory", required = false) String kohesioCategory,
            @RequestParam(value = "projectTypes", required = false) List<String> projectTypes,
            @RequestParam(value = "priority_axis", required = false) String priorityAxis,
            @RequestParam(value = "timeout", required = false) BoundingBox boundingBox,
            Principal principal
    ) throws Exception {
        logger.info("Search project map point: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, granularityRegion);
        facetController.initialize(language);

        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords, language);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }

        String search = filtersGenerator.filterProject(
                expandedQueryText,
                language,
                country,
                theme,
                fund,
                program,
                categoryOfIntervention,
                policyObjective,
                budgetBiggerThen,
                budgetSmallerThen,
                budgetEUBiggerThen,
                budgetEUSmallerThen,
                startDateBefore,
                startDateAfter,
                endDateBefore,
                endDateAfter,
                latitude,
                longitude,
                null,
                region,
                granularityRegion,
                interreg,
                highlighted,
                cci,
                kohesioCategory,
                projectTypes,
                priorityAxis,
                boundingBox,
                limit,
                offset
        );
        String limitS = "";
        if (limit != null)
            limitS = "LIMIT " + limit;
        search += " ?s0 <https://linkedopendata.eu/prop/direct/P127> \"Point(" + coordinate.replace(",", " ") + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral> . ";
        String query =
                "SELECT DISTINCT ?s0 ?label ?curatedLabel ?infoRegioID WHERE { "
                        + " { SELECT ?s0 where { "
                        + search
                        + " } "
                        + limitS
                        + " } "
                        + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. " + " FILTER((LANG(?label)) = \"" + language + "\") } ."
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P581563> ?curatedLabel. " + " FILTER((LANG(?curatedLabel)) = \"" + language + "\") } ."
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . } "
                        + "} ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30, "point");

        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();

            JSONObject item = new JSONObject();
            item.put("item", querySolution.getBinding("s0").getValue().stringValue());
            if (querySolution.getBinding("curatedLabel") != null) {
                item.put("label", ((Literal) querySolution.getBinding("curatedLabel").getValue()).getLabel());
                if (querySolution.getBinding("label") != null) {
                    item.put("originalLabel", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
                } else {
                    item.put("originalLabel", null);
                }
            } else if (querySolution.getBinding("label") != null) {
                item.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
                item.put("originalLabel", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
            } else {
                item.put("label", null);
                item.put("originalLabel", null);
            }
            if (querySolution.getBinding("infoRegioID") != null) {
                item.put("isHighlighted", true);
            } else {
                item.put("isHighlighted", false);
            }
            if ((boolean) item.get("isHighlighted")) {
                result.add(0, item);
            } else {
                result.add(item);
            }
        }
        return new ResponseEntity<JSONArray>((JSONArray) result, HttpStatus.OK);
    }

    @GetMapping(value = "/facet/eu/project/region", produces = "application/json")
    public NutsRegion euIdCoordinates( //
                                       @RequestParam(value = "id") String id,
                                       @RequestParam(value = "language", defaultValue = "en") String language)
            throws Exception {
        logger.info("Get coordinates by ID : id {}, language {}", id, language);
        String query =
                "select ?s0 ?coordinates where { "
                        + " VALUES ?s0 { <"
                        + id
                        + "> } "

                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } }";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2, "facet");

        String coo = "";
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();

            if (querySolution.getBinding("coordinates") != null) {
                coo = ((Literal) querySolution.getBinding("coordinates").getValue()).stringValue();
            }
        }
        query =
                "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                        + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                        + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                        + "SELECT ?id ?label ?geoJson ?label1 ?id1 ?label2 ?id2 ?label3 ?id3  WHERE { "
                        + "?s rdf:type <http://nuts.de/NUTS3> . "
                        + "?s <http://nuts.de/geometry> ?o . "
                        + " FILTER (geof:sfWithin(\"" + coo + "\"^^geo:wktLiteral,?o)) "
                        + "?s <http://nuts.de/geoJson> ?geoJson . "
                        + "?s rdfs:label ?label . "
                        + "?s <http://nuts.de/id> ?id . "
                        + "OPTIONAL {?s <http://nuts.de/contained> ?contained1 . "
                        + "          ?contained1 rdfs:label ?label1 .  "
                        + "          ?contained1 <http://nuts.de/id> ?id1 . "
                        + "           OPTIONAL {?contained1 <http://nuts.de/contained> ?contained2 . "
                        + "          ?contained2 rdfs:label ?label2 .  "
                        + "          ?contained2 <http://nuts.de/id> ?id2 . "
                        + "           OPTIONAL {?contained2 <http://nuts.de/contained> ?contained3 . "
                        + "          ?contained3 rdfs:label ?label3 . "
                        + "          ?contained3 <http://nuts.de/id> ?id3 . }}} "
                        + "}";
        resultSet = sparqlQueryService.executeAndCacheQuery(getSparqlEndpointNuts, query, 5, "facet");

        NutsRegion nutsRegion = new NutsRegion();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            if (querySolution.getBinding("id") != null) {
                nutsRegion.setId(((Literal) querySolution.getBinding("id").getValue()).stringValue());
            }
            if (querySolution.getBinding("label") != null) {
                nutsRegion.setLabel(((Literal) querySolution.getBinding("label").getValue()).stringValue());
            }
            if (querySolution.getBinding("label1") != null) {
                nutsRegion.setLabelUpper1(((Literal) querySolution.getBinding("label1").getValue()).stringValue());
            }
            if (querySolution.getBinding("id1") != null) {
                nutsRegion.setIdUpper1(((Literal) querySolution.getBinding("id1").getValue()).stringValue());
            }
            if (querySolution.getBinding("label2") != null) {
                nutsRegion.setLabelUpper2(((Literal) querySolution.getBinding("label2").getValue()).stringValue());
            }
            if (querySolution.getBinding("id2") != null) {
                nutsRegion.setIdUpper2(((Literal) querySolution.getBinding("id2").getValue()).stringValue());
            }
            if (querySolution.getBinding("label3") != null) {
                nutsRegion.setLabelUpper3(((Literal) querySolution.getBinding("label3").getValue()).stringValue());
            }
            if (querySolution.getBinding("id3") != null) {
                nutsRegion.setIdUpper3(((Literal) querySolution.getBinding("id3").getValue()).stringValue());
            }
            if (querySolution.getBinding("geoJson") != null) {
                nutsRegion.setGeoJson(((Literal) querySolution.getBinding("geoJson").getValue()).stringValue());
            }
        }
        return nutsRegion;
    }

    @GetMapping(value = "/facet/eu/map/nearby", produces = "application/json")
    public ResponseEntity<JSONObject> geoIp(HttpServletRequest request) throws Exception {
        logger.info("Find coordinates of given IP");
        String ip = httpReqRespUtils.getClientIpAddressIfServletRequestExist(request);
        GeoIp.Coordinates coordinates2 = geoIp.compute(ip);
        ResponseEntity<JSONObject> result = euSearchProjectMap(
                "en", null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                coordinates2.getLatitude(), coordinates2.getLongitude(),
                null, null,
                null, 2000,
                0, null,
                null, null,
                null, null, null,
                null, null, null,
                400, null
        );
        JSONObject mod = result.getBody();
        mod.put("coordinates", coordinates2.getLatitude() + "," + coordinates2.getLongitude());
        return new ResponseEntity<JSONObject>((JSONObject) mod, HttpStatus.OK);
    }


    private JSONArray findUpperRegions(String region, String lang) {
        JSONArray upperRegions = new JSONArray();
        JSONObject upperRegion;

        do {
            upperRegion = findUpperRegion(region, lang);
            if (upperRegion != null) {
                upperRegions.add(upperRegion);
                region = (String) upperRegion.get("region");
            }
        } while (!"https://linkedopendata.eu/entity/Q1".equals(region) && upperRegion != null);
        return upperRegions;
    }

    private JSONObject findUpperRegion(String region, String lang) {
        for (String key : facetController.nutsRegion.keySet()) {
            Nut n = facetController.nutsRegion.get(key);
            if (n.narrower.contains(region)) {
                String query = "ASK { <" + n.uri + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2727537> . }";
                boolean resultSet = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, query, 20);
                if (!resultSet) {
                    JSONObject o = new JSONObject();
                    o.put("region", n.uri);
                    if (n.name.containsKey(lang)) {
                        o.put("regionLabel", n.name.get(lang));
                    } else {
                        o.put("regionLabel", n.name.get("en"));
                    }
                    return o;
                }
            }
        }
        return null;
    }
}
