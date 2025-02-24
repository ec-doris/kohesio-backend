package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final GeoJSONReader geoJSONReader = new GeoJSONReader();
    private static final WKTReader wktReader = new WKTReader();

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/search/project/map", produces = "application/json")
    public ResponseEntity<JSONObject> euSearchProjectMap(
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
        return euSearchProjectMap(
                language,
                keywords,
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
                nuts3,
                limit,
                offset,
                town,
                radius,
                interreg,
                highlighted,
                cci,
                kohesioCategory,
                projectTypes,
                priorityAxis,
                boundingBoxString,
                zoom,
                timeout,
                principal,
                false
        );
    }

    public ResponseEntity<JSONObject> euSearchProjectMap(
            String language,
            String keywords,
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
            String region,
            String granularityRegion,
            String nuts3,
            Integer limit,
            Integer offset,
            String town,
            Long radius,
            Boolean interreg,
            Boolean highlighted,
            List<String> cci,
            String kohesioCategory,
            List<String> projectTypes,
            String priorityAxis,
            String boundingBoxString,
            Integer zoom,
            Integer timeout,
            Principal principal,
            boolean cache
    ) throws Exception {

        logger.info("Search Projects on map: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} region {} limit {} offset {} granularityRegion {}, lat {} long {} timeout {} interreg {} town {} boundingBox {}, priorityAxis {}, projectType {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, region, limit, offset, granularityRegion, latitude, longitude, timeout, interreg, town, boundingBoxString, priorityAxis, projectTypes);
        facetController.initialize(language);
        if (timeout == null) {
            timeout = 300;
        }
        BoundingBox boundingBox = null;
        if (boundingBoxString != null) {
            boundingBox = BoundingBox.createFromString(boundingBoxString);
        }

        //simplify the query
        String c = country;

        if (nuts3 != null && facetController.nutsRegion.containsKey(nuts3) && facetController.nutsRegion.get(nuts3).type.contains("nuts3")) {
            granularityRegion = nuts3;
        }

        if (granularityRegion != null) {
            c = null;
        }
        // expand the keywords
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords, language);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }
        // if the town is set, find it's location via nominatim geo-coding
        if (town != null && boundingBox == null) {
            eu.ec.doris.kohesio.payload.Coordinate tmpCoordinates = nominatimService.getCoordinatesFromTown(town);
            if (tmpCoordinates != null) {
                latitude = String.valueOf(tmpCoordinates.getLat());
                longitude = String.valueOf(tmpCoordinates.getLng());
            }
        }

        String search = filtersGenerator.filterProject(
                expandedQueryText, language, c, theme, fund, program, categoryOfIntervention,
                policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen,
                budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore,
                endDateAfter, latitude, longitude, null, region, granularityRegion,
                interreg, highlighted, cci, kohesioCategory, projectTypes, priorityAxis, boundingBox, limit, offset, true
        );

        // there is a bounding box
        if (boundingBox != null) {
            return handleBoundingBox(
                    language, keywords, country, granularityRegion,
                    limit, offset, town, zoom,
                    timeout, boundingBox, search, cache
            );
        }
        //computing the number of results
        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + search + "}";
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

    private ResponseEntity<JSONObject> handleBoundingBox(
            String language, String keywords, String country, String granularityRegion,
            Integer limit, Integer offset, String town, Integer zoom,
            Integer timeout, BoundingBox boundingBox, String search,
            boolean cache
    ) throws Exception {
        BoundingBox bboxToUse = boundingBox;
        if (town != null) {
            bboxToUse = nominatimService.getBboxFromTown(town);
            if (bboxToUse == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Town not found");
            }
        } else if (granularityRegion != null && !granularityRegion.equals("https://linkedopendata.eu/entity/Q1")) {
            Nut nut = facetController.nutsRegion.get(granularityRegion);
            if (nut != null && nut.geoJson != null && !nut.geoJson.isEmpty()) {
//                logger.info("Loading Geojson: {}", nut.geoJson.replace("'", "\""));
                Geometry geometry = geoJSONReader.read(nut.geoJson.replace("'", "\""));
                if (zoom == -1) {
                    bboxToUse = new BoundingBox(geometry.getEnvelopeInternal());
                }
            }
        }

        if (zoom == -1) {
            zoom = bboxToUse.getZoomLevel();
        }

        logger.info("zoom = {}", zoom);
        if (zoom < 10 && (town == null || town.isEmpty())) {
            HashMap<String, Zone> tmp = getCoordinatesByGeographicSubdivision(
                    bboxToUse,
                    zoom,
                    search,
                    language,
                    granularityRegion,
                    country,
                    cache ? 300 : 20 // if it's during cache calculation we give more time than on live
            );
//            logger.info("CGS : {}", tmp);
            return createResponse(tmp, search, language, granularityRegion, timeout);
        }
        // in this case create the clusters by taking all points
        List<Feature> features = getProjectsPoints(
                language, search, bboxToUse, granularityRegion,
                limit, offset, keywords != null, timeout
        );
        Instant instant = Instant.now();
        SuperCluster superCluster = clusterService.createCluster(
                features.toArray(new Feature[0]),
                60,
                256,
                0,
                17,
                64
        );
        logger.info("Time to getcluster: {}", Duration.between(instant, Instant.now()).toMillis());
        List<Feature> clusters = prepareCluster(superCluster, bboxToUse, zoom);
        return createResponse(superCluster, clusters, bboxToUse, zoom, search, language, granularityRegion);
    }

    private BoundingBox findBoundingbox(List<double[]> features) {
        double maxX = Double.MIN_VALUE;
        double minX = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        for (double[] coords : features) {
            if (coords[0] > maxX) {
                maxX = coords[0];
            }
            if (coords[0] < minX) {
                minX = coords[0];
            }
            if (coords[1] > maxY) {
                maxY = coords[1];
            }
            if (coords[1] < minY) {
                minY = coords[1];
            }
        }
        return new BoundingBox(minY, minX, maxY, maxX);
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
                offset,
                false
        );

