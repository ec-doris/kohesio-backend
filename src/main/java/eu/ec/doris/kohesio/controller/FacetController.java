package eu.ec.doris.kohesio.controller;


import eu.ec.doris.kohesio.geoIp.GeoIp;
import eu.ec.doris.kohesio.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.payload.Nut;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FacetController {
    private static final Logger logger = LoggerFactory.getLogger(FacetController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Value("${kohesio.directory}")
    String location;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @Value("${kohesio.sparqlEndpointNuts}")
    String getSparqlEndpointNuts;

    @Autowired
    GeoIp geoIp;

    @Autowired
    HttpReqRespUtils httpReqRespUtils;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    HashMap<String, Nut> nutsRegion = null;

    public void clear() {
        nutsRegion = null;
    }

    public void initialize(String language) throws Exception {
        logger.info("Initializing Facet Controller...");
        if (nutsRegion == null) {
            nutsRegion = new HashMap<String, Nut>();
            //computing nuts information
            List<String> gran = new ArrayList<String>();
            gran.add("continent");
            gran.add("country");
            gran.add("nuts1");
            gran.add("nuts2");
            gran.add("nuts3");
            for (String g : gran) {
                String filter = "";
                if (g.equals("continent")) {
                    filter = " VALUES ?region { <https://linkedopendata.eu/entity/Q1> } . ?region <https://linkedopendata.eu/prop/direct/P104>  ?region2 .";
                }
                if (g.equals("country")) {
                    filter = " <https://linkedopendata.eu/entity/Q1> <https://linkedopendata.eu/prop/direct/P104>  ?region . ";
                }
                if (g.equals("nuts1")) {
                    filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407317> . ";
                }
                if (g.equals("nuts2")) {
                    filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407316> .";
                }
                if (g.equals("nuts3")) {
                    filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407315> .";
                }

                String query = "SELECT DISTINCT ?region ?country ?nuts_code ?regionLabel (LANG(?regionLabel) AS ?lang) WHERE {"
                        + filter
                        + " ?region <http://www.w3.org/2000/01/rdf-schema#label> ?regionLabel ."
                        + " OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P32> ?country } "
                        + " OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P192> ?nuts_code } "
                        + "}";
                TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30);
                while (resultSet.hasNext()) {
                    BindingSet querySolution = resultSet.next();
                    String key = querySolution.getBinding("region").getValue().stringValue();
                    if (nutsRegion.containsKey(key)) {
                        Nut nut = nutsRegion.get(key);
                        nut.type.add(g);
                        if (querySolution.getBinding("regionLabel") != null) {
                            nut.name.put(
                                    querySolution.getBinding("lang").getValue().stringValue(),
                                    querySolution.getBinding("regionLabel").getValue().stringValue()
                            );
                        }
                    } else {
                        Nut nut = new Nut();
                        nut.uri = key;
                        nut.type.add(g);
                        nut.granularity = g;
                        if (nutsRegion.get(key) != null) {
                            nut = nutsRegion.get(key);
                        }
                        if (querySolution.getBinding("regionLabel") != null) {
                            nut.name.put(
                                    querySolution.getBinding("lang").getValue().stringValue(),
                                    querySolution.getBinding("regionLabel").getValue().stringValue()
                            );
                        }
                        if (querySolution.getBinding("country") != null) {
                            nut.country = querySolution.getBinding("country").getValue().stringValue();
                        }
                        if (querySolution.getBinding("nuts_code") != null) {
                            nut.nutsCode = querySolution.getBinding("nuts_code").getValue().stringValue();
                        }
                        nutsRegion.put(key, nut);
                    }
                }

            }
            //retrieving the narrower concept
            for (String key : nutsRegion.keySet()) {
                String query = "";
                if (nutsRegion.get(key).type.contains("continent")) {
                    query =
                            "SELECT ?region2 where {" +
                                    " <https://linkedopendata.eu/entity/Q1> <https://linkedopendata.eu/prop/direct/P104> ?region2 . }";
                }
                if (nutsRegion.get(key).type.contains("country")) {
                    query =
                            "SELECT ?region2 where {" +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407317> . " +
                                    " }";
                }
                if (nutsRegion.get(key).type.contains("nuts1")) {
                    query =
                            "SELECT ?region2 where {" +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407316> . " +
                                    "}";
                }
                if (nutsRegion.get(key).type.contains("nuts2")) {
                    query =
                            "SELECT ?region2 where {" +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                                    " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q4407315> . }";
                }
                if (!query.equals("")) {
                    TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30);
                    logger.debug("Is empty result set: " + resultSet.hasNext());
                    while (resultSet.hasNext()) {
                        BindingSet querySolution = resultSet.next();
                        if (querySolution.getBinding("region2") != null) {
                            logger.debug(querySolution.getBinding("region2").getValue().stringValue());
                            if (!querySolution.getBinding("region2").getValue().stringValue().equals(key)) {
                                if (nutsRegion.get(key).narrower.contains(querySolution.getBinding("region2").getValue().stringValue()) == false) {
                                    nutsRegion.get(key).narrower.add(querySolution.getBinding("region2").getValue().stringValue());
                                }
                            }
                        }
                    }
                }
            }
// retrieving the geoJson geometries
            for (String key : nutsRegion.keySet()) {
                String geometry = " ?nut <http://nuts.de/geoJson> ?regionGeo . ";
                if (nutsRegion.get(key).type.contains("continent")) {
                    geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
                }
                if (nutsRegion.get(key).type.contains("country")) {
                    geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
                }
//        if (nutsRegion.get(key).type.equals("nuts1")){
//          geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
//        }

                String query =
                        "SELECT ?regionGeo where {" +
                                "?nut <http://nuts.de/linkedopendata> <" + nutsRegion.get(key).uri + "> . " +
                                geometry +
                                " }";
                TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 20);
                while (resultSet.hasNext()) {
                    BindingSet querySolution = resultSet.next();
                    nutsRegion.get(key).geoJson = querySolution.getBinding("regionGeo").getValue().stringValue();
                }
            }

            // skipping regions that are statistical only
            gran = new ArrayList<>();
            gran.add("nuts2");
            gran.add("nuts1");
            gran.add("country");
            for (String g : gran) {
                for (String key : nutsRegion.keySet()) {
                    if (nutsRegion.get(key).type.contains(g)) {
                        List<String> nonStatisticalNuts = new ArrayList<>();
                        for (String nutsCheckStatistical : nutsRegion.get(key).narrower) {
                            String query =
                                    "ASK { <" + nutsCheckStatistical + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2727537> . }";
                            boolean resultSet = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, query, 20);
                            if (resultSet) {
                                for (String childNut : nutsRegion.get(nutsCheckStatistical).narrower) {
                                    nonStatisticalNuts.add(childNut);
                                }
                            } else {
                                nonStatisticalNuts.add(nutsCheckStatistical);
                            }
                        }
                        nutsRegion.get(key).narrower = nonStatisticalNuts;
                    }
                }
            }

        }
    }

    @GetMapping(value = "facet/eu/nuts3")
    public JSONArray facetNuts(
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {
        initialize(language);
        List<JSONObject> jsonValues = new ArrayList<>();
        if (qid != null && nutsRegion.containsKey(qid)) {
            Nut nutQid = nutsRegion.get(qid);
            JSONObject element = new JSONObject();

            element.put("instance", nutQid.uri);
            element.put("name", nutQid.nutsCode + " - " + nutQid.name.get(language));
            element.put("country", nutQid.country);
            element.put("nuts_code", nutQid.nutsCode);
            jsonValues.add(element);
        } else if (region != null && nutsRegion.containsKey(region)) {
            Nut nutRegion = nutsRegion.get(region);

            nutRegion.narrower.forEach(s -> {
                Nut nut = nutsRegion.get(s);
                JSONObject element = new JSONObject();

                element.put("instance", nut.uri);
                element.put("name", nut.nutsCode + " - " + nut.name.get(language));
                element.put("country", nut.country);
                element.put("nuts_code", nut.nutsCode);
                jsonValues.add(element);

            });
        } else if (country != null) {
            nutsRegion.forEach((s, nut) -> {
                if (country.equals(nut.country)) {
                    if (nut.type.contains("nuts3")) {
                        JSONObject element = new JSONObject();

                        element.put("instance", nut.uri);
                        element.put("name", nut.nutsCode + " - " + nut.name.get(language));
                        element.put("country", nut.country);
                        element.put("nuts_code", nut.nutsCode);
                        jsonValues.add(element);
                    }
                }
            });
        } else {
            nutsRegion.forEach((s, nut) -> {
                if (nut.type.contains("nuts3")) {
                    JSONObject element = new JSONObject();

                    element.put("instance", nut.uri);
                    element.put("name", nut.nutsCode + " - " + nut.name.get(language));
                    element.put("country", nut.country);
                    element.put("nuts_code", nut.nutsCode);
                    jsonValues.add(element);
                }
            });
        }

        /*
        Collections.sort(jsonValues, new Comparator<JSONObject>() {
            private static final String KEY_NAME = "instance";
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Integer.compare(
                        Integer.parseInt(
                                ((String) a.get(KEY_NAME))
                                        .replace("https://linkedopendata.eu/entity/Q", "")
                        ),
                        Integer.parseInt(
                                ((String) b.get(KEY_NAME))
                                        .replace("https://linkedopendata.eu/entity/Q", "")
                        )
                );
            }
        });
        //*/

        JSONArray results = new JSONArray();
        results.addAll(jsonValues);
        return results;
    }

    @GetMapping(value = "facet/eu/statistics", produces = "application/json")
    public JSONObject facetEuStatistics() throws Exception {
        logger.info("Get EU statistics");
        JSONObject statistics = new JSONObject();
        String query = "SELECT (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . "
                + "} ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 10);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            statistics.put("numberProjects", ((Literal) querySolution.getBinding("c").getValue()).intValue());
        }
        query = "SELECT (COUNT(DISTINCT ?s0) AS ?c) WHERE { "
                + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q196899> . "
                + "} ";
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 10);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            statistics.put("numberBeneficiaries", ((Literal) querySolution.getBinding("c").getValue()).intValue());
        }
