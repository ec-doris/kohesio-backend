package eu.ec.doris.kohesio.controller;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/api")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    @Value("${kohesio.directory}")
    String location;

    @Autowired
    ProjectController projectController;
    @Autowired
    BeneficiaryController beneficiaryController;
    @Autowired
    FacetController facetController;
    @Autowired
    MapController mapController;


    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    @PostMapping(value = "/facet/eu/cache/generate", produces = "application/json")
    public void generateCache() throws Exception {
        logger.debug("Start generating map recursively");
        recursiveMap(null);
        logger.debug("End recursive map");
        // cache statistics
        facetController.facetEuStatistics();
        ArrayList<String> countries = new ArrayList<>();
        countries.add(null);
        for (Object jsonObject : facetController.facetEuCountries("en")) {
            JSONObject o = (JSONObject) jsonObject;
            countries.add(o.get("instance").toString());
        }
        // cache countries
        for (String country : countries) {
            Boolean[] orderStartDate = {null, true, false};
            for (Boolean b : orderStartDate) {
                try {
                    projectController.euSearchProject("en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null,
                            null, null, b, null, null, null,
                            null, null, null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            Boolean[] orderEndDate = {null, true, false};
            for (Boolean b : orderEndDate) {
                try {
                projectController.euSearchProject("en", null, country, null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, b, null, null,
                        null, null, null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            Boolean[] orderEuBudget = {null, true, false};
            for (Boolean b : orderEuBudget) {
                try{
                projectController.euSearchProject("en", null, country, null, null, null,
                        null, null, null, null,
                        null, null, null, null, null,
                        null, null, null, b, null, null,
                        null, null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            Boolean[] orderTotalBudget = {null, true, false};
            for (Boolean b : orderTotalBudget) {
                try{
                projectController.euSearchProject("en", null, country, null, null, null,
                        null, null, null, null,
                        null, null, null, null, null,
                        null, null, null, null, b, null, null,
                        null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            // cache regions
            JSONArray regions = facetController.facetEuRegions(country,"en");
            for (Object region: regions) {
                try{
                    String regio = ((JSONObject) region).get("region").toString();
                    projectController.euSearchProject("en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, false, null, null,
                            regio, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            // cache policy objective
            JSONArray policies = facetController.facetPolicyObjective("en");
            for (Object policy: policies) {
                try{
                    String polic = ((JSONObject) policy).get("instance").toString();
                    projectController.euSearchProject("en", null, country, null, null, null,
                            null, polic, null, null,
                            null, null, null, null, null,
                            null, null, null, null, false, null, null,
                            null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            // cache thematic objective
            JSONArray themes = facetController.facetEuThematicObjective("en");
            for (Object theme: themes) {
                try{
                    String t = ((JSONObject) theme).get("instance").toString();
                    projectController.euSearchProject("en", null, country, t, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, false, null, null,
                            null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }

            // cache the programs
            JSONArray programs = facetController.facetEuPrograms("en", country, null);
            for (Object program: programs) {
                try{
                    String p = ((JSONObject) program).get("instance").toString();
                    projectController.euSearchProject("en", null, country, null, null, p,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, false, null, null,
                            null, 1000, 1, 100,null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        // cache the funds
        JSONArray funds = facetController.facetEuFunds("en");
        for (Object fund: funds) {
            try{
                String t = ((JSONObject) fund).get("instance").toString();
                projectController.euSearchProject("en", null, null, null, t, null,
                        null, null, null, null,
                        null, null, null, null, null,
                        null, null, null, null, false, null, null,
                        null, 1000, 1, 100,null);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        // cache the amounts
        List<Long> lower_bound = new ArrayList<>();
        List<Long> upper_bound = new ArrayList<>();
        lower_bound.add(0L);
        upper_bound.add(1000L);
        lower_bound.add(1000L);
        upper_bound.add(10000L);
        lower_bound.add(10000L);
        upper_bound.add(100000L);
        lower_bound.add(100000L);
        upper_bound.add(1000000L);
        lower_bound.add(1000000L);
        upper_bound.add(10000000L);
        lower_bound.add(10000000L);
        upper_bound.add(100000000L);
        lower_bound.add(100000000L);
        upper_bound.add(1000000000L);
        lower_bound.add(1000000000L);
        upper_bound.add(10000000000L);
        for (int i=0; i< lower_bound.size(); i++) {
            try{
                projectController.euSearchProject("en", null, null, null, null, null,
                        null, null, lower_bound.get(i), upper_bound.get(i),
                        null, null, null, null, null,
                        null, null, null, null, false, null, null,
                        null, 1000, 1, 100,null);
                projectController.euSearchProject("en", null, null, null, null, null,
                        null, null, null, null, lower_bound.get(i), upper_bound.get(i), null, null, null,
                        null, null, null, null, false, null, null,
                        null, 1000, 1, 100,null);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        for (String country : countries) {

            Boolean[] orderEuBudget = {true, false};
            for (Boolean b : orderEuBudget) {
                try{
                beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                        null,null, b, null, null, 1000, 1, null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            Boolean[] orderTotalBudget = {true, false};
            for (Boolean b : orderTotalBudget) {
                try{
                beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                        null, null,null, b, null, 1000, 1, null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            Boolean[] orderNumProjects = {true, false};
            for (Boolean b : orderNumProjects) {
                try{
                beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                        null, null,null, null, b, 1000, 1, null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
            JSONArray regions = new JSONArray();
            if(country != null) {
                regions = facetController.facetEuRegions(country, "en");
            }
            for (Object region : regions) {
                String r = ((JSONObject) region).get("region").toString();
                try{
                    beneficiaryController.euSearchBeneficiaries("en", null, country, r, null, null, null,
                            null, null,null, false, null, 1000, 1, null);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }

        }
//        for (String country : countries) {
//            JSONArray regions = new JSONArray();
//            if(country != null) {
//                regions = facetController.facetEuRegions(country, "en");
//            }
//            regions.add(null);
//            for (Object region : regions) {
//                JSONArray funds = facetController.facetEuFunds("en");
//                funds.add(null);
//                for (Object fund : funds) {
//                    JSONArray programs = new JSONArray();
//                    if(country != null) {
//                        programs = facetController.facetEuPrograms("en", country);
//                    }
//                    programs.add(null);
//                    for (Object program : programs) {
//                        String r = null;
//                        if (region != null) {
//                            r = ((JSONObject) region).get("region").toString();
//                        }
//                        String f = null;
//                        if (fund != null) {
//                            f = ((JSONObject) fund).get("instance").toString();
//                        }
//                        String p = null;
//                        if (program != null) {
//                            p = ((JSONObject) program).get("instance").toString();
//                        }
//                        Boolean[] orderEuBudget = {null, true, false};
//                        for (Boolean b : orderEuBudget) {
//                            beneficiaryController.euSearchBeneficiaries(
//                                    "en", null, country, r, null, null, f, p, null,b, null, null, 1000, 0, null);
//                        }
//                        JSONArray policies = facetController.facetPolicyObjective("en");
//                        for (Object policy: policies) {
//                            String polic = null;
//                            if(policy != null)
//                                polic = ((JSONObject) policy).get("instance").toString();
//                            projectController.euSearchProject("en", null, country, null, null, null,
//                                    null, polic, null, null,
//                                    null, null, null, null, null,
//                                    null, null, null, null, false, null, null,
//                                    null, 1000, 1,50, null);
//                            mapController.euSearchProjectMap("en", null, country, null, f, p, null,
//                                    polic, null, null, null,
//                                    null, null, null, null,
//                                    null, null, null, r, r, null, 0, 400, null);
//                        }
//                        logger.info("End generating cache!");
//                    }
//                }
//            }
//        }
    }


    @PostMapping(value = "/facet/eu/cache/clean", produces = "application/json")
    public void cleanCache() throws Exception {
        File dir = new File(location + "/facet/cache/");
        if (dir.exists()) {
            FileUtils.cleanDirectory(dir);
        }
        facetController.clear();
    }

    void recursiveMap(String granularityRegion) throws Exception {
        logger.debug("Resolving for region: " + granularityRegion);
        ResponseEntity responseEntity = mapController.euSearchProjectMap("en", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, granularityRegion, null, 0, 400, null);
        logger.debug("Response result: " + responseEntity.getBody());
        if (((JSONObject) responseEntity.getBody()).get("subregions") instanceof JSONArray) {
            for (Object element : (JSONArray) ((JSONObject) responseEntity.getBody()).get("subregions")) {
                logger.debug("Subregion: " + ((JSONObject) element).get("region").toString());
                if (!((JSONObject) element).get("region").toString().equals(granularityRegion)) {
                    recursiveMap(((JSONObject) element).get("region").toString());
                }
            }
        }
    }


}
