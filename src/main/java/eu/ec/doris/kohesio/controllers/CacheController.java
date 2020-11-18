package eu.ec.doris.kohesio.controllers;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.File;

public class CacheController {

    @Value("${kohesio.directory}")
    String location;

    @Autowired
    ProjectController projectController;
    @Autowired
    BeneficiaryController beneficiaryController;
    @Autowired
    EuController euController;
    @Autowired
    MapController mapController;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
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
        Boolean[] orderStartDate = {null, true, false};
        for (Boolean b : orderStartDate){
          projectController.euSearchProject("en", null, country, null, null, null,
                  null, null, null, null,
                  null, null, null, null,
                  null, null, b, null, null, null,
                  null, null, null, 1000, 1, null);
        }
        Boolean[] orderEndDate = {null, true, false};
        for (Boolean b : orderEndDate){
          projectController.euSearchProject("en", null, country, null, null, null,
                  null, null, null, null,
                  null, null, null, null,
                  null, null, null, b, null, null,
                  null, null, null, 1000, 1, null);
        }
        Boolean[] orderEuBudget = {null, true, false};
        for (Boolean b : orderEuBudget){
          projectController.euSearchProject("en", null, country, null, null, null,
                  null, null, null, null,
                  null, null, null, null, null,
                  null, null, null, b, null, null,
                  null, null, 1000, 1, null);
        }
        Boolean[] orderTotalBudget = {null, true, false};
        for (Boolean b : orderTotalBudget) {
          projectController.euSearchProject("en", null, country, null, null, null,
                  null, null, null, null,
                  null, null, null, null, null,
                  null, null, null, null, b, null, null,
                  null, 1000, 1, null);
        }
    }

    for (String country : countries) {
      Boolean[] orderEuBudget = {null, true, false};
      for (Boolean b : orderEuBudget){
        beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                null,b,false,false,1000,1,null);
      }
      Boolean[] orderTotalBudget = {null, true, false};
      for (Boolean b : orderTotalBudget) {
        beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                null,null,b,null,1000,1,null);
      }
      Boolean[] orderNumProjects = {null, true, false};
      for (Boolean b : orderNumProjects) {
        beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                null,null,null,b,1000,1,null);
      }
    }
    for (String country : countries) {
      if (country!=null) {
        JSONArray regions = euController.facetEuRegions(country, "en");
        regions.add(null);
        for (Object region : regions) {
          JSONArray funds = euController.facetEuFunds("en");
          funds.add(null);
          for (Object fund : funds) {
            JSONArray programs = euController.facetEuPrograms("en", country);
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
              beneficiaryController.euSearchBeneficiaries(
                      "en", null, country, r, null, null, f, p, false,false,false,0,0,null);
              mapController.euSearchProjectMap("en", null, country, null, f, p, null,null,null,null,null,null,null,null,null,null,null,null,r,r,null,0,400,null);
              System.out.println("Done");
            }
          }
        }
      }
    }
  }

  void recursiveMap(String granularityRegion) throws Exception {
    System.out.println("Resolving for "+granularityRegion);
    ResponseEntity responseEntity = mapController.euSearchProjectMap("en", null, null, null, null, null, null,null,null,null,null,null,null,null,null,null,null,null,null,granularityRegion,null,0,400,null);
    System.out.println("Hello world "+responseEntity.getBody());
    if (((JSONObject)responseEntity.getBody()).get("subregions") instanceof JSONArray){
      for (Object element : (JSONArray)((JSONObject)responseEntity.getBody()).get("subregions")){
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
}
