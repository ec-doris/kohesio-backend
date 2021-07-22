package eu.ec.doris.kohesio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ec.doris.kohesio.payload.Beneficiary;
import eu.ec.doris.kohesio.payload.BeneficiaryList;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")

public class BeneficiaryController {
    private static final Logger logger = LoggerFactory.getLogger(BeneficiaryController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    private static DecimalFormat df2 = new DecimalFormat("0.00");

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @GetMapping(value = "/facet/eu/beneficiary", produces = "application/json")
    public ResponseEntity euBenfeciaryId(@RequestParam(value = "id") String id,
                                         @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {

        String publicSparqlEndpoint = "https://query.linkedopendata.eu/bigdata/namespace/wdq/sparql";
        String queryCheck = "ask {\n" +
                " <" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q196899>\n" +
                "}";

        boolean resultAsk = sparqlQueryService.executeBooleanQuery(publicSparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
            String queryCheckRedirect = " select ?redirect where { "+
                    " <https://linkedopendata.eu/entity/Q257756> <http://www.w3.org/2002/07/owl#sameAs> ?redirect } ";
            TupleQueryResult resultSet1 = sparqlQueryService.executeAndCacheQuery(publicSparqlEndpoint, queryCheckRedirect, 3);
            if (resultSet1.hasNext()){
                return euBenfeciaryId(resultSet1.next().getBinding("redirect").getValue().stringValue(),language);
            } else {
                JSONObject result = new JSONObject();
                result.put("message", "Bad Request - beneficiary ID not found");
                return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
            }
        }
        String query1 = "select ?s0 ?country ?countryCode ?beneficiaryLabel_en ?beneficiaryLabel ?description ?website ?image ?logo ?coordinates ?wikipedia where {\n"
                + " VALUES ?s0 { <"
                + id
                + "> } " +
                "  ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country .  \n" +
                "  ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . \n "+
                "  OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel_en . \n" +
                "  FILTER(LANG(?beneficiaryLabel_en) = \""+language+"\" ) } \n" +
                "  OPTIONAL { ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . \n" +
                "  FILTER((LANG(?beneficiaryLabel) = \"en\" && ?country = <https://linkedopendata.eu/entity/Q2> )\n" +
                "          || (LANG(?beneficiaryLabel) = \"fr\" && ?country = <https://linkedopendata.eu/entity/Q20> )  \n" +
                "              || (LANG(?beneficiaryLabel) = \"it\" && ?country = <https://linkedopendata.eu/entity/Q15> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"pl\" && ?country = <https://linkedopendata.eu/entity/Q13> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"cs\" && ?country = <https://linkedopendata.eu/entity/Q25> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"el\" && ?country = <https://linkedopendata.eu/entity/Q17> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"el\" && ?country = <https://linkedopendata.eu/entity/Q31> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"sv\" && ?country = <https://linkedopendata.eu/entity/Q11> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"hr\" && ?country = <https://linkedopendata.eu/entity/Q30> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"ro\" && ?country = <https://linkedopendata.eu/entity/Q28> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"da\" && ?country = <https://linkedopendata.eu/entity/Q12> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"lv\" && ?country = <https://linkedopendata.eu/entity/Q24> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"de\" && ?country = <https://linkedopendata.eu/entity/Q16> ) \n" +
                "              || (LANG(?beneficiaryLabel) = \"pt\" && ?country = <https://linkedopendata.eu/entity/Q18> ) ) } \n" +
                "  OPTIONAL {  ?s0 <http://schema.org/description> ?description .  FILTER (lang(?description)=\""+language+"\") }\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P67> ?website .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P147> ?image .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P537> ?logo .}\n" +
                "  OPTIONAL {  ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates .}\n" +
                "  OPTIONAL{ " +
                "      ?wikipedia schema:about ?s0 ; " +
                "                 schema:inLanguage \"" + language + "\" ;" +
                "                 schema:isPartOf <https://" + language + ".wikipedia.org/> ." + "}\n " +
                "}";

        String query2 = "select ?s0 (sum(?euBudget) as ?totalEuBudget) (sum(?budget) as ?totalBudget) (count(?project) as ?numberProjects) (min(?startTime) as ?minStartTime) (max(?endTime) as ?maxEndTime) where {\n" +
                " VALUES ?s0 { <" +
                id +
                "> } " +
                "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 .  \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . }\n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P20> ?startTime . }\n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P33> ?endTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P838> ?endTime . } \n" +
                "  \n" +
                "} group by ?s0";

        String query3 = "select ?project ?label ?euBudget ?budget ?startTime ?endTime ?fundLabel where {\n" +
                " VALUES ?s0 { <" +
                id +
                "> } " +
                "  ?project <http://www.w3.org/2000/01/rdf-schema#label> ?label .\n" +
                "  FILTER (lang(?label)=\""+language+"\") .\n" +
                "  ?project <https://linkedopendata.eu/prop/direct/P889> ?s0 .  \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P20> ?startTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P33> ?endTime . } \n" +
                "  OPTIONAL {?project <https://linkedopendata.eu/prop/direct/P1584> ?fund . \n" +
                "            ?fund <https://linkedopendata.eu/prop/direct/P1583> ?fundLabel } \n " +
                "} order by DESC(?euBudget) limit 100 ";
        TupleQueryResult resultSet1 = sparqlQueryService.executeAndCacheQuery(publicSparqlEndpoint, query1, 30);
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
                String wikipedia =  querySolution.getBinding("wikipedia").getValue().stringValue();
                result.put("wikipedia", wikipedia);
                // if wikipedia link extract the description from wikipedia
                String url = "https://" + language + ".wikipedia.org/w/api.php?format=json&action=query&prop=extracts&exintro=&origin=*&explaintext=&titles=" + URLDecoder.decode(wikipedia.replace("https://" + language + ".wikipedia.org/wiki/", ""), StandardCharsets.UTF_8.toString());
                System.out.println(url);
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response.getStatusCode().equals(HttpStatus.OK)){
                    System.out.println(response.getBody());
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response.getBody());
                    if (root.findValue("extract")!=null){
                        String desc = root.findValue("extract").textValue();
                        result.put("description", desc+" (from Wikipedia)");
                    }
                }
            } else {
                result.put("wikipedia", "");
            }
            JSONArray images = new JSONArray();
            if (querySolution.getBinding("image") != null) {
                images.add(querySolution.getBinding("image").getValue().stringValue());
            }
            if (querySolution.getBinding("logo") != null) {
                images.add(querySolution.getBinding("logo").getValue().stringValue());
            }
            result.put("images", images);
        }
        TupleQueryResult resultSet2 = sparqlQueryService.executeAndCacheQuery(publicSparqlEndpoint, query2, 30);
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
        TupleQueryResult resultSet3 = sparqlQueryService.executeAndCacheQuery(publicSparqlEndpoint, query3, 30);
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
    @GetMapping(value = "/facet/eu/search/beneficiaries", produces = "application/json")
    public ResponseEntity euSearchBeneficiaries( //
                                                 @RequestParam(value = "language", defaultValue = "en") String language,
                                                 @RequestParam(value = "name", required = false) String keywords, //
                                                 @RequestParam(value = "country", required = false) String country, //
                                                 @RequestParam(value = "region", required = false) String region, //
                                                 @RequestParam(value = "latitude", required = false) String latitude, //
                                                 @RequestParam(value = "longitude", required = false) String longitude, //
                                                 @RequestParam(value = "fund", required = false) String fund, //
                                                 @RequestParam(value = "program", required = false) String program, //

                                                 @RequestParam(value = "orderEuBudget", defaultValue = "false") Boolean orderEuBudget,
                                                 @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,
                                                 @RequestParam(value = "orderNumProjects", required = false) Boolean orderNumProjects,
                                                 @RequestParam(value = "limit", defaultValue = "200") int limit,
                                                 @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                 Principal principal)
            throws Exception {
        logger.info("Beneficiary search language {}, name {}, country {}, region {}, latitude {}, longitude {}, fund {}, program {}", language, keywords, country, region, latitude, longitude, fund, program);

        int inputOffset = offset;
        int inputLimit = limit;
        if (offset <= 990) {
            offset = 0;
            limit = 1000;
        }
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
                    "?beneficiary <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                            + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                            + keywords.replace("\"", "\\\"")
                            + "\" ; "
                            + "<http://www.openrdf.org/contrib/lucenesail#snippet> ?snippet ] . ";
        }

        if (country != null) {
            search += "?beneficiary <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
        }

        if (region != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1845> <" + region + "> . ";
        }

        if (latitude != null && longitude != null) {
            search +=
                    "?project <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                            + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
                            + longitude
                            + " "
                            + latitude
                            + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)< 100000) . ";
        }

        if (fund != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1584> <" + fund + "> . ";
        }

        if (program != null) {
            search += "?project <https://linkedopendata.eu/prop/direct/P1368> <" + program + "> . ";
        }

        search = search+ "   ?project <https://linkedopendata.eu/prop/direct/P889> ?beneficiary . "
                + "   optional { ?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget .} "
                + "   optional { ?project <https://linkedopendata.eu/prop/direct/P474> ?budget . } ";

        String queryCount = "select (count(?beneficiary) as ?c) where { " +
                "{select ?beneficiary where {\n" +
                search
                + " } group by ?beneficiary }" +
                "}";
        System.out.println(queryCount);
        TupleQueryResult countResultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, queryCount, 25);
        int numResults = 0;
        if (countResultSet.hasNext()) {
            BindingSet querySolution = countResultSet.next();
            numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
            //System.out.println(querySolution.getBinding("beneficiary").getValue());
        }

        String orderBy = "";

        if (orderEuBudget != null) {
            if (orderEuBudget) {
                orderBy = "order by asc(?totalEuBudget)";
            } else {
                orderBy = "order by desc(?totalEuBudget)";
            }
        }
        if (orderTotalBudget != null) {
            if (orderTotalBudget) {
                orderBy = "order by asc(?totalBudget)";
            } else {
                orderBy = "order by desc(?totalBudget)";
            }
        }
        if (orderNumProjects != null) {
            if (orderNumProjects) {
                orderBy = "order by asc(?numberProjects)";
            } else {
                orderBy = "order by desc(?numberProjects)";
            }
        }
        String query =
                "select ?beneficiary ?beneficiaryLabel ?beneficiaryLabel_en ?country ?countryCode ?numberProjects ?totalEuBudget ?totalBudget ?link where { "
                        + " { SELECT ?beneficiary (count(?project) as ?numberProjects) (sum(?budget) as ?totalBudget) (sum(?euBudget) as ?totalEuBudget) where { "
                        + search
                        +"} group by ?beneficiary "+
                        orderBy
                        + " limit " + limit
                        + " offset " + offset
                        + "} "
                        + "  OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel_en . \n"
                        + "              FILTER(LANG(?beneficiaryLabel_en) = \""+language+"\" ) } \n"
                        + " OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . "
                        + "            ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country .   "
                        + "             FILTER((LANG(?beneficiaryLabel) = \"en\" && ?country = <https://linkedopendata.eu/entity/Q2> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"fr\" && ?country = <https://linkedopendata.eu/entity/Q20> )  "
                        + "                 || (LANG(?beneficiaryLabel) = \"it\" && ?country = <https://linkedopendata.eu/entity/Q15> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"pl\" && ?country = <https://linkedopendata.eu/entity/Q13> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"cs\" && ?country = <https://linkedopendata.eu/entity/Q25> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"el\" && ?country = <https://linkedopendata.eu/entity/Q17> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"el\" && ?country = <https://linkedopendata.eu/entity/Q31> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"sv\" && ?country = <https://linkedopendata.eu/entity/Q11> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"hr\" && ?country = <https://linkedopendata.eu/entity/Q30> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"ro\" && ?country = <https://linkedopendata.eu/entity/Q28> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"pt\" && ?country = <https://linkedopendata.eu/entity/Q18> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"lv\" && ?country = <https://linkedopendata.eu/entity/Q24> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"de\" && ?country = <https://linkedopendata.eu/entity/Q16> ) "
                        + "                 || (LANG(?beneficiaryLabel) = \"da\" && ?country = <https://linkedopendata.eu/entity/Q12> ) )  "
                        + " }"
                        + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P1> ?link. } "
                        + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country. "
                        + "            ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . } "
                        + "} ";
        logger.info(query);
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 30);

        ArrayList<Beneficiary> beneficiaries = new ArrayList<Beneficiary>();
        if (resultSet != null) {
            Beneficiary beneficary = new Beneficiary();
            String previewsKey = "";
            while (resultSet.hasNext()) {
                BindingSet querySolution = resultSet.next();
                String currentKey = querySolution.getBinding("beneficiary").getValue().stringValue();
                if (!previewsKey.equals(currentKey)) {
                    if (!previewsKey.equals("")) {
                        beneficary.computeCofinancingRate();
                        beneficiaries.add(beneficary);
                    }
                    beneficary = new Beneficiary();
                    beneficary.setId(currentKey);
                    previewsKey = currentKey;
                }

                if (querySolution.getBinding("beneficiaryLabel") != null) {
                    beneficary.setLabel(
                            ((Literal) querySolution.getBinding("beneficiaryLabel").getValue()).getLabel());
                }

                if (querySolution.getBinding("beneficiaryLabel_en") != null && !querySolution.getBinding("beneficiaryLabel_en").equals("")) {
                    beneficary.setLabel(
                            ((Literal) querySolution.getBinding("beneficiaryLabel_en").getValue()).getLabel());
                }

                if (querySolution.getBinding("country") != null) {
                    beneficary.setCountry(querySolution.getBinding("country").getValue().stringValue());
                }

                if (querySolution.getBinding("countryCode") != null) {
                    beneficary.setCountryCode(
                            ((Literal) querySolution.getBinding("countryCode").getValue()).stringValue());
                }

                if (querySolution.getBinding("numberProjects") != null) {
                    beneficary.setNumberProjects(
                            ((Literal) querySolution.getBinding("numberProjects").getValue()).intValue());
                }

                if (querySolution.getBinding("totalEuBudget") != null) {

                    double val = ((Literal) querySolution.getBinding("totalEuBudget").getValue()).doubleValue();
                    if(val != 0) {
                        beneficary.setEuBudget(
                                String.valueOf(
                                        Precision.round(
                                                val,
                                                2)));
                    }
                    else{
                        beneficary.setEuBudget("");
                    }
                }

                if (querySolution.getBinding("totalBudget") != null) {
                    double val = ((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue();
                    if(val != 0 ) {
                        beneficary.setBudget(
                                String.valueOf(
                                        Precision.round(
                                                val,
                                                2)));
                    }else{
                        // meaning that there is no budgets for associated projects  ( sum(budgets) = 0  and budgets= [] )
                        beneficary.setBudget("");
                    }
                }

                if (querySolution.getBinding("link") != null) {
                    beneficary.setLink(
                            "http://wikidata.org/entity/"
                                    + ((Literal) querySolution.getBinding("link").getValue()).getLabel());
                }
            }
            if (!previewsKey.equals("")) {
                beneficary.computeCofinancingRate();
                beneficiaries.add(beneficary);
            }
        }
        BeneficiaryList finalRes = new BeneficiaryList();
        finalRes.setNumberResults(numResults);
        if(offset <= 990) {
            for (int i = inputOffset; i < Math.min(beneficiaries.size(), inputOffset + inputLimit); i++) {
                finalRes.getList().add(beneficiaries.get(i));
            }
        }else{
            finalRes.setList(beneficiaries);
        }
        return new ResponseEntity<BeneficiaryList>(finalRes, HttpStatus.OK);
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
                                          Principal principal,
                                          @Context HttpServletResponse response)
            throws Exception {
        // if "limit" parameter passed to get a specific number of rows just pass it to euSearchBeneficiaries
        // by default it export 1000
        BeneficiaryList beneficiaryList = ((BeneficiaryList) euSearchBeneficiaries(language, keywords, country, region, latitude, longitude, fund, program, false, false, false, 1000, 0, principal).getBody());
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
                                                              Principal principal)
            throws Exception {
        // if "limit" parameter passed to get a specific number of rows just pass it to euSearchBeneficiaries
        // by default it export 1000
        BeneficiaryList beneficiaryList = ((BeneficiaryList) euSearchBeneficiaries(language, keywords, country, region, latitude, longitude, fund, program, false, false, false, 1000, 0, principal).getBody());
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
            cell.setCellValue(beneficiary.getLabel());
            cell = row.createCell(1);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
            cell.setCellValue(Double.parseDouble(beneficiary.getBudget()));
            cell = row.createCell(2);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
            cell.setCellValue(Double.parseDouble(beneficiary.getEuBudget()));
            cell = row.createCell(3);
            cell.setCellType(HSSFCell.CELL_TYPE_NUMERIC);
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

}