//        if (boundingBox != null && zoom < 18) {
//            SuperCluster superCluster = clusterService.createCluster(
//                    getProjectsPoints(
//                            language,
//                            search,
//                            boundingBox,
//                            granularityRegion,
//                            limit,
//                            offset,
//                            keywords != null,
//                            timeout
//                    )
//            );
//            eu.ec.doris.kohesio.payload.Coordinate coords = new eu.ec.doris.kohesio.payload.Coordinate(coordinate);
//            if (!superCluster.containsPointAtCoordinates(coords)) {
//                return new ResponseEntity<>(new JSONArray(), HttpStatus.OK);
////                return new ResponseEntity<>(mapPointBbox(superCluster, language, coords, zoom, timeout), HttpStatus.OK) ;
//            } else {
//                eu.ec.doris.kohesio.payload.Coordinate coordsFromCluster = superCluster.getCoordinateFromPointAtCoordinates(coords);
//                coordinate = coordsFromCluster.toBasicCoords();
//            }
//        }

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
    public ResponseEntity<JSONObject> geoIp(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "useCluster", defaultValue = "false") boolean useCluster,
            HttpServletRequest request
    ) throws Exception {
        logger.info("Find coordinates of given IP : {}, {}", language, useCluster);
        String ip = httpReqRespUtils.getClientIpAddressIfServletRequestExist(request);
        GeoIp.Coordinates coordinates2 = geoIp.compute(ip);
        ResponseEntity<JSONObject> result = euSearchProjectMap(
                language, null,
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
        if (useCluster) {
            JSONArray array = (JSONArray) mod.get("list");
            List<double[]> coords = new ArrayList<>();
            for (Object object : array) {
                JSONObject jsonObject = (JSONObject) object;
                String coordString = (String) jsonObject.get("coordinates");
                coords.add(new eu.ec.doris.kohesio.payload.Coordinate(coordString).coords());
            }
            BoundingBox boundingBox = findBoundingbox(coords);
            logger.info("found bbox : {}", boundingBox);
            ResponseEntity<JSONObject> result2 = euSearchProjectMap(
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
                    null, null, boundingBox.toBounds(),
                    -1, 400, null
            );
            mod = result2.getBody();
        }
        mod.put("coordinates", coordinates2.getLatitude() + "," + coordinates2.getLongitude());
        return new ResponseEntity<>(mod, HttpStatus.OK);
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

    private HashMap<String, Zone> getCoordinatesByGeographicSubdivision(
            BoundingBox bbox,
            int zoom,
            String search,
            String language,
            String granularityRegion,
            String country,
            int timeout
    ) throws Exception {
        // 1. compute for all NUTS1, NUTS2, NUTS3 that are non-statistical, the number of projects they contain 
        String restrictNuts = "";
        // if the country is set, restrict to nuts in the country
        if (country != null) {
            restrictNuts = " ?nuts <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }
        if (granularityRegion != null) {
            restrictNuts = " ?nuts <https://linkedopendata.eu/prop/direct/P1845>* <" + granularityRegion + "> . ";
        }
        String queryCount = "SELECT ?nuts (COUNT(DISTINCT ?s0)  AS ?count) WHERE { "
                + search
                // the projects must have a coordinate otherwise when zooming in there will be no point
                + " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                + " ?s0 <https://linkedopendata.eu/prop/direct/P1845> ?nuts . "
                + " FILTER EXISTS { "
                + restrictNuts
                + " { "
                + " ?nuts <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407315> "
                + " } UNION { "
                + " ?nuts <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407316> "
                + " } UNION { "
                + " ?nuts <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4407317> "
                + " } UNION { "
                + " ?nuts <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q510> "
                + " } ";
        if (granularityRegion != null && !"https://linkedopendata.eu/entity/Q1".equals(granularityRegion)) {
            queryCount += " UNION { VALUES ?nuts {<" + granularityRegion + "> }}";
        }
        queryCount += " }} GROUP BY ?nuts";

        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(
                sparqlEndpoint,
                queryCount,
                timeout,
                "map"
        );
        // store the numbers in a hash map where the key is the NUTS url
        HashMap<String, Integer> uriCount = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet bindings = resultSet.next();
            String nutsOfCount = bindings.getBinding("nuts").getValue().stringValue();
            Integer count = Integer.parseInt(bindings.getBinding("count").getValue().stringValue());
            uriCount.put(nutsOfCount, count);
        }
        // 2. take only the nuts that are matching to the zoom level
        // if the zoom is lower than 6 we show the numbers of the whole country
        String query = "";
        String type = "";
        if (zoom < 5) {
            // Get Country in bbox
            query = "SELECT * WHERE {"
                    + " ?s <http://nuts.de/linkedopendata> ?lid; "
                    + " <http://nuts.de/geometry> ?geo; "
                    + " a <http://nuts.de/NUTS0>. "
                    + "} ";
            type = "COUNTRY";
        }
        // if the zoom is between 4 and 9 we show the numbers of the nuts 1 or 2
        else if (zoom <= 6) {
            query = "SELECT * WHERE {"
                    + " ?s <http://nuts.de/linkedopendata> ?lid . "
                    + " ?s <http://nuts.de/geometry> ?geo . "
                    + " ?s a <http://nuts.de/NUTS1>  "
                    + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                    + "} ";
            type = "NUTS1";
        }
        // if the zoom is between 4 and 9 we show the numbers of the nuts 1 or 2
        else if (zoom <= 7) {
            query = "SELECT * WHERE {"
                    + " ?s <http://nuts.de/linkedopendata> ?lid . "
                    + " ?s <http://nuts.de/geometry> ?geo . "
                    + " ?s a <http://nuts.de/NUTS2>  "
                    + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                    + "} ";
            type = "NUTS2";
        }
        // if the zoom is higher than 9 we show the numbers of the nuts 2 or 3
        else {
            query = "SELECT * WHERE {"
                    + " ?s <http://nuts.de/linkedopendata> ?lid . "
                    + " ?s <http://nuts.de/geometry> ?geo . "
                    + " ?s a <http://nuts.de/NUTS3> "
                    + " FILTER(<http://www.opengis.net/def/function/geosparql/sfIntersects>(?geo, " + bbox.toLiteral() + "))"
                    + "} ";
            type = "NUTS3";
        }
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, "map");
        HashMap<String, Zone> result = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String uri = querySolution.getBinding("s").getValue().stringValue();
            String lid = querySolution.getBinding("lid").getValue().stringValue();
            String geo = querySolution.getBinding("geo").getValue().stringValue();
            if (uriCount.containsKey(lid)) {
                Zone zone = new Zone(uri, lid, geo, type, uriCount.get(lid));
                result.put(lid, zone);
            }
        }
        if (result.isEmpty() && uriCount.containsKey(granularityRegion)) {
            Nut nut = facetController.nutsRegion.get(granularityRegion);
            GeoJsonReader geoJsonReader = new GeoJsonReader();
            Geometry geometry = geoJsonReader.read(nut.geoJson.replace("'", "\""));
            Zone zone = new Zone("", granularityRegion, geometry.toText(), type, uriCount.get(granularityRegion));
            result.put(granularityRegion, zone);
        }
        return result;
    }

    private ResponseEntity<JSONObject> createResponse(
            HashMap<String, Zone> res,
            String search,
            String language,
            String granularityRegion,
            int timeout
    ) throws Exception {
        if (granularityRegion == null) {
            granularityRegion = "https://linkedopendata.eu/entity/Q1";
        }
        HashMap<String, Object> result = new HashMap<>();
        JSONArray resultList = new JSONArray();
        Instant start = Instant.now();
        for (Zone z : res.values()) {
            HashMap<String, Object> element = new HashMap<>();
            if (facetController.nutsRegion.containsKey(z.getLid())) {
                element.put("regionLabel", facetController.nutsRegion.get(z.getLid()).name.get(language));
            } else {
                element.put("regionLabel", "");
            }
            element.put("region", z.getLid());
            //element.put("geo", z.getGeo());
            element.put("count", z.getNumberProjects());
            if (z.getNumberProjects() == 1) {
                String query = "SELECT ?coords WHERE { "
                        + search
                        + " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coords . "
                        + " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + z.getLid() + "> . "
                        + "}";
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
            } else if ("COUNTRY".equals(z.getType()) && facetController.nutsRegion.containsKey(z.getLid())) {
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
            element.put("cluster", true);
            resultList.add(new JSONObject(element));
        }
        Instant end = Instant.now();
        Duration elapsedTime = Duration.between(start, end);
        logger.debug("Count number projects each zone: {} milliseconds", elapsedTime.toMillis());
        result.put("subregions", resultList);
        result.put("region", granularityRegion);
        result.put("upperRegions", new JSONArray());
        result.put("geoJson", facetController.nutsRegion.get(granularityRegion).geoJson);
        result.put("regionLabel", facetController.nutsRegion.get(granularityRegion).name.get(language));

        return new ResponseEntity<>(new JSONObject(result), HttpStatus.OK);
    }

    private List<Feature> prepareCluster(SuperCluster superCluster, BoundingBox bbox, Integer zoom) {
        return clusterService.getCluster(
                superCluster,
                bbox,
                zoom
        );
    }

    private List<Feature> getProjectsPoints(
            String language,
            String search,
            BoundingBox boundingBox,
            String granularityRegion,
            Integer limit,
            Integer offset,
            boolean useLuceneForGeoSparql,
            int timeout
    ) throws Exception {
        logger.info("Search project map point: language {} search {} boundingBox {} limit {} offset {}", language, search, boundingBox, limit, offset);
        String tmpSearch = search.replaceAll("\\?s0 <https://linkedopendata.eu/prop/direct/P127> \\?coordinates \\.", "")
                .replaceAll("\\?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> \\.", "")
                .replaceAll("FILTER\\(<http://www.opengis.net/def/function/geosparql/ehContains>\\(.*\\)", "")
                .replaceAll("FILTER\\(<http://www.opengis.net/def/function/geosparql/distance>.*\\)", "");

        String query = "SELECT DISTINCT ?s0 ?coordinates WHERE { "
                + " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> ."
                + " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates .";
        if (!tmpSearch.replaceAll("\\s", "").isEmpty()) {
            Pattern instanceOfPattern = Pattern.compile("\\?s0 <https://linkedopendata.eu/prop/direct/P35> <([^>]*)>\\s*\\.?");
            Matcher instanceOfMatcher = instanceOfPattern.matcher(tmpSearch);
            while (instanceOfMatcher.find()) {
                String uri = instanceOfMatcher.group(1);
                System.err.println(uri);
                query += "?s0 <https://linkedopendata.eu/prop/direct/P35> <" + uri + ">.";
            }
            if (!useLuceneForGeoSparql) {
                query += " FILTER EXISTS { ";
            }
            query += " " + tmpSearch + " ";
            if (!useLuceneForGeoSparql) {
                query += " }";
            }
        }
        String filterBbox = "FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(" + boundingBox.toLiteral() + ",?coordinates))";
        // hack to run the geosparql not over lucene in case there is a freetext query over lucene
        if (useLuceneForGeoSparql) {
            filterBbox = "FILTER(<http://www.opengis.net/def/function/geosparql/sfWithin>(?coordinates," + boundingBox.toLiteral() + "))";
            query += " " + filterBbox + " ";
        } else {
            query += " " + filterBbox + " " + filterBbox + " ";
        }
        if (granularityRegion != null) {
            Nut nut = facetController.nutsRegion.get(granularityRegion);
            Geometry geometryGranularityRegion = geoJSONReader.read(nut.geoJson.replace("'", "\""));
            if (nut.type.contains("country") || nut.country.equals("https://linkedopendata.eu/entity/Q15")) {
                query += " " + "FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + geometryGranularityRegion.convexHull().toText() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates)) ";
            } else {
                query += " " + "FILTER(<http://www.opengis.net/def/function/geosparql/ehContains>(\"" + geometryGranularityRegion.toText() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates)) ";
            }
        }
        query += "}";

        logger.info("sparql={}", sparqlEndpoint);
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, false, "point");
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
                properties.put("projects", projects);
                Feature feature = new Feature(writer.write(geometry), properties);
                features.add(feature);
            }
        }
        return features;
    }

    private ResponseEntity<JSONObject> createResponse(SuperCluster superCluster, List<Feature> features, BoundingBox boundingBox, int zoom, String search, String language, String granularityRegion) throws Exception {
        HashMap<String, Object> result = new HashMap<>();
        List<JSONObject> subregions = new ArrayList<>();
        for (Feature feature : features) {
            HashMap<String, Object> element = new HashMap<>();
            if (feature.getProperties().containsKey("cluster") && (boolean) feature.getProperties().get("cluster")) {
                element.put("count", feature.getProperties().get("point_count"));
            } else {
                element.put("count", ((List<String>) feature.getProperties().get("projects")).size());
            }
            if (feature.getGeometry() instanceof Point) {
                Point point = (Point) feature.getGeometry();
                eu.ec.doris.kohesio.payload.Coordinate coordinate = new eu.ec.doris.kohesio.payload.Coordinate(point.getCoordinates());
                element.put("cluster", !superCluster.containsPointAtCoordinates(coordinate));
                if (superCluster.containsPointAtCoordinates(coordinate)) {
                    eu.ec.doris.kohesio.payload.Coordinate coordinate1 = superCluster.getCoordinateFromPointAtCoordinates(coordinate);
                    element.put("coordinates", coordinate1.toBasicCoords());
                } else {
                    element.put("coordinates", coordinate.toBasicCoords());
                }
            } else {
                element.put("cluster", false);
                element.put("coordinates", null);
            }
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
