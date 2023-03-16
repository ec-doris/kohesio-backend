package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ec.doris.kohesio.payload.Beneficiary;
import eu.ec.doris.kohesio.payload.BeneficiaryList;
import eu.ec.doris.kohesio.services.FiltersGenerator;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.util.Precision;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.ss.usermodel.CellType;
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
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.DecimalFormat;
import java.util.*;

@RestController
@RequestMapping("/api")

public class BeneficiaryController {
    private static final Logger logger = LoggerFactory.getLogger(BeneficiaryController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Autowired
    FiltersGenerator filtersGenerator;

    private static DecimalFormat df2 = new DecimalFormat("0.00");

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/beneficiary", produces = "application/json")
    public ResponseEntity euBenfeciaryId(
            @RequestParam(value = "id") String id,
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "100") int pageSize
    ) throws Exception {

        logger.info("Beneficiary search by ID: id {}, language {}", id, language);
        String queryCheck = "ask {\n" +
                " <" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q196899>\n" +
                "}";

        boolean resultAsk = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
//            String queryCheckRedirect = " select ?redirect where { "+
//                    " <https://linkedopendata.eu/entity/Q257756> <http://www.w3.org/2002/07/owl#sameAs> ?redirect } ";
//            TupleQueryResult resultSet1 = sparqlQueryService.executeAndCacheQuery(publicSparqlEndpoint, queryCheckRedirect, 3);
//            if (resultSet1.hasNext()){
//                return euBenfeciaryId(resultSet1.next().getBinding("redirect").getValue().stringValue(),language);
//            } else {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - beneficiary ID not found");
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
//            }
        }

        String labelsFilter = getBeneficiaryLabelsFilter();
        String query1 = "SELECT ?s0 ?country ?countryCode ?beneficiaryLabel_en ?beneficiaryLabel ?transliteration ?description ?website ?image ?logo ?coordinates ?wikipedia WHERE {\n"
                + " VALUES ?s0 { <"
                + id
                + "> } " +
                "  ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country .  \n" +
                "  ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . \n "
                + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/P7> ?benefStatement . "
                + "  ?benefStatement <https://linkedopendata.eu/prop/qualifier/P4393> ?transliteration ."
                + " }" +
                "  OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel_en . \n" +
                "  FILTER(LANG(?beneficiaryLabel_en) = \"" + language + "\" ) } \n" +
                "  OPTIONAL { ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . \n" +
                labelsFilter +
                " } \n" +

                "  OPTIONAL {  ?s0 <http://schema.org/description> ?description .  FILTER (lang(?description)=\"" + language + "\") }\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P67> ?website .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P147> ?image .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P537> ?logo .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates .}\n" +
                "  OPTIONAL{ " +
                "      ?wikipedia <http://schema.org/about> ?s0 ; " +
                "                 <http://schema.org/inLanguage> \"" + language + "\" ;" +
                "                 <http://schema.org/isPartOf> <https://" + language + ".wikipedia.org/> ." + "}\n " +
                "}";

