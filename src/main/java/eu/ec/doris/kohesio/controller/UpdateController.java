package eu.ec.doris.kohesio.controller;


import eu.ec.doris.kohesio.services.SPARQLQueryService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import org.json.simple.JSONObject;
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

import java.io.IOException;

@RestController
@RequestMapping("/wikibase/update")
public class UpdateController {
    private static final Logger logger = LoggerFactory.getLogger(UpdateController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;


    @GetMapping(value = "/project", produces = "application/json")
    public ResponseEntity updateProject(
            @RequestParam(value = "id") String id,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "language", defaultValue = "en") String language
    ) throws Exception {

        logger.info("Project search by ID: id {}, language {}", id, language);

        String queryCheck = "ASK { <"
                + id
                + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> }";
        boolean resultAsk = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - project ID not found");
            return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
        } else {
            String tripleToDelete = "";
            String tripleToInsert = "";
            String tripleToWhere = "";
            if (label != null) {
                tripleToDelete = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581563> ?o . ";
                tripleToWhere = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581563> ?o . FILTER (LANG(?o) = \"" + language + "\")";
                tripleToInsert = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581563> \"" + label + "\"@" + language + " . ";
            }
            if (description != null) {
                tripleToDelete = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581562> ?o . ";
                tripleToWhere = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581562> ?o . FILTER (LANG(?o) = \"" + language + "\")";

                tripleToInsert = "<" + id + "> <https://linkedopendata.eu/prop/direct/P581562> \"" + description + "\"@" + language + " . ";
            }

            if (tripleToDelete.isEmpty() || tripleToInsert.isEmpty()) {
                JSONObject result = new JSONObject();
                result.put("message", "Bad Request - nothing to update");
                return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
            }
            String queryDelete = "DELETE {" + tripleToDelete + "}"
                    + " WHERE { "
                    + "<" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934>. "
                    + tripleToWhere
                    + " }";
            String queryInsert = " INSERT DATA {" + tripleToInsert + "}";

            System.err.println(queryDelete);
            System.err.println(queryInsert);
            sparqlQueryService.executeUpdateQuery(sparqlEndpoint, queryDelete, 20);
            sparqlQueryService.executeUpdateQuery(sparqlEndpoint, queryInsert, 20);

            JSONObject result = new JSONObject();
            result.put("message", queryDelete + " " + queryInsert);
            return new ResponseEntity<JSONObject>(result, HttpStatus.OK);
        }
    }

    @GetMapping(value = "/pods", produces = "application/json")
    public void getPods() throws IOException, ApiException {

        // loading the in-cluster config, including:
        //   1. service-account CA
        //   2. service-account bearer-token
        //   3. service-account namespace
        //   4. master endpoints(ip, port) from pre-set environment variables
        ApiClient client = ClientBuilder.cluster().build();
//        ApiClient client = ClientBuilder.defaultClient();
        System.err.println("client: " + client.getBasePath());
        System.err.println("client: " + client);

        // if you prefer not to refresh service account token, please use:
//        ApiClient client = ClientBuilder.oldCluster().build();

        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(client);

        // the CoreV1Api loads default api-client from global configuration.
        CoreV1Api api = new CoreV1Api();

        // list all pods in all namespaces
        V1PodList list = api.listPodForAllNamespaces(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        for (V1Pod item : list.getItems()) {
            System.out.println(item.getMetadata().getName());
        }
    }
}
