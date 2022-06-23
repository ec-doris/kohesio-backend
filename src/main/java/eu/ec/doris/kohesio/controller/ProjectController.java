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
import org.apache.lucene.search.Query;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.Binding;
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.eclipse.rdf4j.sail.lucene.SearchFields;

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
            String query =
                    "PREFIX wd: <https://linkedopendata.eu/entity/>\n" +
                            "PREFIX wdt: <https://linkedopendata.eu/prop/direct/>\n" +
                            "PREFIX ps: <https://linkedopendata.eu/prop/statement/>\n" +
                            "PREFIX p: <https://linkedopendata.eu/prop/>\n" +
                            "SELECT ?s0 ?snippet ?label ?description ?infoRegioUrl ?startTime ?endTime ?expectedEndTime ?budget ?euBudget ?cofinancingRate ?image ?imageCopyright ?video ?coordinates  ?countryLabel " +
                            "?countryCode ?programLabel ?programInfoRegioUrl ?categoryLabel ?fundLabel ?themeId ?themeLabel ?themeIdInferred ?themeLabelInferred ?policyId ?policyLabel ?managingAuthorityLabel" +
                            " ?beneficiaryLink ?beneficiary ?beneficiaryLabelRight ?beneficiaryLabel ?transliteration ?beneficiaryWikidata ?beneficiaryWebsite ?beneficiaryString ?source ?source2 " +
                            "?regionId ?regionLabel ?regionUpper1Label ?regionUpper2Label ?regionUpper3Label ?is_statistical_only_0 ?is_statistical_only_1 ?is_statistical_only_2 WHERE { "
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
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P1742> ?infoRegioUrl . }"
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P838> ?expectedEndTime . } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P837> ?cofinancingRate. } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image } . "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P851> ?blank . "
                            + " ?blank <https://linkedopendata.eu/prop/statement/P851> ?image . "
                            + " ?blank <https://linkedopendata.eu/prop/qualifier/P836> ?summary . "
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
                            + "             OPTIONAL { ?program <https://linkedopendata.eu/prop/direct/P1742> ?programInfoRegioUrl . }"
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

                            + " OPTIONAL {"
                            + "                 ?s0 <https://linkedopendata.eu/prop/direct/P1848> ?theme."
                            + "                 ?theme <https://linkedopendata.eu/prop/direct/P1105> ?themeId. "
                            + "                 ?theme <http://www.w3.org/2000/01/rdf-schema#label> ?themeLabel. "
                            + "                 FILTER((LANG(?themeLabel)) = \""
                            + language
                            + "\") } "

                            + " OPTIONAL {"
                            + "           ?s0 <https://linkedopendata.eu/prop/direct/P888> ?category."
                            + "           OPTIONAL { "
                            + "                 ?category <https://linkedopendata.eu/prop/direct/P1848> ?themeInferred."
                            + "                 ?themeInferred <https://linkedopendata.eu/prop/direct/P1105> ?themeIdInferred. "
                            + "                 ?themeInferred <http://www.w3.org/2000/01/rdf-schema#label> ?themeLabelInferred . "
                            + "                 FILTER((LANG(?themeLabelInferred)) = \""
                            + language
                            + "\") } } "
                            + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1848> ?theme.  "
                            + "           ?theme <https://linkedopendata.eu/prop/direct/P1849> ?policy."
                            + "           ?policy <https://linkedopendata.eu/prop/direct/P1747> ?policyId. "
                            + "           ?policy <http://www.w3.org/2000/01/rdf-schema#label> ?policyLabel. "
                            + "           FILTER((LANG(?policyLabel)) = \""
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
                            + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P67> ?beneficiaryWebsite . } "
                            + "          OPTIONAL { ?beneficiaryLink <https://linkedopendata.eu/prop/P7> ?benefStatement . "
                            + "                 ?benefStatement <https://linkedopendata.eu/prop/qualifier/P4393> ?transliteration ."
                            + "          }"
                            + " } "
                            + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P841> ?beneficiaryString .}"

                            + " OPTIONAL { SELECT ?s0 ?region ?regionId ?regionLabel {"
                            + " VALUES ?s0 { <"
                            + id
                            + "> } "
                            + " ?s0  wdt:P1845  ?region . "
                            + "     ?region  wdt:P35  wd:Q2576750 . "
                            + "     OPTIONAL { ?region  wdt:P192  ?regionId . }"
                            + "     OPTIONAL { ?region <http://www.w3.org/2000/01/rdf-schema#label> ?regionLabel . "
                            + "         FILTER ( lang(?regionLabel) = \"" + language + "\" ) "
                            + "     }"
                            + "     FILTER(STRLEN(STR(?regionId))>=5)"
                            + "  } "

