package eu.ec.doris.kohesio.controller;

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
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.impl.TupleQueryResultBuilder;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mapstruct.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api")
public class FacetController {
  private static final Logger logger = LoggerFactory.getLogger(FacetController.class);

  @Value("${kohesio.directory}")
  String location;

  @Value("${kohesio.sparqlEndpoint}")
  String sparqlEndpoint;

  @Value("${kohesio.sparqlEndpointNuts}")
  String getSparqlEndpointNuts;

  // Set this to allow browser requests from other websites
  @ModelAttribute
  public void setVaryResponseHeader(HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
  }

  @GetMapping(value = "/facet/eu/regions", produces = "application/json")
  public JSONArray facetEuRegions( //
                                   @RequestParam(value = "country", required = false) String country,
                                   @RequestParam(value = "language", defaultValue = "en") String language)
          throws Exception {
    String row;
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    InputStream input = loader.getResourceAsStream("regions.csv");
    BufferedReader csvReader = new BufferedReader(new BufferedReader(new InputStreamReader(input, "UTF-8")));
    JSONArray result = new JSONArray();
    while ((row = csvReader.readLine()) != null) {
      String[] data = row.split(";");
      if (country.equals("https://linkedopendata.eu/entity/Q2") && data[0].equals("IE")
              || country.equals("https://linkedopendata.eu/entity/Q15") && data[0].equals("IT")
              || country.equals("https://linkedopendata.eu/entity/Q13") && data[0].equals("PL")
              || country.equals("https://linkedopendata.eu/entity/Q25") && data[0].equals("CZ")
              || country.equals("https://linkedopendata.eu/entity/Q20") && data[0].equals("FR")
              || country.equals("https://linkedopendata.eu/entity/Q12") && data[0].equals("DK")) {
        JSONObject element = new JSONObject();
        element.put("region", data[4]);
        element.put("name", data[3]);
        result.add(element);
      }
    }
    csvReader.close();
    return result;
  }

