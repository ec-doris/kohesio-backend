package eu.ec.doris.kohesio.controller;

import eu.ec.doris.kohesio.geoIp.GeoIp;
import eu.ec.doris.kohesio.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.payload.Nut;
import eu.ec.doris.kohesio.payload.NutsRegion;
import eu.ec.doris.kohesio.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.services.ExpandedQuery;
import eu.ec.doris.kohesio.services.FiltersGenerator;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import eu.ec.doris.kohesio.services.SimilarityService;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")

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
            @RequestParam(value = "categoryOfIntervention", required = false)
                    String categoryOfIntervention,
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
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            Integer timeout,
            Principal principal)
            throws Exception {
        logger.info("Search Projects on map: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, region, limit, offset, granularityRegion);
        facetController.initialize(language);
        if (timeout == null) {
            timeout = 70;
        }

        //simplify the query
        String c = country;
        if (granularityRegion != null) {
            c = null;
        }
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if(keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }

        String search = filtersGenerator.filterProject(expandedQueryText, c, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, granularityRegion, limit, offset);
        //computing the number of results
        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + search + "} ";
        int numResults = 0;
        if (limit == null || limit > 2000) {
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout);

            if (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
            }
        }
        logger.debug("Number of results {}", numResults);
        if (numResults <= 2000 || (granularityRegion != null && facetController.nutsRegion.get(granularityRegion).narrower.size() == 0)) {
            return mapReturnCoordinates(search, country, region, granularityRegion, latitude, longitude, limit, offset, timeout);
        } else {
            if (granularityRegion == null) {
                granularityRegion = "https://linkedopendata.eu/entity/Q1";
                query =
                        "SELECT ?region (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                                + search
                                + " ?s0 <https://linkedopendata.eu/prop/direct/P32> ?region . "
                                + " } GROUP BY ?region ";
            } else {
                query =
                        "SELECT ?region (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                                + search
                                + " ?s0 <https://linkedopendata.eu/prop/direct/P1845> ?region . "
                                + " } GROUP BY ?region ";
            }
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout);


            HashMap<String, JSONObject> subRegions = new HashMap();
            for (String r : facetController.nutsRegion.get(granularityRegion).narrower) {
                JSONObject element = new JSONObject();
                element.put("region", r);
                element.put("regionLabel", facetController.nutsRegion.get(r).name);
                element.put("geoJson", facetController.nutsRegion.get(r).geoJson);
                element.put("count", 0);
                subRegions.put(r, element);
            }

            boolean foundNextNutsLevel = false;
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                if (subRegions.containsKey(querySolution.getBinding("region").getValue().stringValue())) {
                    JSONObject element = subRegions.get(querySolution.getBinding("region").getValue().stringValue());
                    element.put("count", ((Literal) querySolution.getBinding("c").getValue()).intValue());
                    if (((Literal) querySolution.getBinding("c").getValue()).intValue()!=0){
                        foundNextNutsLevel = true;
                    }
                    subRegions.put(querySolution.getBinding("region").getValue().stringValue(), element);
                }
            }
            // this happens when we have for example nuts 1 information but not nuts 2 information for the projects
            if (foundNextNutsLevel == false){
                return mapReturnCoordinates(search, country, region, granularityRegion, latitude, longitude,limit, offset, timeout);
            }

            JSONArray resultList = new JSONArray();
            for (String key : subRegions.keySet()) {
                resultList.add(subRegions.get(key));
            }

            JSONObject result = new JSONObject();
            result.put("region", granularityRegion);
            result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name);
            result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
            result.put("subregions", resultList);

            return new ResponseEntity<JSONObject>(result, HttpStatus.OK);
        }
    }

    ResponseEntity<JSONObject> mapReturnCoordinates(String search, String country, String region, String granularityRegion, String latitude, String longitude,Integer limit, Integer offset, int timeout) throws Exception {
        logger.debug("granularityRegion {}, limit {}",granularityRegion,limit);
        String optional = " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. ";
        // not performing
        if (granularityRegion != null) {
            optional += " ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry> ?o . ";
            //check if granularity region is a country, if yes the filter is not needed
            boolean isCountry = false;
            for (Object jsonObject : facetController.facetEuCountries("en")) {
                JSONObject o = (JSONObject) jsonObject;
                if (granularityRegion.equals(o.get("instance"))) {
                    isCountry = true;
                }
            }
            // this is a hack to show brittany
            if (isCountry == false && !granularityRegion.equals("https://linkedopendata.eu/entity/Q3487")) {
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
                            + "} ";
        }
        else {
            query =
                    "SELECT DISTINCT ?coordinates ?infoRegioID WHERE { "
                            + " { SELECT ?s0 WHERE { "
                            + search
                            + " } "/*LIMIT "
                            + limit
                            + " OFFSET "
                            + offset*/
                            + " } "
                            + optional
                            + "} ";
        }
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout);

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

        HashMap<String,Boolean> unique_highlighted = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String coordinates = ((Literal) querySolution.getBinding("coordinates").getValue())
                    .getLabel()
                    .replace("Point(", "")
                    .replace(")", "")
                    .replace(" ", ",");
            if(querySolution.getBinding("infoRegioID") != null)
                unique_highlighted.put(coordinates, true);
            else if (!unique_highlighted.containsKey(coordinates))
                unique_highlighted.put(coordinates, false);
        }
        JSONArray resultList = new JSONArray();
        for (String coordinates : unique_highlighted.keySet()){
            JSONObject point = new JSONObject();
            point.put("coordinates",coordinates);
            point.put("isHighlighted",unique_highlighted.get(coordinates));
            resultList.add(point);
        }

        JSONObject result = new JSONObject();
        result.put("list", resultList);
        if (granularityRegion != null) {
            result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
        } else if (country != null && region == null) {
            result.put("geoJson", facetController.nutsRegion.get(country).geoJson);
        } else if (country != null && region != null) {
            result.put("geoJson", facetController.nutsRegion.get(region).geoJson);
        } else {
            result.put("geoJson", "");
        }
        return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
    }

    @GetMapping(value = "/facet/eu/search/project/map/point", produces = "application/json")
    public ResponseEntity euSearchProjectMapPoint(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "keywords", required = false) String keywords, //
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "categoryOfIntervention", required = false)
                    String categoryOfIntervention,
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
            Principal principal)
            throws Exception {
        logger.info("Search project map point: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, granularityRegion);
        facetController.initialize(language);

        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if(keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }

        String search = filtersGenerator.filterProject(
                expandedQueryText,
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
                region,
                granularityRegion,
                limit,
                offset
        );
        String limitS = "";
        if (limit != null)
            limitS = "LIMIT " + limit;
        search += " ?s0 <https://linkedopendata.eu/prop/direct/P127> \"Point(" + coordinate.replace(",", " ") + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral> . ";
        String query =
                "SELECT DISTINCT ?s0 ?label ?infoRegioID WHERE { "
                        + " { SELECT ?s0 where { "
                        + search
                        + " } "
                        + limitS
                        + " } "
                        + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                        + "             FILTER((LANG(?label)) = \""
                        + language
                        + "\") } ."
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . } "
                        + "} ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30);

        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();

            JSONObject item = new JSONObject();
            item.put("item", querySolution.getBinding("s0").getValue().stringValue());
            if (querySolution.getBinding("label") != null) {
                item.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
            }
            if (querySolution.getBinding("infoRegioID") != null) {
                item.put("isHighlighted", true);
            } else {
                item.put("isHighlighted", false);
            }
            if ((boolean)item.get("isHighlighted")){
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
        logger.info("Get coordinates by ID : id {}, language {}",id, language);
        String query =
                "select ?s0 ?coordinates where { "
                        + " VALUES ?s0 { <"
                        + id
                        + "> } "

                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } }";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);

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
        resultSet = sparqlQueryService.executeAndCacheQuery(getSparqlEndpointNuts, query, 5);

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
        ResponseEntity<JSONObject> result = euSearchProjectMap("en", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, coordinates2.getLatitude(), coordinates2.getLongitude(), null, null, 2000, 0, 400, null);
        JSONObject mod = result.getBody();
        mod.put("coordinates", coordinates2.getLatitude() + "," + coordinates2.getLongitude());
        return new ResponseEntity<JSONObject>((JSONObject) mod, HttpStatus.OK);
    }