//                            "         OPTIONAL\n" +
//                            "           { \n" +
//                            "             \n" +
//                            "               ?region  wdt:P35  ?regionType .\n" +
//                            "             OPTIONAL {\n" +
//                            "                  ?region p:P35 ?blank .\n" +
//                            "                  ?blank ps:P35 ?is_statistical_only_0 .\n" +
//                            "                 filter(?is_statistical_only_0 = wd:Q2727537)\n" +
//                            "               }\n" +
//                            "            \n" +
//                            "             FILTER ( ( ?regionType = wd:Q2576750 ))\n" +
//                            "           \n" +
//                            "         OPTIONAL\n" +
//                            "           { \n" +
//                            "             \n" +
//                            "             ?region   wdt:P1845  ?regionUpper1 .\n" +
//                            "             ?regionUpper1 wdt:P35  ?regionType1 .\n" +
//                            "            \n" +
//                            "             OPTIONAL {\n" +
//                            "                ?regionUpper1 p:P35 ?blank_1 .\n" +
//                            "                 ?blank_1 ps:P35 ?is_statistical_only_1 .\n" +
//                            "                 filter(?is_statistical_only_1 = wd:Q2727537)\n" +
//                            "               }\n" +
//                            "            \n" +
//                            "             FILTER ( ( ?regionType1 = wd:Q2576674 ) )\n" +
//                            "             ?regionUpper1\n" +
//                            "                       <http://www.w3.org/2000/01/rdf-schema#label>  ?regionUpper1Label\n" +
//                            "             FILTER ( lang(?regionUpper1Label) = \""+language+"\" )\n" +
//                            "           }\n" +
//                            "         OPTIONAL\n" +
//                            "           { ?regionUpper1\n" +
//                            "                       wdt:P1845  ?regionUpper2 .\n" +
//                            "             ?regionUpper2 wdt:P35  ?regionType2 .\n" +
//                            "            \n" +
//                            "            OPTIONAL {\n" +
//                            "              \n" +
//                            "             ?regionUpper2 p:P35 ?blank_2 .\n" +
//                            "             ?blank_2 ps:P35 ?is_statistical_only_2 .\n" +
//                            "              filter(?is_statistical_only_2 = wd:Q2727537)\n" +
//                            "              }\n" +
//                            "            \n" +
//                            "             FILTER ( ( ?regionType2 = wd:Q2576630 ) )\n" +
//                            "             ?regionUpper2\n" +
//                            "                       <http://www.w3.org/2000/01/rdf-schema#label>  ?regionUpper2Label\n" +
//                            "             FILTER ( lang(?regionUpper2Label) = \""+language+"\" )\n" +
//                            "           }\n" +
//                            "         OPTIONAL\n" +
//                            "           { ?regionUpper2\n" +
//                            "                       wdt:P1845  ?regionUpper3 .\n" +
//                            "             ?regionUpper3\n" +
//                            "                       <http://www.w3.org/2000/01/rdf-schema#label>  ?regionUpper3Label .\n" +
//                            "             ?regionUpper3 p:P35 ?blank_country .\n" +
//                            "            ?blank_country ps:P35 wd:Q510 ." +
//                            "             FILTER ( lang(?regionUpper3Label) = \"en\" )\n" +
//                            "           }\n" +
//                            "     |      }\n" +
                            + " } "
                            + " } ";


            TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 2, false);
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
            result.put("fundLabel", "");
            result.put("programmingPeriodLabel", "2014-2020");
            result.put("programLabel", "");
            result.put("programWebsite", "");
            result.put("programInfoRegioUrl", "");
            result.put("themeIds", new JSONArray());
            result.put("themeLabels", new JSONArray());
            result.put("policyIds", new JSONArray());
            result.put("policyLabels", new JSONArray());
            result.put("projectWebsite", "");
            result.put("coordinates", new JSONArray());
            result.put("images", new JSONArray());
            result.put("videos", new JSONArray());
            result.put("beneficiaries", new JSONArray());
            result.put("managingAuthorityLabel", "");
            result.put("region", "");
            result.put("geoJson", new JSONArray());
            result.put("regionUpper1", "");
            result.put("regionUpper2", "");
            result.put("regionUpper3", "");
            result.put("infoRegioUrl", "");

            HashSet<String> regionIDs = new HashSet<>();
            HashSet<String> coordinatesSet = new HashSet<>();
            HashSet<String> regions = new HashSet<>();
            HashSet<String> interventionFieldsSet = new HashSet<>();
            HashSet<String> themeLabels = new HashSet<>();
            HashSet<String> themeIds = new HashSet<>();
            HashSet<String> policyLabels = new HashSet<>();
            HashSet<String> policyIds = new HashSet<>();

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
                        ((JSONArray) result.get("countryCode")).add(
                                querySolution.getBinding("countryCode").getValue().stringValue()
                        );
                }

                if (querySolution.getBinding("categoryLabel") != null) {
                    String interventionField = querySolution.getBinding("categoryLabel").getValue().stringValue();
                    if (!interventionFieldsSet.contains(interventionField)) {
                        interventionFieldsSet.add(interventionField);
                        result.put("categoryLabels", interventionFieldsSet);
                    }
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
                if (querySolution.getBinding("programInfoRegioUrl") != null) {
                    result.put(
                            "programInfoRegioUrl",
                            querySolution.getBinding("programInfoRegioUrl").getValue().stringValue()
                    );
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
                if (querySolution.getBinding("source2") != null) {
                    result.put(
                            "programWebsite", querySolution.getBinding("source2").getValue().stringValue());
                }


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
                    JSONArray images = (JSONArray) result.get("videos");
                    String im = querySolution.getBinding("video").getValue().stringValue();
                    if (!images.contains(im)) {
                        images.add(im);
                        result.put("videos", images);
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
                if (querySolution.getBinding("regionLabel") != null && querySolution.getBinding("is_statistical_only_0") == null) {
                    result.put("region", ((Literal) querySolution.getBinding("regionLabel").getValue())
                            .stringValue());
                }
                if (querySolution.getBinding("regionUpper1Label") != null && querySolution.getBinding("is_statistical_only_1") == null) {
                    result.put("regionUpper1", ((Literal) querySolution.getBinding("regionUpper1Label").getValue())
                            .stringValue());
                }
                if (querySolution.getBinding("regionUpper2Label") != null && querySolution.getBinding("is_statistical_only_2") == null) {
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
                    if (!result.get("countryLabel").equals(regionText))
                        regionText += ", " + String.join(", ", (JSONArray) result.get("countryLabel"));

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
                        // check if the regioId has already been seen - could be that a project is contained in multipl geometries
                        regionIDs.add(regionId);
                        regions.add(regionLabel);
                        query =
                                "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                                        + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                                        + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                                        + "SELECT ?id ?geoJson  WHERE { "
                                        + "?s <http://nuts.de/id> \'" + regionId + "\' . "
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
                result.put("regionText", "multiple locations, " + String.join(", ", (JSONArray) result.get("countryLabel")));
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
                                           Integer timeout,
                                           Principal principal)
            throws Exception {
        if (timeout == null) {
            timeout = 20;
            if (keywords == null) {
                timeout = 100;
            }
        }
        logger.info("Project search: language {}, keywords {}, country {}, theme {}, fund {}, program {}, region {}, timeout {}", language, keywords, country, theme, fund, program, region, timeout);

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
                null,
                limit,
                offset
        );

        int numResults = 0;

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
            orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P474> ?budget. ";
            if (orderTotalBudget) {
                orderBy = "order by asc(?budget)";
            } else {
                orderBy = "order by desc(?budget)";
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
        //search = "";

        if (search.equals(
                "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ")) {
            search = " { SELECT ?s0 ?snippet where { " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . " +
                    "    } " +
                    "  } UNION { SELECT ?s0 ?snippet where { " +
                    "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> ." +
                    "    } " +
                    "    }";
        }

        search += " " + orderQuery;
        query =
                "SELECT ?s0 ?snippet ?label ?description ?startTime ?endTime ?expectedEndTime ?totalBudget ?euBudget ?image ?imageCopyright ?coordinates ?objectiveId ?countrycode ?summary WHERE { "
                        + " { SELECT ?s0 ?description WHERE { "
                        + search
                        + " } " + orderBy + " LIMIT "
                        + limit
                        + " OFFSET "
                        + offset
                        + " } "
                        + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                        + " FILTER((LANG(?label)) = \""
                        + language
                        + "\") }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P836> ?description. FILTER((LANG(?description)) = \""
                        + language
                        + "\") } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P838> ?expectedEndTime . }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime . } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime . } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P851> ?blank . "
                        + " ?blank <https://linkedopendata.eu/prop/statement/P851> ?image . "
                        + " ?blank <https://linkedopendata.eu/prop/qualifier/P1743> ?imageCopyright . } "
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P474> ?totalBudget. }"
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country . ?country <https://linkedopendata.eu/prop/direct/P173> ?countrycode .} "
                        + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P888> ?category .  OPTIONAL {?category <https://linkedopendata.eu/prop/direct/P1848> ?objective. ?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId. } } "
                        + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P836> ?summary. "
                        + " FILTER(LANG(?summary)=\"" + language + "\")"
                        + "} "
                        + "} ";


        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout);

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
        Set<String> imageCopyright = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        Set<String> objectiveId = new HashSet<>();
        Set<String> countrycode = new HashSet<>();
        Set<String> summary = new HashSet<>();
        ArrayList<String> similarWords = new ArrayList<>();

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
                    project.setCopyrightImages(new ArrayList<>(imageCopyright));
                    project.setCoordinates(new ArrayList<String>(coordinates));
                    project.setObjectiveIds(new ArrayList<String>(objectiveId));
                    project.setCountrycode(new ArrayList<String>(countrycode));
                    project.setSummary(new ArrayList<>(summary));
                    resultList.add(project);
                    snippet = new HashSet<>();
                    label = new HashSet<>();
                    description = new HashSet<>();
                    startTime = new HashSet<>();
                    endTime = new HashSet<>();
                    euBudget = new HashSet<>();
                    totalBudget = new HashSet<>();
                    image = new HashSet<>();
                    imageCopyright = new HashSet<>();
                    coordinates = new HashSet<>();
                    objectiveId = new HashSet<>();
                    countrycode = new HashSet<>();
                    summary = new HashSet<>();
                    similarWords = new ArrayList<>();
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
            if (querySolution.getBinding("description") != null && expandedQuery == null)
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
            if (querySolution.getBinding("imageCopyright") != null) {
                imageCopyright.add("© " + querySolution.getBinding("imageCopyright").getValue().stringValue());
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
            if (querySolution.getBinding("summary") != null)
                summary.add(querySolution.getBinding("summary").getValue().stringValue());

            // try to create the snippet based on the given expanded query
            if (expandedQuery != null) {
                StringBuilder textInput = new StringBuilder();
                if (querySolution.getBinding("label") != null) {
                    String labelText = ((Literal) querySolution.getBinding("label").getValue()).getLabel();
                    textInput.append(labelText);
                }
                textInput.append("<br/>");

                if (querySolution.getBinding("description") != null) {
                    String descriptionText = ((Literal) querySolution.getBinding("description").getValue()).getLabel();
                    textInput.append(descriptionText);
                }
                String snippetText = getSnippet(expandedQuery.getExpandedQuery(), textInput.toString());
                // replace the description with the snippet text
                description.add(snippetText);

            }
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
            project.setCopyrightImages(new ArrayList<>(imageCopyright));
            project.setCoordinates(new ArrayList<String>(coordinates));
            project.setObjectiveIds(new ArrayList<String>(objectiveId));
            project.setCountrycode(new ArrayList<String>(countrycode));
            project.setSummary(new ArrayList<String>(summary));
            resultList.add(project);
        }
        ProjectList projectList = new ProjectList();
        int upperLimit = 990;
        if (keywords != null)
            upperLimit = 90;
        if (offset <= upperLimit) {
            for (int i = inputOffset; i < Math.min(resultList.size(), inputOffset + inputLimit); i++) {
                projectList.getList().add(resultList.get(i));
            }
        } else {
            projectList.setList(resultList);
        }
        projectList.setNumberResults(numResults);

        if (expandedQuery != null && expandedQuery.getKeywords() != null) {
            for (SimilarWord similarWord : expandedQuery.getKeywords()) {
                similarWords.add(similarWord.getWord());
            }
        }
        projectList.setSimilarWords(similarWords);
        return new ResponseEntity<ProjectList>(projectList, HttpStatus.OK);
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
            Principal principal)
            throws Exception {
        logger.info("Project image search: language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, null);

        // expand the query keywords
        ExpandedQuery expandedQuery = null;
        String expandedQueryText = null;
        if (keywords != null) {
            expandedQuery = similarityService.expandQuery(keywords);
            expandedQueryText = expandedQuery.getExpandedQuery();
        }

        String search = filtersGenerator.filterProject(expandedQueryText, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, null, limit, offset);

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
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
            if (project.getTotalBudgets().size() > 0)
                cell.setCellValue(Double.parseDouble(project.getTotalBudgets().get(0)));
            cell = row.createCell(3);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
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
            if (project.getSummary().size() > 0) {
                cell.setCellValue(project.getSummary().get(0));
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
                                String.join("|", project.getSummary())
                        )
                );
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

