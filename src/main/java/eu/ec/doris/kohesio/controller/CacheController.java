package eu.ec.doris.kohesio.controller;

import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/wikibase")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    @Value("${kohesio.directory}")
    String location;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @Autowired
    ProjectController projectController;
    @Autowired
    BeneficiaryController beneficiaryController;
    @Autowired
    FacetController facetController;
    @Autowired
    MapController mapController;
    @Autowired
    SPARQLQueryService sparqlQueryService;

    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }


    @PostMapping(value = "/facet/eu/cache/generate", produces = "application/json")
    public void generateCache() throws Exception {
        facetController.initialize("en");
        logger.debug("Start generating map recursively");
        recursiveMap(null);
        cacheMapWithBoundingBox();
        logger.debug("End recursive map");
        // cache statistics
        facetController.facetEuStatistics();
        ArrayList<String> countries = new ArrayList<>();
        countries.add(null);
        for (Object jsonObject : facetController.facetEuCountries("en", null)) {
            JSONObject o = (JSONObject) jsonObject;
            countries.add(o.get("instance").toString());
        }
        // cache countries
        for (String country : countries) {
            Boolean[] orderStartDate = {null, true, false};
            for (Boolean b : orderStartDate) {
                try {
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null,
                            null, null, b, null, null, null, null, null,
                            null, null, null, 1, 0, 400, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Boolean[] orderEndDate = {null, true, false};
            for (Boolean b : orderEndDate) {
                try {
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null,
                            null, null, null, b, null, null, null, null,
                            null, null, null, 1, 0, 100, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Boolean[] orderEuBudget = {null, true, false};
            for (Boolean b : orderEuBudget) {
                try {
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, b, null, null,
                            null, null, null, null, 1, 0, 100, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Boolean[] orderTotalBudget = {null, true, false};
            for (Boolean b : orderTotalBudget) {
                try {
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, b, null, null,
                            null, null, null, 1, 0, 100, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // cache regions
            JSONArray regions = facetController.facetEuRegions(country, "en", null);
            for (Object region : regions) {
                try {
                    String regio = ((JSONObject) region).get("region").toString();
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, false, null, null, null, null,
                            regio, 1, 0, 400, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // cache policy objective
            JSONArray policies = facetController.facetPolicyObjective("en");
            for (Object policy : policies) {
                String polic = ((JSONObject) policy).get("instance").toString();
                try {
                    projectController.euSearchProject(
                            "en", null, country, null, null, null,
                            null, polic, null, null,
                            null, null, null, null, null,
                            null, null, null, null,
                            null, null,
                            null,
                            null, null, null, 1, 0, 400, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                JSONArray themes = facetController.facetEuThematicObjective("en", polic, null);
                for (Object theme : themes) {
                    try {
                        String t = ((JSONObject) theme).get("instance").toString();
                        projectController.euSearchProject(
                                "en", null, country, t, null, null,
                                null, polic, null, null,
                                null, null, null, null, null,
                                null, null, null, null, null, null, null,
                                null, null, null, 1, 0, 400, null
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
            // cache thematic objective
            JSONArray themes = facetController.facetEuThematicObjective("en");
            for (Object theme : themes) {
                try {
                    String t = ((JSONObject) theme).get("instance").toString();
                    projectController.euSearchProject(
                            "en", null, country, t, null, null,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, null, null, null,
                            null, null, null, 1, 0, 400, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // cache the programs
            JSONArray programs = facetController.facetEuPrograms("en", country, null, null, null, null);
            for (Object program : programs) {
                try {
                    String p = ((JSONObject) program).get("instance").toString();
                    projectController.euSearchProject(
                            "en", null, country, null, null, p,
                            null, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, null, null, null,
                            null, null, null, 1, 0, 400, null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // cache the funds
        JSONArray funds = facetController.facetEuFunds("en", null);
        for (Object fund : funds) {
            try {
                String t = ((JSONObject) fund).get("instance").toString();
                projectController.euSearchProject(
                        "en", null, null, null, t, null,
                        null, null, null, null,
                        null, null, null, null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, 1, 0, 400, null
                );
            } catch (Exception e) {
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
        for (int i = 0; i < lower_bound.size(); i++) {
            try {
                projectController.euSearchProject(
                        "en", null, null, null, null, null,
                        null, null, lower_bound.get(i), upper_bound.get(i),
                        null, null, null, null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, 1, 0, 400, null);
                projectController.euSearchProject("en", null, null, null, null, null,
                        null, null, null, null, lower_bound.get(i), upper_bound.get(i), null, null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, 1, 0, 400, null
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        JSONArray areaOfInterventions = facetController.facetEuCategoryOfIntervention("en", null);
        for (Object areaOfIntervention : areaOfInterventions) {
            try {
                JSONArray interventionFileds = (JSONArray) ((JSONObject) areaOfIntervention).get("options");
                for (Object intervention : interventionFileds) {
                    List<String> t = new ArrayList<>();
                    t.add(((JSONObject) intervention).get("instance").toString());
                    projectController.euSearchProject(
                            "en", null, null, null, null, null,
                            t, null, null, null,
                            null, null, null, null, null,
                            null, null, null, null, null, null, null,
                            null, null, null, 1, 0, 100, null
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (String country : countries) {

            Boolean[] orderEuBudget = {true, false};
            for (Boolean b : orderEuBudget) {
                try {
                    beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                            null, null, b, null, null, 1, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Boolean[] orderTotalBudget = {true, false};
            for (Boolean b : orderTotalBudget) {
                try {
                    beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                            null, null, null, b, null, 1, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Boolean[] orderNumProjects = {true, false};
            for (Boolean b : orderNumProjects) {
                try {
                    beneficiaryController.euSearchBeneficiaries("en", null, country, null, null, null, null,
                            null, null, null, null, b, 1, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            JSONArray regions = new JSONArray();
            if (country != null) {
                regions = facetController.facetEuRegions(country, "en", null);
            }
            for (Object region : regions) {
                String r = ((JSONObject) region).get("region").toString();
                try {
                    beneficiaryController.euSearchBeneficiaries("en", null, country, r, null, null, null,
                            null, null, null, false, null, 1, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @PostMapping(value = "/facet/eu/cache/generate/map")
    public void generateCacheMap() throws Exception {
        facetController.initialize("en");
        cacheMapWithBoundingBox();
    }

    private List<String> getListFromApi(JSONArray array, String key) {
        List<String> list = new ArrayList<>();
        for (Object jso : array) {
            String value = (String) ((JSONObject) jso).get(key);
            if (value != null && !value.isEmpty() && !list.contains(value)) {
                list.add(value);
            }
        }
        return list;
    }

    private void wrapperMap(
            String country,
            String theme,
            String fund,
            String program,
            String policyObjective,
            String region,
            String granularityRegion,
            Boolean interreg,
            List<String> projectTypes,
            String priorityAxis
    ) throws Exception {
        String boundingBoxString = "{\"_southWest\":{\"lat\":22.43134015636062,\"lng\":-44.6484375},\"_northEast\":{\"lat\":65.18303007291382,\"lng\":52.73437500000001}}";
        mapController.euSearchProjectMap(
                "en", null, country, theme,
                fund, program, null, policyObjective,
                null, null, null, null,
                null, null, null, null,
                null, null, region, granularityRegion,
                null, null, 0, null,
                null, interreg, null, null,
                null, projectTypes, priorityAxis, boundingBoxString,
                4, 400, null, true
        );
    }

    private void cacheMapWithBoundingBox() throws Exception {
        String language = "en";
        List<String> countries = getListFromApi(facetController.facetEuCountries(language, null), "instance");
        countries.add(null);
        for (String country : countries) {
            wrapperMap(
                    country, null, null, null,
                    null, null, country,
                    null, null, null
            );
            List<String> regions = getListFromApi(facetController.facetEuRegions(country, "en", null), "region");
            for (String region : regions) {
                String query = "ASK { <" + region + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q2727537> . }";
                boolean resultSet = sparqlQueryService.executeBooleanQuery(
                        sparqlEndpoint,
                        query,
                        20
                );
                if (!resultSet) {
                    wrapperMap(
                            country, null, null, null,
                            null, region, region,
                            null, null, null
                    );
                }
            }

            List<String> programs = getListFromApi(facetController.facetEuPrograms("en", country, null, null, null, null), "region");
            for (String program : programs) {
                wrapperMap(
                        country, null, null, program,
                        null, null, null,
                        null, null, null
                );

                List<String> priorityAxis = getListFromApi(facetController.facetEuPriorityAxis("en", null, country, program), "instance");
                for (String priorityAxi : priorityAxis) {
                    wrapperMap(
                            country, null, null, program,
                            null, null, null,
                            null, null, priorityAxi
                    );
                }
            }

            List<String> policies = getListFromApi(facetController.facetPolicyObjective("en"), "instance");
            for (String policy : policies) {
                wrapperMap(
                        country, null, null, null,
                        policy, null, country,
                        null, null, null
                );
                List<String> themesOfPolicy = getListFromApi(facetController.facetEuThematicObjective("en", policy, null), "instance");
                for (String themeOfPolicy : themesOfPolicy) {
                    wrapperMap(
                            country, themeOfPolicy, null, null,
                            policy, null, country,
                            null, null, null
                    );
                }
            }

            List<String> themes = getListFromApi(facetController.facetEuThematicObjective("en"), "instance");
            for (String theme : themes) {
                wrapperMap(
                        country, theme, null, null,
                        null, null, country,
                        null, null, null
                );
            }
        }

        List<String> policies = getListFromApi(facetController.facetPolicyObjective("en"), "instance");
        for (String policy : policies) {
            wrapperMap(
                    null, null, null, null,
                    policy, null, null,
                    null, null, null
            );
            List<String> themesOfPolicy = getListFromApi(facetController.facetEuThematicObjective("en", policy, null), "instance");
            for (String themeOfPolicy : themesOfPolicy) {
                wrapperMap(
                        null, themeOfPolicy, null, null,
                        policy, null, null,
                        null, null, null
                );
            }
        }

        List<String> themes = getListFromApi(facetController.facetEuThematicObjective("en"), "instance");
        for (String theme : themes) {
            wrapperMap(
                    null, theme, null, null,
                    null, null, null,
                    null, null, null
            );
        }

        List<String> funds = getListFromApi(facetController.facetEuFunds("eu", null), "instance");
        for (String fund : funds) {
            wrapperMap(
                    null, null, fund, null,
                    null, null, null,
                    null, null, null
            );
        }

        for (String projectCollection : facetController.projectTypes) {
            wrapperMap(
                    null, null, null, null,
                    null, null, null,
                    null, Collections.singletonList(projectCollection), null
            );
        }

        { // interreg
            wrapperMap(
                    null, null, null, null,
                    null, null, null,
                    false, null, null
            );
            wrapperMap(
                    null, null, null, null,
                    null, null, null,
                    true, null, null
            );
        }

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
        logger.debug("Resolving for region: {}", granularityRegion);
        ResponseEntity<JSONObject> responseEntity = mapController.euSearchProjectMap(
                "en", null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null, granularityRegion,
                null, null,
                0, null,
                null, null,
                null, null, null,
                null, null, null,
                null, 400, null, true
        );
        logger.debug("Response result: {}", responseEntity.getBody());
        if ((responseEntity.getBody()).get("subregions") instanceof JSONArray) {
            for (Object element : (JSONArray) (responseEntity.getBody()).get("subregions")) {
                logger.debug("Subregion: {}", ((JSONObject) element).get("region").toString());
                if (!((JSONObject) element).get("region").toString().equals(granularityRegion)) {
                    recursiveMap(((JSONObject) element).get("region").toString());
                }
            }
        }
    }


}
