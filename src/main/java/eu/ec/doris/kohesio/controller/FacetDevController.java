package eu.ec.doris.kohesio.controller;

import com.maxmind.geoip2.exception.GeoIp2Exception;

import eu.ec.doris.kohesio.controller.geoIp.GeoIp;
import eu.ec.doris.kohesio.controller.geoIp.HttpReqRespUtils;
import eu.ec.doris.kohesio.controller.payload.Beneficiary;
import eu.ec.doris.kohesio.controller.payload.NutsRegion;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.algebra.Str;
import org.eclipse.rdf4j.query.impl.TupleQueryResultBuilder;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mapstruct.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.DecimalFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/dev")
public class FacetDevController {
  private static final Logger logger = LoggerFactory.getLogger(FacetDevController.class);

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

  HashMap<String, Nut> nutsRegion = null;

  // Set this to allow browser requests from other websites
  @ModelAttribute
  public void setVaryResponseHeader(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
  }

  void initialize(String language) throws Exception {
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
          filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576630> .";
        }
        if (g.equals("nuts2")) {
          filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576674> .";
        }
        if (g.equals("nuts3")) {
          filter = " ?region <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576750> .";
        }

        String query =
                "SELECT ?region ?regionLabel where {" +
                        filter +
                        " ?region <http://www.w3.org/2000/01/rdf-schema#label> ?regionLabel . " +
                        "             FILTER((LANG(?regionLabel)) = \"" + language + "\") . " +
                        "}";
        logger.info(query);
        TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
        while (resultSet.hasNext()) {
          BindingSet querySolution = resultSet.next();
          String key = querySolution.getBinding("region").getValue().stringValue();
          Nut nut = new Nut();
          nut.uri = key;
          nut.type = g;
          if (nutsRegion.get(key) != null) {
            nut = nutsRegion.get(key);
          }
          if (querySolution.getBinding("regionLabel") != null) {
            nut.name = querySolution.getBinding("regionLabel").getValue().stringValue();
          }
          nutsRegion.put(key, nut);
        }
      }
      //retriving the narrower concept
      for (String key : nutsRegion.keySet()) {
        String query = "";
        if (nutsRegion.get(key).type.equals("continent")) {
          query =
                  "SELECT ?region2 where {" +
                          " <https://linkedopendata.eu/entity/Q1> <https://linkedopendata.eu/prop/direct/P104> ?region2 . }";
        }
        if (nutsRegion.get(key).type.equals("country")) {
          query =
                  "SELECT ?region2 where {" +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576630> . }";
        }
        if (nutsRegion.get(key).type.equals("nuts1")) {
          query =
                  "SELECT ?region2 where {" +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576674> . }";
        }
        if (nutsRegion.get(key).type.equals("nuts2")) {
          query =
                  "SELECT ?region2 where {" +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P1845> <" + nutsRegion.get(key).uri + "> . " +
                          " ?region2 <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2576750> . }";
        }
        if (query.equals("") == false) {
          TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
          while (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            if (querySolution.getBinding("region2") != null) {
              if (nutsRegion.get(key).narrower.contains(querySolution.getBinding("region2").getValue().stringValue()) == false) {
                nutsRegion.get(key).narrower.add(querySolution.getBinding("region2").getValue().stringValue());
              }
            }
          }
        }
      }
      //retriving the geoJson geometries
      for (String key : nutsRegion.keySet()) {
        String geometry = " ?nut <http://nuts.de/geoJson> ?regionGeo . ";
        if (nutsRegion.get(key).type.equals("continent")) {
          geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
        }
        if (nutsRegion.get(key).type.equals("country")) {
          geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
        }
//        if (nutsRegion.get(key).type.equals("nuts1")){
//          geometry = " ?nut <http://nuts.de/geoJson20M> ?regionGeo . ";
//        }

        //this is a hack and should be removed
        String search = nutsRegion.get(key).uri;
        if (nutsRegion.get(key).uri.equals("https://linkedopendata.eu/entity/Q2616107")){
          search = "https://linkedopendata.eu/entity/Q3532";
        }

        String query =
                "SELECT ?regionGeo where {" +
                        "?nut <http://nuts.de/linkedopendata> <" + search+ "> . " +
                        geometry +
                        " }";
        logger.info(query);
        TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
        while (resultSet.hasNext()) {
          BindingSet querySolution = resultSet.next();
          nutsRegion.get(key).geoJson = querySolution.getBinding("regionGeo").getValue().stringValue();
        }
      }
    }
  }

  @GetMapping(value = "facet/eu/statistics", produces = "application/json")
  public JSONObject facetEuStatistics() throws Exception {
    JSONObject statistics = new JSONObject();
    String query = "SELECT (count(?s0) as ?c) where { "
            + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . "
            + "} ";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      statistics.put("numberProjects", ((Literal) querySolution.getBinding("c").getValue()).intValue());
    }
    query = "SELECT (count(?s0) as ?c) where { "
            + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q196899> . "
            + "} ";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      statistics.put("numberBeneficiaries", ((Literal) querySolution.getBinding("c").getValue()).intValue());
    }
    query = "SELECT (sum(?o) as ?sum) where { "
            + "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . "
            + "    ?s0  <https://linkedopendata.eu/prop/direct/P835>  ?o . "
            + "} ";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      DecimalFormat df2 = new DecimalFormat("#.##");
      statistics.put("totalEuBudget", df2.format(((Literal) querySolution.getBinding("sum").getValue()).doubleValue()));
    }

    JSONObject themes = new JSONObject();
    query = "SELECT (COUNT(?s0) as ?c ) WHERE {" +
            "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
            "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236692> .   " +
            " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
            "}";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      themes.put("lowCarbonEconomy", ((Literal) querySolution.getBinding("c").getValue()).doubleValue());
    }

    query = "SELECT (COUNT(?s0) as ?c ) WHERE {" +
            "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
            "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236693> .   " +
            " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
            "}";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      themes.put("climateChangeAdaptation", ((Literal) querySolution.getBinding("c").getValue()).intValue());
    }

    query = "SELECT (COUNT(?s0) as ?c ) WHERE {" +
            "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. " +
            "?category <https://linkedopendata.eu/prop/direct/P1848> <https://linkedopendata.eu/entity/Q236694> .   " +
            " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
            "}";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      themes.put("enviromentProtection", ((Literal) querySolution.getBinding("c").getValue()).intValue());
    }

    query = "SELECT (COUNT(?s0) as ?c ) WHERE {" +
            "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category." +
            " ?category <https://linkedopendata.eu/prop/direct/P1849> <https://linkedopendata.eu/entity/Q2547987> . " +
            "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
            "}";
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      themes.put("greenerAndCarbonFreeEurope", ((Literal) querySolution.getBinding("c").getValue()).intValue());
    }

    statistics.put("themes", themes);

    return statistics;
  }

  @GetMapping(value = "/facet/eu/regions", produces = "application/json")
  public JSONArray facetEuRegions( //
                                   @RequestParam(value = "country", required = false) String country,
                                   @RequestParam(value = "language", defaultValue = "en") String language)
          throws Exception {
    String row;
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    InputStream input = loader.getResourceAsStream("regions2.csv");
    BufferedReader csvReader = new BufferedReader(new BufferedReader(new InputStreamReader(input, "UTF-8")));

    List<JSONObject> jsonValues = new ArrayList<JSONObject>();


    while ((row = csvReader.readLine()) != null) {
      String[] data = row.split(";");
      System.out.println();
      if (country.equals("https://linkedopendata.eu/entity/Q2") && data[0].equals("IE")
              || country.equals("https://linkedopendata.eu/entity/Q15") && data[0].equals("IT")
              || country.equals("https://linkedopendata.eu/entity/Q13") && data[0].equals("PL")
              || country.equals("https://linkedopendata.eu/entity/Q25") && data[0].equals("CZ")
              || country.equals("https://linkedopendata.eu/entity/Q20") && data[0].equals("FR")
              || country.equals("https://linkedopendata.eu/entity/Q12") && data[0].equals("DK")) {
        JSONObject element = new JSONObject();
        element.put("region", data[6]);
        element.put("name", data[3]);
        jsonValues.add(element);
      }
    }
    csvReader.close();

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


  @GetMapping(value = "/facet/eu/countries", produces = "application/json")
  public JSONArray facetEuCountries(
                                   @RequestParam(value = "language", defaultValue = "en") String language)
          throws Exception {
    List<JSONObject> jsonValues = new ArrayList<JSONObject>();
    JSONObject element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q2");
    jsonValues.add(element);
    element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q15");
    jsonValues.add(element);
    element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q13");
    jsonValues.add(element);
    element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q25");
    jsonValues.add(element);
    element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q20");
    jsonValues.add(element);
    element = new JSONObject();
    element.put("instance", "https://linkedopendata.eu/entity/Q12");
    jsonValues.add(element);

    for (int i = 0; i < jsonValues.size(); i++) {
      String query = "select ?instanceLabel where { "
                      + " <"+jsonValues.get(i).get("instance")+ "> rdfs:label ?instanceLabel . "
              + " FILTER (lang(?instanceLabel)=\""
              + language
              + "\")"
              + "}";
      TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
      while (resultSet.hasNext()) {
        BindingSet querySolution = resultSet.next();
        jsonValues.get(i).put("instanceLabel", querySolution.getBinding("instanceLabel").getValue().stringValue());
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

  @GetMapping(value = "/facet/eu/funds", produces = "application/json")
  public JSONArray facetEuFunds( //
                                 @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {
    String query =
            ""
                    + "select ?fund ?fundLabel ?id where { "
                    + " ?fund <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2504365> . "
                    + " ?fund <https://linkedopendata.eu/prop/direct/P1583> ?id ."

                    + " ?fund rdfs:label ?fundLabel . "
                    + " FILTER (lang(?fundLabel)=\""
                    + language
                    + "\")"
                    + "} order by ?id ";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
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

  @GetMapping(value = "/facet/eu/policy_objective", produces = "application/json")
  public JSONArray facetPolicyObjective( //
                                         @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {
    String query =
            ""
                    + "select ?fund ?fundLabel where { "
                    + " ?fund <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2547986> . "
                    + " ?fund rdfs:label ?fundLabel . "
                    + " FILTER (lang(?fundLabel)=\""
                    + language
                    + "\")"
                    + "}";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("fund").toString());
      element.put("instanceLabel", querySolution.getBinding("fundLabel").getValue().stringValue());
      result.add(element);
    }
    return result;
  }

  @GetMapping(value = "/facet/eu/categoriesOfIntervention", produces = "application/json")
  public JSONArray facetEuCategoryOfIntervention( //
                                                  @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {

    String query =
            ""
                    + "select ?instance ?instanceLabel ?id where { "
                    + " ?instance <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q200769> . "
                    + " ?instance <https://linkedopendata.eu/prop/direct/P869>  ?id . "
                    + " ?instance rdfs:label ?instanceLabel . "
                    + " FILTER (lang(?instanceLabel)=\""
                    + language
                    + "\")"
                    + "} order by ?id";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("instance").toString());
      element.put(
              "instanceLabel", querySolution.getBinding("id").getValue().stringValue() + " - " + querySolution.getBinding("instanceLabel").getValue().stringValue());
      result.add(element);
    }
    return result;
  }

  @GetMapping(value = "/facet/eu/programs", produces = "application/json")
  public JSONArray facetEuPrograms( //
                                    @RequestParam(value = "language", defaultValue = "en") String language,
                                    @RequestParam(value = "country", required = false) String country)
          throws Exception {
    String query =
            ""
                    + "select ?program ?programLabel ?cci where { "
                    + " ?program <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2463047> . "
                    + " ?program <https://linkedopendata.eu/prop/direct/P1367>  ?cci . ";

    if (country != null) {
      query += " ?program <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
    }

    query +=
            " ?program rdfs:label ?programLabel . "
                    + " FILTER (lang(?programLabel)=\""
                    + language
                    + "\")"
                    + "} order by ?cci ";
    System.out.println(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("program").getValue().toString());
      element.put(
              "instanceLabel", querySolution.getBinding("cci").getValue().stringValue() + " - " + querySolution.getBinding("programLabel").getValue().stringValue());
      result.add(element);
    }
    return result;
  }

  @GetMapping(value = "/facet/eu/thematic_objectives", produces = "application/json")
  public JSONArray facetEuThematicObjective( //
                                             @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {
    String kb = "eu";
    String user = "Max";
    String query =
            ""
                    + "select ?to ?toLabel ?id where { "
                    + " ?to <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q236700> . "
                    + " ?to <https://linkedopendata.eu/prop/direct/P1105>  ?id . "
                    + " ?to rdfs:label ?toLabel . "
                    + " FILTER (lang(?toLabel)=\""
                    + language
                    + "\")"
                    + "} order by ?id ";

    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("to").toString());
      element.put("instanceLabel", querySolution.getBinding("toLabel").getValue().stringValue());
      result.add(element);
    }
    return result;
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

    String search = filterProject(keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset);


    String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + search + "} ";
    System.out.println(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 25);
    int numResults = 0;
    if (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
    }

    String orderQuery = "";

    String orderBy = "";
    if(orderStartDate != null){
      orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P20> ?startTime .";
      if(orderStartDate){
        orderBy = "order by asc(?startTime)";
      }else{
        orderBy = "order by desc(?startTime)";
      }
    }
    if(orderEndDate != null){
      orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P33> ?endTime .";
      if(orderEndDate){
        orderBy = "order by asc(?endTime)";
      }else{
        orderBy = "order by desc(?endTime)";
      }
    }
    if(orderEuBudget != null){
      orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P835> ?euBudget. ";
      if(orderEuBudget){
        orderBy = "order by asc(?euBudget)";
      }else{
        orderBy = "order by desc(?euBudget)";
      }
    }
    if(orderTotalBudget != null){
      orderQuery += "?s0 <https://linkedopendata.eu/prop/direct/P474> ?totalBudget. ";
      if(orderTotalBudget){
        orderBy = "order by asc(?totalBudget)";
      }else{
        orderBy = "order by desc(?totalBudget)";
      }
    }
    if (search.equals(
            "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ")) {
      search += " { SELECT ?s0 ?snippet where { " +
              "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . " +
              "      ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . "+
              "    } " +
              "  } UNION { SELECT ?s0 ?snippet where { " +
              "      ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> ." +
              "    } " +
              "    }";
    }
    search += " "+orderQuery;
    query =
            "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?totalBudget ?euBudget ?image ?coordinates ?objectiveId ?countrycode where { "
                    + " { SELECT ?s0 ?snippet where { "
                    + search
                    + " } "+orderBy+" limit "
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
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 30);

    JSONArray resultList = new JSONArray();
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
          resultList.add(
                  toJson(
                          previewsKey,
                          snippet,
                          label,
                          description,
                          startTime,
                          endTime,
                          euBudget,
                          totalBudget,
                          image,
                          coordinates,
                          objectiveId,
                          countrycode));
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
      if (querySolution.getBinding("snippet") != null){
        String s = ((Literal) querySolution.getBinding("snippet").getValue()).getLabel();
        if (!s.endsWith(".")){
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
      if (querySolution.getBinding("euBudget") != null)
        euBudget.add(((Literal) querySolution.getBinding("euBudget").getValue()).getLabel());
      if (querySolution.getBinding("totalBudget") != null)
        totalBudget.add(((Literal) querySolution.getBinding("totalBudget").getValue()).getLabel());

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
      resultList.add(
              toJson(
                      previewsKey,
                      snippet,
                      label,
                      description,
                      startTime,
                      endTime,
                      euBudget,
                      totalBudget,
                      image,
                      coordinates,
                      objectiveId,
                      countrycode));
    }
    JSONObject result = new JSONObject();
    result.put("list", resultList);
    result.put("numberResults", numResults);
    return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
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
          @RequestParam(value = "granularityRegion", required = false) String granularityRegion,
          @RequestParam(value = "limit", required = false) Integer limit,
          @RequestParam(value = "offset", defaultValue = "0") Integer offset,
          Integer timeout,
          Principal principal)
          throws Exception {
    logger.info("language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, granularityRegion);
    initialize(language);
    if (timeout==null){
      timeout = 50;
    }
    System.out.println("filterProject ");

    //simplify the query
    String c = country;
    if (granularityRegion!=null){
      c = null;
    }
    String search = filterProject(keywords, c, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset);

    //computing the number of results
    String searchCount = search;
    if (granularityRegion != null) {
      searchCount += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + granularityRegion + ">";
    }
    String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + searchCount + "} ";
    System.out.println(query);
    int numResults = 0;
    System.out.println("Limit "+limit);
    if (limit == null || limit > 2000) {
      TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, timeout);

      if (resultSet.hasNext()) {
        BindingSet querySolution = resultSet.next();
        numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
      }
    }
    logger.info("Number of results {}", numResults);
    if (numResults <= 2000 || (granularityRegion != null && nutsRegion.get(granularityRegion).narrower.size()==0)) {
      if (granularityRegion != null) {
        search += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + granularityRegion + "> .";
      }
      String optional = " ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. ";

      // not performing
      if (granularityRegion != null) {
        optional += " ?nut <http://nuts.de/linkedopendata> <" + granularityRegion + ">  . ?nut  <http://nuts.de/geometry> ?o . ";
        //check if granularity region is a country, if yes the filter is not needed
        boolean isCountry = false;
        for (Object jsonObject : facetEuCountries("en")){
          JSONObject o = (JSONObject) jsonObject;
          if (granularityRegion.equals(o.get("instance"))){
            isCountry = true;
          }
        }
        if (isCountry == false) {
          optional+= "FILTER (<http://www.opengis.net/def/function/geosparql/sfWithin>(?coordinates, ?o)) . ";
        }
      }

      if (limit == null) {
        limit = 2000;
      }

      query =
              "SELECT DISTINCT ?coordinates WHERE { "
                      + " { SELECT ?s0 where { "
                      + search
                      + " } limit "
                      + limit
                      + " offset "
                      + offset
                      + " } "
                      + optional
                      + "} ";
      logger.info(query);
      TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, timeout);

      JSONArray resultList = new JSONArray();
      Set<String> coordinates = new HashSet<>();
      boolean hasEntry = resultSet.hasNext();
      while (resultSet.hasNext()) {
        BindingSet querySolution = resultSet.next();
        resultList.add(((Literal) querySolution.getBinding("coordinates").getValue())
                .getLabel()
                .replace("Point(", "")
                .replace(")", "")
                .replace(" ", ","));
      }
      JSONObject result = new JSONObject();
      result.put("list", resultList);
      if (granularityRegion != null) {
        result.put("geoJson", nutsRegion.get(granularityRegion).geoJson);
      } else if (country != null && region == null) {
        result.put("geoJson", nutsRegion.get(country).geoJson);
      } else if (country != null && region != null) {
        result.put("geoJson", nutsRegion.get(region).geoJson);
      } else {
        result.put("geoJson", "");
      }
      return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
    } else {
      if (granularityRegion == null) {
        granularityRegion = "https://linkedopendata.eu/entity/Q1";
      }

      query =
              "SELECT ?region (count(?s0) as ?c) where { "
                      + search
                      + " ?s0 <https://linkedopendata.eu/prop/direct/P1845> ?region . "
                      + " } group by ?region ";
      logger.info(query);
      TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 30);


      HashMap<String, JSONObject> subRegions = new HashMap();
      for (String r : nutsRegion.get(granularityRegion).narrower) {
        JSONObject element = new JSONObject();
        element.put("region", r);
        element.put("regionLabel", nutsRegion.get(r).name);
        element.put("geoJson", nutsRegion.get(r).geoJson);
        element.put("count", 0);
        subRegions.put(r, element);
      }

      while (resultSet.hasNext()) {
        BindingSet querySolution = resultSet.next();
        //System.out.println(querySolution.getBinding("region").getValue().stringValue()+"---"+((Literal) querySolution.getBinding("c").getValue()).intValue());
        if (subRegions.containsKey(querySolution.getBinding("region").getValue().stringValue())) {
          JSONObject element = subRegions.get(querySolution.getBinding("region").getValue().stringValue());
          element.put("count", ((Literal) querySolution.getBinding("c").getValue()).intValue());
          subRegions.put(querySolution.getBinding("region").getValue().stringValue(), element);
        }
      }

      JSONArray resultList = new JSONArray();
      for (String key : subRegions.keySet()) {
        resultList.add(subRegions.get(key));
      }

      JSONObject result = new JSONObject();
      result.put("region", granularityRegion);
      result.put("regionLabel", nutsRegion.get(granularityRegion).name);
      result.put("geoJson", nutsRegion.get(granularityRegion).geoJson);
      result.put("subregions", resultList);

      return new ResponseEntity<JSONObject>(result, HttpStatus.OK);
    }
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
          @RequestParam(value = "granularityRegion", required = false) String granularityRegion,
          @RequestParam(value = "limit", required = false) Integer limit,
          @RequestParam(value = "offset", defaultValue = "0") Integer offset,
          @RequestParam(value = "coordinate", required = false) String coordinate,
          Principal principal)
          throws Exception {
    logger.info("language {} keywords {} country {} theme {} fund {} program {} categoryOfIntervention {} policyObjective {} budgetBiggerThen {} budgetSmallerThen {} budgetEUBiggerThen {} budgetEUSmallerThen {} startDateBefore {} startDateAfter {} endDateBefore {} endDateAfter {} latitude {} longitude {} region {} limit {} offset {} granularityRegion {}", language, keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset, granularityRegion);
    initialize(language);
    System.out.println("filterProject ");
    String search = filterProject(keywords, country, theme, fund, program, categoryOfIntervention, policyObjective, budgetBiggerThen, budgetSmallerThen, budgetEUBiggerThen, budgetEUSmallerThen, startDateBefore, startDateAfter, endDateBefore, endDateAfter, latitude, longitude, region, limit, offset);

    search += " ?s0 <https://linkedopendata.eu/prop/direct/P127> \"Point(" + coordinate.replace(",", " ") + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral> . ";
    String query =
            "SELECT DISTINCT ?s0 ?label WHERE { "
                    + " { SELECT ?s0 where { "
                    + search
                    + " } "
                    + " } "
                    + " OPTIONAL {?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                    + "             FILTER((LANG(?label)) = \""
                    + language
                    + "\") } ."
                    + "} ";

    logger.info(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 30);

    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();

      JSONObject item = new JSONObject();
      item.put("item", querySolution.getBinding("s0").getValue().stringValue());
      if (querySolution.getBinding("label") != null) {
        item.put("label", ((Literal) querySolution.getBinding("label").getValue()).getLabel());
      }
      result.add(item);
    }
    return new ResponseEntity<JSONArray>((JSONArray) result, HttpStatus.OK);
  }


  class Nut{
    String uri;
    String type;
    String name="";
    String geoJson="";
    List<String> narrower = new ArrayList<String>();
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
    searchCount += " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image . ";
    String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + searchCount + "} ";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 25);
    int numResults = 0;
    if (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
    }
    logger.info("Number of results {}", numResults);

      query =
              "SELECT ?s0 ?image where { "
                      + search
                      + " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. "
                      + " } limit "
                      + limit
                      + " offset "
                      + offset ;
      logger.info(query);
      resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);

      JSONArray resultList = new JSONArray();
      Set<String> images = new HashSet<>();
      while (resultSet.hasNext()) {
        BindingSet querySolution = resultSet.next();
        JSONObject item = new JSONObject();
        item.put("item", querySolution.getBinding("s0").getValue().stringValue());
        item.put("image", querySolution.getBinding("image").getValue().stringValue());
        resultList.add(item);
      }
      JSONObject result = new JSONObject();
      result.put("list", resultList);
      result.put("numberResults", numResults);
      return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
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
      if(!keywords.contains("AND") && !keywords.contains("OR") && !keywords.contains("NOT") ) {
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

  @GetMapping(value = "/facet/eu/project", produces = "application/json")
  public JSONObject euId( //
                          @RequestParam(value = "id") String id,
                          @RequestParam(value = "language", defaultValue = "en") String language)
          throws Exception {
    String query =
            "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?budget ?euBudget ?cofinancingRate ?image ?imageCopyright ?video ?coordinates  ?countryLabel ?countryCode ?programLabel ?categoryLabel ?fundLabel ?objectiveId ?objectiveLabel ?managingAuthorityLabel ?beneficiaryLink ?beneficiary ?beneficiaryLabel ?beneficiaryWikidata ?beneficiaryWebsite ?source ?source2 ?regionId ?regionLabel ?regionUpper1Label ?regionUpper2Label ?regionUpper3Label where { "
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
                    + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . "
                    + "             FILTER(LANG(?beneficiaryLabel) = \"" + language + "\" || LANG(?regionLabel) = \"en\" || LANG(?regionLabel) = \"fr\" || LANG(?regionLabel) = \"it\" || LANG(?regionLabel) = \"pl\" || LANG(?regionLabel) = \"cs\" || LANG(?regionLabel) = \"da\" )}"
                    + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P1> ?beneficiaryID .  "
                    + "          BIND(CONCAT(\"http://wikidata.org/entity/\",STR( ?beneficiaryID )) AS ?beneficiaryWikidata ) . }"
                    + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P67> ?beneficiaryWebsite . } } "
                    + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P1845> ?region .  "
                    + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P192> ?regionId .} "
                    + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2576750> . "
                    + "             ?region <http://www.w3.org/2000/01/rdf-schema#label> ?regionLabel . "
                    + "             FILTER((LANG(?regionLabel)) = \"" + language + "\") }"
                    + "           OPTIONAL {?region <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper1 .  "
                    + "             ?regionUpper1 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2576674> . "
                    + "             ?regionUpper1 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper1Label . "
                    + "             FILTER((LANG(?regionUpper1Label)) = \"" + language + "\") } "
                    + "           OPTIONAL {?regionUpper1 <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper2 ."
                    + "             ?regionUpper2 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2576630> . "
                    + "             ?regionUpper2 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper2Label . "
                    + "             FILTER((LANG(?regionUpper2Label)) = \"" + language + "\") }  "
                    + "           OPTIONAL { ?regionUpper2 <https://linkedopendata.eu/prop/direct/P1845> ?regionUpper3 . "
                    + "           ?regionUpper3 <http://www.w3.org/2000/01/rdf-schema#label> ?regionUpper3Label . "
                    + "           ?regionUpper3 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q510> ."
                    + "           FILTER((LANG(?regionUpper3Label)) = \"" + language + "\") }} "
                    + "} ";
    logger.info("Retrieving results");
    TupleQueryResult resultSet = executeAndCacheQuery("https://query.linkedopendata.eu/bigdata/namespace/wdq/sparql", query, 2, false);
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
        for (Object i : images){
          if (((JSONObject)i).get("image").toString().equals(im) && found == false){
            found = true;
          }
        }
        if (found==false) {
          image.put("image",im);
          if (querySolution.getBinding("imageCopyright") != null) {
            image.put("imageCopyright",querySolution.getBinding("imageCopyright").getValue().stringValue());
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
          if (querySolution.getBinding("beneficiaryLabel") != null) {
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
      if (result.get("region")!=null){
        String regionText = (String)result.get("region");
        if (!((String)result.get("region")).equals(((String)result.get("regionUpper1")))){
          regionText += ", "+(String)result.get("regionUpper1");
        }
        if (!((String)result.get("regionUpper1")).equals(((String)result.get("regionUpper2")))){
          regionText += ", "+(String)result.get("regionUpper2");
        }
        if (!((String)result.get("regionUpper2")).equals(((String)result.get("regionUpper3")))){
          regionText += ", "+(String)result.get("regionUpper3");
        }
        result.put("regionText",regionText);
      } else {
        result.put("regionText",(String)result.get("countryLabel"));
      }

      if (querySolution.getBinding("regionId") != null && result.get("geoJson").equals("")) {
        query =
                "PREFIX geof: <http://www.opengis.net/def/function/geosparql/> "
                        + "PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                        + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                        + "SELECT ?id ?geoJson  WHERE { "
                        + "?s <http://nuts.de/id> \'"+((Literal) querySolution.getBinding("regionId").getValue()).stringValue()+ "\' . "
                        + "?s <http://nuts.de/geoJson> ?geoJson . "

                + "}";
        logger.info(query);
        logger.info("Retrieving nuts geometry");
        TupleQueryResult resultSet2 = executeAndCacheQuery(getSparqlEndpointNuts, query, 5);
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
    return result;
  }

  @GetMapping(value = "/facet/eu/project/region", produces = "application/json")
  public NutsRegion euIdCoordinates( //
                                     @RequestParam(value = "id") String id,
                                     @RequestParam(value = "language", defaultValue = "en") String language)
          throws Exception {
    String query =
            "select ?s0 ?coordinates where { "
                    + " VALUES ?s0 { <"
                    + id
                    + "> } "

                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } }";
    System.out.println(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);

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
    logger.info(query);
    resultSet = executeAndCacheQuery(getSparqlEndpointNuts, query, 5);

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

  @GetMapping(value = "/facet/eu/search/beneficiaries", produces = "application/json")
  public List euSearchBeneficiaries( //
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
    logger.info("Beneficiary search language {}, name {}, country {}, region {}, latitude {}, longitude {}, fund {}, program {}",language,keywords, country,region,latitude,longitude,fund,program);
    String search = "";
    if (keywords != null) {
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

    String query =
            "select ?beneficiary ?beneficiaryLabel ?country ?countryCode ?numberProjects ?totalEuBudget ?totalBudget ?link where { "
                    + " { SELECT ?beneficiary (count(?project) as ?numberProjects) (sum(?budget) as ?totalBudget) (sum(?euBudget) as ?totalEuBudget) where { "
                    + search
                    + "   ?project <https://linkedopendata.eu/prop/direct/P889> ?beneficiary . "
                    + "   ?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . "
                    + "   ?project <https://linkedopendata.eu/prop/direct/P474> ?budget . "
                    + " } group by ?beneficiary order by desc(?totalEuBudget) limit 500 } "
                    + " OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel . "
                    + "            ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country .   "
                    + "             FILTER((LANG(?beneficiaryLabel) = \"en\" && ?country = <https://linkedopendata.eu/entity/Q2> ) "
                    + "                 || (LANG(?beneficiaryLabel) = \"fr\" && ?country = <https://linkedopendata.eu/entity/Q20> )  "
                    + "                 || (LANG(?beneficiaryLabel) = \"it\" && ?country = <https://linkedopendata.eu/entity/Q15> ) "
                    + "                 || (LANG(?beneficiaryLabel) = \"pl\" && ?country = <https://linkedopendata.eu/entity/Q13> ) "
                    + "                 || (LANG(?beneficiaryLabel) = \"cs\" && ?country = <https://linkedopendata.eu/entity/Q25> ) "
                    + "                 || (LANG(?beneficiaryLabel) = \"da\" && ?country = <https://linkedopendata.eu/entity/Q12> ) )  "
                    + " }"
                    + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P1> ?link. } "
                    + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country. "
                    + "            ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . } "
                    + "} ";
    logger.info(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 30);

    List<Beneficiary> beneficiaries = new ArrayList<Beneficiary>();
    if (resultSet!=null) {
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
          beneficary.setEuBudget(
                  String.valueOf(
                          Precision.round(
                                  ((Literal) querySolution.getBinding("totalEuBudget").getValue()).doubleValue(),
                                  2)));
        }

        if (querySolution.getBinding("totalBudget") != null) {
          beneficary.setBudget(
                  String.valueOf(
                          Precision.round(
                                  ((Literal) querySolution.getBinding("totalBudget").getValue()).doubleValue(),
                                  2)));
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

    return beneficiaries;
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
    List<Beneficiary> beneficiaryList =
            euSearchBeneficiaries(language, keywords, country, region, latitude, longitude,fund,program,principal);
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
      for (Beneficiary beneficiary : beneficiaryList) {
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
    List<Beneficiary> beneficiaryList =
            euSearchBeneficiaries(language, keywords, country, region, latitude, longitude,fund,program,principal);
    String filename = "beneficiary_export.csv";
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
    for (Beneficiary beneficiary : beneficiaryList) {
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
    headers.set("Content-Disposition", "attachment; filename=\"beneficiary_export.xls\"");
    return new ResponseEntity<byte[]>(fileOut.toByteArray(), headers, HttpStatus.OK);
  }

  @PostMapping(value = "/facet/eu/cache/generate", produces = "application/json")
  public void generateCache() throws Exception {
    System.out.println("Start recoursive");
    recursiveMap(null);
    System.out.println("end recoursive");
    String[] countries = {
            null,
            "https://linkedopendata.eu/entity/Q15",
            "https://linkedopendata.eu/entity/Q2",
            "https://linkedopendata.eu/entity/Q25",
            "https://linkedopendata.eu/entity/Q20",
            "https://linkedopendata.eu/entity/Q13",
            "https://linkedopendata.eu/entity/Q12",
    };

    for (String country : countries) {
      int[] offset = {0,15,30,45,60,75,90,105,120,135,150};
      for (int o : offset) {
        Boolean[] orderStartDate = {null, true, false};
        for (Boolean b : orderStartDate){
          euSearchProject("en", null, country, null, null, null, null, null, null, null, null, null, null, null, null, null, b, null, null, null, null, null, null, 15, o, null);
        }
        Boolean[] orderEndDate = {null, true, false};
        for (Boolean b : orderEndDate){
          euSearchProject("en", null, country, null, null, null, null, null, null, null, null, null, null, null, null, null, null, b, null, null, null, null, null, 15, o, null);
        }
        Boolean[] orderEuBudget = {null, true, false};
        for (Boolean b : orderEuBudget){
          euSearchProject("en", null, country, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, b, null, null, null, null, 15, o, null);
        }
        Boolean[] orderTotalBudget = {null, true, false};
        for (Boolean b : orderTotalBudget) {
          euSearchProject("en", null, country, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, b, null, null, null, 15, o, null);
        }
      }
    }

    for (String country : countries) {
      euSearchBeneficiaries("en", null, country, null, null, null, null,null,null);
    }
    for (String country : countries) {
      if (country!=null) {
        JSONArray regions = facetEuRegions(country, "en");
        regions.add(null);
        for (Object region : regions) {
          JSONArray funds = facetEuFunds("en");
          funds.add(null);
          for (Object fund : funds) {
            JSONArray programs = facetEuPrograms("en", country);
            programs.add(null);
            for (Object program : programs) {
              String r = null;
              if (region != null) {
                r = ((JSONObject) region).get("region").toString();
              }
              String f = null;
              if (fund != null) {
                f = ((JSONObject) fund).get("instance").toString();
              }
              String p = null;
              if (program != null) {
                p = ((JSONObject) program).get("instance").toString();
              }
              System.out.println("euSearchBeneficiaries");
              euSearchBeneficiaries(
                      "en", null, country, r, null, null, f, p, null);
              euSearchProjectMap("en", null, country, null, f, p, null,null,null,null,null,null,null,null,null,null,null,null,r,r,null,0,400,null);
              System.out.println("Done");
            }
          }
        }
      }
    }
  }

  void recursiveMap(String granularityRegion) throws Exception {
    System.out.println("Resolving for "+granularityRegion);
    ResponseEntity responseEntity = euSearchProjectMap("en", null, null, null, null, null, null,null,null,null,null,null,null,null,null,null,null,null,null,granularityRegion,null,0,400,null);
    System.out.println("Hello world "+responseEntity.getBody());
    if (responseEntity.getBody() instanceof JSONArray){
      for (Object element : (JSONArray)responseEntity.getBody()){
        System.out.println("Hello world "+((JSONObject)element).get("region").toString());
        if (!((JSONObject)element).get("region").toString().equals(granularityRegion)) {
          recursiveMap(((JSONObject) element).get("region").toString());
        }
      }
    }

  }

  @PostMapping(value = "/facet/eu/cache/clean", produces = "application/json")
  public void cleanCache() throws Exception {
    File dir = new File(location + "/facet/cache/");
    if (dir.exists()) {
      FileUtils.cleanDirectory(dir);
    }
  }

  public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout) throws Exception {
    return this.executeAndCacheQuery(sparqlEndpoint, query, timeout, true);
  }

  public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout, boolean cache) throws Exception {
    logger.info(query);
    long start = System.nanoTime();
    File dir = new File(location + "/facet/cache/");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    // check if the query is cached
    if (dir.exists() && cache == true) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (Integer.parseInt(file.getName()) == query.hashCode()) {
            System.out.println(query.hashCode());
            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);
            try {
              sparqlResultsJSONParser.parseQueryResult(
                      new FileInputStream(location + "/facet/cache/" + query.hashCode()));
              long end = System.nanoTime();
              logger.info("Was cached "+(end - start)/100000);
              return tupleQueryResultHandler.getQueryResult();
            } catch(QueryResultParseException e){
              System.out.println("Wrong in cache timeout "+timeout);
            }
          }
        }
      }
    }
    // execute and cache the query if not found before
    Map<String, String> additionalHttpHeaders = new HashMap();
    additionalHttpHeaders.put("timeout",String.valueOf(timeout));
    SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
    repo.setAdditionalHttpHeaders(additionalHttpHeaders);

    try {
    TupleQueryResult resultSet =
            repo.getConnection().prepareTupleQuery(query).evaluate();
    FileOutputStream out = new FileOutputStream(location + "/facet/cache/" + query.hashCode());
    TupleQueryResultHandler writer = new SPARQLResultsJSONWriter(out);
    QueryResults.report(resultSet, writer);


    SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
    TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
    sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);

    sparqlResultsJSONParser.parseQueryResult(
            new FileInputStream(location + "/facet/cache/" + query.hashCode()));
      long end = System.nanoTime();
      logger.info("Was NOT cached "+(end - start)/1000000);
      return tupleQueryResultHandler.getQueryResult();
    } catch(QueryEvaluationException e){
      logger.error("Malformed query ["+query+"]");
    } catch (QueryResultParseException e){
      System.out.println("To heavy timeout "+query+" --- "+timeout);
    }
    return null;
  }

  @GetMapping(value = "/facet/eu/map/nearby", produces = "application/json")
  public ResponseEntity<JSONObject> geoIp(HttpServletRequest request) throws Exception {
    String ip = httpReqRespUtils.getClientIpAddressIfServletRequestExist(request);
    System.out.println(ip);
    GeoIp.Coordinates coordinates2 = geoIp.compute(ip);
    ResponseEntity<JSONObject> result = euSearchProjectMap("en", null, null, null, null, null, null,null,null,null,null,null,null,null,null,null,coordinates2.getLatitude(),coordinates2.getLongitude(),null,null,2000,0,400,null);
    JSONObject mod = result.getBody();
    mod.put("coordinates",coordinates2.getLatitude()+","+coordinates2.getLongitude());
    return new ResponseEntity<JSONObject>((JSONObject) mod, HttpStatus.OK);
  }

  JSONObject toJson(
          String key,
          Set<String> snippet,
          Set<String> label,
          Set<String> description,
          Set<String> startTime,
          Set<String> endTime,
          Set<String> euBudget,
          Set<String> totalBudget,
          Set<String> image,
          Set<String> coordinates,
          Set<String> objectiveId,
          Set<String> countrycode) {
    JSONObject element = new JSONObject();
    element.put("item", key.replace("https://linkedopendata.eu/entity/", ""));
    element.put("link", key);

    JSONArray snippets = new JSONArray();
    snippets.addAll(new ArrayList<String>(snippet));
    element.put("snippet", snippets);

    JSONArray labels = new JSONArray();
    labels.addAll(new ArrayList<String>(label));
    element.put("labels", labels);

    JSONArray descriptions = new JSONArray();
    descriptions.addAll(new ArrayList<String>(description));
    element.put("descriptions", descriptions);

    JSONArray startTimes = new JSONArray();
    startTimes.addAll(new ArrayList<String>(startTime));
    element.put("startTimes", startTimes);

    JSONArray endTimes = new JSONArray();
    endTimes.addAll(new ArrayList<String>(endTime));
    element.put("endTimes", endTimes);

    JSONArray euBudgets = new JSONArray();
    euBudgets.addAll(new ArrayList<String>(euBudget));
    element.put("euBudgets", euBudgets);

    JSONArray totalBudgets = new JSONArray();
    totalBudgets.addAll(new ArrayList<String>(totalBudget));
    element.put("totalBudgets",totalBudget);
    JSONArray images = new JSONArray();
    images.addAll(new ArrayList<String>(image));
    element.put("images", images);

    JSONArray coordiantess = new JSONArray();
    coordiantess.addAll(new ArrayList<String>(coordinates));
    element.put("coordinates", coordiantess);

    JSONArray objectiveIds = new JSONArray();
    objectiveIds.addAll(new ArrayList<String>(objectiveId));
    element.put("objectiveIds", objectiveIds);

    JSONArray countrycodes = new JSONArray();
    countrycodes.addAll(new ArrayList<String>(countrycode));
    element.put("countrycode", countrycodes);

    return element;
  }
}