  @GetMapping(value = "/facet/eu/funds", produces = "application/json")
  public JSONArray facetEuFunds( //
                                 @RequestParam(value = "language", defaultValue = "en") String language) throws Exception {

    String kb = "eu";
    String user = "Max";

    String query =
            ""
                    + "select ?fund ?fundLabel where { "
                    + " ?fund <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2504365> . "
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

    String kb = "eu";
    String user = "Max";

    String query =
            ""
                    + "select ?instance ?instanceLabel where { "
                    + " ?instance <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q200769> . "
                    + " ?instance rdfs:label ?instanceLabel . "
                    + " FILTER (lang(?instanceLabel)=\""
                    + language
                    + "\")"
                    + "}";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint,query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("instance").toString());
      element.put(
              "instanceLabel", querySolution.getBinding("instanceLabel").getValue().stringValue());
      result.add(element);
    }
    return result;
  }

  @GetMapping(value = "/facet/eu/programs", produces = "application/json")
  public JSONArray facetEuPrograms( //
                                    @RequestParam(value = "language", defaultValue = "en") String language,
                                    @RequestParam(value = "country", required = false) String country)
          throws Exception {

    String kb = "eu";
    String user = "Max";

    String query =
            ""
                    + "select ?program ?programLabel where { "
                    + " ?program <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q2463047> . ";

    if (country != null) {
      query += " ?program <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
    }

    query +=
            " ?program rdfs:label ?programLabel . "
                    + " FILTER (lang(?programLabel)=\""
                    + language
                    + "\")"
                    + "}";

    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 2);
    JSONArray result = new JSONArray();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      JSONObject element = new JSONObject();
      element.put("instance", querySolution.getBinding("program").toString());
      element.put(
              "instanceLabel", querySolution.getBinding("programLabel").getValue().stringValue());
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
                    + "select ?to ?toLabel where { "
                    + " ?to <https://linkedopendata.eu/prop/direct/P35>  <https://linkedopendata.eu/entity/Q236700> . "
                    + " ?to rdfs:label ?toLabel . "
                    + " FILTER (lang(?toLabel)=\""
                    + language
                    + "\")"
                    + "}";

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
                                         @RequestParam(value = "latitude", required = false) String latitude,
                                         @RequestParam(value = "longitude", required = false) String longitude,
                                         @RequestParam(value = "region", required = false) String region,
                                         @RequestParam(value = "limit", defaultValue = "200") int limit,
                                         @RequestParam(value = "offset", defaultValue = "0") int offset,
                                         Principal principal)
          throws Exception {
    logger.info("language {} keywords {} country {} theme {} fund {} region {}",language,keywords,country,theme,fund,region);

    String search = filterProject(keywords,country, theme, fund,program, categoryOfIntervention,policyObjective,budgetBiggerThen,budgetSmallerThen,budgetEUBiggerThen,budgetEUSmallerThen,startDateBefore,startDateAfter,endDateBefore,endDateAfter,latitude,longitude,region,limit,offset);


    String query = "SELECT (COUNT(?s0) as ?c ) WHERE {" + search + "} ";
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 15);
    int numResults = 0;
    if (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      numResults = ((Literal) querySolution.getBinding("c").getValue()).intValue();
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

    query =
            "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?euBudget ?image ?coordinates ?objectiveId ?countrycode where { "
                    + " { SELECT ?s0 ?snippet where { "
                    + search
                    + " } limit "
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
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P147> ?image. } "
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. } "
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } "
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P32> ?country . ?country 	<https://linkedopendata.eu/prop/direct/P173> ?countrycode .} "
                    + " OPTIONAL {?s0 <https://linkedopendata.eu/prop/direct/P888> ?category .  ?category <https://linkedopendata.eu/prop/direct/P302> ?objective. ?objective <https://linkedopendata.eu/prop/direct/P1105> ?objectiveId. } "
                    + "} ";
    System.out.println(query);
    resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);

    JSONArray resultList = new JSONArray();
    String previewsKey = "";
    Set<String> snippet = new HashSet<>();
    Set<String> label = new HashSet<>();
    Set<String> description = new HashSet<>();
    Set<String> startTime = new HashSet<>();
    Set<String> endTime = new HashSet<>();
    Set<String> euBudget = new HashSet<>();
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
          image = new HashSet<>();
          coordinates = new HashSet<>();
          objectiveId = new HashSet<>();
          countrycode = new HashSet<>();
        }
        previewsKey = querySolution.getBinding("s0").getValue().stringValue();
      }
      if (querySolution.getBinding("snippet") != null)
        snippet.add(((Literal) querySolution.getBinding("snippet").getValue()).getLabel());
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
  public ResponseEntity euSearchProjectMap( //
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
                                            @RequestParam(value = "limit", defaultValue = "2000") int limit,
                                            @RequestParam(value = "offset", defaultValue = "0") int offset,
                                            Principal principal)
          throws Exception {
    logger.info("language {} keywords {} country {} theme {} fund {} region {}",language,keywords,country,theme,fund,region);

    String search = filterProject(keywords,country,theme, fund,program, categoryOfIntervention,policyObjective,budgetBiggerThen,budgetSmallerThen,budgetEUBiggerThen,budgetEUSmallerThen,startDateBefore,startDateAfter,endDateBefore,endDateAfter,latitude,longitude,region,limit,offset);

    if (search.equals(
            "   ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ")) {
      search += " ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. ";
    }

    String query =
            "select ?s0 ?label ?coordinates where { "
                    + " { SELECT ?s0 ?snippet where { "
                    + search
                    + " } limit "
                    + limit
                    + " offset "
                    + offset
                    + " } "
                    + " ?s0 <http://www.w3.org/2000/01/rdf-schema#label> ?label. "
                    + " FILTER((LANG(?label)) = \""
                    + language
                    + "\") "
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates. } "
                    + "} ";
    System.out.println(query);
    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 10);

    JSONArray resultList = new JSONArray();
    String previewsKey = "";
    Set<String> label = new HashSet<>();
    Set<String> coordinates = new HashSet<>();
    boolean hasEntry = resultSet.hasNext();
    while (resultSet.hasNext()) {
      BindingSet querySolution = resultSet.next();
      String currentKey = querySolution.getBinding("s0").getValue().stringValue();
      if (!previewsKey.equals(currentKey)) {
        if (!previewsKey.equals("")) {
          JSONObject element = new JSONObject();
          element.put("item", previewsKey.replace("https://linkedopendata.eu/entity/", ""));
          element.put("link", previewsKey);

          JSONArray labels = new JSONArray();
          labels.addAll(new ArrayList<String>(label));
          element.put("labels", labels);

          JSONArray coordiantes = new JSONArray();
          coordiantes.addAll(new ArrayList<String>(coordinates));
          element.put("coordinates", coordiantes);

          resultList.add(element);



          label = new HashSet<>();
          coordinates = new HashSet<>();
        }
        previewsKey = querySolution.getBinding("s0").getValue().stringValue();
      }
      if (querySolution.getBinding("label") != null)
        label.add(((Literal) querySolution.getBinding("label").getValue()).getLabel());
      if (querySolution.getBinding("coordinates") != null) {
        coordinates.add(
                ((Literal) querySolution.getBinding("coordinates").getValue())
                        .getLabel()
                        .replace("Point(", "")
                        .replace(")", "")
                        .replace(" ", ","));
      }
    }
    if (hasEntry) {
      JSONObject element = new JSONObject();
      element.put("item", previewsKey.replace("https://linkedopendata.eu/entity/", ""));
      element.put("link", previewsKey);

      JSONArray labels = new JSONArray();
      labels.addAll(new ArrayList<String>(label));
      element.put("labels", labels);

      JSONArray coordiantes = new JSONArray();
      coordiantes.addAll(new ArrayList<String>(coordinates));
      element.put("coordinates", coordiantes);
    }
    JSONObject result = new JSONObject();
    result.put("list", resultList);
    return new ResponseEntity<JSONObject>((JSONObject) result, HttpStatus.OK);
  }


  private String filterProject(String keywords,String country,String theme,String fund,String program, String categoryOfIntervention,
                               String policyObjective,Integer budgetBiggerThen,Integer budgetSmallerThen,Integer budgetEUBiggerThen,Integer budgetEUSmallerThen,String startDateBefore,String startDateAfter,
                               String endDateBefore,
                               String endDateAfter,
                               String latitude,
                               String longitude,
                               String region,
                               int limit,
                               int offset) throws IOException {
    String search = "";
    if (keywords != null) {
      search +=
              "?s0 <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                      + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                      + keywords.replace("\"", "\\\"")
                      + "\" ; "
                      + "<http://www.openrdf.org/contrib/lucenesail#snippet> ?snippet ] . ";
    }

    if (country != null) {
      search += "?s0 <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
    }

    if (theme != null) {
      search +=
              "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
                      + "?category <https://linkedopendata.eu/prop/direct/P302> <"
                      + theme
                      + "> . ";
    }

    if (policyObjective != null) {
      search +=
              "?s0 <https://linkedopendata.eu/prop/direct/P888> ?category. "
                      + "?category <https://linkedopendata.eu/prop/direct/P302> <"
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
        search += "FILTER( ?budget > " + budgetEUBiggerThen + ")";
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
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      InputStream input = loader.getResourceAsStream("regions.csv");
      BufferedReader csvReader = new BufferedReader(new BufferedReader(new InputStreamReader(input, "UTF-8")));
      String coordinates = "";
      String row;
      while ((row = csvReader.readLine()) != null) {
        String[] data = row.split(";");
        if (data.length>4){
          if (region.equals(data[4])){
            coordinates = data[5];
          }
        }
      }
      coordinates =
              coordinates
                      .replace("Point(", "")
                      //.replace("\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>", "")
                      .replace(")", "");

      latitude = coordinates.split(" ")[1];
      longitude = coordinates.split(" ")[0];
    }

    if (latitude != null && longitude != null) {
      search +=
              "?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                      + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
                      + longitude
                      + " "
                      + latitude
                      + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)/1000< 100) . ";
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
            "select ?s0 ?snippet ?label ?description ?startTime ?endTime ?budget ?euBudget ?cofinancingRate ?image ?video ?coordinates  ?countryLabel ?countryCode ?programLabel ?categoryLabel ?fundLabel ?objectiveId ?objectiveLabel ?managingAuthorityLabel ?beneficiaryLink ?beneficiary ?beneficiaryLabel ?beneficiaryWikidata ?source ?source2 where { "
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
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P147> ?image. } "
                    + " OPTIONAL { ?s0 <https://linkedopendata.eu/prop/direct/P851> ?image. } "
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
                    + "           ?category <https://linkedopendata.eu/prop/direct/P302> ?objective."
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
                    + "          OPTIONAL {?beneficiaryLink <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel .} "
                    + "          OPTIONAL {?beneficiaryLink <https://linkedopendata.eu/prop/direct/P1> ?beneficiaryID . "
                    + "          BIND(CONCAT(\"http://wikidata.org/entity/\",STR( ?beneficiaryID )) AS ?beneficiaryWikidata ) .}  }"
                    + "} ";
    TupleQueryResult resultSet = executeAndCacheQuery("https://query.linkedopendata.eu/bigdata/namespace/wdq/sparql", query, 2);

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
    result.put("region","");
    result.put("geoJson","");
    result.put("regionUpper1","");
    result.put("regionUpper2","");
    result.put("regionUpper3","");


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
        String im = querySolution.getBinding("image").getValue().stringValue();
        if (!images.contains(im)) {
          images.add(im);
          result.put("images", images);
        }
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
          beneficiaries.add(beneficary);
        }
      }

      if (querySolution.getBinding("managingAuthorityLabel") != null) {
        result.put(
                "managingAuthorityLabel",
                ((Literal) querySolution.getBinding("managingAuthorityLabel").getValue())
                        .stringValue());
      }
    }
    NutsRegion nutsRegion = euIdCoordinates(id,language);
    System.out.println("COMputing nuts");
    if (nutsRegion.getLabel()!=null){
      result.put("region",nutsRegion.getLabel());
    }
    if (nutsRegion.getGeoJson()!=null){
      result.put("geoJson",nutsRegion.getGeoJson());
    }
    if (nutsRegion.getLabelUpper1()!=null){
      result.put("regionUpper1",nutsRegion.getLabelUpper1());
    }
    if (nutsRegion.getLabelUpper2()!=null){
      result.put("regionUpper2",nutsRegion.getLabelUpper2());
    }
    if (nutsRegion.getLabelUpper3()!=null){
      result.put("regionUpper3",nutsRegion.getLabelUpper3());
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
                    +"PREFIX geo: <http://www.opengis.net/ont/geosparql#> "
                    +"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                    +"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                    +"SELECT ?id ?label ?geoJson ?label1 ?id1 ?label2 ?id2 ?label3 ?id3  WHERE { "
                    +"?s rdf:type <http://nuts.de/NUTS3> . "
                    +"?s <http://nuts.de/geometry> ?o . "
                    +" FILTER (geof:sfWithin(\""+coo+"\"^^geo:wktLiteral,?o)) "
                    +"?s <http://nuts.de/geoJson> ?geoJson . "
                    +"?s rdfs:label ?label . "
                    +"?s <http://nuts.de/id> ?id . "
                    +"OPTIONAL {?s <http://nuts.de/contained> ?contained1 . "
                    +"          ?contained1 rdfs:label ?label1 .  "
                    +"          ?contained1 <http://nuts.de/id> ?id1 . "
                    +"           OPTIONAL {?contained1 <http://nuts.de/contained> ?contained2 . "
                    +"          ?contained2 rdfs:label ?label2 .  "
                    +"          ?contained2 <http://nuts.de/id> ?id2 . "
                    +"           OPTIONAL {?contained2 <http://nuts.de/contained> ?contained3 . "
                    +"          ?contained3 rdfs:label ?label3 . "
                    +"          ?contained3 <http://nuts.de/id> ?id3 . }}} "
                    +"}";
    System.out.println(query);
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
                                     Principal principal)
          throws Exception {

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
      search += "?project <https://linkedopendata.eu/prop/direct/P32> <" + country + "> . ";
    }

    if (region != null) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      InputStream input = loader.getResourceAsStream("regions.csv");
      BufferedReader csvReader = new BufferedReader(new BufferedReader(new InputStreamReader(input, "UTF-8")));
      String coordinates = "";
      String row;
      while ((row = csvReader.readLine()) != null) {
        String[] data = row.split(";");
        if (data.length>4){
          if (region.replace("https://linkedopendata.eu/entity/","").equals(data[4])){
            coordinates = data[5];
          }
        }
      }
      coordinates =
              coordinates
                      .replace("Point(", "")
                      //.replace("\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>", "")
                      .replace(")", "");

      latitude = coordinates.split(" ")[1];
      longitude = coordinates.split(" ")[0];
    }

    if (latitude != null && longitude != null) {
      search +=
              "?project <https://linkedopendata.eu/prop/direct/P127> ?coordinates . "
                      + "FILTER ( <http://www.opengis.net/def/function/geosparql/distance>(\"POINT("
                      + longitude
                      + " "
                      + latitude
                      + ")\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>,?coordinates,<http://www.opengis.net/def/uom/OGC/1.0/metre>)/1000< 100) . ";
    }

    String query =
            "select ?beneficiary ?beneficiaryLabel ?country ?countryCode ?numberProjects ?totalEuBudget ?totalBudget ?link where { "
                    + " { SELECT ?beneficiary (count(?project) as ?numberProjects) (sum(?budget) as ?totalBudget) (sum(?euBudget) as ?totalEuBudget) where { "
                    + search
                    + "   ?project <https://linkedopendata.eu/prop/direct/P889> ?beneficiary . "
                    + "   ?project <https://linkedopendata.eu/prop/direct/P835> ?euBudget . "
                    + "   ?project <https://linkedopendata.eu/prop/direct/P474> ?budget . "
                    + " } group by ?beneficiary order by desc(?totalEuBudget) limit 500 } "
                    + " OPTIONAL { ?beneficiary <http://www.w3.org/2000/01/rdf-schema#label> ?beneficiaryLabel. "
                    + "             FILTER(LANG(?beneficiaryLabel) = \"en\" || LANG(?beneficiaryLabel) = \"fr\" || LANG(?beneficiaryLabel) = \"it\" || LANG(?beneficiaryLabel) = \"pl\" || LANG(?beneficiaryLabel) = \"cs\" || LANG(?beneficiaryLabel) = \"da\")  }"
                    + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P1> ?link. } "
                    + " OPTIONAL { ?beneficiary <https://linkedopendata.eu/prop/direct/P32> ?country. "
                    + "            ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . } "
                    + "} ";

    TupleQueryResult resultSet = executeAndCacheQuery(sparqlEndpoint, query, 30);

    List<Beneficiary> beneficiaries = new ArrayList<Beneficiary>();
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
                                        Principal principal,
                                        @Context HttpServletResponse response)
          throws Exception {
    List<Beneficiary> beneficiaryList =
            euSearchBeneficiaries(language, keywords, country, region, latitude, longitude, principal);
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
                                                            Principal principal)
          throws Exception {
    List<Beneficiary> beneficiaryList =
            euSearchBeneficiaries(language, keywords, country, region, latitude, longitude, principal);
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
    euSearchBeneficiaries("en", null, null, null, null, null, null);
    String[] countries = {
            "https://linkedopendata.eu/entity/Q15",
            "https://linkedopendata.eu/entity/Q2",
            "https://linkedopendata.eu/entity/Q25",
            "https://linkedopendata.eu/entity/Q20",
            "https://linkedopendata.eu/entity/Q13",
            "https://linkedopendata.eu/entity/Q12"
    };
    for (String country : countries) {
      euSearchBeneficiaries("en", null, country, null, null, null, null);
    }
    for (String country : countries) {
      JSONArray regions = facetEuRegions(country, "en");
      for (Object region : regions) {
        euSearchBeneficiaries(
                "en", null, country, ((JSONObject) region).get("region").toString(), null, null, null);
      }
      for (Object region : regions) {
        euSearchProject(
                "en", null, country, null, null, null, null, null, null,null,null,null,null,null,null,null,null,null,((JSONObject) region).get("region").toString(),200,0,null);
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
    File dir = new File(location + "/facet/cache/");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    // check if the query is cached
    if (dir.exists()) {
      File[] files = dir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (Integer.parseInt(file.getName()) == query.hashCode()) {
            System.out.println(query.hashCode());
            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);
            sparqlResultsJSONParser.parseQueryResult(
                    new FileInputStream(location + "/facet/cache/" + query.hashCode()));
            return tupleQueryResultHandler.getQueryResult();
          }
        }
      }
    }
    // execute and cache the query if not found before
    Map<String, String> additionalHttpHeaders = new HashMap();
    additionalHttpHeaders.put("timeout",String.valueOf(timeout));
    SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
    repo.setAdditionalHttpHeaders(additionalHttpHeaders);

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
    return tupleQueryResultHandler.getQueryResult();
  }

  JSONObject toJson(
          String key,
          Set<String> snippet,
          Set<String> label,
          Set<String> description,
          Set<String> startTime,
          Set<String> endTime,
          Set<String> euBudget,
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