        String query2 = "SELECT ?s0 (SUM(?euBudget) AS ?totalEuBudget) (SUM(?budget) AS ?totalBudget) (COUNT(DISTINCT?project) AS ?numberProjects) (MIN(?startTime) AS ?minStartTime) (MAX(?endTime) AS ?maxEndTime) WHERE {\n" +
                " VALUES ?s0 { <" +
                id +
                "> } " +
                "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 .  \n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +

                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . }\n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P20> ?startTime . }\n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P33> ?endTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P838> ?endTime . } \n" +
                "  \n" +
                "} GROUP BY ?s0";

        String query3 = "SELECT ?project ?label ?euBudget ?budget ?startTime ?endTime ?fundLabel WHERE {\n" +
                " VALUES ?s0 { <" +
                id +
                "> } " +
                "  ?project <http://www.w3.org/2000/01/rdf-schema#label> ?label .\n" +
                "  FILTER (lang(?label)=\"" + language + "\") .\n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . \n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 .  \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P20> ?startTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P33> ?endTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P1584> ?fund . \n" +
                "            ?fund <https://linkedopendata.eu/prop/direct/P1583> ?fundLabel } \n " +
                "} ORDER BY DESC(?euBudget) LIMIT " + pageSize + "OFFSET " + pageSize * page;

        String query4 = "SELECT ?fundLabel (sum(?euBudget) as ?totalEuBudget) WHERE { "
                + " VALUES ?s0 { <" + id + "> } "
                + "  ?project <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . "
                + "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 . "
                + "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . } "
                + "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P1584> ?fund . "
                + "            ?fund <https://linkedopendata.eu/prop/direct/P1583> ?fundLabel } "
                + " } GROUP BY ?fundLabel ORDER BY DESC(?totalEuBudget)";

        TupleQueryResult resultSet1 = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query1, 30, "beneficiaryDetail");
        JSONObject result = new JSONObject();
        result.put("item", id.replace("https://linkedopendata.eu/entity/", ""));
        while (resultSet1.hasNext()) {
            BindingSet querySolution = resultSet1.next();
            if (querySolution.getBinding("country") != null) {
                result.put("country", querySolution.getBinding("country").getValue().stringValue());
            }
            if (querySolution.getBinding("countryCode") != null) {
                result.put("countryCode", querySolution.getBinding("countryCode").getValue().stringValue());
            }
            if (querySolution.getBinding("beneficiaryLabel") != null) {
                result.put("beneficiaryLabel", ((Literal) querySolution.getBinding("beneficiaryLabel").getValue()).getLabel());
            }
            if (querySolution.getBinding("beneficiaryLabel_en") != null) {
                result.put("beneficiaryLabel", ((Literal) querySolution.getBinding("beneficiaryLabel_en").getValue()).getLabel());
            }
            if (querySolution.getBinding("transliteration") != null) {
                result.put("transliteration", ((Literal) querySolution.getBinding("transliteration").getValue()).getLabel());
            }
            if (querySolution.getBinding("description") != null) {
                result.put("description", querySolution.getBinding("description").getValue().stringValue());
            } else {
                result.put("description", "");
            }
            if (querySolution.getBinding("website") != null) {
                result.put("website", querySolution.getBinding("website").getValue().stringValue());
            } else {
                result.put("website", "");
            }
            if (querySolution.getBinding("coordinates") != null) {
                result.put("coordinates", querySolution.getBinding("coordinates").getValue().stringValue());
            } else {
                result.put("coordinates", "");
            }
            if (querySolution.getBinding("wikipedia") != null) {
                String wikipedia = querySolution.getBinding("wikipedia").getValue().stringValue();
                result.put("wikipedia", wikipedia);
                // if wikipedia link extract the description from wikipedia
                String url = "https://" + language + ".wikipedia.org/w/api.php?format=json&action=query&prop=extracts&exintro=&origin=*&explaintext=&titles=" + URLDecoder.decode(wikipedia.replace("https://" + language + ".wikipedia.org/wiki/", ""), StandardCharsets.UTF_8.toString());
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().equals(HttpStatus.OK)) {
                    logger.debug(response.getBody());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response.getBody());
                    if (root.findValue("extract") != null) {
                        String desc = root.findValue("extract").textValue();
                        result.put("description", desc + " (from Wikipedia)");
                    }
                }
            } else {
                result.put("wikipedia", "");
            }
            JSONArray images = new JSONArray();
            if (querySolution.getBinding("image") != null) {
                images.add(querySolution.getBinding("image").getValue().stringValue().replace("http://commons.wikimedia.org/", "https://commons.wikimedia.org/"));
            }
            if (querySolution.getBinding("logo") != null) {
                images.add(querySolution.getBinding("logo").getValue().stringValue().replace("http://commons.wikimedia.org/", "https://commons.wikimedia.org/"));
            }
            result.put("images", images);
        }
        TupleQueryResult resultSet2 = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query2, 30, "beneficiaryDetail");
        while (resultSet2.hasNext()) {
            BindingSet querySolution = resultSet2.next();
            if (querySolution.getBinding("totalEuBudget") != null) {
                result.put("totalEuBudget", df2.format(((Literal) querySolution.getBinding("totalEuBudget").getValue()).doubleValue()));
            }
            if (querySolution.getBinding("totalBudget") != null) {
                result.put("totalBudget", df2.format(((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue()));
            }
            if (querySolution.getBinding("minStartTime") != null) {
                result.put("minStartTime", querySolution.getBinding("minStartTime").getValue().stringValue().split("T")[0]);
            }
            if (querySolution.getBinding("maxEndTime") != null) {
                result.put("maxEndTime", querySolution.getBinding("maxEndTime").getValue().stringValue().split("T")[0]);
            }
            if (querySolution.getBinding("numberProjects") != null) {
                result.put("numberProjects", ((Literal) querySolution.getBinding("numberProjects").getValue()).intValue());
            }
        }
        JSONArray projects = new JSONArray();
        TupleQueryResult resultSet3 = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query3, 30, "beneficiaryDetail");
        if (resultSet3 != null) {
            while (resultSet3.hasNext()) {
                JSONObject project = new JSONObject();
                BindingSet querySolution = resultSet3.next();
                if (querySolution.getBinding("project") != null) {
                    project.put("project", querySolution.getBinding("project").getValue().stringValue());
                }
                if (querySolution.getBinding("label") != null) {
                    project.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
                }
                if (querySolution.getBinding("euBudget") != null) {
                    project.put("euBudget", df2.format(((Literal) querySolution.getBinding("euBudget").getValue()).doubleValue()));
                }
                if (querySolution.getBinding("budget") != null) {
                    project.put("budget", df2.format(((Literal) querySolution.getBinding("budget").getValue()).doubleValue()));
                }
                if (querySolution.getBinding("fundLabel") != null) {
                    project.put("fundLabel", ((Literal) querySolution.getBinding("fundLabel").getValue()).getLabel());
                }
                if (querySolution.getBinding("startTime") != null) {
                    project.put("startTime", querySolution.getBinding("startTime").getValue().stringValue().split("T")[0]);
                }
                if (querySolution.getBinding("endTime") != null) {
                    project.put("endTime", querySolution.getBinding("endTime").getValue().stringValue().split("T")[0]);
                }
                projects.add(project);
            }
        }
        JSONArray budgetsPerFund = new JSONArray();
        TupleQueryResult resultSet4 = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query4, 30, "beneficiaryDetail");
        if (resultSet4 != null) {
            while (resultSet4.hasNext()) {
                JSONObject budgetPerFund = new JSONObject();
                BindingSet querySolution = resultSet4.next();
                if (querySolution.getBinding("totalEuBudget") != null) {
                    budgetPerFund.put("totalEuBudget", df2.format(((Literal) querySolution.getBinding("totalEuBudget").getValue()).doubleValue()));
                }
                if (querySolution.getBinding("fundLabel") != null) {
                    budgetPerFund.put("fundLabel", ((Literal) querySolution.getBinding("fundLabel").getValue()).getLabel());
                } else {
                    budgetPerFund.put("fundLabel", "Other funds");
                }
                budgetsPerFund.add(budgetPerFund);
            }
        }
        result.put("budgetsPerFund", budgetsPerFund);
        result.put("projects", projects);
        return new ResponseEntity(result, HttpStatus.OK);

    }

    @GetMapping(value = "/facet/eu/search/beneficiaries", produces = "application/json")
    public ResponseEntity euSearchBeneficiaries(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "name", required = false) String keywords,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude,
            @RequestParam(value = "fund", required = false) String fund,
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "beneficiaryType", required = false) String beneficiaryType,
            @RequestParam(value = "orderEuBudget", required = false) Boolean orderEuBudget,
            @RequestParam(value = "orderTotalBudget", defaultValue = "false") Boolean orderTotalBudget,
            @RequestParam(value = "orderNumProjects", required = false) Boolean orderNumProjects,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            Principal principal
    ) throws Exception {
        int timeout = 20;
        if (keywords == null) {
            timeout = 500;
        }
        logger.info("Beneficiary search: language {}, name {}, country {}, region {}, latitude {}, longitude {}, fund {}, program {}, orderEuBudget {}, orderTotalBudget {}, orderNumProjects {}, timeout {}", language, keywords, country, region, latitude, longitude, fund, program, orderEuBudget, orderTotalBudget, orderNumProjects, timeout);


        int inputOffset = offset;
        int inputLimit = limit;
        if (offset != Integer.MIN_VALUE) {
            if (offset <= 1000 - inputLimit) {
                offset = 0;
                limit = 1000;
            }
        } else {
            offset = 0;
            inputOffset = 0;
        }
        String search = "";

        search += "?project <https://linkedopendata.eu/prop/direct/P889> ?beneficiary . "
                + "?project <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";

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
            search += "?beneficiary <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                    + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                    + keywords.replace("\"", "\\\"")
                    + "\" ; "
                    + " <http://www.openrdf.org/contrib/lucenesail#indexid> <http://the-qa-company.com/modelcustom/Ben> ; "
                    + "<http://www.openrdf.org/contrib/lucenesail#snippet> ?snippet ] . ";
        }

        if (country != null) {
            search += " VALUES ?country { <" + country + "> } ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country. ";
        }

        if (region != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . ";
        }

        if (latitude != null && longitude != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                    + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
                    + longitude + " " + latitude
                    + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)< 100000) . ";
        }

        if (fund != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1584> <" + fund + "> . ";
        }

        if (program != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1368> <" + program + "> . ";
        }


        if (beneficiaryType != null) {
            if (beneficiaryType.equals("private")) {
                search += "?beneficiary <https://linkedopendata.eu/prop/P35> ?blank_class . "
                        + "?blank_class <https://linkedopendata.eu/prop/statement/P35> <https://linkedopendata.eu/entity/Q2630487>. ";
            } else if (beneficiaryType.equals("public")) {
                search += "?beneficiary <https://linkedopendata.eu/prop/P35> ?blank_class . "
                        + "?blank_class <https://linkedopendata.eu/prop/statement/P35> <https://linkedopendata.eu/entity/Q2630486>. ";
            }
        }
        String queryCount = "SELECT (COUNT(DISTINCT ?beneficiary) AS ?c) WHERE { " + search + " }";
        logger.debug(queryCount);


        TupleQueryResult countResultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, queryCount, timeout, "beneficiarySearch");

        search += "OPTIONAL { ?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget.} "
                + "OPTIONAL { ?project <https://linkedopendata.eu/prop/direct/P474> ?budget.} ";

        int numResults = 0;
        if (countResultSet.hasNext()) {
            BindingSet querySolution = countResultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }


        String orderBy = "";
        Comparator<Object> orderComparator = null;
        if (orderTotalBudget != null) {
            if (orderTotalBudget) {
                orderBy = " ORDER BY ASC(?totalBudget) ";
                orderComparator = Comparator.comparing(beneficiary -> {
                    if (!"".equals(((Beneficiary) beneficiary).getBudget())) {
                        return Double.parseDouble(((Beneficiary) beneficiary).getBudget());
                    }
                    return 0.;
                });
            } else {
                orderBy = " ORDER BY DESC(?totalBudget) ";
                orderComparator = Comparator.comparing(beneficiary -> {
                    if (!"".equals(((Beneficiary) beneficiary).getBudget())) {
                        return Double.parseDouble(((Beneficiary) beneficiary).getBudget());
                    }
                    return 0.;
                }).reversed();
            }
        }
        if (orderEuBudget != null) {
            if (orderEuBudget) {
                orderBy = " ORDER BY ASC(?totalEuBudget) ";
                orderComparator = Comparator.comparing(beneficiary -> {
                    if (!"".equals(((Beneficiary) beneficiary).getBudget())) {
                        return Double.parseDouble(((Beneficiary) beneficiary).getEuBudget());
                    }
                    return 0.;
                });
            } else {
                orderBy = " ORDER BY DESC(?totalEuBudget) ";
                orderComparator = Comparator.comparing(beneficiary -> {
                    if (!"".equals(((Beneficiary) beneficiary).getBudget())) {
                        return Double.parseDouble(((Beneficiary) beneficiary).getEuBudget());
                    }
                    return 0.;
                }).reversed();
            }
        }
        if (orderNumProjects != null) {
            if (orderNumProjects) {
                orderBy = " ORDER BY ASC(?numberProjects)";
                orderComparator = Comparator.comparing(beneficiary -> ((Beneficiary) beneficiary).getNumberProjects());
            } else {
                orderBy = " ORDER BY DESC(?numberProjects)";
                orderComparator = Comparator.comparing(beneficiary -> ((Beneficiary) beneficiary).getNumberProjects()).reversed();
            }
        }
        String labelsFilter = getBeneficiaryLabelsFilter();

        String countryInfo = "OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country. "
                + "?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . } ";


        String mainQuery = "SELECT ?beneficiary (COUNT(DISTINCT ?project) AS ?numberProjects) (SUM(?budget) AS ?totalBudget) (SUM(?euBudget) AS ?totalEuBudget) { "
                + search
                + "} GROUP BY ?beneficiary " +
                orderBy
                + " LIMIT " + limit
                + " OFFSET " + offset;

        HashMap<String, Beneficiary> beneficiaries = new HashMap<>();

        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, mainQuery, timeout, "beneficiarySearch");
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
                        String uri = querySolution.getBinding("beneficiary").getValue().stringValue();
                        Beneficiary beneficiary = new Beneficiary();
                        beneficiary.setId(uri);
                        if (querySolution.getBinding("numberProjects") != null) {
                            beneficiary.setNumberProjects(
                                    ((Literal) querySolution.getBinding("numberProjects").getValue()).intValue());
                        }
                        if (querySolution.getBinding("totalEuBudget") != null) {

                            double val = ((Literal) querySolution.getBinding("totalEuBudget").getValue()).doubleValue();
                            if (val != 0) {
                                beneficiary.setEuBudget(
                                        String.valueOf(
                                                Precision.round(
                                                        val,
                                                        2)));
                            } else {
                                beneficiary.setEuBudget("");
                            }
                        }
                        if (querySolution.getBinding("totalBudget") != null) {
                            double val = ((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue();
                            if (val != 0) {
                                beneficiary.setBudget(
                                        String.valueOf(
                                                Precision.round(
                                                        val,
                                                        2)));
                            } else {
                                // meaning that there is no budgets for associated projects  ( sum(budgets) = 0  and budgets= [] )
                                beneficiary.setBudget("");
                            }
                        }
                        beneficiaries.put(uri, beneficiary);
                        values.append(" ").append("<").append(uri).append(">");
                        indexLimit++;
                    }
                }
            } else {
                values.append(" ").append("<").append(querySolution.getBinding("beneficiary").getValue().stringValue()).append(">");
            }
        }

        String query = "SELECT ?beneficiary ?beneficiaryLabel ?beneficiaryLabel_en ?country ?countryCode ?link ?transliteration { "
                + " VALUES ?beneficiary { " + values + " }"
                + "  OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel_en ."
                + "              FILTER(LANG(?beneficiaryLabel_en) = \"" + language + "\" ) } "
                + " OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . "
                + "            ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country .   "
                + labelsFilter
                + " }"
                + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P1> ?link. } "
                + countryInfo
                + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/P7> ?benefStatement . "
                + "  ?benefStatement <https://linkedopendata.eu/prop/qualifier/P4393> ?transliteration ."
                + " }"
                + "} ";
        logger.debug(query);
        resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, timeout, false, "beneficiarySearch");

        if (resultSet != null) {
            String previewsKey = "";
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                String currentKey = querySolution.getBinding("beneficiary").getValue().stringValue();
                Beneficiary beneficiary = beneficiaries.get(currentKey);
                beneficiary.computeCofinancingRate();

                if (querySolution.getBinding("beneficiaryLabel") != null) {
                    beneficiary.setLabel(
                            ((Literal) querySolution.getBinding("beneficiaryLabel").getValue()).getLabel()
                    );
                }

                if (querySolution.getBinding("beneficiaryLabel_en") != null && !querySolution.getBinding("beneficiaryLabel_en").equals("")) {
                    beneficiary.setLabel(
                            ((Literal) querySolution.getBinding("beneficiaryLabel_en").getValue()).getLabel()
                    );
                }

                if (querySolution.getBinding("country") != null) {
                    beneficiary.setCountry(
                            querySolution.getBinding("country").getValue().stringValue()
                    );
                }

                if (querySolution.getBinding("countryCode") != null) {
                    beneficiary.setCountryCode(
                            ((Literal) querySolution.getBinding("countryCode").getValue()).stringValue());
                }

                if (querySolution.getBinding("link") != null) {
                    beneficiary.setLink(
                            "http://wikidata.org/entity/"
                                    + ((Literal) querySolution.getBinding("link").getValue()).getLabel());
                }

                if (querySolution.getBinding("transliteration") != null && querySolution.getBinding("beneficiaryLabel_en") == null) {
                    beneficiary.setTransliteration(
                            querySolution.getBinding("transliteration").getValue().stringValue()
                    );
                }
            }
        }
        BeneficiaryList finalRes = new BeneficiaryList();
        finalRes.setNumberResults(numResults);
        List<Beneficiary> tmpRes = new ArrayList<>(beneficiaries.values());
        if (orderComparator != null) {
            tmpRes.sort(orderComparator);
        }
        finalRes.setList(new ArrayList<>(tmpRes));

        return new ResponseEntity<>(finalRes, HttpStatus.OK);
    }

    @GetMapping(value = "/facet/eu/search/beneficiaries/csv", produces = "application/json")
    public void euSearchBeneficiariesCSV( //
                                          @RequestParam(value = "language", defaultValue = "en") String language,
                                          @RequestParam(value = "name", required = false) String keywords, //
                                          @RequestParam(value = "country", required = false) String country, //
                                          @RequestParam(value = "region", required = false) String region, //
                                          @RequestParam(value = "latitude", required = false) String latitude, //
                                          @RequestParam(value = "longitude", required = false) String longitude, //
                                          @RequestParam(value = "fund", required = false) String fund, //
                                          @RequestParam(value = "program", required = false) String program, //
                                          @RequestParam(value = "beneficiaryType", required = false) String beneficiaryType, //
                                          @RequestParam(value = "limit", defaultValue = "1000") int limit,
                                          Principal principal,
                                          @Context HttpServletResponse response)
            throws Exception {
        // if "limit" parameter passed to get a specific number of rows just pass it to euSearchBeneficiaries
        // by default it export 1000

        final int SPECIAL_OFFSET = Integer.MIN_VALUE;
        final int MAX_LIMIT = 2000;

        BeneficiaryList beneficiaryList = ((BeneficiaryList) euSearchBeneficiaries(
                language,
                keywords,
                country,
                region,
                latitude,
                longitude,
                fund,
                program,
                beneficiaryType,
                false,
                false,
                false,
                Math.min(limit, MAX_LIMIT),
                SPECIAL_OFFSET,
                principal
        ).getBody());
        String filename = "beneficiary_export.csv";
        try {
            response.setContentType("text/csv");
            response.setCharacterEncoding("UTF-8");
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            CSVPrinter csvPrinter =
                    new CSVPrinter(
                            response.getWriter(),
                            CSVFormat.DEFAULT.withHeader(
                                    "BENEFICIARY NAME", "TOTAL BUDGET", "AMOUNT EU SUPPORT", "NUMBER OF PROJECTS"));
            for (Beneficiary beneficiary : beneficiaryList.getList()) {
                csvPrinter.printRecord(
                        Arrays.asList(
                                beneficiary.getLabel(),
                                beneficiary.getBudget(),
                                beneficiary.getEuBudget(),
                                beneficiary.getNumberProjects()));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping(value = "/facet/eu/search/beneficiaries/excel", produces = "application/json")
    public ResponseEntity<byte[]> euSearchBeneficiariesExcel( //
                                                              @RequestParam(value = "language", defaultValue = "en") String language,
                                                              @RequestParam(value = "name", required = false) String keywords, //
                                                              @RequestParam(value = "country", required = false) String country, //
                                                              @RequestParam(value = "region", required = false) String region, //
                                                              @RequestParam(value = "latitude", required = false) String latitude, //
                                                              @RequestParam(value = "longitude", required = false) String longitude, //
                                                              @RequestParam(value = "fund", required = false) String fund, //
                                                              @RequestParam(value = "program", required = false) String program, //
                                                              @RequestParam(value = "beneficiaryType", required = false) String beneficiaryType, //
                                                              @RequestParam(value = "limit", defaultValue = "1000") int limit,
                                                              Principal principal)
            throws Exception {
        // if "limit" parameter passed to get a specific number of rows just pass it to euSearchBeneficiaries
        // by default it export 1000

        final int SPECIAL_OFFSET = Integer.MIN_VALUE;
        final int MAX_LIMIT = 2000;
        BeneficiaryList beneficiaryList = ((BeneficiaryList) euSearchBeneficiaries(
                language,
                keywords,
                country,
                region,
                latitude,
                longitude,
                fund,
                program,
                beneficiaryType,
                false,
                false,
                false,
                Math.min(limit, MAX_LIMIT),
                SPECIAL_OFFSET,
                principal
        ).getBody());
        XSSFWorkbook hwb = new XSSFWorkbook();
        XSSFSheet sheet = hwb.createSheet("beneficiary_export");
        int rowNumber = 0;
        XSSFRow row = sheet.createRow(rowNumber);
        XSSFCell cell = row.createCell(0);
        cell.setCellValue("BENEFICIARY NAME");
        cell = row.createCell(1);
        cell.setCellValue("TOTAL BUDGET");
        cell = row.createCell(2);
        cell.setCellValue("AMOUNT EU SUPPORT");
        cell = row.createCell(3);
        cell.setCellValue("NUMBER OF PROJECTS");
        for (Beneficiary beneficiary : beneficiaryList.getList()) {
            rowNumber++;
            row = sheet.createRow(rowNumber);
            cell = row.createCell(0);
            if (!"".equals(beneficiary.getLabel())) {
                cell.setCellValue(beneficiary.getLabel());
            }
            cell = row.createCell(1);
            cell.setCellType(CellType.NUMERIC);
            if (!"".equals(beneficiary.getBudget())) {
                cell.setCellValue(Double.parseDouble(beneficiary.getBudget()));
            }
            cell = row.createCell(2);
            cell.setCellType(CellType.NUMERIC);
            if (!"".equals(beneficiary.getEuBudget())) {
                cell.setCellValue(Double.parseDouble(beneficiary.getEuBudget()));
            }
            cell = row.createCell(3);
            cell.setCellType(CellType.NUMERIC);
            cell.setCellValue(beneficiary.getNumberProjects());
        }
        ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
        hwb.write(fileOut);
        fileOut.close();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/vnd.ms-excel");
        headers.set("Content-Disposition", "attachment; filename=\"beneficiary_export.xlsx\"");
        return new ResponseEntity<byte[]>(fileOut.toByteArray(), headers, HttpStatus.OK);
    }

    @GetMapping(value = "/facet/eu/beneficiary/project", produces = "application/json")
    public ResponseEntity euBenfeciaryIdProject(@RequestParam(value = "id") String id,
                                                @RequestParam(value = "language", defaultValue = "en") String language,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "pageSize", defaultValue = "100") int pageSize
    ) throws Exception {
        logger.info("Beneficiary search projects by ID: id {}, language {}", id, language);
        String queryCheck = "ask { " +
                "<" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q196899> " +
                "}";

        boolean resultAsk = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - beneficiary ID not found");
            return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
        }
        String query3 = "select ?project ?label ?euBudget ?budget ?startTime ?endTime ?fundLabel where {\n" +
                " VALUES ?s0 { <" +
                id +
                "> } " +
                "  ?project <http://www.w3.org/2000/01/rdf-schema#label> ?label .\n" +
                "  FILTER (lang(?label)=\"" + language + "\") .\n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . \n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 .  \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P20> ?startTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P33> ?endTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P1584> ?fund . \n" +
                "            ?fund <https://linkedopendata.eu/prop/direct/P1583> ?fundLabel } \n " +
                "} order by DESC(?euBudget) limit " + pageSize + "OFFSET " + pageSize * page;

        JSONObject result = new JSONObject();
        result.put("item", id.replace("https://linkedopendata.eu/entity/", ""));

        JSONArray projects = new JSONArray();
        TupleQueryResult resultSet3 = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query3, 30, "beneficiaryProject");
        if (resultSet3 != null) {
            while (resultSet3.hasNext()) {
                JSONObject project = new JSONObject();
                BindingSet querySolution = resultSet3.next();
                if (querySolution.getBinding("project") != null) {
                    project.put("project", querySolution.getBinding("project").getValue().stringValue());
                }
                if (querySolution.getBinding("label") != null) {
                    project.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
                }
                if (querySolution.getBinding("euBudget") != null) {
                    project.put("euBudget", df2.format(((Literal) querySolution.getBinding("euBudget").getValue()).doubleValue()));
                }
                if (querySolution.getBinding("budget") != null) {
                    project.put("budget", df2.format(((Literal) querySolution.getBinding("budget").getValue()).doubleValue()));
                }
                if (querySolution.getBinding("fundLabel") != null) {
                    project.put("fundLabel", ((Literal) querySolution.getBinding("fundLabel").getValue()).getLabel());
                }
                if (querySolution.getBinding("startTime") != null) {
                    project.put("startTime", querySolution.getBinding("startTime").getValue().stringValue().split("T")[0]);
                }
                if (querySolution.getBinding("endTime") != null) {
                    project.put("endTime", querySolution.getBinding("endTime").getValue().stringValue().split("T")[0]);
                }
                projects.add(project);
            }
        }
        result.put("projects", projects);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    private String getBeneficiaryLabelsFilter() {
        String labelsFilter = "FILTER(";
        HashMap<String, List<String>> countriesCodeMapping = filtersGenerator.getCountriesCodeMapping();
        int count = 0;
        for (Map.Entry<String, List<String>> entry : countriesCodeMapping.entrySet()) {
            String countryQID = entry.getKey();
            List<String> languageCode = entry.getValue();
            labelsFilter += "( ";
            for (int i = 0; i < languageCode.size() - 1; i++) {
                labelsFilter += " LANG(?beneficiaryLabel) = \"" + languageCode.get(i) + "\" && ?country = " + countryQID + " ||  ";
            }
            labelsFilter += " LANG(?beneficiaryLabel) = \"" + languageCode.get(languageCode.size() - 1) + "\" && ?country = " + countryQID + " ) ";
            if (count < countriesCodeMapping.size() - 1)
                labelsFilter += "|| ";
            count++;
        }
        labelsFilter += ")";
        return labelsFilter;
    }
}
