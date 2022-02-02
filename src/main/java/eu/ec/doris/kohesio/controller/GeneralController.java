package eu.ec.doris.kohesio.controller;


import eu.ec.doris.kohesio.payload.General;
import eu.ec.doris.kohesio.payload.GeneralList;
import eu.ec.doris.kohesio.services.FiltersGenerator;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")


public class GeneralController {
    private static final Logger logger = LoggerFactory.getLogger(GeneralController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Autowired
    FiltersGenerator filtersGenerator;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    @GetMapping(value = "/facet/eu/search/general", produces = "application/json")
    public ResponseEntity euSearchGeneral(
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "orderEuBudget", defaultValue = "false") Boolean orderEuBudget,
            @RequestParam(value = "orderTotalBudget", required = false) Boolean orderTotalBudget,
            @RequestParam(value = "orderNumProjects", required = false) Boolean orderNumProjects,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            Principal principal
    )
            throws Exception {
        logger.info("General search: language {}, keywords {}", language, keywords);


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
            search += "?general <http://www.openrdf.org/contrib/lucenesail#matches> [ "
                    + "<http://www.openrdf.org/contrib/lucenesail#query> \""
                    + keywords.replace("\"", "\\\"") + "\" ; "
                    + "<http://www.openrdf.org/contrib/lucenesail#snippet> ?snippet ] . ";
        }
        search += search + " ?general <https://linkedopendata.eu/prop/direct/P35> ?type . "
                + " FILTER (?type = <https://linkedopendata.eu/entity/Q9934> || ?type = <https://linkedopendata.eu/entity/Q196899>)} "
        ;

        String orderBy = "";

        if (orderEuBudget != null) {
            if (orderEuBudget) {
                orderBy = "ORDER BY ASC(?totalEuBudget)";
            } else {
                orderBy = "ORDER BY DESC(?totalEuBudget)";
            }
        }
        if (orderTotalBudget != null) {
            if (orderTotalBudget) {
                orderBy = "ORDER BY ASC(?totalBudget)";
            } else {
                orderBy = "ORDER BY DESC(?totalBudget)";
            }
        }
        if (orderNumProjects != null) {
            if (orderNumProjects) {
                orderBy = "ORDER BY ASC(?numberProjects)";
            } else {
                orderBy = "ORDER BY DESC(?numberProjects)";
            }
        }

        String labelsFilter = getGeneralLangLabelsFilter();
        String query =
                "SELECT ?general ?generalLabel ?generalLabel_en ?country ?countryCode ?link ?type ?transliteration { "
                        + " { SELECT DISTINCT ?general { "
                        + search
                        + "} "
                        + orderBy
                        + " LIMIT " + limit
                        + " OFFSET " + offset
                        + "} "
                        + " ?general <https://linkedopendata.eu/prop/direct/P35> ?type . "
                        + " VALUES(?type){(<https://linkedopendata.eu/entity/Q9934>) (<https://linkedopendata.eu/entity/Q196899>)} "
                        + "  OPTIONAL { ?general <http://www.w3.org/2000/01/rdf-schema#label> ?generalLabel_en ."
                        + "              FILTER(LANG(?generalLabel_en) = \"" + language + "\" ) } "
                        + " OPTIONAL { ?general <http://www.w3.org/2000/01/rdf-schema#label> ?generalLabel . "
                        + "            ?general <https://linkedopendata.eu/prop/direct/P32> ?country .   "
                        + labelsFilter
                        + " }"
                        + " OPTIONAL { ?general <https://linkedopendata.eu/prop/direct/P1> ?link. } "
                        + " OPTIONAL { ?general <https://linkedopendata.eu/prop/direct/P32> ?country. "
                        + "            ?country <https://linkedopendata.eu/prop/direct/P173> ?countryCode . } "
                        + " OPTIONAL { ?general <https://linkedopendata.eu/prop/P7> ?generalStatement . "
                        + "  ?generalStatement <https://linkedopendata.eu/prop/qualifier/P4393> ?transliteration ."
                        + " }"
                        + "} ";
        logger.debug(query);
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(sparqlEndpoint, query, 120);
        ArrayList<General> generals = new ArrayList<>();
        if (resultSet != null) {
            while (resultSet.hasNext()) {
                General general = new General();
                BindingSet querySolution = resultSet.next();
                if (querySolution.getBinding("generalLabel") != null) {
                    general.setLabel(
                            querySolution.getBinding("generalLabel").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("generalLabel_en") != null) {
                    general.setLabel(
                            querySolution.getBinding("generalLabel_en").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("country") != null) {
                    general.setCountry(
                            querySolution.getBinding("country").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("countryCode") != null) {
                    general.setCountryCode(
                            querySolution.getBinding("countryCode").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("link") != null) {
                    general.setLink(
                            querySolution.getBinding("link").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("transliteration") != null && querySolution.getBinding("generalLabel_en") == null) {
                    general.setTransliteration(
                            querySolution.getBinding("transliteration").getValue().stringValue()
                    );
                }
                if (querySolution.getBinding("type") != null) {
                    general.setType(
                            querySolution.getBinding("type").getValue().stringValue()
                    );
                }
                generals.add(general);
            }
        }
        GeneralList finalResult = new GeneralList();
        finalResult.setList(generals);
        finalResult.setNumberResults(generals.size());
        return new ResponseEntity<>(finalResult, HttpStatus.OK);
    }

    private String getGeneralLangLabelsFilter() {
        StringBuilder labelsFilter = new StringBuilder("FILTER(");
        HashMap<String, List<String>> countriesCodeMapping = filtersGenerator.getCountriesCodeMapping();
        int count = 0;
        for (Map.Entry<String, List<String>> entry : countriesCodeMapping.entrySet()) {
            String countryQID = entry.getKey();
            List<String> languageCode = entry.getValue();
            labelsFilter.append("( ");
            for (int i = 0; i < languageCode.size() - 1; i++) {
                labelsFilter.append(" LANG(?generalLabel) = \"").append(languageCode.get(i)).append("\" && ?country = ").append(countryQID).append(" ||  ");
            }
            labelsFilter.append(" LANG(?generalLabel) = \"").append(languageCode.get(languageCode.size() - 1)).append("\" && ?country = ").append(countryQID).append(" ) ");
            if (count < countriesCodeMapping.size() - 1)
                labelsFilter.append("|| \n");
            count++;
        }
        labelsFilter.append(")");
        return labelsFilter.toString();
    }

}
