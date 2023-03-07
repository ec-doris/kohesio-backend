package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.ec.doris.kohesio.payload.*;
import eu.ec.doris.kohesio.services.ExpandedQuery;
import eu.ec.doris.kohesio.services.FiltersGenerator;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import eu.ec.doris.kohesio.services.SimilarityService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.util.Precision;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.sail.lucene.SearchFields;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.mapstruct.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")

public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

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

    @Value("${kohesio.directory}")
    String cacheDirectory;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/project", produces = "application/json")
    public ResponseEntity
    euProjectID( //
                 @RequestParam(value = "id") String id,
                 @RequestParam(value = "language", defaultValue = "en") String language)
            throws Exception {

        logger.info("Project search by ID: id {}, language {}", id, language);

        String queryCheck = "ASK {\n" +
                " <" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> " +
                "}";


        boolean resultAsk = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - project ID not found");
            return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
        } else {
            String query = "PREFIX wd: <https://linkedopendata.eu/entity/> "
                    + "PREFIX wdt: <https://linkedopendata.eu/prop/direct/> "
                    + "PREFIX ps: <https://linkedopendata.eu/prop/statement/> "
                    + "PREFIX p: <https://linkedopendata.eu/prop/> "
                    + "SELECT ?s0 ?snippet ?label ?description ?infoRegioUrl ?startTime ?endTime "
                    + "?expectedEndTime ?budget ?euBudget ?cofinancingRate ?image ?imageCopyright ?youtube "
                    + "?video ?tweet ?coordinates  ?countryLabel ?countryCode ?program ?programLabel ?program_cci "
                    + "?programInfoRegioUrl ?categoryLabel ?categoryID ?fund ?fundId ?fundLabel ?fundWebsite ?themeId "
                    + "?themeLabel ?themeIdInferred ?themeLabelInferred ?policyId ?policyLabel "
                    + "?managingAuthorityLabel ?beneficiaryLink ?beneficiary ?beneficiaryLabelRight "
                    + "?beneficiaryLabel ?transliteration ?beneficiaryWikidata ?beneficiaryWebsite "
                    + "?beneficiaryString ?source ?source2 ?keepUrl WHERE { "
                    + "VALUES ?s0 { <" + id + "> } "
                    + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER((LANG(?label)) = \"" + language + "\") }"
                    + " OPTIONAL { ?s0 wdt:P836 ?description. FILTER((LANG(?description)) = \"" + language + "\") } "
                    + " OPTIONAL { ?s0 wdt:P1742 ?infoRegioUrl . }"
                    + " OPTIONAL { ?s0 wdt:P20 ?startTime . } "
                    + " OPTIONAL { ?s0 wdt:P33 ?endTime . } "
                    + " OPTIONAL { ?s0 wdt:P838 ?expectedEndTime . } "
                    + " OPTIONAL { ?s0 wdt:P835 ?euBudget. } "
                    + " OPTIONAL { ?s0 wdt:P474 ?budget. } "
                    + " OPTIONAL { ?s0 wdt:P837 ?cofinancingRate. } "
                    + " OPTIONAL { ?s0 wdt:P851 ?image } . "
                    + " OPTIONAL { ?s0 wdt:P2210 ?youtube } . "
                    + " OPTIONAL { ?s0 wdt:P562941 ?keepId. wd:P562941 wdt:P877 ?formatter. BIND(REPLACE(?keepId, '^(.+)$', ?formatter) AS ?keepUrl). } . "
                    + " OPTIONAL { ?s0 p:P851 ?blank . "
                    + " ?blank ps:P851 ?image . "
                    + " ?blank <https://linkedopendata.eu/prop/qualifier/P836> ?summary . "
                    + " ?blank <https://linkedopendata.eu/prop/qualifier/P1743> ?imageCopyright . } "
                    + " OPTIONAL { ?s0 wdt:P1746 ?video . }"
                    + " OPTIONAL { ?s0 wdt:P1416 ?tweet . }"
                    + " OPTIONAL { ?s0 wdt:P1360 ?sou . "
                    + " BIND(CONCAT(\"http://www.opencoesione.gov.it/progetti/\",STR( ?sou )) AS ?source ) . }"
                    + " OPTIONAL { ?s0 wdt:P32 ?country . "
                    + "            ?country wdt:P173 ?countryCode . "
                    + "             ?country <http://www.w3.org/2000/01/rdf-schema#label> ?countryLabel. "
                    + "             FILTER((LANG(?countryLabel)) = \"" + language + "\") }"
                    + " OPTIONAL { ?s0 wdt:P1368 ?program ."
                    + "             OPTIONAL { ?program wdt:P1367  ?program_cci . } "
                    + "             ?program wdt:P1586 ?managingAuthority. "
                    + "             ?program <http://www.w3.org/2000/01/rdf-schema#label> ?programLabel. "
                    + "             OPTIONAL { ?program wdt:P1742 ?programInfoRegioUrl . }"
                    + "             OPTIONAL { ?program wdt:P1750 ?source2 . }"
                    + "             FILTER((LANG(?programLabel)) = \"" + language + "\") ."
                    + "             ?managingAuthority <http://www.w3.org/2000/01/rdf-schema#label> ?managingAuthorityLabel. } "
                    + " OPTIONAL { ?s0 wdt:P888 ?category ."
                    + "             OPTIONAL { ?category <http://www.w3.org/2000/01/rdf-schema#label> ?categoryLabel. "
                    + "                         FILTER((LANG(?categoryLabel)) = \"" + language + "\") }"
                    + " OPTIONAL { ?category wdt:P869 ?categoryID . } }"
                    + " OPTIONAL {"
                    + "                 ?s0 wdt:P1848 ?theme."
                    + "                 ?theme wdt:P1105 ?themeId. "
                    + "                 ?theme <http://www.w3.org/2000/01/rdf-schema#label> ?themeLabel. "
                    + "                 FILTER((LANG(?themeLabel)) = \"" + language + "\") } "
                    + " OPTIONAL {"
                    + "           ?s0 wdt:P888 ?category."
                    + "           OPTIONAL { "
                    + "                 ?category wdt:P1848 ?themeInferred."
                    + "                 ?themeInferred wdt:P1105 ?themeIdInferred. "
                    + "                 ?themeInferred <http://www.w3.org/2000/01/rdf-schema#label> ?themeLabelInferred . "
                    + "                 FILTER((LANG(?themeLabelInferred)) = \"" + language + "\") } } "
                    + " OPTIONAL {?s0 wdt:P1848 ?theme.  "
                    + "           ?theme wdt:P1849 ?policy."
                    + "           ?policy wdt:P1747 ?policyId. "
                    + "           ?policy <http://www.w3.org/2000/01/rdf-schema#label> ?policyLabel. "
                    + "           FILTER((LANG(?policyLabel)) = \"" + language + "\") } "
                    + " OPTIONAL {?s0 wdt:P1584 ?fund. "
                    + "           ?fund wdt:P1583 ?fundId."
                    + "           OPTIONAL {?fund <http://www.w3.org/2000/01/rdf-schema#label> ?fundLabel. FILTER((LANG(?fundLabel)) = \"" + language + "\") }"
                    + "           OPTIONAL {?fund wdt:P67 ?fundWebsite . } "
                    + "} "
                    + " OPTIONAL { ?s0 wdt:P889 ?beneficiaryLink . "
                    + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabelRight . "
                    + "             FILTER(LANG(?beneficiaryLabelRight) = \"" + language + "\" ) } "
                    + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . }"
                    + "          OPTIONAL {?beneficiaryLink wdt:P1 ?beneficiaryID .  "
                    + "          BIND(CONCAT(\"http://wikidata.org/entity/\",STR( ?beneficiaryID )) AS ?beneficiaryWikidata ) . }"
                    + "          OPTIONAL {?beneficiaryLink wdt:P67 ?beneficiaryWebsite . } "
                    + "          OPTIONAL { ?beneficiaryLink p:P7 ?benefStatement . "
                    + "                 ?benefStatement <https://linkedopendata.eu/prop/qualifier/P4393> ?transliteration ."
                    + "          }"
                    + " } "
                    + " OPTIONAL { ?s0 wdt:P841 ?beneficiaryString .}"
                    + " } ";
            String queryCoordinates = "PREFIX wd: <https://linkedopendata.eu/entity/> "
                    + "PREFIX wdt: <https://linkedopendata.eu/prop/direct/> "
                    + "SELECT ?coordinates WHERE { <" + id + "> wdt:P127 ?coordinates. }";

            String queryRegion = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                    "PREFIX wd: <https://linkedopendata.eu/entity/> " +
                    "PREFIX wdt: <https://linkedopendata.eu/prop/direct/> " +
                    "SELECT ?region ?regionId ?regionLabel { "
                    + "<" + id + "> wdt:P1845 ?region . "
                    + "FILTER EXISTS { ?region  wdt:P35  wd:Q4407315 . } "
                    + "OPTIONAL { ?region  wdt:P192  ?regionId . } "
                    + "OPTIONAL { ?region rdfs:label ?regionLabel . "
                    + "FILTER ( lang(?regionLabel) = \"" + language + "\" ) "
                    + "} "
                    + "FILTER(STRLEN(STR(?regionId))>=5) "
                    + "}";

            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 3, false);
            TupleQueryResult resultSetCoords = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, queryCoordinates, 3, false);
            TupleQueryResult resultSetRegion = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, queryRegion, 3, false);

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
            result.put("countryLabel", new JSONArray());
            result.put("countryCode", new JSONArray());
            result.put("categoryLabels", new JSONArray());
            result.put("categoryIDs", new JSONArray());
            result.put("fundLabel", "");
            result.put("fundWebsite", "");


            HashSet<HashMap> programs = new HashSet<>();
            HashSet<HashMap> funds = new HashSet<>();

            result.put("funds", funds);
            result.put("program", programs);
            result.put("themeIds", new JSONArray());
            result.put("themeLabels", new JSONArray());
            result.put("policyIds", new JSONArray());
            result.put("policyLabels", new JSONArray());
            result.put("projectWebsite", "");
            result.put("coordinates", new JSONArray());
            result.put("images", new JSONArray());
            result.put("videos", new JSONArray());
            result.put("tweets", new JSONArray());
            result.put("beneficiaries", new JSONArray());
            result.put("managingAuthorityLabel", "");
            result.put("region", "");
            result.put("geoJson", new JSONArray());
            result.put("regionUpper1", "");
            result.put("regionUpper2", "");
            result.put("regionUpper3", "");
            result.put("infoRegioUrl", "");
            result.put("keepUrl", "");

            HashSet<String> regionIDs = new HashSet<>();
            HashSet<String> coordinatesSet = new HashSet<>();
            HashSet<String> regions = new HashSet<>();
            HashSet<String> interventionFieldsSet = new HashSet<>();
            HashSet<String> interventionFieldsIDSet = new HashSet<>();
            HashSet<String> themeLabels = new HashSet<>();
            HashSet<String> themeIds = new HashSet<>();
            HashSet<String> policyLabels = new HashSet<>();
            HashSet<String> policyIds = new HashSet<>();


            HashMap<String, JSONObject> tmpFunds = new HashMap<>();
            HashMap<String, HashMap<String, Object>> tmpPrograms = new HashMap<>();
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();

                if (querySolution.getBinding("budget") != null) {
                    result.put(
                            "budget", ((Literal) querySolution.getBinding("budget").getValue()).stringValue());
                }

                if (querySolution.getBinding("keepUrl") != null) {
                    result.put(
                            "keepUrl", ((Literal) querySolution.getBinding("keepUrl").getValue()).stringValue());
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
                if (querySolution.getBinding("infoRegioUrl") != null) {
                    result.put(
                            "infoRegioUrl",
                            querySolution.getBinding("infoRegioUrl").getValue().stringValue()
                    );
                }
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
                    if (!((JSONArray) result.get("countryLabel")).contains(querySolution.getBinding("countryLabel").getValue().stringValue())) {
                        ((JSONArray) result.get("countryLabel")).add(
                                querySolution.getBinding("countryLabel").getValue().stringValue()
                        );
                    }
                }

                if (querySolution.getBinding("countryCode") != null) {
                    if (!((JSONArray) result.get("countryCode")).contains(querySolution.getBinding("countryCode").getValue().stringValue()))
                        if (!"GR".equals(querySolution.getBinding("countryCode").getValue().stringValue())) {
                            ((JSONArray) result.get("countryCode")).add(
                                    querySolution.getBinding("countryCode").getValue().stringValue()
                            );
                        } else {
                            ((JSONArray) result.get("countryCode")).add(
                                    "EL"
                            );
                        }

                }

                if (querySolution.getBinding("categoryLabel") != null) {
                    String interventionField = querySolution.getBinding("categoryLabel").getValue().stringValue();
                    if (!interventionFieldsSet.contains(interventionField)) {
                        interventionFieldsSet.add(interventionField);
                        result.put("categoryLabels", interventionFieldsSet);
                    }
                }

                if (querySolution.getBinding("categoryID") != null) {
                    String interventionField = querySolution.getBinding("categoryID").getValue().stringValue();
                    if (!interventionFieldsIDSet.contains(interventionField)) {
                        interventionFieldsIDSet.add(interventionField);
                        result.put("categoryIDs", interventionFieldsIDSet);
                    }
                }
                if (querySolution.getBinding("fund") != null) {
                    JSONObject fund;
                    if (tmpFunds.containsKey(querySolution.getBinding("fund").getValue().stringValue())) {
                        fund = tmpFunds.get(querySolution.getBinding("fund").getValue().stringValue());
                    } else {
                        fund = new JSONObject();
                        tmpFunds.put(querySolution.getBinding("fund").getValue().stringValue(), fund);
                    }
                    fund.put(
                            "id",
                            querySolution.getBinding("fundId").getValue().stringValue()
                    );
                    if (querySolution.getBinding("fundLabel") != null) {
                        fund.put(
                                "label",
                                querySolution.getBinding("fundLabel").getValue().stringValue()
                        );
                        fund.put(
                                "fullLabel",
                                querySolution.getBinding("fundId").getValue().stringValue()
                                        + " - "
                                        + querySolution.getBinding("fundLabel").getValue().stringValue()
                        );
                    } else {
                        fund.put(
                                "fullLabel",
                                querySolution.getBinding("fundId").getValue().stringValue()
                        );
                    }
                    if (querySolution.getBinding("fundWebsite") != null) {
                        fund.put(
                                "website",
                                querySolution.getBinding("fundWebsite").getValue().stringValue()
                        );
                    }
                    if (querySolution.getBinding("fundId") != null) {

                    }

                }
                HashMap<String, Object> program;
                if (querySolution.getBinding("program") != null) {
                    String programQID = querySolution.getBinding("program").getValue().stringValue();
                    if (!tmpPrograms.containsKey(programQID)) {
                        tmpPrograms.put(programQID, new HashMap<>());
                    }
                    program = tmpPrograms.get(programQID);
                    program.put(
                            "link",
                            querySolution.getBinding("program").getValue().stringValue()
                    );

                    if (querySolution.getBinding("programLabel") != null) {
                        program.put(
                                "programLabel",
                                ((Literal) querySolution.getBinding("programLabel").getValue()).stringValue());

                        if (querySolution.getBinding("program_cci") != null) {
                            program.put(
                                    "programFullLabel",
                                    ((Literal) querySolution.getBinding("program_cci").getValue()).stringValue() + " - " + ((Literal) querySolution.getBinding("programLabel").getValue()).stringValue());
                        }

                    }
                    if (querySolution.getBinding("programInfoRegioUrl") != null) {
                        program.put(
                                "programInfoRegioUrl",
                                querySolution.getBinding("programInfoRegioUrl").getValue().stringValue()
                        );
                    }
                    if (querySolution.getBinding("source2") != null) {
                        if (!program.containsKey("programWebsite")) {
                            program.put("programWebsite", new ArrayList<String>());
                        }
                        ((ArrayList<String>) program.get("programWebsite")).add(
                                querySolution.getBinding("source2").getValue().stringValue()
                        );
                    }
                    program.put("programmingPeriodLabel", "2014-2020");
                }

                if (querySolution.getBinding("themeId") != null) {
                    String themeId = querySolution.getBinding("themeId").getValue().stringValue();
                    if (!themeIds.contains(themeId)) {
                        themeIds.add(themeId);
                        result.put("themeIds", themeIds);
                    }
                } else {
                    if (querySolution.getBinding("themeIdInferred") != null) {
                        String themeId = querySolution.getBinding("themeIdInferred").getValue().stringValue();
                        if (!themeIds.contains(themeId)) {
                            themeIds.add(themeId);
                            result.put("themeIds", themeIds);
                        }
                    }
                }

                if (querySolution.getBinding("themeLabel") != null) {
                    String themeLabel = querySolution.getBinding("themeLabel").getValue().stringValue();
                    if (!themeLabels.contains(themeLabel)) {
                        themeLabels.add(themeLabel);
                        result.put("themeLabels", themeLabels);
                    }
                } else {
                    if (querySolution.getBinding("themeLabelInferred") != null) {
                        String themeLabel = querySolution.getBinding("themeLabelInferred").getValue().stringValue();
                        if (!themeLabels.contains(themeLabel)) {
                            themeLabels.add(themeLabel);
                            result.put("themeLabels", themeLabels);
                        }
                    }
                }
                if (querySolution.getBinding("policyId") != null) {
                    String policyId = querySolution.getBinding("policyId").getValue().stringValue();
                    if (!policyIds.contains(policyId)) {
                        policyIds.add(policyId);
                        result.put("policyIds", policyIds);
                    }
                }

                if (querySolution.getBinding("policyLabel") != null) {
                    String policyLabel = querySolution.getBinding("policyLabel").getValue().stringValue();
                    if (!policyLabels.contains(policyLabel)) {
                        policyLabels.add(policyLabel);
                        result.put("policyLabels", policyLabels);
                    }
                }

                if (querySolution.getBinding("source") != null) {
                    result.put(
                            "projectWebsite", ((Literal) querySolution.getBinding("source").getValue()).stringValue());
                }

                if (querySolution.getBinding("image") != null) {
                    JSONArray images = (JSONArray) result.get("images");
                    JSONObject image = new JSONObject();
                    String im = querySolution.getBinding("image").getValue().stringValue();
                    boolean found = false;
                    for (Object i : images) {
                        if (((JSONObject) i).get("image").toString().equals(im) && !found) {
                            found = true;
                        }
                    }
                    if (!found) {
                        image.put("image", im);
                        if (querySolution.getBinding("imageCopyright") != null) {
                            image.put("imageCopyright", "© " + querySolution.getBinding("imageCopyright").getValue().stringValue());
                        }
                        images.add(image);
                    }
                    result.put("images", images);
                }
                if (querySolution.getBinding("video") != null) {
                    JSONArray videos = (JSONArray) result.get("videos");
                    String im = querySolution.getBinding("video").getValue().stringValue();
                    if (!videos.contains(im)) {
                        videos.add(im);
                        result.put("videos", videos);
                    }
                }
                if (querySolution.getBinding("youtube") != null) {
                    JSONArray videos = (JSONArray) result.get("videos");
                    String im = "https://www.youtube.com/watch?v=" + querySolution.getBinding("youtube").getValue().stringValue();
                    if (!videos.contains(im)) {
                        videos.add(im);
                        result.put("videos", videos);
                    }
                }

                if (querySolution.getBinding("tweet") != null) {
                    JSONArray tweets = (JSONArray) result.get("tweets");
                    String im = "https://twitter.com/i/status/" + querySolution.getBinding("tweet").getValue().stringValue();
                    if (!tweets.contains(im)) {
                        tweets.add(im);
                        result.put("tweets", tweets);
                    }
                }

                JSONArray beneficiaries = (JSONArray) result.get("beneficiaries");
                if (querySolution.getBinding("beneficiaryLink") != null) {

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
                        } else if (querySolution.getBinding("beneficiaryLabel") != null) {
                            String label =
                                    ((Literal) querySolution.getBinding("beneficiaryLabel").getValue()).stringValue();
                            beneficary.put("beneficiaryLabel", label);
                        } else {
                            if (querySolution.getBinding("beneficiaryString") != null)
                                beneficary.put("beneficiaryLabel", querySolution.getBinding("beneficiaryString").getValue().stringValue());
                            else {
                                beneficary.put("beneficiaryLabel", "");
                            }
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
                        if (querySolution.getBinding("transliteration") != null) {
                            beneficary.put("transliteration", querySolution.getBinding("transliteration").getValue().stringValue());
                        }
                        beneficiaries.add(beneficary);
                    }
                } else {
                    if (querySolution.getBinding("beneficiaryString") != null) {
                        String ben = querySolution.getBinding("beneficiaryString").getValue().stringValue();
                        boolean found = false;
                        for (Object beneficiary : beneficiaries) {
                            if (((JSONObject) beneficiary).get("beneficiaryLabel").equals(ben)) {
                                found = true;
                            }
                        }
                        if (!found) {
                            JSONObject beneficiary = new JSONObject();
                            beneficiary.put("beneficiaryLabel", querySolution.getBinding("beneficiaryString").getValue().stringValue());
                            beneficiary.put("link", "");

                            beneficiaries.add(beneficiary);

                        }
                    }
                }

                if (querySolution.getBinding("managingAuthorityLabel") != null) {
                    result.put(
                            "managingAuthorityLabel",
                            ((Literal) querySolution.getBinding("managingAuthorityLabel").getValue())
                                    .stringValue());
                }
            }
            programs.addAll(tmpPrograms.values());
            funds.addAll(tmpFunds.values());

            while (resultSetCoords.hasNext()) {
                BindingSet querySolution = resultSetCoords.next();

                if (querySolution.getBinding("coordinates") != null) {
                    JSONArray coordinates = (JSONArray) result.get("coordinates");
                    String coo = ((Literal) querySolution.getBinding("coordinates").getValue()).stringValue();
                    //if (!coordinates.contains(coo.replace("Point(", "").replace(")", "").replace(" ", ","))) {
                    if (!coordinatesSet.contains(coo)) {
                        coordinatesSet.add(coo);
                        coordinates.add(coo);
                        result.put("coordinates", coordinates);
                    }
                }
            }
            while (resultSetRegion.hasNext()) {
                BindingSet querySolution = resultSetRegion.next();
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
                    if (!((String) result.get("region")).equals(((String) result.get("regionUpper1"))) && !result.get("regionUpper1").equals("")) {
                        regionText += ", " + (String) result.get("regionUpper1");
                    }
                    if (!((String) result.get("regionUpper1")).equals(((String) result.get("regionUpper2"))) && !result.get("regionUpper2").equals("")) {
                        regionText += ", " + (String) result.get("regionUpper2");
                    }
                    if (!result.get("regionUpper3").equals("") && !((String) result.get("regionUpper2")).equals(((String) result.get("regionUpper3")))) {
                        regionText += ", " + (String) result.get("regionUpper3");
                    }
                    if (!result.get("countryLabel").equals(regionText)) {
                        regionText += ", " + String.join(", ", (JSONArray) result.get("countryLabel"));
                    }
                    result.put("regionText", regionText);
                } else {
                    result.put("regionText", String.join(", ", (JSONArray) result.get("countryLabel")));
                }
                String regionId = "";
                if (querySolution.getBinding("regionId") != null) {
                    regionId = ((Literal) querySolution.getBinding("regionId").getValue()).stringValue();
                } else {
                    // replace with country code because there is no nuts
                    regionId = querySolution.getBinding("countryCode").getValue().stringValue();
                    if (regionId.equals("GR")) {
                        // exception for Greece to use EL as nuts code and not GR
                        regionId = "EL";
                    }
                }
                if (regionId != null) {
                    JSONArray geoJsons = (JSONArray) result.get("geoJson");
                    String regionLabel = (String) result.get("region");
                    if (!regionIDs.contains(regionId) /*&& !regions.contains(regionLabel)*/) {
                        // check if the regioId has already been seen - could be that a project is contained in multiple geometries
                        regionIDs.add(regionId);
                        regions.add(regionLabel);
                        query =
                                "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                                        + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                                        + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                                        + "SELECT ?id ?geoJson  WHERE { "
                                        + "?s <http://nuts.de/id> '" + regionId + "' . "
                                        + "?s <http://nuts.de/geoJson> ?geoJson . "
                                        + "}";
                        logger.debug("Retrieving nuts geometry");
                        TupleQueryResult resultSet2 = sparqlQueryService.executeAndCacheQuery(getSparqlEndpointNuts, query, 5);

                        NutsRegion nutsRegion = new NutsRegion();
                        while (resultSet2.hasNext()) {
                            BindingSet querySolution2 = resultSet2.next();
                            if (querySolution2.getBinding("geoJson") != null) {
                                geoJsons.add(querySolution2.getBinding("geoJson").getValue().stringValue());
                            }
                        }
                    }
                }
            }

            if (((JSONArray) result.get("geoJson")).size() == 0) {
                JSONArray geoJsons = (JSONArray) result.get("geoJson");
                for (int i = 0; i < ((JSONArray) result.get("countryCode")).size(); i++) {
                    String countryCode = (String) ((JSONArray) result.get("countryCode")).get(i);
                    if (countryCode.equals("GR")) {
                        // exception for Greece to use EL as nuts code and not GR
                        countryCode = "EL";
                    }
                    if (!regionIDs.contains(countryCode)) {
                        // check if the regioId has already been seen - could be that a project is contained in multiple geometries
                        regionIDs.add(countryCode);
                        query =
                                "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                                        + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                                        + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                                        + "SELECT ?id ?geoJson  WHERE { "
                                        + "?s <http://nuts.de/id> '" + countryCode + "' . "
                                        + "?s <http://nuts.de/geoJson> ?geoJson . "
                                        + "}";
                        logger.debug("Retrieving nuts geometry");
                        TupleQueryResult resultSet2 = sparqlQueryService.executeAndCacheQuery(getSparqlEndpointNuts, query, 5);

                        NutsRegion nutsRegion = new NutsRegion();
                        while (resultSet2.hasNext()) {
                            BindingSet querySolution2 = resultSet2.next();
                            if (querySolution2.getBinding("geoJson") != null) {
                                geoJsons.add(querySolution2.getBinding("geoJson").getValue().stringValue());
                            }
                        }
                    }
                }
            }

            if (regionIDs.size() > 1) {
                // means multiple region - change regionText
                result.put("regionText", "Multiple locations, " + String.join(", ", (JSONArray) result.get("countryLabel")));
            }
            if (result.get("regionText") == null || result.get("regionText") != null && ((String) result.get("regionText")).length() == 0) {
                result.put("regionText", String.join(", ", (JSONArray) result.get("countryLabel")));
            }
            return new ResponseEntity<JSONObject>(result, HttpStatus.OK);
        }
    }


    public class Coordinates {
        String latitude;
        String longitude;

        public Coordinates(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLatitude() {
            return latitude;
        }

        public void setLatitude(String latitude) {
            this.latitude = latitude;
        }

        public String getLongitude() {
            return longitude;
        }

        public void setLongitude(String longitude) {
            this.longitude = longitude;
        }
    }

    private Coordinates getCoordinatesFromTown(String town) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String urlTemplate = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search/search")
                .queryParam("q", town)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .encode().toUriString();
        System.out.println(urlTemplate);

        HttpEntity<String> response = new RestTemplate().exchange(
                urlTemplate,
                HttpMethod.GET, entity, String.class
        );
        System.out.println(response.getBody());
        try {
            JSONArray JsonArray = (JSONArray) new JSONParser().parse(response.getBody());
            System.out.println(((JSONObject) JsonArray.get(0)).get("lat"));
            System.out.println(((JSONObject) JsonArray.get(0)).get("lon"));

            return new Coordinates(
                    ((JSONObject) JsonArray.get(0)).get("lat").toString(),
                    ((JSONObject) JsonArray.get(0)).get("lon").toString()
            );
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ResponseEntity euSearchProject(
            String language,
            String keywords, //
            String country,
            String theme,
            String fund,
            String program,
            String categoryOfIntervention,
            String policyObjective,
            Long budgetBiggerThen,
            Long budgetSmallerThen,
            Long budgetEUBiggerThen,
            Long budgetEUSmallerThen,
            String startDateBefore,
            String startDateAfter,
            String endDateBefore,
            String endDateAfter,

            Boolean orderStartDate,
            Boolean orderEndDate,
            Boolean orderEuBudget,
            Boolean orderTotalBudget,

            String latitude,
            String longitude,
            String region,
            int limit,
            int offset,
            Integer timeout,
            Principal principal)
            throws Exception {
        return euSearchProject(
                language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective,
                budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore,
                startDateAfter, endDateBefore, endDateAfter, orderStartDate, orderEndDate, orderEuBudget,
                orderTotalBudget, latitude, longitude, region, limit, offset, null, null, null, null, null, null,
                timeout, principal
        );
    }

    @GetMapping(value = "/facet/eu/search/project", produces = "application/json")
    public ResponseEntity euSearchProject(
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

            @RequestParam(value = "orderStartDate", required = false) Boolean orderStartDate,
            @RequestParam(value = "orderEndDate", required = false) Boolean orderEndDate,
            @RequestParam(value = "orderEuBudget", required = false) Boolean orderEuBudget,
            @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "limit", defaultValue = "5000") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "town", required = false) String town,
            @RequestParam(value = "radius", required = false) Long radius,
            @RequestParam(value = "nuts3", required = false) String nuts3,
            @RequestParam(value = "interreg", required = false) Boolean interreg,
            @RequestParam(value = "highlighted", required = false) Boolean highlighted,
            @RequestParam(value = "cci", required = false) String cci,
            Integer timeout,
            Principal principal
    )
            throws Exception {
        if (timeout == null) {
            timeout = 20;
            if (keywords == null) {
                timeout = 200;
            }
        }
        logger.info("Project search: language {}, keywords {}, country {}, theme {}, fund {}, program {}, region {}, timeout {}", language, keywords, country, theme, fund, program, region, timeout);
        logger.info("interreg {}", interreg);
        int inputOffset = offset;
        int inputLimit = limit;
        if (offset != Integer.MIN_VALUE) {
            // if not special offset then cache the and optimize the limits..
            if (keywords != null) {
                // in case of keywords, optimize to 100 projects for performance issues
                if (offset < 100) {
                    offset = 0;
                    limit = 100;
                }
            } else {
                if (offset < 1000) {
                    offset = 0;
                    limit = 1000;
                }
            }
        } else {
            offset = 0;
            inputOffset = 0;
        }
        // expand the query keywords
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            long start = System.nanoTime();
            expandedQuery = similarityService.expandQuery(keywords, language);
            expandedQueryText = expandedQuery.getExpandedQuery();
            logger.info("Expansion time " + (System.nanoTime() - start) / 1000000);
        }
        if (town != null) {
            Coordinates tmpCoordinates = getCoordinatesFromTown(town);
            if (tmpCoordinates != null) {
                latitude = tmpCoordinates.getLatitude();
                longitude = tmpCoordinates.getLongitude();

            }
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
                radius,
                region,
                nuts3,
                interreg,
                highlighted,
                cci,
                limit,
                offset
        );

        int numResults = 0;

        String orderQuery = "";

        String orderBy = "";
        if (orderStartDate != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime .";
            if (orderStartDate) {
                orderBy = "ORDER BY ASC(?startTime)";
            } else {
                orderBy = "ORDER BY DESC(?startTime)";
            }
        }
        if (orderEndDate != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime .";
            if (orderEndDate) {
                orderBy = "ORDER BY ASC(?endTime)";
            } else {
                orderBy = "ORDER BY DESC(?endTime)";
            }
        }
        if (orderEuBudget != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. ";
            if (orderEuBudget) {
                orderBy = "ORDER BY ASC(?euBudget)";
            } else {
                orderBy = "ORDER BY DESC(?euBudget)";
            }
        }
        if (orderTotalBudget != null) {
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget. ";
            if (orderTotalBudget) {
                orderBy = "ORDER BY ASC(?budget)";
            } else {
                orderBy = "ORDER BY DESC(?budget)";
            }
        }

        // pass cache = false in order to stop caching the semantic search results

        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + search + "} ";
        // count the results with the applied filters
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout);
        if (resultSet != null && resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }

        search += " " + orderQuery;
        String mainQuery;
        if ("".equals(orderQuery)) {
            mainQuery = "SELECT DISTINCT ?s0 WHERE { {" + search + " ?s0 <https://linkedopendata.eu/prop/P851> ?blank . ?blank <https://linkedopendata.eu/prop/statement/P851> ?image. } UNION { " + search + "} } " + orderBy + " LIMIT " + limit + " OFFSET " + offset;
        } else {
            mainQuery = "SELECT DISTINCT ?s0 WHERE {" + search + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/P851> ?blank . ?blank <https://linkedopendata.eu/prop/statement/P851> ?image. } } " + orderBy + " LIMIT " + limit + " OFFSET " + offset;
        }
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, mainQuery, timeout);
        StringBuilder values = new StringBuilder();
        int indexLimit = 0;
        int indexOffset = 0;
        while (resultSet.hasNext()) {

            BindingSet querySolution = resultSet.next();
            if (offset == 0) {
                if (indexOffset < inputOffset) {
                    indexOffset++;
                } else {
                    if (indexLimit < inputLimit) {
                        values.append(" ").append("<").append(querySolution.getBinding("s0").getValue().stringValue()).append(">");
                        indexLimit++;
                    }
                }
            } else {
                values.append(" ").append("<").append(querySolution.getBinding("s0").getValue().stringValue()).append(">");
            }
        }
        query =
                "SELECT ?s0 ?label ?startTime ?endTime ?expectedEndTime ?totalBudget ?euBudget ?image ?imageCopyright ?coordinates ?objectiveId ?countryCode ?description WHERE { "
                        + " VALUES ?s0 { " + values + " }"
                        + " OPTIONAL { ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER((LANG(?label)) = \"" + language + "\") }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P836> ?description . FILTER((LANG(?description)) = \"" + language + "\") }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P838> ?expectedEndTime . }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime . }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime . }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P851> ?blank . ?blank <https://linkedopendata.eu/prop/statement/P851> ?image . OPTIONAL { ?blank <https://linkedopendata.eu/prop/qualifier/P1743> ?imageCopyright . }}"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P474> ?totalBudget. }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country . OPTIONAL {?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . }}"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1848> ?objective. OPTIONAL {?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId.} }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P888> ?category .  OPTIONAL {?category <https://linkedopendata.eu/prop/direct/P1848> ?objective. OPTIONAL {?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId.}}}"
                        + "} ";


        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, false);

        HashMap<String, Project> resultMap = new HashMap<>();
        ArrayList<Project> orderedResult = new ArrayList<>();
        ArrayList<String> similarWords = new ArrayList<>();

        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            String iriItem = querySolution.getBinding("s0").getValue().stringValue();
            if (!resultMap.containsKey(iriItem)) {

                Project project = new Project();
                project.setItem(iriItem.replace("https://linkedopendata.eu/entity/", ""));
                project.setLink(iriItem);

                project.setSnippet(new ArrayList<>());
                project.setLabels(new ArrayList<>());
                project.setDescriptions(new ArrayList<>());
                project.setStartTimes(new ArrayList<>());
                project.setEndTimes(new ArrayList<>());
                project.setEuBudgets(new ArrayList<>());
                project.setTotalBudgets(new ArrayList<>());
                project.setImages(new ArrayList<>());
                project.setCopyrightImages(new ArrayList<>());
                project.setCoordinates(new ArrayList<>());
                project.setObjectiveIds(new ArrayList<>());
                project.setCountrycode(new ArrayList<>());

                resultMap.put(
                        iriItem,
                        project
                );
                orderedResult.add(project);
            }
            Project project = resultMap.get(iriItem);
            if (querySolution.getBinding("label") != null) {
                ArrayList<String> labels = project.getLabels();
                String value = querySolution.getBinding("label").getValue().stringValue();
                if (!labels.contains(value)) {
                    labels.add(value);
                }
            }
            if (querySolution.getBinding("startTime") != null) {
                ArrayList<String> startTimes = project.getStartTimes();
                String value = querySolution.getBinding("startTime").getValue().stringValue().split("T")[0];
                if (!startTimes.contains(value)) {
                    startTimes.add(value);
                }
            }
            if (querySolution.getBinding("endTime") != null) {
                ArrayList<String> endTimes = project.getEndTimes();
                String value = querySolution.getBinding("endTime").getValue().stringValue().split("T")[0];
                if (!endTimes.contains(value)) {
                    endTimes.add(value);
                }
            }
            if (querySolution.getBinding("endTime") == null && querySolution.getBinding("expectedEndTime") != null) {
                ArrayList<String> endTimes = project.getEndTimes();
                String value = querySolution.getBinding("expectedEndTime").getValue().stringValue().split("T")[0];
                if (!endTimes.contains(value)) {
                    endTimes.add(value);
                }
            }
            if (querySolution.getBinding("totalBudget") != null) {
                ArrayList<String> totalBudgets = project.getTotalBudgets();
                String value = String.valueOf(
                        Precision.round(
                                ((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue(), 2
                        )
                );
                if (!totalBudgets.contains(value)) {
                    totalBudgets.add(value);
                }
            }
            if (querySolution.getBinding("euBudget") != null) {
                ArrayList<String> euBudgets = project.getEuBudgets();
                String value = String.valueOf(
                        Precision.round(
                                ((Literal) querySolution.getBinding("euBudget").getValue()).doubleValue(), 2
                        )
                );
                if (!euBudgets.contains(value)) {
                    euBudgets.add(value);
                }
            }
            if (querySolution.getBinding("image") != null) {
                ArrayList<String> images = project.getImages();
                String value = querySolution.getBinding("image").getValue().stringValue();
                if (!images.contains(value)) {
                    images.add(value);
                }
            }
            if (querySolution.getBinding("imageCopyright") != null) {
                ArrayList<String> copyrightImages = project.getCopyrightImages();
                String value = "© " + querySolution.getBinding("image").getValue().stringValue();
                if (!copyrightImages.contains(value)) {
                    copyrightImages.add(value);
                }
            }
            if (querySolution.getBinding("coordinates") != null) {
                ArrayList<String> coordinates = project.getCoordinates();
                String value = ((Literal) querySolution.getBinding("coordinates").getValue())
                        .getLabel()
                        .replace("Point(", "")
                        .replace(")", "")
                        .replace(" ", ",");
                if (!coordinates.contains(value)) {
                    coordinates.add(value);
                }
            }
            if (querySolution.getBinding("objectiveId") != null) {
                ArrayList<String> objectiveIds = project.getObjectiveIds();
                String value = querySolution.getBinding("objectiveId").getValue().stringValue();
                if (!objectiveIds.contains(value)) {
                    objectiveIds.add(value);
                }
            }
            if (querySolution.getBinding("countryCode") != null) {
                ArrayList<String> countryCodes = project.getCountrycode();
                String value = querySolution.getBinding("countryCode").getValue().stringValue();
                if (!countryCodes.contains(value)) {
                    countryCodes.add(value);
                }
            }
            if (querySolution.getBinding("description") != null) {
                ArrayList<String> descriptions = project.getDescriptions();
                String value = querySolution.getBinding("description").getValue().stringValue();
                if (!descriptions.contains(value)) {
                    descriptions.add(value);
                }
            }
        }

        ProjectList projectList = new ProjectList();

        projectList.setList(orderedResult);
        projectList.setNumberResults(numResults);

        if (expandedQuery != null && expandedQuery.getKeywords() != null) {
            for (SimilarWord similarWord : expandedQuery.getKeywords()) {
                similarWords.add(similarWord.getWord());
            }
        }
        projectList.setSimilarWords(similarWords);
        return new ResponseEntity<>(projectList, HttpStatus.OK);
    }

    private String getSnippet(String queryText, String text) {

        Query query = null;
        String snippet = "";
        try {
            query = new QueryParser("title", new StandardAnalyzer()).parse(queryText);
            Formatter formatter = new SimpleHTMLFormatter(SearchFields.HIGHLIGHTER_PRE_TAG,
                    SearchFields.HIGHLIGHTER_POST_TAG);
            Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
            Analyzer analyzer = new StandardAnalyzer();
            TokenStream tokenStream = analyzer.tokenStream("test", new StringReader(text));
            snippet = highlighter.getBestFragments(tokenStream, text, 2, "...");
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (InvalidTokenOffsetsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return snippet;
    }

    private SemanticSearchResult getProjectsURIsfromSemanticSearch(String keywords, boolean cache, int offset, int limit) {
        String url = null;
        //String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8.toString());
        url = "http://kohesio-search-dev.eu-west-1.elasticbeanstalk.com/search?query=" + keywords + "&page=" + offset + "&page_size=" + limit;

        String path = cacheDirectory + "/facet/semantic-search";
        File dir = new File(path);

        if (!dir.exists()) {
            dir.mkdirs();
        }
        String query = url;
//        if(dir.exists() && cache){
//            try {
//                ArrayList<String> projectsURIs = new ArrayList<>();
//                Scanner input = new Scanner(new File(path+"/"+query.hashCode()));
//                while (input.hasNext()){
//                    String projectURI  = input.next();
//                    projectsURIs.add(projectURI);
//                }
//                return projectsURIs;
//            } catch (FileNotFoundException e) {
//                logger.error("Could not find cache file, probably not cahced");
//            }
//        }

        ArrayList<String> listProjectURIs = new ArrayList<>();
        int numberOfResult = 0;
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
//                FileOutputStream out = new FileOutputStream(path+"/"+ query.hashCode());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                ObjectNode result = (ObjectNode) root;
                numberOfResult = ((JsonNode) result.get("total_hits")).intValue();
                ArrayNode hits = (ArrayNode) result.get("hits");
                for (int i = 0; i < hits.size(); i++) {
                    String projectURI = hits.get(i).get("uri").textValue();
                    listProjectURIs.add(projectURI);
                    projectURI += "\n";
//                    out.write(projectURI.getBytes());
                }
//                out.close();
            } else {
                logger.error("Error in HTTP request!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        SemanticSearchResult result = new SemanticSearchResult();
        result.setNumberOfResults(numberOfResult);
        result.setProjectsURIs(listProjectURIs);
        return result;
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
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset,
            @RequestParam(value = "town", required = false) String town,
            @RequestParam(value = "radius", required = false) Long radius,
            Principal principal)
            throws Exception {
        logger.info("Project image search: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, null);

        // expand the query keywords
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords, language);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }


        if (town != null) {
            Coordinates tmpCoordinates = getCoordinatesFromTown(town);
            if (tmpCoordinates != null) {
                latitude = tmpCoordinates.getLatitude();
                longitude = tmpCoordinates.getLongitude();

            }
        }
        String search = filtersGenerator.filterProject(expandedQueryText, language, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, radius, region, null, null, null, null, limit, offset);

        //computing the number of results
        String searchCount = search;
        searchCount += " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?title. ";
        String query = "SELECT (COUNT(DISTINCT ?s0) as ?c ) WHERE {" + searchCount
                + " FILTER((LANG(?title)) = \""
                + language
                + "\") "
                + "} ";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 25);
        int numResults = 0;
        if (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }
        logger.debug("Number of results {}", numResults);

        query =
                "SELECT ?s0 ?image ?imageCopyright ?title where { "
                        + search
                        + " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. "
                        + " ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?title. "
                        + " FILTER((LANG(?title)) = \""
                        + language
                        + "\") "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P851> ?blank . "
                        + " ?blank <https://linkedopendata.eu/prop/statement/P851> ?image . "
                        + " ?blank <https://linkedopendata.eu/prop/qualifier/P836> ?summary . "
                        + " ?blank <https://linkedopendata.eu/prop/qualifier/P1743> ?imageCopyright . } "
                        + " } limit "
                        + limit
                        + " offset "
                        + offset;
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 10);

        JSONArray resultList = new JSONArray();
        Set<String> images = new HashSet<>();
        while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            JSONObject item = new JSONObject();
            item.put("item", querySolution.getBinding("s0").getValue().stringValue());
            item.put("image", querySolution.getBinding("image").getValue().stringValue());
            if (querySolution.getBinding("imageCopyright") != null) {
                item.put("imageCopyright", "© " + querySolution.getBinding("imageCopyright").getValue().stringValue());
            }
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
                                                        @RequestParam(value = "budgetBiggerThan", required = false) Long budgetBiggerThen,
                                                        @RequestParam(value = "budgetSmallerThan", required = false) Long budgetSmallerThen,
                                                        @RequestParam(value = "budgetEUBiggerThan", required = false) Long budgetEUBiggerThen,
                                                        @RequestParam(value = "budgetEUSmallerThan", required = false) Long budgetEUSmallerThen,
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
                                                        @RequestParam(value = "limit", defaultValue = "1000") int limit,
                                                        @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                        Principal principal,
                                                        @Context HttpServletResponse response)
            throws Exception {
        final int SPECIAL_OFFSET = Integer.MIN_VALUE;
        final int MAX_LIMIT = 2000;
        // pass a special_offset to skip the caching and query up to the given limit or 10k projects
        ProjectList projectList = (ProjectList) euSearchProject(language, keywords, country, theme, fund, program,
                categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen,
                budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, orderStartDate,
                orderEndDate, orderEuBudget, orderTotalBudget, latitude, longitude, region, Math.min(limit, MAX_LIMIT),
                SPECIAL_OFFSET, 30, principal).getBody();
        XSSFWorkbook hwb = new XSSFWorkbook();
        XSSFSheet sheet = hwb.createSheet("project_export");
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
        cell = row.createCell(7);
        cell.setCellValue("SUMMARY");

        for (Project project : projectList.getList()) {
            rowNumber++;
            row = sheet.createRow(rowNumber);
            cell = row.createCell(0);
            cell.setCellValue(project.getItem());
            cell = row.createCell(1);
            cell.setCellValue(String.join("|", project.getLabels()));
            cell = row.createCell(2);
            cell.setCellType(CellType.NUMERIC);
            if (project.getTotalBudgets().size() > 0)
                cell.setCellValue(Double.parseDouble(project.getTotalBudgets().get(0)));
            cell = row.createCell(3);
            cell.setCellType(CellType.NUMERIC);
            if (project.getEuBudgets().size() > 0)
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

            cell = row.createCell(7);
            if (project.getDescriptions().size() > 0) {
                cell.setCellValue(project.getDescriptions().get(0));
            } else {
                cell.setCellValue("");
            }

        }
        ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
        hwb.write(fileOut);
        fileOut.close();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/vnd.ms-excel");
        headers.set("Content-Disposition", "attachment; filename=\"project_export.xlsx\"");
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
                                    @RequestParam(value = "budgetBiggerThan", required = false) Long budgetBiggerThen,
                                    @RequestParam(value = "budgetSmallerThan", required = false) Long budgetSmallerThen,
                                    @RequestParam(value = "budgetEUBiggerThan", required = false) Long budgetEUBiggerThen,
                                    @RequestParam(value = "budgetEUSmallerThan", required = false) Long budgetEUSmallerThen,
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
                                    @RequestParam(value = "limit", defaultValue = "1000") int limit,
                                    @RequestParam(value = "offset", defaultValue = "0") int offset,
                                    Principal principal,
                                    @Context HttpServletResponse response)
            throws Exception {
        final int SPECIAL_OFFSET = Integer.MIN_VALUE;
        final int MAX_LIMIT = 2000;
        // pass a special_offset to skip the caching and query up to the given limit or 10k projects
        ProjectList projectList =
                (ProjectList) euSearchProject(language, keywords, country, theme, fund, program,
                        categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen,
                        budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore,
                        endDateAfter, orderStartDate, orderEndDate, orderEuBudget, orderTotalBudget, latitude,
                        longitude, region, Math.min(limit, MAX_LIMIT), SPECIAL_OFFSET, 20, principal).getBody();
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
                                    "ID", "PROJECT NAME", "TOTAL BUDGET", "AMOUNT EU SUPPORT", "START DATE", "END DATE", "COUNTRY", "SUMMARY"));
            for (Project project : projectList.getList()) {
                logger.debug("Project: " + project.getItem());
                csvPrinter.printRecord(
                        Arrays.asList(
                                String.join("|", project.getItem()),
                                String.join("|", project.getLabels()),
                                String.join("|", project.getTotalBudgets()),
                                String.join("|", project.getEuBudgets()),
                                String.join("|", project.getStartTimes()),
                                String.join("|", project.getEndTimes()),
                                String.join("|", project.getCountrycode()),
                                String.join("|", project.getDescriptions())
                        )
                );
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