//        query = "SELECT (SUM(?o) AS ?sum) WHERE { "
//                + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . "
//                + "    ?s0  <https://linkedopendata.eu/prop/direct/P835>  ?o . "
//                + "} ";
//        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
//        while (resultSet.hasNext()) {
//            BindingSet querySolution = resultSet.next();
//            DecimalFormat df2 = new DecimalFormat("#.##");
//            statistics.put("totalEuBudget", df2.format(((Literal) querySolution.getBinding("sum").getValue()).doubleValue()));
//        }

        JSONObject themes = new JSONObject();
        query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE {" +
                "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
                "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236692> .   " +
                " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                "}";
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            themes.put("lowCarbonEconomy", ((Literal) querySolution.getBinding("c").getValue()).doubleValue());
        }

        query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE {" +
                "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
                "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236693> .   " +
                " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                "}";
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            themes.put("climateChangeAdaptation", ((Literal) querySolution.getBinding("c").getValue()).intValue());
        }

        query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE {" +
                "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
                "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236694> .   " +
                " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                "}";
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            themes.put("enviromentProtection", ((Literal) querySolution.getBinding("c").getValue()).intValue());
        }

        query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE {" +
                "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category." +
                " ?category <https://linkedopendata.eu/prop/direct/P1849> <https://linkedopendata.eu/entity/Q2547987> . " +
                "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                "}";
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            themes.put("greenerAndCarbonFreeEurope", ((Literal) querySolution.getBinding("c").getValue()).intValue());
        }

        statistics.put("themes", themes);

        return statistics;
    }

    @GetMapping(value = "/facet/eu/countries", produces = "application/json")
    public JSONArray facetEuCountries(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "qid", required = false) String qid
    )
            throws Exception {
        logger.info("Get list of countries");
        List<JSONObject> jsonValues = new ArrayList<JSONObject>();
        String query = "SELECT DISTINCT ?country WHERE {";
        if (qid != null) {
            query += " VALUES ?country { <" + qid + "> }";
        }
        query += " <https://linkedopendata.eu/entity/Q1> <https://linkedopendata.eu/prop/direct/P104> ?country . }";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 20);
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            element.put("instance", querySolution.getBinding("country").getValue().stringValue());
            jsonValues.add(element);
        }

        for (int i = 0; i < jsonValues.size(); i++) {
            query = "select ?instanceLabel where { "
                    + " <" + jsonValues.get(i).get("instance") + "> rdfs:label ?instanceLabel . "
                    + " FILTER (lang(?instanceLabel)=\""
                    + language
                    + "\")"
                    + "}";
            resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                jsonValues.get(i).put("instanceLabel", querySolution.getBinding("instanceLabel").getValue().stringValue());
            }
            query = "select ?instanceImage where { "
                    + " <" + jsonValues.get(i).get("instance") + "> <https://linkedopendata.eu/prop/direct/P21> ?instanceImage . "
                    + "}";
            resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                jsonValues.get(i).put("instanceImage", querySolution.getBinding("instanceImage").getValue().stringValue());

            }
        }

        Collections.sort(jsonValues, new Comparator<JSONObject>() {
            //You can change "Name" with "ID" if you want to sort by ID
            private static final String KEY_NAME = "instanceLabel";

            @Override
            public int compare(JSONObject a, JSONObject b) {
                String valA = new String();
                String valB = new String();
                valA = (String) a.get(KEY_NAME);
                valB = (String) b.get(KEY_NAME);
                return valA.compareTo(valB);
                //if you want to change the sort order, simply use the following:
                //return -valA.compareTo(valB);
            }
        });

        JSONArray result = new JSONArray();
        for (int i = 0; i < jsonValues.size(); i++) {
            result.add(jsonValues.get(i));
        }

        return result;
    }

    @GetMapping(value = "/facet/eu/regions", produces = "application/json")
    public JSONArray facetEuRegions(
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "qid", required = false) String qid
    )
            throws Exception {
        initialize(language);
        logger.info("Get EU regions");
        List<JSONObject> jsonValues = new ArrayList<JSONObject>();
        if (qid != null && nutsRegion.containsKey(qid)) {
            JSONObject element = new JSONObject();
            element.put("region", qid);
            String query = "SELECT ?instanceLabel ?instanceLabelEn WHERE {"
                    + " VALUES ?region { <" + qid + "> }"
                    + " OPTIONAL { ?region rdfs:label ?instanceLabel ."
                    + " FILTER (lang(?instanceLabel)=\"" + language + "\") }"
                    + " OPTIONAL { ?region rdfs:label ?instanceLabelEn . FILTER(LANG(?instanceLabelEn)=\"en\")}"
                    + "}";
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                if (querySolution.getBinding("instanceLabel") != null) {
                    element.put("name", querySolution.getBinding("instanceLabel").getValue().stringValue());
                } else {
                    element.put("name", querySolution.getBinding("instanceLabelEn").getValue().stringValue());
                }
            }
            jsonValues.add(element);
        } else if (nutsRegion.containsKey(country)) {
            for (String region : nutsRegion.get(country).narrower) {
                JSONObject element = new JSONObject();
                element.put("region", region);
                String query = "SELECT ?instanceLabel ?instanceLabelEn WHERE { "
                        + " VALUES ?region { <" + region + "> } "
                        + " OPTIONAL { ?region rdfs:label ?instanceLabel . "
                        + "   FILTER (lang(?instanceLabel)=\""
                        + language
                        + "\") } "
                        + " OPTIONAL { ?region rdfs:label ?instanceLabelEn . FILTER (lang(?instanceLabelEn)=\"en\")  } "
                        + "}";
                TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
                while (resultSet.hasNext()) {
                    BindingSet querySolution = resultSet.next();
                    if (querySolution.getBinding("instanceLabel") != null) {
                        element.put("name", querySolution.getBinding("instanceLabel").getValue().stringValue());
                    } else {
                        element.put("name", querySolution.getBinding("instanceLabelEn").getValue().stringValue());
                    }
                }
                System.out.println(element.toJSONString());
                jsonValues.add(element);
            }
        }
