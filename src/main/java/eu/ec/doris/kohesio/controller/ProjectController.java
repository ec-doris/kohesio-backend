package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ec.doris.kohesio.payload.NutsRegion;
import eu.ec.doris.kohesio.payload.Project;
import eu.ec.doris.kohesio.payload.ProjectList;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.util.Precision;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mapstruct.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")

public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @Value("${kohesio.sparqlEndpointNuts}")
    String getSparqlEndpointNuts;

    @Value("${kohesio.directory}")
    String cacheDirectory;

    // Set this to allow browser requests from other websites
    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/project", produces = "application/json")
    public ResponseEntity euProjectID( //
                                       @RequestParam(value = "id") String id,
                                       @RequestParam(value = "language", defaultValue = "en") String language)
            throws Exception {

        String queryCheck = "ASK {\n" +
                " <" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> " +
                "}";


        boolean resultAsk = sparqlQueryService.executeBooleanQuery("https://query.linkedopendata.eu/bigdata/namespace/wdq/sparql", queryCheck, 2);
        if (!resultAsk) {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - project ID not found");
            return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
        } else {
            String query =
                    "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?expectedEndTime ?budget ?euBudget ?cofinancingRate ?image ?imageCopyright ?video ?coordinates  ?countryLabel ?countryCode ?programLabel ?categoryLabel ?fundLabel ?objectiveId ?objectiveLabel ?managingAuthorityLabel ?beneficiaryLink ?beneficiary ?beneficiaryLabelRight ?beneficiaryLabel ?beneficiaryWikidata ?beneficiaryWebsite ?source ?source2 ?regionId ?regionLabel ?regionUpper1Label ?regionUpper2Label ?regionUpper3Label where { "
                            + " VALUES ?s0 { <"
                            + id
                            + "> } "
                            + " ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                            + " FILTER((LANG(?label)) = \""
                            + language
                            + "\") "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P836> ?description. FILTER((LANG(?description)) = \""
                            + language
                            + "\") } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P838> ?expectedEndTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P837> ?cofinancingRate. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P851> ?blank . "
                            + " ?blank <https://linkedopendata.eu/prop/statement/P851> ?image . "
//                    + " ?blank <https://linkedopendata.eu/prop/qualifier/P836> ?summary . "
                            + " ?blank <https://linkedopendata.eu/prop/qualifier/P1743> ?imageCopyright . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1746> ?video . }"
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1360> ?sou . "
                            + " BIND(CONCAT(\"http://www.opencoesione.gov.it/progetti/\",STR( ?sou )) AS ?source ) . }"
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country . "
                            + "            ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . "
                            + "             ?country <http://www.w3.org/2000/01/rdf-schema#label> ?countryLabel. "
                            + "             FILTER((LANG(?countryLabel)) = \""
                            + language
                            + "\") }"
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1368> ?program ."
                            + "             ?program <https://linkedopendata.eu/prop/direct/P1586> ?managingAuthority. "
                            + "             ?program <http://www.w3.org/2000/01/rdf-schema#label> ?programLabel. "
                            + "             FILTER((LANG(?programLabel)) = \""
                            + language
                            + "\") ."
                            + "             ?managingAuthority <http://www.w3.org/2000/01/rdf-schema#label> ?managingAuthorityLabel. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1368> ?program ."
                            + "             ?program <https://linkedopendata.eu/prop/direct/P1750> ?source2 . }"
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P888> ?category ."
                            + "             ?category <http://www.w3.org/2000/01/rdf-schema#label> ?categoryLabel. "
                            + "             FILTER((LANG(?categoryLabel)) = \""
                            + language
                            + "\") }"
                            + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P888> ?category.  "
                            + "           ?category <https://linkedopendata.eu/prop/direct/P1848> ?objective."
                            + "           ?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId. "
                            + "           ?objective <http://www.w3.org/2000/01/rdf-schema#label> ?objectiveLabel. "
                            + "           FILTER((LANG(?objectiveLabel)) = \""
                            + language
                            + "\") } "
                            + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1584> ?fund.  "
                            + "           ?fund <http://www.w3.org/2000/01/rdf-schema#label> ?fundLabel. "
                            + "           FILTER((LANG(?fundLabel)) = \""
                            + language
                            + "\") } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P889> ?beneficiaryLink . "
                            + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabelRight . "
                            + "             FILTER(LANG(?beneficiaryLabelRight) = \"" + language + "\" ) } "
                            + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . }"
                            + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P1> ?beneficiaryID .  "
                            + "          BIND(CONCAT(\"http://wikidata.org/entity/\",STR( ?beneficiaryID )) AS ?beneficiaryWikidata ) . }"
                            + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P67> ?beneficiaryWebsite . } } "
                            + "         OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1845> ?region .  "
                            + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P192> ?regionId .} "
                            + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P35> ?regionType . "
                            + "             FILTER( ?regionType = <https://linkedopendata.eu/entity/Q2576750>  || ?regionType = <https://linkedopendata.eu/entity/Q2576674> )"
                            + "             ?region <http://www.w3.org/2000/01/rdf-schema#label> ?regionLabel . "
                            + "             FILTER((LANG(?regionLabel)) = \"" + language + "\") }"
                            + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper1 .  "
                            + "             ?regionUpper1 <https://linkedopendata.eu/prop/direct/P35>  ?regionType1 . "
                            + "             FILTER(?regionType1 = <https://linkedopendata.eu/entity/Q2576674> || ?regionType1 = <https://linkedopendata.eu/entity/Q2576630>)"
                            + "             ?regionUpper1 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper1Label . "
                            + "             FILTER((LANG(?regionUpper1Label)) = \"" + language + "\") } "
                            + "           OPTIONAL {?regionUpper1 <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper2 ."
                            + "             ?regionUpper2 <https://linkedopendata.eu/prop/direct/P35> ?regionType2 . "
                            + "             FILTER(?regionType2 = <https://linkedopendata.eu/entity/Q2576630> || ?regionType2 = <https://linkedopendata.eu/entity/Q510>)"
                            + "             ?regionUpper2 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper2Label . "
                            + "             FILTER((LANG(?regionUpper2Label)) = \"" + language + "\") }  "
                            + "           OPTIONAL { ?regionUpper2 <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper3 . "
                            + "           ?regionUpper3 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper3Label . "
                            + "           ?regionUpper3 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q510> ."
                            + "           FILTER((LANG(?regionUpper3Label)) = \"" + language + "\") }} "
                            + "} ";
            logger.info("Retrieving results");
            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery("https://query.linkedopendata.eu/bigdata/namespace/wdq/sparql", query, 2, false);
            logger.info("Executed");

            JSONObject result = new JSONObject();
            result.put("item", id.replace("https://linkedopendata.eu/entity/", ""));
            result.put("link", id);
            result.put("label", "");
            result.put("description", "");
            result.put("startTime", "");
            result.put("endTime", "");
            result.put("budget", "");
            result.put("euBudget", "");
            result.put("cofinancingRate", "");
            result.put("countryLabel", "");
            result.put("countryCode", "");
            result.put("categoryLabel", "");
            result.put("fundLabel", "");
            result.put("programmingPeriodLabel", "2014-2020");
            result.put("programLabel", "");
            result.put("programWebsite", "");
            result.put("objectiveId", "");
            result.put("objectiveLabel", "");
            result.put("projectWebsite", "");
            result.put("coordinates", new JSONArray());
            result.put("images", new JSONArray());
            result.put("videos", new JSONArray());
            result.put("beneficiaries", new JSONArray());
            result.put("managingAuthorityLabel", "");
            result.put("region", "");
            result.put("geoJson", "");
            result.put("regionUpper1", "");
            result.put("regionUpper2", "");
            result.put("regionUpper3", "");


            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();

                if (querySolution.getBinding("budget") != null) {
                    result.put(
                            "budget", ((Literal) querySolution.getBinding("budget").getValue()).stringValue());
                }

                if (querySolution.getBinding("label") != null) {
                    result.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
                }

                if (querySolution.getBinding("description") != null) {
                    result.put(
                            "description",
                            ((Literal) querySolution.getBinding("description").getValue()).getLabel());
                }
                //
                if (querySolution.getBinding("startTime") != null) {
                    result.put(
                            "startTime",
                            ((Literal) querySolution.getBinding("startTime").getValue())
                                    .stringValue()
                                    .split("T")[0]);
                }

                if (querySolution.getBinding("endTime") != null) {
                    result.put(
                            "endTime",
                            ((Literal) querySolution.getBinding("endTime").getValue()).stringValue().split("T")[0]);
                }

                if (querySolution.getBinding("endTime") == null && querySolution.getBinding("expectedEndTime") != null) {
                    result.put(
                            "endTime",
                            ((Literal) querySolution.getBinding("expectedEndTime").getValue()).stringValue().split("T")[0]);
                }

                if (querySolution.getBinding("budget") != null) {
                    result.put(
                            "budget", ((Literal) querySolution.getBinding("budget").getValue()).stringValue());
                }

                if (querySolution.getBinding("euBudget") != null) {
                    result.put(
                            "euBudget", ((Literal) querySolution.getBinding("euBudget").getValue()).stringValue());
                }

                if (querySolution.getBinding("cofinancingRate") != null) {
                    result.put(
                            "cofinancingRate",
                            ((Literal) querySolution.getBinding("cofinancingRate").getValue())
                                    .stringValue()
                                    .replace("+", ""));
                }

                if (querySolution.getBinding("countryLabel") != null) {
                    result.put(
                            "countryLabel",
                            ((Literal) querySolution.getBinding("countryLabel").getValue()).stringValue());
                }

                if (querySolution.getBinding("countryCode") != null) {
                    result.put(
                            "countryCode",
                            ((Literal) querySolution.getBinding("countryCode").getValue()).stringValue());
                }

                if (querySolution.getBinding("categoryLabel") != null) {
                    result.put(
                            "categoryLabel",
                            ((Literal) querySolution.getBinding("categoryLabel").getValue()).stringValue());
                }

                if (querySolution.getBinding("fundLabel") != null) {
                    result.put(
                            "fundLabel",
                            ((Literal) querySolution.getBinding("fundLabel").getValue()).stringValue());
                }

                if (querySolution.getBinding("programLabel") != null) {
                    result.put(
                            "programLabel",
                            ((Literal) querySolution.getBinding("programLabel").getValue()).stringValue());
                }

                if (querySolution.getBinding("objectiveId") != null) {
                    result.put(
                            "objectiveId",
                            ((Literal) querySolution.getBinding("objectiveId").getValue()).stringValue());
                }

                if (querySolution.getBinding("objectiveLabel") != null) {
                    result.put(
                            "objectiveLabel",
                            ((Literal) querySolution.getBinding("objectiveLabel").getValue()).stringValue());
                }

                if (querySolution.getBinding("source") != null) {
                    result.put(
                            "projectWebsite", ((Literal) querySolution.getBinding("source").getValue()).stringValue());
                }
                if (querySolution.getBinding("source2") != null) {
                    result.put(
                            "programWebsite", querySolution.getBinding("source2").getValue().stringValue());
                }


                if (querySolution.getBinding("coordinates") != null) {
                    JSONArray coordinates = (JSONArray) result.get("coordinates");
                    String coo = ((Literal) querySolution.getBinding("coordinates").getValue()).stringValue();
                    if (!coordinates.contains(coo.replace("Point(", "").replace(")", "").replace(" ", ","))) {
                        coordinates.add(coo);
                        result.put("coordinates", coordinates);
                    }
                }

                if (querySolution.getBinding("image") != null) {
                    JSONArray images = (JSONArray) result.get("images");
                    JSONObject image = new JSONObject();
                    String im = querySolution.getBinding("image").getValue().stringValue();
                    boolean found = false;
                    for (Object i : images) {
                        if (((JSONObject) i).get("image").toString().equals(im) && found == false) {
                            found = true;
                        }
                    }
                    if (found == false) {
                        image.put("image", im);
                        if (querySolution.getBinding("imageCopyright") != null) {
                            image.put("imageCopyright", querySolution.getBinding("imageCopyright").getValue().stringValue());
                        }
                        images.add(image);
                    }
                    result.put("images", images);
                }


                if (querySolution.getBinding("video") != null) {
                    JSONArray images = (JSONArray) result.get("videos");
                    String im = querySolution.getBinding("video").getValue().stringValue();
                    if (!images.contains(im)) {
                        images.add(im);
                        result.put("videos", images);
                    }
                }

                if (querySolution.getBinding("beneficiaryLink") != null) {
                    JSONArray beneficiaries = (JSONArray) result.get("beneficiaries");
                    String ben = querySolution.getBinding("beneficiaryLink").getValue().stringValue();
                    boolean found = false;
                    for (int i = 0; i < beneficiaries.size(); i++) {
                        if (((JSONObject) beneficiaries.get(i)).get("link").equals(ben)) {
                            found = true;
                        }
                    }
                    if (found == false) {
                        JSONObject beneficary = new JSONObject();
                        beneficary.put("link", ben);
                        if (querySolution.getBinding("beneficiaryLabelRight") != null) {
                            String label =
                                    ((Literal) querySolution.getBinding("beneficiaryLabelRight").getValue()).stringValue();
                            beneficary.put("beneficiaryLabel", label);
                        } else if (querySolution.getBinding("beneficiaryLabel") != null){
                            String label =
                                    ((Literal) querySolution.getBinding("beneficiaryLabel").getValue()).stringValue();
                            beneficary.put("beneficiaryLabel", label);
                        } else {
                            beneficary.put("beneficiaryLabel", "");
                        }
                        if (querySolution.getBinding("beneficiaryWikidata") != null) {
                            String benID =
                                    ((Literal) querySolution.getBinding("beneficiaryWikidata").getValue())
                                            .stringValue();
                            beneficary.put("wikidata", benID);
                        } else {
                            beneficary.put("wikidata", "");
                        }
                        if (querySolution.getBinding("beneficiaryWebsite") != null) {
                            String benID =
                                    querySolution.getBinding("beneficiaryWebsite").getValue().stringValue();
                            beneficary.put("website", benID);
                        } else {
                            beneficary.put("website", "");
                        }
                        beneficiaries.add(beneficary);
                    }
                }

                if (querySolution.getBinding("managingAuthorityLabel") != null) {
                    result.put(
                            "managingAuthorityLabel",
                            ((Literal) querySolution.getBinding("managingAuthorityLabel").getValue())
                                    .stringValue());
                }
                if (querySolution.getBinding("regionLabel") != null) {
                    result.put("region", ((Literal) querySolution.getBinding("regionLabel").getValue())
                            .stringValue());
                }
                if (querySolution.getBinding("regionUpper1Label") != null) {
                    result.put("regionUpper1", ((Literal) querySolution.getBinding("regionUpper1Label").getValue())
                            .stringValue());
                }
                if (querySolution.getBinding("regionUpper2Label") != null) {
                    result.put("regionUpper2", ((Literal) querySolution.getBinding("regionUpper2Label").getValue())
                            .stringValue());
                }
                if (querySolution.getBinding("regionUpper3Label") != null) {
                    result.put("regionUpper3", ((Literal) querySolution.getBinding("regionUpper3Label").getValue())
                            .stringValue());
                }
                if (result.get("region") != "") {
                    String regionText = (String) result.get("region");
                    if (!((String) result.get("region")).equals(((String) result.get("regionUpper1")))) {
                        regionText += ", " + (String) result.get("regionUpper1");
                    }
                    if (!((String) result.get("regionUpper1")).equals(((String) result.get("regionUpper2")))) {
                        regionText += ", " + (String) result.get("regionUpper2");
                    }
                    if (!result.get("regionUpper3").equals("") && !((String) result.get("regionUpper2")).equals(((String) result.get("regionUpper3")))) {
                        regionText += ", " + (String) result.get("regionUpper3");
                    }
                    result.put("regionText", regionText);
                } else {

                    result.put("regionText", (String) result.get("countryLabel"));
                }

                if (querySolution.getBinding("regionId") != null && result.get("geoJson").equals("")) {
                    query =
                            "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                                    + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                                    + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                                    + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                                    + "SELECT ?id ?geoJson  WHERE { "
                                    + "?s <http://nuts.de/id> \'" + ((Literal) querySolution.getBinding("regionId").getValue()).stringValue() + "\' . "
                                    + "?s <http://nuts.de/geoJson> ?geoJson . "

                                    + "}";
                    logger.info(query);
                    logger.info("Retrieving nuts geometry");
                    TupleQueryResult resultSet2 = sparqlQueryService.executeAndCacheQuery(getSparqlEndpointNuts, query, 5);
                    logger.info("Retrieved");

                    NutsRegion nutsRegion = new NutsRegion();
                    while (resultSet2.hasNext()) {
                        BindingSet querySolution2 = resultSet2.next();
                        if (querySolution2.getBinding("geoJson") != null) {
                            result.put("geoJson", ((Literal) querySolution2.getBinding("geoJson").getValue())
                                    .stringValue());
                        }

                    }
                }
            }
            return new ResponseEntity<JSONObject>(result, HttpStatus.OK);
        }
    }

    @GetMapping(value = "/facet/eu/search/project", produces = "application/json")
    public ResponseEntity euSearchProject( //
                                           @RequestParam(value = "language", defaultValue = "en") String language,
                                           @RequestParam(value = "keywords", required = false) String keywords, //
                                           @RequestParam(value = "country", required = false) String country,
                                           @RequestParam(value = "theme", required = false) String theme,
                                           @RequestParam(value = "fund", required = false) String fund,
                                           @RequestParam(value = "program", required = false) String program,
                                           @RequestParam(value = "categoryOfIntervention", required = false)
                                                   String categoryOfIntervention,
                                           @RequestParam(value = "policyObjective", required = false) String policyObjective,
                                           @RequestParam(value = "budgetBiggerThan", required = false) Integer budgetBiggerThen,
                                           @RequestParam(value = "budgetSmallerThan", required = false) Integer budgetSmallerThen,
                                           @RequestParam(value = "budgetEUBiggerThan", required = false) Integer budgetEUBiggerThen,
                                           @RequestParam(value = "budgetEUSmallerThan", required = false) Integer budgetEUSmallerThen,
                                           @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
                                           @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
                                           @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
                                           @RequestParam(value = "endDateAfter", required = false) String endDateAfter,

                                           @RequestParam(value = "orderStartDate", required = false) Boolean orderStartDate,
                                           @RequestParam(value = "orderEndDate", required = false) Boolean orderEndDate,
                                           @RequestParam(value = "orderEuBudget", required = false) Boolean orderEuBudget,
                                           @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,

                                           @RequestParam(value = "latitude", required = false) String latitude,
                                           @RequestParam(value = "longitude", required = false) String longitude,
                                           @RequestParam(value = "region", required = false) String region,
                                           @RequestParam(value = "limit", defaultValue = "200") int limit,
                                           @RequestParam(value = "offset", defaultValue = "0") int offset,
                                           Principal principal)
            throws Exception {
        logger.info("Project search: language {}, keywords {}, country {}, theme {}, fund {}, region {}", language, keywords, country, theme, fund, region);

        int inputOffset = offset;
        int inputLimit = limit;
        if (offset < 1000) {
            offset = 0;
            limit = 1000;
        }
        String search = filterProject(keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset);


        String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + search + "} ";
        System.out.println(query);
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 25);
        int numResults = 0;
        if (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }


        String orderQuery = "";

        String orderBy = "";
        if (orderStartDate != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime .";
            if (orderStartDate) {
                orderBy = "order by asc(?startTime)";
            } else {
                orderBy = "order by desc(?startTime)";
            }
        }
        if (orderEndDate != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime .";
            if (orderEndDate) {
                orderBy = "order by asc(?endTime)";
            } else {
                orderBy = "order by desc(?endTime)";
            }
        }
        if (orderEuBudget != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. ";
            if (orderEuBudget) {
                orderBy = "order by asc(?euBudget)";
            } else {
                orderBy = "order by desc(?euBudget)";
            }
        }
        if (orderTotalBudget != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P474> ?totalBudget. ";
            if (orderTotalBudget) {
                orderBy = "order by asc(?totalBudget)";
            } else {
                orderBy = "order by desc(?totalBudget)";
            }
        }
        if (search.equals(
                "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ")) {
            search += " { SELECT ?s0 ?snippet where { " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . " +
                    "    } " +
                    "  } UNION { SELECT ?s0 ?snippet where { " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> ." +
                    "    } " +
                    "    }";
        }
