package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeo.javasupercluster.SuperCluster;
import eu.ec.doris.kohesio.geoIp.GeoIp;
import eu.ec.doris.kohesio.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.payload.BoundingBox;
import eu.ec.doris.kohesio.payload.Nut;
import eu.ec.doris.kohesio.payload.NutsRegion;
import eu.ec.doris.kohesio.payload.Zone;
import eu.ec.doris.kohesio.services.*;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.wololo.geojson.Feature;
import org.wololo.geojson.GeoJSON;
import org.wololo.geojson.Point;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

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

    @Autowired
    ClusterService clusterService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeoJSONReader geoJSONReader = new GeoJSONReader();
    private static final WKTReader wktReader = new WKTReader();

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
            @RequestParam(value = "zoom", required = false) Integer zoom,
            Integer timeout,
            Principal principal
    ) throws Exception {
        logger.info("Search Projects on map: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} region {} limit {} offset {} granularityRegion {}, lat {} long {} timeout {} interreg {} boundingBox {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, region, limit, offset, granularityRegion, latitude, longitude, timeout, interreg, boundingBoxString);
        facetController.initialize(language);
        if (timeout == null) {
            timeout = 300;
        }
        BoundingBox boundingBox = null;
        if (boundingBoxString != null) {
            boundingBox = BoundingBox.createFromString(boundingBoxString);
//            boundingBox = objectMapper.readValue(boundingBoxString, BoundingBox.class);
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
        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + search;// + "} ";
        if (boundingBox != null) {
            // TODO: this is a tmp fix because it look like the lucene index is not working properly
            query += " FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(" + boundingBox.toLiteral() + ",?coordinates))";
            // End TODO
            int numberTotal = 0;
            ResponseEntity<JSONObject> tmp = getCoordinatesByGeographicSubdivision(
                    boundingBox,
                    zoom,
                    search,
                    language,
                    !(country != null && granularityRegion != null),
                    40
            );
            for (Object o : (JSONArray) tmp.getBody().get("subregions")) {
                numberTotal += (int) ((JSONObject) o).get("count");
            }
            int maxNumberOfprojectBeforeGoingToSubRegion = 10000;
            int mimNumberOfprojectBeforeGoingToSubRegion = 100;
            logger.info("found {} projects", ((JSONArray) tmp.getBody().get("subregions")).size());
            if (zoom >= 9 || numberTotal < maxNumberOfprojectBeforeGoingToSubRegion || ((JSONArray) tmp.getBody().get("subregions")).size() <= 1) {
                logger.info("Number of projects in the bounding box: {}", numberTotal);
                if (numberTotal > mimNumberOfprojectBeforeGoingToSubRegion) {
                    // check if gran
                    if (granularityRegion != null && !granularityRegion.equals("https://linkedopendata.eu/entity/Q1")) {
                        logger.info(facetController.nutsRegion.get(granularityRegion).geoJson);
                        Geometry geometry = geoJSONReader.read(facetController.nutsRegion.get(granularityRegion).geoJson.replace("'", "\""));
                        logger.info("{}\n{}", boundingBox.toGeometry(), geometry);
                        if (!boundingBox.toGeometry().contains(geometry)) {
                            boundingBox = new BoundingBox(geometry.getEnvelopeInternal());
                            logger.info("changing bbox to fit the region ask {}", boundingBox);
                        } else {
                            logger.info("keeping bbox");
                        }
                    }
                    List<Feature> features = getProjectsPoints(
                            language, search, boundingBox, limit, offset, timeout
                    );
                    List<Feature> clusters = prepareCluster(features, boundingBox, zoom);
                    logger.info("cluster: {} \nfound: {} projects \nwith {}", clusters.size(), features.size(), search);
                    return createResponse(clusters, zoom, search, language, granularityRegion);
                }
                return mapReturnCoordinates(
                        language,
                        search,
                        country,
                        region,
                        granularityRegion,
                        latitude,
                        longitude,
                        cci,
                        boundingBox,
                        limit,
                        offset,
                        timeout
                );
            }
            return tmp;
        }
        query += "}";
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
        int maxProject = 2000;
        if (!hasLowerGranularity || numResults <= maxProject || hasCoordinates || isGreekNuts2) {
            return mapReturnCoordinates(
                    language,
                    search,
                    country,
                    region,
                    granularityRegion,
                    latitude,
                    longitude,
                    cci,
                    boundingBox,
                    limit,
                    offset,
                    timeout
            );
        } else {
            // remove the bounding box filter and coordinate triple for search
            search = search.replaceAll(
                    "FILTER\\(<http://www\\.opengis\\.net/def/function/geosparql/ehContains>\\(.*\\)",
                    ""
            );
            search = search.replace("?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates .", "");

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
            boolean isStat = false;
            if (!"<https://linkedopendata.eu/entity/Q1>".equals(uri)) {
                isStat = sparqlQueryService.executeBooleanQuery(
                        sparqlEndpoint,
                        "ASK { <" + uri + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2727537> . }",
                        false,
                        20
                );
            }
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

    ResponseEntity<JSONObject> mapReturnCoordinates(String language, String search, String country, String region, String granularityRegion, String latitude, String longitude, List<String> cci, BoundingBox boundingBox, Integer limit, Integer offset, int timeout) throws Exception {
        logger.debug("granularityRegion {}, limit {}, cci {}", granularityRegion, limit, cci);
        String optional = " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. ";
        // not performing
        if (granularityRegion != null) {
            if ("country".equals(facetController.nutsRegion.get(granularityRegion).granularity)) {
                optional += " OPTIONAL {SELECT DISTINCT ?o WHERE { ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry20M> ?o . }} ";

            } else {
                optional += " OPTIONAL {SELECT DISTINCT ?o WHERE { ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry> ?o . }} ";
            }
            // check if granularity region is a country, if yes the filter is not needed
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
        } else if (boundingBox != null) {
            query = "SELECT DISTINCT ?coordinates ?infoRegioID WHERE { "
                    + search
//                    + " FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + boundingBox.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>, ?coordinates)) "
                    + " FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + boundingBox.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates))"
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . }"
                    + "}";
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
            @RequestParam(value = "boundingBox", required = false) String boundingBoxString,
            @RequestParam(value = "zoom", required = false) Integer zoom,
            Integer timeout,
            Principal principal
    ) throws Exception {
        logger.info("Search project map point: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, granularityRegion);
        facetController.initialize(language);

        if (timeout == null) {
            timeout = 300;
        }
        BoundingBox boundingBox = null;
        if (boundingBoxString != null) {
//            boundingBox = objectMapper.readValue(boundingBoxString, BoundingBox.class);
            boundingBox = BoundingBox.createFromString(boundingBoxString);
        }

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

        if (boundingBox != null) {
            eu.ec.doris.kohesio.payload.Coordinate coords = new eu.ec.doris.kohesio.payload.Coordinate(coordinate);
            return new ResponseEntity<>(mapPointBbox(language, search, boundingBox, limit, offset, coords, zoom, timeout), HttpStatus.OK);
        }

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
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private JSONArray mapPointBbox(
            String language,
            String search,
            BoundingBox boundingBox,
            Integer limit,
            Integer offset,
            eu.ec.doris.kohesio.payload.Coordinate coordinate,
            int zoom,
            int timeout
    ) throws Exception {
        List<Feature> features = clusterService.getPointsInCluster(
                getProjectsPoints(
                        language,
                        search,
                        boundingBox,
                        limit,
                        offset,
                        timeout
                ),
                coordinate,
                boundingBox,
                zoom
        );

        Set<String> projectUris = new HashSet<>();
        for (Feature proj : features) {
            projectUris.addAll((List<String>) proj.getProperties().get("projects"));
        }
        List<String> urisList = new ArrayList<>(projectUris);
        logger.info("retrieving info for {} project(s) from {} coordinates at cluster coordinate {}", projectUris.size(), features.size(), coordinate.toLiteral());
        JSONArray results = new JSONArray();
        int step = 1000;
        for (int i = 0; i < urisList.size(); i += step) {
            String query = "SELECT DISTINCT ?s0 ?label ?curatedLabel ?infoRegioID WHERE { "
                    + "VALUES ?s0 { <"
                    + String.join("> <", urisList.subList(i, Math.min(urisList.size(), i + step)))
                    + "> }"
                    + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER((LANG(?label)) = \""
                    + language
                    + "\") } "
                    + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P581563> ?curatedLabel. FILTER((LANG(?curatedLabel)) = \""
                    + language
                    + "\") } "
                    + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . } "
                    + "}";
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "point");
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();

                HashMap<String, Object> item = new HashMap<>();

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
                JSONObject r = new JSONObject(item);
//                logger.info("i = {}, {}, {}", i, urisList.size(), results.size());
                if ((boolean) item.get("isHighlighted")) {
                    results.add(0, r);
                } else {
                    results.add(r);
                }
            }
        }
        return results;
    }


    @GetMapping(value = "/facet/eu/project/region", produces = "application/json")
    public NutsRegion euIdCoordinates(
            @RequestParam(value = "id") String id,
            @RequestParam(value = "language", defaultValue = "en") String language
    ) throws Exception {
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
                null, 500,
                0, null,
                null, null,
                null, null, null,
                null, null, null,
                null, 400, null
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

    private ResponseEntity<JSONObject> getCoordinatesByGeographicSubdivision(BoundingBox bbox, int zoom, String search, String language, boolean forceBaseCountry, int timeout) throws Exception {

        // Get Country in bbox
        String withinCountry = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS0>. "
//                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

//        String intersectCountry = "SELECT * WHERE {"
//                + " ?s <http://nuts.de/linkedopendata> ?lid; "
//                + " <http://nuts.de/geometry> ?geo; "
//                + " a <http://nuts.de/NUTS1>. "
//                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
//                + "} ";

        // Get NUTS 1 in bbox
        String withinNuts1 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS1>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        String intersectNuts1 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS1>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        // Get NUTS 2 in bbox
        String withinNuts2 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS2>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        String intersectNuts2 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS2>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        // Get NUTS 3 in bbox
        String withinNuts3 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS3>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        String intersectNuts3 = "SELECT * WHERE {"
                + " ?s <http://nuts.de/linkedopendata> ?lid; "
                + " <http://nuts.de/geometry> ?geo; "
                + " a <http://nuts.de/NUTS3>. "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        // Get LAU in bbox
        String intersectLAU = "SELECT * WHERE {"
                + " ?s <http://laus.de/linkedopendata> ?lid; "
                + " <http://laus.de/geometry> ?geo; "
                + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                + "} ";

        if (zoom <= 4 && forceBaseCountry) {
            return createResponse(getZoneByQuery(withinCountry, "COUNTRY", timeout), search, language, timeout);
        }
        if (!getZoneByQuery(withinNuts1, "NUTS1", timeout).isEmpty()) {
            return createResponse(getZoneByQuery(intersectNuts1, "NUTS1", timeout), search, language, timeout);
        }
        if (!getZoneByQuery(withinNuts2, "NUTS2", timeout).isEmpty()) {
            return createResponse(getZoneByQuery(intersectNuts2, "NUTS2", timeout), search, language, timeout);
        }
//        if (!getZoneByQuery(withinNuts3, "NUTS3", timeout).isEmpty()) {
        return createResponse(getZoneByQuery(intersectNuts3, "NUTS3", timeout), search, language, timeout);
//        }
//        return createResponse(getZoneByQuery(intersectLAU, "LAU", timeout), search, language);


    }

    private ResponseEntity<JSONObject> createResponse(HashMap<String, Zone> res, String search, String language, int timeout) throws Exception {
//        logger.info("WE ARE HERE {} | {} ", search, language);
        String granularityRegion = "https://linkedopendata.eu/entity/Q1";
        HashMap<String, Object> result = new HashMap<>();

        String tmpsearch = search.replaceAll(
                "FILTER\\(<http://www\\.opengis\\.net/def/function/geosparql/ehContains>\\(.*\\)",
                ""
        );
        JSONArray resultList = new JSONArray();
//        ArrayList<JSONObject> resultList = new ArrayList<>();
        // count the number of project in each zone
        Instant start = Instant.now();
        for (Zone z : res.values()) {

            z.queryNumberProjects(sparqlQueryService, sparqlEndpoint, tmpsearch, 30);
            if (z.getNumberProjects() == 0) {
                continue;
            }

            HashMap<String, Object> element = new HashMap<>();

            if (!"LAU".equals(z.getType())) {
                if (facetController.nutsRegion.containsKey(z.getLid())) {
                    element.put("regionLabel", facetController.nutsRegion.get(z.getLid()).name.get(language));
                } else {
                    element.put("regionLabel", "");
                }
            } else {
                element.put("regionLabel", "");
            }
            element.put("region", z.getLid());
//            element.put("geoJson", facetController.nutsRegion.get(z.getLid()).geoJson);
            element.put("count", z.getNumberProjects());
//            element.put("center", z.getCenterWkt());
            if ("COUNTRY".equals(z.getType()) && facetController.nutsRegion.containsKey(z.getLid())) {
                String query = "SELECT ?coords WHERE { <" + z.getLid() + "> <https://linkedopendata.eu/prop/direct/P127> ?coords.}LIMIT 1";

                TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map2");
                if (resultSet.hasNext()) {
                    BindingSet querySolution = resultSet.next();
                    String coordsString = querySolution.getBinding("coords").getValue().stringValue();
                    Coordinate pt = wktReader.read(coordsString).getCoordinate();
                    String ret = pt.x + "," + pt.y;
                    element.put("coordinates", ret);
                } else {
                    element.put("coordinates", z.getCenter());
                }

            } else {
                element.put("coordinates", z.getCenter());
            }
//            element.put("isHighlighted", false);
            element.put("cluster", true);
            resultList.add(new JSONObject(element));
        }
        Instant end = Instant.now();
        Duration elapsedTime = Duration.between(start, end);
        logger.debug("Count number projects each zone: {} milliseconds", elapsedTime.toMillis());
//        result.put("list", resultList);
//        result.put("subregions", resultList);
        result.put("subregions", resultList);
        result.put("region", granularityRegion);
//        result.put("upperRegions", findUpperRegions(granularityRegion, language));
        result.put("upperRegions", new JSONArray());
        result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));
        result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
        return new ResponseEntity<>(new JSONObject(result), HttpStatus.OK);
    }

    private HashMap<String, Zone> getZoneByQuery(String query, String type, int timeout) throws Exception {
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map2");
        HashMap<String, Zone> result = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String uri = querySolution.getBinding("s").getValue().stringValue();
            String lid = querySolution.getBinding("lid").getValue().stringValue();
            String geo = querySolution.getBinding("geo").getValue().stringValue();

            Zone zone = new Zone(uri, lid, geo, type);
            if (!result.containsKey(lid)) {
                result.put(lid, zone);
            }
            result.put(lid, zone);
        }
        return result;
    }

    private List<Feature> prepareCluster(SuperCluster superCluster, BoundingBox bbox, Integer zoom) {
        return clusterService.getCluster(
                superCluster,
                bbox,
                zoom
        );
    }

    private List<Feature> prepareCluster(List<Feature> features, BoundingBox bbox, Integer zoom) {
        return clusterService.getCluster(
                clusterService.createCluster(
                        features.toArray(new Feature[0]),
                        60,
                        256,
                        0,
                        20,
                        64
                ),
                bbox,
                zoom
        );

    }

    private List<Feature> getProjectsPoints(
            String language,
            String search,
//            String country,
//            String region,
//            String granularityRegion,
//            String latitude,
//            String longitude,
//            List<String> cci,
            BoundingBox boundingBox,
            Integer limit,
            Integer offset,
            int timeout
    ) throws Exception {
        logger.info("Search project map point: language {} search {} boundingBox {} limit {} offset {}", language, search, boundingBox, limit, offset);
        String query = "SELECT DISTINCT ?s0 ?coordinates WHERE { "
                + search
                + " FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + boundingBox.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates))"
//                + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1741> ?infoRegioID . }"
                + "}";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "point");
        HashMap<Geometry, List<String>> projectByCoordinates = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String coordinates = ((Literal) querySolution.getBinding("coordinates").getValue())
                    .getLabel();
            Geometry geometry = wktReader.read(coordinates);
            String project = querySolution.getBinding("s0").getValue().stringValue();
            if (!projectByCoordinates.containsKey(geometry)) {
                projectByCoordinates.put(geometry, new ArrayList<>());
            }
            projectByCoordinates.get(geometry).add(project);
        }
        List<Feature> features = new ArrayList<>();
        GeoJSONWriter writer = new GeoJSONWriter();
        for (Geometry geometry : projectByCoordinates.keySet()) {
            List<String> projects = projectByCoordinates.get(geometry);
            for (String uri : projects) {
                Map<String, Object> properties = new HashMap<>();
//                properties.put("project", uri);
                properties.put("projects", projects);
                Feature feature = new Feature(writer.write(geometry), properties);
                features.add(feature);
            }
        }
        return features;
    }

    private ResponseEntity<JSONObject> createResponse(List<Feature> features, int zoom, String search, String language, String granularityRegion) throws Exception {
        return createResponse(clusterService.createCluster(features.toArray(new Feature[0])), features, zoom, search, language, granularityRegion);
    }

    private ResponseEntity<JSONObject> createResponse(SuperCluster superCluster, List<Feature> features, int zoom, String search, String language, String granularityRegion) throws Exception {
        HashMap<String, Object> result = new HashMap<>();
//        result.put("list", features);
        List<JSONObject> subregions = new ArrayList<>();
//        logger.info("Features: {}", features);
        for (Feature feature : features) {
            HashMap<String, Object> element = new HashMap<>();

            List<Feature> nbPoint = clusterService.getPointsInCluster(
                    superCluster,
                    zoom,
                    new eu.ec.doris.kohesio.payload.Coordinate(((Point) feature.getGeometry()).getCoordinates())
            );


//            Boolean isClusterFromCluster = (Boolean) feature.getProperties().get("cluster");
//            logger.info(
//                    "Is cluster : {} | Point count : {} | Project count : {}",
//                    isClusterFromCluster,
//                    feature.getProperties().get("point_count"),
//                    ((List<String>) feature.getProperties().get("projects")).size()
//            );
            if (feature.getProperties().containsKey("cluster") && (boolean) feature.getProperties().get("cluster")) {
                element.put("count", feature.getProperties().get("point_count"));
                element.put("cluster", feature.getProperties().get("cluster"));
//                element.put("projects", feature.getProperties().get("projects"));
            } else {
//                element.put("projects", feature.getProperties().get("projects"));
                element.put("count", ((List<String>) feature.getProperties().get("projects")).size());
                element.put("cluster", false);
            }
            if (feature.getGeometry() instanceof Point) {
                Point point = (Point) feature.getGeometry();
                element.put("coordinates", point.getCoordinates()[0] + "," + point.getCoordinates()[1]);
            } else {
                element.put("coordinates", null);
            }
            element.put("cluster", nbPoint.size() != 1);
            element.put("nbPoint", nbPoint.size());
            subregions.add(new JSONObject(element));
        }
        if (granularityRegion == null) {
            granularityRegion = "https://linkedopendata.eu/entity/Q1";
        }
        result.put("subregions", subregions);
        result.put("region", granularityRegion);
        result.put("upperRegions", findUpperRegions(granularityRegion, language));
        result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));
        result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
        return new ResponseEntity<>(new JSONObject(result), HttpStatus.OK);
    }

    public List<Feature> getPointInfoFromBbox(
            eu.ec.doris.kohesio.payload.Coordinate coordinate,
            String boundingBoxString,
            Integer zoom,
            Integer timeout,
            Principal principal
    ) throws JsonProcessingException {
        BoundingBox boundingBox = null;
        if (boundingBoxString != null) {
//            boundingBox = objectMapper.readValue(boundingBoxString, BoundingBox.class);
            boundingBox = BoundingBox.createFromString(boundingBoxString);
        }
        List<Feature> list = new ArrayList<>();
        return clusterService.getPointsInCluster(
                list.toArray(new Feature[]{}),
                coordinate,
                boundingBox,
                zoom
        );
    }


}