//    private String filterProject(String keywords, String country, String theme, String fund, String program, String categoryOfIntervention,
//                                 String policyObjective, Integer budgetBiggerThen, Integer budgetSmallerThen, Integer budgetEUBiggerThen, Integer budgetEUSmallerThen, String startDateBefore, String startDateAfter,
//                                 String endDateBefore,
//                                 String endDateAfter,
//                                 String latitude,
//                                 String longitude,
//                                 String region,
//                                 String granularityRegion,
//                                 Integer limit,
//                                 Integer offset) throws IOException {
//        String search = "";
//        if (keywords != null) {
//            if (!keywords.contains("AND") && !keywords.contains("OR") && !keywords.contains("NOT")) {
//                String[] words = keywords.split(" ");
//                StringBuilder keywordsBuilder = new StringBuilder();
//                for (int i = 0; i < words.length - 1; i++) {
//                    keywordsBuilder.append(words[i]).append(" AND ");
//                }
//                keywordsBuilder.append(words[words.length - 1]);
//                keywords = keywordsBuilder.toString();
//            }
//            search +=
//                    "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
//                            + "<http://www.openrdf.org/contrib/lucenesail#query> \""
//                            + keywords.replace("\"", "\\\"")
//                            + "\" ] .";
//        }
//
//        if (country != null && region == null) {
//            search += "?s0 <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
//        }
//
//        if (theme != null) {
//            search +=
//                    "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
//                            + "?category <https://linkedopendata.eu/prop/direct/P1848> <"
//                            + theme
//                            + "> . ";
//        }
//
//        if (policyObjective != null) {
//            search +=
//                    "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
//                            + "?category <https://linkedopendata.eu/prop/direct/P1849> <"
//                            + policyObjective
//                            + "> . ";
//        }
//
//        if (fund != null) {
//            search += "?s0 <https://linkedopendata.eu/prop/direct/P1584> <" + fund + "> . ";
//        }
//
//        if (program != null) {
//            search += "?s0 <https://linkedopendata.eu/prop/direct/P1368> <" + program + "> . ";
//        }
//
//        if (categoryOfIntervention != null) {
//            search +=
//                    "?s0 <https://linkedopendata.eu/prop/direct/P888> <" + categoryOfIntervention + "> . ";
//        }
//
//        if (budgetBiggerThen != null) {
//            search +=
//                    " ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget . "
//                            + "FILTER( ?budget > "
//                            + budgetBiggerThen
//                            + ")";
//        }
//
//        if (budgetSmallerThen != null || budgetBiggerThen != null) {
//            search += " ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget . ";
//            if (budgetBiggerThen != null) {
//                search += "FILTER( ?budget > " + budgetBiggerThen + ")";
//            }
//            if (budgetSmallerThen != null) {
//                search += "FILTER( ?budget < " + budgetSmallerThen + ")";
//            }
//        }
//
//        if (budgetEUBiggerThen != null || budgetEUSmallerThen != null) {
//            search += " ?s0 <https://linkedopendata.eu/prop/direct/P835> ?budgetEU . ";
//            if (budgetEUBiggerThen != null) {
//                search += "FILTER( ?budgetEU > " + budgetEUBiggerThen + ")";
//            }
//            if (budgetEUSmallerThen != null) {
//                search += "FILTER( ?budgetEU < " + budgetEUSmallerThen + ")";
//            }
//        }
//
//        if (startDateBefore != null || startDateAfter != null) {
//            search += " ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startDate . ";
//            if (startDateBefore != null) {
//                search +=
//                        "FILTER( ?startDate <= \""
//                                + startDateBefore
//                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
//            }
//            if (startDateAfter != null) {
//                search +=
//                        "FILTER( ?startDate >= \""
//                                + startDateAfter
//                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
//            }
//        }
//
//        if (endDateBefore != null || endDateAfter != null) {
//            search += " ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endDate . ";
//            if (endDateBefore != null) {
//                search +=
//                        "FILTER( ?endDate <= \""
//                                + endDateBefore
//                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
//            }
//            if (endDateAfter != null) {
//                search +=
//                        "FILTER( ?endDate >= \""
//                                + endDateAfter
//                                + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)";
//            }
//        }
//
//        if (region != null) {
//            search += "?s0 <https://linkedopendata.eu/prop/direct/P1845>* <" + region + "> . ";
//        }
//
//        if (granularityRegion != null) {
//            search += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + granularityRegion + "> . ";
//        }
//
//        if (latitude != null && longitude != null) {
//            search +=
//                    "?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
//                            + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
//                            + longitude
//                            + " "
//                            + latitude
//                            + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)< 100000) . ";
//        }
//
//        search +=
//                "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";
//        return search;
//    }

}