//        if(keywords != null && country == null && theme == null && fund == null && program == null && categoryOfIntervention == null
//        && policyObjective == null && budgetBiggerThen == null && budgetSmallerThen == null && budgetEUBiggerThen == null &&
//                budgetEUSmallerThen == null && startDateBefore == null &&
//                startDateAfter == null && endDateBefore == null && endDateAfter == null && latitude == null && longitude == null && region == null){
//
//            // pass cache = false in order to stop caching the semantic search results
//            ArrayList<String> projectsURIs = getProjectsURIsfromSemanticSearch(keywords,true);
//            if(projectsURIs.size() > 0) {
//                search = "";
//                search += "VALUES ?s0 {";
//                for (String uri : projectsURIs) {
//                    String uriStr = "<"+uri+">";
//                    search+= uriStr+" ";
//                }
//                search+="}";
//            }else{
//                System.out.println("Semantic search API returned empty result!!");
//            }
//            numResults = projectsURIs.size();
//            //search = "";
//        }
        search += " " + orderQuery;
        query =
                "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?expectedEndTime ?totalBudget ?euBudget ?image ?coordinates ?objectiveId ?countrycode where { "
                        + " { SELECT ?s0 ?snippet where { "
                        + search
                        + " } " + orderBy + " limit "
                        + limit
                        + " offset "
                        + offset
                        + " } "
                        + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                        + " FILTER((LANG(?label)) = \""
                        + language
                        + "\") }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P836> ?description. FILTER((LANG(?description)) = \""
                        + language
                        + "\") } "
                        + "OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P838> ?expectedEndTime . }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime . } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime . } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. } "
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P474> ?totalBudget. }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country . ?country 	<https://linkedopendata.eu/prop/direct/P173> ?countrycode .} "
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P888> ?category .  ?category <https://linkedopendata.eu/prop/direct/P1848> ?objective. ?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId. } "
                        + "} ";
        System.out.println(query);
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30);

        ArrayList<Project> resultList = new ArrayList<Project>();
        String previewsKey = "";
        Set<String> snippet = new HashSet<>();
        Set<String> label = new HashSet<>();
        Set<String> description = new HashSet<>();
        Set<String> startTime = new HashSet<>();
        Set<String> endTime = new HashSet<>();
        Set<String> euBudget = new HashSet<>();
        Set<String> totalBudget = new HashSet<>();
        Set<String> image = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        Set<String> objectiveId = new HashSet<>();
        Set<String> countrycode = new HashSet<>();
        boolean hasEntry = resultSet.hasNext();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String currentKey = querySolution.getBinding("s0").getValue().stringValue();
            if (!previewsKey.equals(currentKey)) {
                if (!previewsKey.equals("")) {
                    Project project = new Project();
                    project.setItem(previewsKey.replace("https://linkedopendata.eu/entity/", ""));
                    project.setLink(previewsKey);
                    project.setSnippet(new ArrayList<String>(snippet));
                    project.setLabels(new ArrayList<String>(label));
                    project.setDescriptions(new ArrayList<String>(description));
                    project.setStartTimes(new ArrayList<String>(startTime));
                    project.setEndTimes(new ArrayList<String>(endTime));
                    project.setEuBudgets(new ArrayList<String>(euBudget));
                    project.setTotalBudgets(new ArrayList<String>(totalBudget));
                    project.setImages(new ArrayList<String>(image));
                    project.setCoordinates(new ArrayList<String>(coordinates));
                    project.setObjectiveIds(new ArrayList<String>(objectiveId));
                    project.setCountrycode(new ArrayList<String>(countrycode));
                    resultList.add(project);
                    snippet = new HashSet<>();
                    label = new HashSet<>();
                    description = new HashSet<>();
                    startTime = new HashSet<>();
                    endTime = new HashSet<>();
                    euBudget = new HashSet<>();
                    totalBudget = new HashSet<>();
                    image = new HashSet<>();
                    coordinates = new HashSet<>();
                    objectiveId = new HashSet<>();
                    countrycode = new HashSet<>();
                }
                previewsKey = querySolution.getBinding("s0").getValue().stringValue();
            }
            if (querySolution.getBinding("snippet") != null) {
                String s = ((Literal) querySolution.getBinding("snippet").getValue()).getLabel();
                if (!s.endsWith(".")) {
                    s += "...";
                }
                snippet.add(s);
            }


            if (querySolution.getBinding("label") != null)
                label.add(((Literal) querySolution.getBinding("label").getValue()).getLabel());
            if (querySolution.getBinding("description") != null)
                description.add(((Literal) querySolution.getBinding("description").getValue()).getLabel());
            if (querySolution.getBinding("startTime") != null)
                startTime.add(
                        ((Literal) querySolution.getBinding("startTime").getValue()).getLabel().split("T")[0]);
            if (querySolution.getBinding("endTime") != null)
                endTime.add(
                        ((Literal) querySolution.getBinding("endTime").getValue()).getLabel().split("T")[0]);
            if (querySolution.getBinding("endTime") == null && querySolution.getBinding("expectedEndTime") != null) {
                endTime.add(((Literal) querySolution.getBinding("expectedEndTime").getValue()).stringValue().split("T")[0]);
            }
            if (querySolution.getBinding("euBudget") != null)
                euBudget.add(
                        String.valueOf(
                                Precision.round(((Literal) querySolution.getBinding("euBudget").getValue()).doubleValue(), 2)));
            if (querySolution.getBinding("totalBudget") != null)
                totalBudget.add(String.valueOf(
                        Precision.round(((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue(), 2)));

            if (querySolution.getBinding("image") != null) {
                image.add(querySolution.getBinding("image").getValue().stringValue());
            }
            if (querySolution.getBinding("coordinates") != null) {
                coordinates.add(
                        ((Literal) querySolution.getBinding("coordinates").getValue())
                                .getLabel()
                                .replace("Point(", "")
                                .replace(")", "")
                                .replace(" ", ","));
            }
            if (querySolution.getBinding("objectiveId") != null)
                objectiveId.add(((Literal) querySolution.getBinding("objectiveId").getValue()).getLabel());
            if (querySolution.getBinding("countrycode") != null)
                countrycode.add(((Literal) querySolution.getBinding("countrycode").getValue()).getLabel());
        }
        if (hasEntry) {
            Project project = new Project();
            project.setItem(previewsKey.replace("https://linkedopendata.eu/entity/", ""));
            project.setLink(previewsKey);
            project.setSnippet(new ArrayList<String>(snippet));
            project.setLabels(new ArrayList<String>(label));
            project.setDescriptions(new ArrayList<String>(description));
            project.setStartTimes(new ArrayList<String>(startTime));
            project.setEndTimes(new ArrayList<String>(endTime));
            project.setEuBudgets(new ArrayList<String>(euBudget));
            project.setTotalBudgets(new ArrayList<String>(totalBudget));
            project.setImages(new ArrayList<String>(image));
            project.setCoordinates(new ArrayList<String>(coordinates));
            project.setObjectiveIds(new ArrayList<String>(objectiveId));
            project.setCountrycode(new ArrayList<String>(countrycode));
            resultList.add(project);
        }
        ProjectList projectList = new ProjectList();
        if(offset <= 990) {
            for (int i = inputOffset; i < Math.min(resultList.size(), inputOffset + inputLimit); i++) {
                projectList.getList().add(resultList.get(i));
            }
        }else{
            projectList.setList(resultList);
        }
        projectList.setNumberResults(numResults);
        return new ResponseEntity<ProjectList>(projectList, HttpStatus.OK);
    }

    private ArrayList<String> getProjectsURIsfromSemanticSearch(String keywords,boolean cache) {
        String url = null;
        try {
            url = "http://54.74.15.102:5000/search?text=" + URLEncoder.encode(keywords, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String path = cacheDirectory+"/facet/semantic-search";
        File dir = new File(path);

        System.out.println("The directory of cache: "+dir.getAbsolutePath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String query = url;
        if(dir.exists() && cache){
            try {
                ArrayList<String> projectsURIs = new ArrayList<>();
                Scanner input = new Scanner(new File(path+"/"+query.hashCode()));
                while (input.hasNext()){
                    String projectURI  = input.next();
                    projectsURIs.add(projectURI);
                }
                return projectsURIs;
            } catch (FileNotFoundException e) {
                logger.error("Could not find cache file, probably not cahced");
            }
        }

        ArrayList<String> listProjectURIs = new ArrayList<>();
        try {
            System.out.println(url);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                FileOutputStream out = new FileOutputStream(path+"/"+ query.hashCode());
                System.out.println(response.getBody());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                if (root.findValue("results") != null) {
                    JsonNode results = root.findValue("results");
                    for (int i = 0; i < results.size(); i++) {
                        String projectURI = results.get(i).textValue();
                        listProjectURIs.add(projectURI);
                        projectURI+="\n";
                        out.write(projectURI.getBytes());
                    }
                }
                out.close();
            } else {
                System.err.println("Error in HTTP request!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listProjectURIs;
    }

    @GetMapping(value = "/facet/eu/search/project/image", produces = "application/json")
    public ResponseEntity euSearchProjectImage(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "keywords", required = false) String keywords, //
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "theme", required = false) String theme,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "categoryOfIntervention", required = false)
                    String categoryOfIntervention,
            @RequestParam(value = "policyObjective", required = false) String policyObjective,
            @RequestParam(value = "budgetBiggerThan", required = false) Integer budgetBiggerThen,
            @RequestParam(value = "budgetSmallerThan", required = false) Integer budgetSmallerThen,
            @RequestParam(value = "budgetEUBiggerThan", required = false) Integer budgetEUBiggerThen,
            @RequestParam(value = "budgetEUSmallerThan", required = false) Integer budgetEUSmallerThen,
            @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
            @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
            @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
            @RequestParam(value = "endDateAfter", required = false) String endDateAfter,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            Principal principal)
            throws Exception {
        logger.info("language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, null);
        String search = filterProject(keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset);

        //computing the number of results
        String searchCount = search;
        searchCount += " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?title. ";
        String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + searchCount
                + " FILTER((LANG(?title)) = \""
                + language
                + "\") "
                +"} ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 25);
        int numResults = 0;
        if (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }
        logger.info("Number of results {}", numResults);

        query =
                "SELECT ?s0 ?image ?title where { "
                        + search
                        + " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. "
                        + " ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?title. "
                        + " FILTER((LANG(?title)) = \""
                        + language
                        + "\") "
                        + " } limit "
                        + limit
                        + " offset "
                        + offset;
        logger.info(query);
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 10);

        JSONArray resultList = new JSONArray();
        Set<String> images = new HashSet<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject item = new JSONObject();
            item.put("item", querySolution.getBinding("s0").getValue().stringValue());
            item.put("image", querySolution.getBinding("image").getValue().stringValue());
            item.put("title", querySolution.getBinding("title").getValue().stringValue());

            resultList.add(item);
        }
        JSONObject result = new JSONObject();
        result.put("list", resultList);
        result.put("numberResults", numResults);
        return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
    }


    @GetMapping(value = "/facet/eu/search/project/excel", produces = "application/json")
    public ResponseEntity<byte[]> euSearchProjectExcel( //
                                                        @RequestParam(value = "language", defaultValue = "en") String language,
                                                        @RequestParam(value = "keywords", required = false) String keywords, //
                                                        @RequestParam(value = "country", required = false) String country,
                                                        @RequestParam(value = "theme", required = false) String theme,
                                                        @RequestParam(value = "fund", required = false) String fund,
                                                        @RequestParam(value = "program", required = false) String program,
                                                        @RequestParam(value = "categoryOfIntervention", required = false)
                                                                String categoryOfIntervention,
                                                        @RequestParam(value = "policyObjective", required = false) String policyObjective,
                                                        @RequestParam(value = "budgetBiggerThan", required = false) Integer budgetBiggerThen,
                                                        @RequestParam(value = "budgetSmallerThan", required = false) Integer budgetSmallerThen,
                                                        @RequestParam(value = "budgetEUBiggerThan", required = false) Integer budgetEUBiggerThen,
                                                        @RequestParam(value = "budgetEUSmallerThan", required = false) Integer budgetEUSmallerThen,
                                                        @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
                                                        @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
                                                        @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
                                                        @RequestParam(value = "endDateAfter", required = false) String endDateAfter,

                                                        @RequestParam(value = "orderStartDate", required = false) Boolean orderStartDate,
                                                        @RequestParam(value = "orderEndDate", required = false) Boolean orderEndDate,
                                                        @RequestParam(value = "orderEuBudget", required = false) Boolean orderEuBudget,
                                                        @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,

                                                        @RequestParam(value = "latitude", required = false) String latitude,
                                                        @RequestParam(value = "longitude", required = false) String longitude,
                                                        @RequestParam(value = "region", required = false) String region,
                                                        @RequestParam(value = "limit", defaultValue = "200") int limit,
                                                        @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                        Principal principal,
                                                        @Context HttpServletResponse response)
            throws Exception {
        ProjectList projectList =
                (ProjectList) euSearchProject(language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, orderStartDate, orderEndDate, orderEuBudget, orderTotalBudget, latitude, longitude, region, Math.max(limit, 1000), 0, principal).getBody();
        XSSFWorkbook hwb = new XSSFWorkbook();
        XSSFSheet sheet = hwb.createSheet("beneficiary_export");
        int rowNumber = 0;
        XSSFRow row = sheet.createRow(rowNumber);
        XSSFCell cell = row.createCell(0);
        cell.setCellValue("ID");
        cell = row.createCell(1);
        cell.setCellValue("PROJECT NAME");
        cell = row.createCell(2);
        cell.setCellValue("TOTAL BUDGET");
        cell = row.createCell(3);
        cell.setCellValue("AMOUNT EU SUPPORT");
        cell = row.createCell(4);
        cell.setCellValue("START DATE");
        cell = row.createCell(5);
        cell.setCellValue("END DATE");
        cell = row.createCell(6);
        cell.setCellValue("COUNTRY");

        for (Project project : projectList.getList()) {
            rowNumber++;
            row = sheet.createRow(rowNumber);
            cell = row.createCell(0);
            cell.setCellValue(project.getItem());
            cell = row.createCell(1);
            cell.setCellValue(String.join("|", project.getLabels()));
            cell = row.createCell(2);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
            cell.setCellValue(Double.parseDouble(project.getTotalBudgets().get(0)));
            cell = row.createCell(3);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
            cell.setCellValue(Double.parseDouble(project.getEuBudgets().get(0)));
            cell = row.createCell(4);
            if (project.getStartTimes().size() > 0) {
                cell.setCellValue(project.getStartTimes().get(0));
            }
            cell = row.createCell(5);
            if (project.getEndTimes().size() > 0) {
                cell.setCellValue(project.getEndTimes().get(0));
            }
            cell = row.createCell(6);
            cell.setCellValue(String.join("|", project.getCountrycode()));

        }
        ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
        hwb.write(fileOut);
        fileOut.close();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/vnd.ms-excel");
        headers.set("Content-Disposition", "attachment; filename=\"beneficiary_export.xlsx\"");
        return new ResponseEntity<byte[]>(fileOut.toByteArray(), headers, HttpStatus.OK);
    }

    @GetMapping(value = "/facet/eu/search/project/csv", produces = "application/json")
    public void euSearchProjectCSV( //
                                    @RequestParam(value = "language", defaultValue = "en") String language,
                                    @RequestParam(value = "keywords", required = false) String keywords, //
                                    @RequestParam(value = "country", required = false) String country,
                                    @RequestParam(value = "theme", required = false) String theme,
                                    @RequestParam(value = "fund", required = false) String fund,
                                    @RequestParam(value = "program", required = false) String program,
                                    @RequestParam(value = "categoryOfIntervention", required = false)
                                            String categoryOfIntervention,
                                    @RequestParam(value = "policyObjective", required = false) String policyObjective,
                                    @RequestParam(value = "budgetBiggerThan", required = false) Integer budgetBiggerThen,
                                    @RequestParam(value = "budgetSmallerThan", required = false) Integer budgetSmallerThen,
                                    @RequestParam(value = "budgetEUBiggerThan", required = false) Integer budgetEUBiggerThen,
                                    @RequestParam(value = "budgetEUSmallerThan", required = false) Integer budgetEUSmallerThen,
                                    @RequestParam(value = "startDateBefore", required = false) String startDateBefore,
                                    @RequestParam(value = "startDateAfter", required = false) String startDateAfter,
                                    @RequestParam(value = "endDateBefore", required = false) String endDateBefore,
                                    @RequestParam(value = "endDateAfter", required = false) String endDateAfter,

                                    @RequestParam(value = "orderStartDate", required = false) Boolean orderStartDate,
                                    @RequestParam(value = "orderEndDate", required = false) Boolean orderEndDate,
                                    @RequestParam(value = "orderEuBudget", required = false) Boolean orderEuBudget,
                                    @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,

                                    @RequestParam(value = "latitude", required = false) String latitude,
                                    @RequestParam(value = "longitude", required = false) String longitude,
                                    @RequestParam(value = "region", required = false) String region,
                                    @RequestParam(value = "limit", defaultValue = "200") int limit,
                                    @RequestParam(value = "offset", defaultValue = "0") int offset,
                                    Principal principal,
                                    @Context HttpServletResponse response)
            throws Exception {
        ProjectList projectList =
                (ProjectList) euSearchProject(language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, orderStartDate, orderEndDate, orderEuBudget, orderTotalBudget, latitude, longitude, region, Math.max(limit, 1000), 0, principal).getBody();
        String filename = "project_export.csv";
        try {
            response.setContentType("text/csv");
            response.setCharacterEncoding("UTF-8");
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            CSVPrinter csvPrinter =
                    new CSVPrinter(
                            response.getWriter(),
                            CSVFormat.DEFAULT.withHeader(
                                    "ID", "PROJECT NAME", "TOTAL BUDGET", "AMOUNT EU SUPPORT", "START DATE", "END DATE", "COUNTRY"));
            for (Project project : projectList.getList()) {
                System.out.println(project.getItem());
                csvPrinter.printRecord(
                        Arrays.asList(
                                String.join("|", project.getItem()),
                                String.join("|", project.getLabels()),
                                String.join("|", project.getTotalBudgets()),
                                String.join("|", project.getEuBudgets()),
                                String.join("|", project.getStartTimes()),
                                String.join("|", project.getEndTimes()),
                                String.join("|", project.getCountrycode())
                        )
                );
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String filterProject(String keywords, String country, String theme, String fund, String program, String categoryOfIntervention,
                                 String policyObjective, Integer budgetBiggerThen, Integer budgetSmallerThen, Integer budgetEUBiggerThen, Integer budgetEUSmallerThen, String startDateBefore, String startDateAfter,
                                 String endDateBefore,
                                 String endDateAfter,
                                 String latitude,
                                 String longitude,
                                 String region,
                                 Integer limit,
                                 Integer offset) throws IOException {
        String search = "";
        if (keywords != null) {
            if (!keywords.contains("AND") && !keywords.contains("OR") && !keywords.contains("NOT")) {
                String[] words = keywords.split(" ");
                StringBuilder keywordsBuilder = new StringBuilder();
                for (int i = 0; i < words.length - 1; i++) {
                    keywordsBuilder.append(words[i]).append(" AND ");
                }
                keywordsBuilder.append(words[words.length - 1]);
                keywords = keywordsBuilder.toString();
            }
            search +=
                    "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                            + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                            + keywords.replace("\"", "\\\"")
                            + "\" ] .";
        }

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
            search += "?s0 <https://linkedopendata.eu/prop/direct/P1845>* <" + region + "> . ";
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