//        String row;
//        ClassLoader loader = Thread.currentThread().getContextClassLoader();
//        InputStream input = loader.getResourceAsStream("regions2.csv");
//        BufferedReader csvReader = new BufferedReader(new BufferedReader(new InputStreamReader(input, "UTF-8")));
//
//        List<JSONObject> jsonValues = new ArrayList<JSONObject>();
//
//
//        while ((row = csvReader.readLine()) != null) {
//            String[] data = row.split(";");
//            System.out.println();
//            if (country.equals("https://linkedopendata.eu/entity/Q2") && data[0].equals("IE")
//                    || country.equals("https://linkedopendata.eu/entity/Q15") && data[0].equals("IT")
//                    || country.equals("https://linkedopendata.eu/entity/Q13") && data[0].equals("PL")
//                    || country.equals("https://linkedopendata.eu/entity/Q25") && data[0].equals("CZ")
//                    || country.equals("https://linkedopendata.eu/entity/Q20") && data[0].equals("FR")
//                    || country.equals("https://linkedopendata.eu/entity/Q12") && data[0].equals("DK")) {
//                JSONObject element = new JSONObject();
//                element.put("region", data[6]);
//                element.put("name", data[3]);
//                jsonValues.add(element);
//            }
//        }
//        csvReader.close();

        Collections.sort(jsonValues, new Comparator<JSONObject>() {
            //You can change "Name" with "ID" if you want to sort by ID
            private static final String KEY_NAME = "name";

            @Override
            public int compare(JSONObject a, JSONObject b) {
                String valA = new String();
                String valB = new String();
                valA = (String) a.get(KEY_NAME);
                valB = (String) b.get(KEY_NAME);
                return valA.compareTo(valB);
                //if you want to change the sort order, simply use the following:
                //return -valA.compareTo(valB);
            }
        });

        JSONArray result = new JSONArray();
        for (int i = 0; i < jsonValues.size(); i++) {
            result.add(jsonValues.get(i));
        }

        return result;
    }


    @GetMapping(value = "/facet/eu/funds", produces = "application/json")
    public JSONArray facetEuFunds(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {
        logger.info("Get list of EU funds");
        String query = "SELECT ?fund ?fundLabel ?id WHERE {";

        if (qid != null) {
            query += " VALUES ?fund { <" + qid + "> }";
        }

        query += " ?fund <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2504365> . "
                + " ?fund <https://linkedopendata.eu/prop/direct/P1583> ?id ."
                + " ?fund rdfs:label ?fundLabel . "
                + " FILTER (LANG(?fundLabel)=\""
                + language
                + "\")"
                + "} ORDER BY ?id ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            element.put("instance", querySolution.getBinding("fund").getValue().toString());
            element.put("instanceLabel", querySolution.getBinding("id").getValue().stringValue() + " - " + querySolution.getBinding("fundLabel").getValue().stringValue());
            result.add(element);
        }
        return result;
    }


    public JSONArray facetPolicyObjective(
            @RequestParam(value = "language", defaultValue = "en") String language
    ) throws Exception {
        return facetPolicyObjective(language, null, null);
    }

    @GetMapping(value = "/facet/eu/policy_objectives", produces = "application/json")
    public JSONArray facetPolicyObjective(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {
        logger.info("Get list of policy objectives");
        String query = "SELECT ?po ?poLabel ?id ?to ?toId WHERE { ";
        if (qid != null) {
            query += " VALUES ?po { <" + qid + "> }";
        }
        query += " { SELECT DISTINCT ?po ?poLabel ?id WHERE { "
                + " ?po <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2547986> . "
                + " ?po rdfs:label ?poLabel . "
                + " ?po  <https://linkedopendata.eu/prop/direct/P1747> ?id ."
                + " FILTER(LANG(?poLabel)=\""
                + language
                + "\")"
                + " ?po <https://linkedopendata.eu/prop/direct/P1848> ?to ."
                + " ?to <https://linkedopendata.eu/prop/direct/P1105> ?toId .";
        if (theme != null) {
            query += " FILTER (?to = <" + theme + "> )";
        }
        query += "}}"
                + " ?po <https://linkedopendata.eu/prop/direct/P1848> ?to ."
                + " ?to <https://linkedopendata.eu/prop/direct/P1105> ?toId ."
                + " } ORDER BY ?id";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
        HashMap<String, JSONObject> tempList = new HashMap<>();

        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            String instance = querySolution.getBinding("po").getValue().toString();
            if (tempList.containsKey(instance)) {
                element = tempList.get(instance);
            } else {
                element.put("instance", querySolution.getBinding("po").getValue().toString());
                element.put("instanceLabel", querySolution.getBinding("poLabel").getValue().stringValue());
                element.put("id", querySolution.getBinding("id").getValue().stringValue());
                element.put("theme", new JSONArray());

                tempList.put(querySolution.getBinding("po").getValue().toString(), element);
            }
            JSONObject themeObj = new JSONObject();
            themeObj.put("instance", querySolution.getBinding("to").getValue().stringValue());
            themeObj.put("id", querySolution.getBinding("toId").getValue().stringValue());
            ((JSONArray) element.get("theme")).add(themeObj);
        }

        JSONArray result = new JSONArray();
        for (JSONObject element : tempList.values()) {
            result.add(element);
        }
        return result;
    }


    @GetMapping(value = "/facet/eu/categoriesOfIntervention", produces = "application/json")
    public JSONArray facetEuCategoryOfIntervention(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {

        logger.info("Get list of intervention field...");
        String query = "SELECT ?instance ?instanceLabel ?id ?areaOfIntervention ?areaOfInterventionLabel ?areaOfInterventionId WHERE { ";
        if (qid != null) {
            query += " VALUES ?areaOfIntervention { <" + qid + "> }";
        }
        query += " ?instance <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q200769> . "
                + " ?instance <https://linkedopendata.eu/prop/direct/P869> ?id . "
                + " ?instance <https://linkedopendata.eu/prop/direct/P178453> ?areaOfIntervention . "
                + " ?areaOfIntervention <https://linkedopendata.eu/prop/direct/P178454> ?areaOfInterventionId . "
                + " ?areaOfIntervention rdfs:label ?areaOfInterventionLabel . "
                + " FILTER (lang(?areaOfInterventionLabel)=\"" + language + "\")"
                + " ?instance rdfs:label ?instanceLabel . "
                + " FILTER (lang(?instanceLabel)=\"" + language + "\")"
                + "} ORDER BY ?id";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 5);
        JSONArray result = new JSONArray();
        String areaOfIntervention = "";
        String areaOfInterventionLabel = "";
        String areaOfInterventionId = "";
        JSONArray subset = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            element.put("instance", querySolution.getBinding("instance").getValue().toString());
            String label = querySolution.getBinding("instanceLabel").getValue().stringValue();
            if (label.length() >= 200) {
                label = label.substring(0, 200) + " ...";
            }
            element.put(
                    "instanceLabel", querySolution.getBinding("id").getValue().stringValue() + " - " + label);
            if (areaOfIntervention.equals("")) {
                areaOfIntervention = querySolution.getBinding("areaOfIntervention").getValue().toString();
                areaOfInterventionLabel = querySolution.getBinding("areaOfInterventionLabel").getValue().stringValue();
                areaOfInterventionId = querySolution.getBinding("areaOfInterventionId").getValue().toString();
            }
            if (areaOfIntervention.equals(querySolution.getBinding("areaOfIntervention").getValue().toString())) {
                subset.add(element);
            } else {
                subset.add(element);
                JSONObject newElement = new JSONObject();
                newElement.put("areaOfIntervention", areaOfIntervention);
                newElement.put("areaOfInterventionLabel", areaOfInterventionLabel);
                newElement.put("areaOfInterventionId", areaOfInterventionId);
                newElement.put("options", subset);
                areaOfIntervention = querySolution.getBinding("areaOfIntervention").getValue().toString();
                areaOfInterventionLabel = querySolution.getBinding("areaOfInterventionLabel").getValue().stringValue();
                areaOfInterventionId = querySolution.getBinding("areaOfInterventionId").getValue().toString();
                subset = new JSONArray();
                result.add(newElement);
            }
        }
        JSONObject newElement = new JSONObject();
        newElement.put("areaOfIntervention", areaOfIntervention);
        newElement.put("areaOfInterventionLabel", areaOfInterventionLabel);
        newElement.put("areaOfInterventionId", areaOfInterventionId);
        newElement.put("options", subset);
        result.add(newElement);
        return result;
    }

    @GetMapping(value = "/facet/eu/programs", produces = "application/json")
    public JSONArray facetEuPrograms(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "qid", required = false) String qid,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "interreg", required = false) Boolean interreg
    )
            throws Exception {
        logger.info("Get list of programs");
        String query = "SELECT DISTINCT ?program ?programLabel ?cci ?fund WHERE { ";
        if (qid != null) {
            query += " VALUES ?program { <" + qid + "> }";
        }
        query += " ?program <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2463047> . "
                + " ?program <https://linkedopendata.eu/prop/direct/P1367>  ?cci . "
                + " OPTIONAL{ ?program <https://linkedopendata.eu/prop/direct/P1584> ?fund .}";

        if (country != null) {
            query += " ?program <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }
        if (fund != null) {
            query += " ?program <https://linkedopendata.eu/prop/direct/P1584> ?fundFilter .";
            query += " FILTER(?fundFilter =<" + fund + ">) ";
        }
        if (region != null) {
            query += " { ?program <https://linkedopendata.eu/prop/direct/P2316> ?nuts. ";
            query += " <" + region + "> <https://linkedopendata.eu/prop/direct/P1845>*  ?nuts. } ";
            query += " UNION { ?program <https://linkedopendata.eu/prop/direct/P2316> <" + region + ">.} ";
        }

        if (interreg != null) {
            if (interreg) {
//                query += " FILTER((SUBSTR(?cci, 5 , 2 )) = \"TC\") ";
                query += " ?program <https://linkedopendata.eu/prop/direct/P579160> <https://linkedopendata.eu/entity/Q4554132> . ";
            } else {
//                query += " FILTER((SUBSTR(?cci, 5 , 2 )) != \"TC\") ";
                query += " FILTER NOT EXISTS {?program <https://linkedopendata.eu/prop/direct/P579160> <https://linkedopendata.eu/entity/Q4554132> .} ";
            }
        }

        query +=
                " ?program rdfs:label ?programLabel . "
                        + " FILTER (lang(?programLabel)=\""
                        + language
                        + "\")"
                        + "} order by ?cci ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();

            boolean found = false;
            String program = querySolution.getBinding("program").getValue().toString();
            for (Object o : result) {
                JSONObject element = (JSONObject) o;
                if (element.get("instance").equals(program) && (element.containsKey("funds"))) {
                    ((JSONArray) element.get("funds")).add(querySolution.getBinding("fund").getValue().stringValue());
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject element = new JSONObject();
                element.put("instance", program);
                element.put(
                        "instanceLabel",
                        querySolution.getBinding("cci").getValue().stringValue() + " - " + querySolution.getBinding("programLabel").getValue().stringValue()
                );
                JSONArray funds = new JSONArray();
                if (querySolution.getBinding("fund") != null) {
                    funds.add(querySolution.getBinding("fund").getValue().stringValue());
                }
                element.put("funds", funds);
                result.add(element);
            }
        }
        return result;
    }

    @GetMapping(value = "/facet/eu/thematic_objectives", produces = "application/json")
    public JSONArray facetEuThematicObjective( //
                                               @RequestParam(value = "language", defaultValue = "en") String language,
                                               @RequestParam(value = "policy", required = false) String policy,
                                               @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {

        logger.info("Get list of thematic objectives");
        String query = "SELECT ?to ?toLabel ?id ?policy ?policyId WHERE { ";
        if (qid != null) {
            query += " VALUES ?to { <" + qid + "> }";
        }
        query += " ?to <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q236700> . "
                + " ?to <https://linkedopendata.eu/prop/direct/P1105>  ?id . "
                + " ?to rdfs:label ?toLabel . "
                + " FILTER (LANG(?toLabel)=\""
                + language
                + "\")"
                + " OPTIONAL{?to <https://linkedopendata.eu/prop/direct/P1849> ?policy . "
                + " ?policy <https://linkedopendata.eu/prop/direct/P1747> ?policyId . }";

        if (policy != null) {
            query += " FILTER(?policy = <" + policy + "> ) . ";
        }
        query += "} ORDER BY ?id ";

        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            element.put("instance", querySolution.getBinding("to").getValue().toString());
            element.put("instanceLabel", querySolution.getBinding("toLabel").getValue().stringValue());
            element.put("id", querySolution.getBinding("id").getValue().stringValue());
//            element.put("policyId", querySolution.getBinding("policyId").getValue().stringValue());
            result.add(element);
        }
        return result;
    }

    @GetMapping(value = "/facet/eu/outermost_regions", produces = "application/json")
    public JSONArray facetOutermostRegions( //
                                            @RequestParam(value = "language", defaultValue = "en") String language,
                                            @RequestParam(value = "qid", required = false) String qid
    ) throws Exception {

        logger.info("Get list of outermost regions");
        String query = "PREFIX wd: <https://linkedopendata.eu/entity/>"
                + " PREFIX wdt: <https://linkedopendata.eu/prop/direct/>"
                + " SELECT ?instance ?instanceLabel ?instanceLabel_en ?country ?countryLabel WHERE { ";
        if (qid != null) {
            query += " VALUES ?instance { <" + qid + "> }";
        }
        query += " VALUES ?instance {wd:Q203 wd:Q204 wd:Q205 wd:Q206 wd:Q201 wd:Q2576740 wd:Q198 wd:Q209} "
                + " OPTIONAL { ?instance rdfs:label ?instanceLabel . "
                + " FILTER (lang(?instanceLabel)=\"" + language + "\") .} "
                + " OPTIONAL { ?instance rdfs:label ?instanceLabel_en . "
                + " FILTER (lang(?instanceLabel_en)=\"en\") . "
                + " ?instance wdt:P32 ?country . "
                + " ?country rdfs:label ?countryLabel . "
                + " FILTER (lang(?countryLabel)=\"" + language + "\") } . "
                + " } ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2);
        JSONArray result = new JSONArray();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject element = new JSONObject();
            element.put("instance", querySolution.getBinding("instance").getValue().toString());
            if (querySolution.getBinding("instanceLabel") != null) {
                element.put("instanceLabel", querySolution.getBinding("instanceLabel").getValue().stringValue());
            } else {
                element.put("instanceLabel", querySolution.getBinding("instanceLabel_en").getValue().stringValue());
            }
            element.put("country", querySolution.getBinding("country").getValue().toString());
            element.put("countryLabel", querySolution.getBinding("countryLabel").getValue().stringValue());
            result.add(element);
        }
        return result;
    }


    public JSONArray facetEuThematicObjective( //
                                               @RequestParam(value = "language", defaultValue = "en") String language
    ) throws Exception {
        return facetEuThematicObjective(language, null, null);
    }

    @GetMapping(value = "/facet/eu/loo_metadata", produces = "application/json")
    public JSONArray facetLooMetadata(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "country", required = false) String country
    ) throws Exception {
        String query = "SELECT DISTINCT ?list_of_operation_label ?list_of_operation_id "
                + " ?list_of_operation_qid ?list_of_operation_url ?list_of_operation_first_ingestion "
                + " ?list_of_operation_last_update ?cci ?country ?countryLabel ?countryCode "
                + " WHERE {"
                + " ?list_of_operation_qid <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q4552790>; "
                + "   <https://linkedopendata.eu/prop/direct/P578950> ?list_of_operation_id; "
                + "    rdfs:label ?list_of_operation_label_en; "
                + "    <https://linkedopendata.eu/prop/direct/P578951> ?list_of_operation_url; "
                + "    <https://linkedopendata.eu/prop/direct/P579181> ?prg. "
                + "  FILTER((LANG(?list_of_operation_label_en)) = \"en\") "
                + "  ?prg <https://linkedopendata.eu/prop/direct/P1367> ?cci. "
                + "  OPTIONAL { ?list_of_operation_qid rdfs:label ?list_of_operation_label_lg. FILTER((LANG(?list_of_operation_label_lg)) = \"" + language + "\")} "
                + "  OPTIONAL { ?list_of_operation_qid <https://linkedopendata.eu/prop/direct/P579182> ?list_of_operation_first_ingestion. } "
                + "  OPTIONAL { ?list_of_operation_qid <https://linkedopendata.eu/prop/direct/P579183> ?list_of_operation_last_update. } "
                + "  OPTIONAL { ?list_of_operation_qid <https://linkedopendata.eu/prop/direct/P32> ?country. ?country rdfs:label ?countryLabel; <https://linkedopendata.eu/prop/direct/P173> ?countryCode. FILTER(LANG(?countryLabel) = \"" + language + "\")}. "
                + "  BIND(IF(BOUND(?list_of_operation_label_lg), ?list_of_operation_label_lg, ?list_of_operation_label_en) AS ?list_of_operation_label) ";
        if (country != null) {
            query += "?list_of_operation_qid <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }
        query += "}";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 10);
        HashMap<String, JSONObject> resultMap = new HashMap<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String instanceQid = querySolution.getBinding("list_of_operation_qid").getValue().stringValue();
            JSONObject element;
            if (resultMap.containsKey(instanceQid)) {
                element = resultMap.get(instanceQid);
            } else {
                element = new JSONObject();
                element.put("instance", instanceQid);
                element.put(
                        "instanceLabel",
                        querySolution.getBinding("list_of_operation_label").getValue().stringValue()
                );
                element.put("id", querySolution.getBinding("list_of_operation_id").getValue().stringValue());
                element.put("url", querySolution.getBinding("list_of_operation_url").getValue().stringValue());

                if (querySolution.getBinding("list_of_operation_first_ingestion") != null) {
                    element.put("first_ingestion", querySolution.getBinding("list_of_operation_first_ingestion").getValue().stringValue());
                } else {
                    element.put("first_ingestion", null);
                }
                if (querySolution.getBinding("list_of_operation_last_update") != null) {
                    element.put("last_update", querySolution.getBinding("list_of_operation_last_update").getValue().stringValue());
                } else {
                    element.put("last_update", null);
                }
                if (querySolution.getBinding("list_of_operation_last_update") != null) {
                    element.put("last_update", querySolution.getBinding("list_of_operation_last_update").getValue().stringValue());
                } else {
                    element.put("last_update", null);
                }
                element.put("ccis", new JSONArray());
                element.put("country", new JSONArray());
                resultMap.put(instanceQid, element);
            }
            if (!((JSONArray) element.get("ccis")).contains(querySolution.getBinding("cci").getValue().stringValue())) {
                ((JSONArray) element.get("ccis")).add(querySolution.getBinding("cci").getValue().stringValue());
            }
            if (querySolution.getBinding("country") != null) {
                JSONObject objectCountry = new JSONObject();
                objectCountry.put("qid", querySolution.getBinding("country").getValue().stringValue());
                objectCountry.put("label", querySolution.getBinding("countryLabel").getValue().stringValue());
                objectCountry.put("code", querySolution.getBinding("countryCode").getValue().stringValue());

                if (!((JSONArray) element.get("country")).contains(objectCountry)) {
                    ((JSONArray) element.get("country")).add(objectCountry);
                }
            }
        }

        JSONArray result = new JSONArray();
        resultMap.forEach((s, jsonObject) -> {
            if (((JSONArray)jsonObject.get("country")).size() > 1) {
                JSONObject objectCountry = new JSONObject();
                objectCountry.put("qid", null);
                objectCountry.put("label", "European Territorial Cooperation");
                objectCountry.put("code", "TC");
                jsonObject.put("country", objectCountry);
            }
            else {
                jsonObject.put("country", ((JSONArray) jsonObject.get("country")).get(0));
            }
            result.add(jsonObject);
        });
        return result;
    }
}