package eu.ec.doris.kohesio.controller;


import eu.ec.doris.kohesio.payload.MonolingualString;
import eu.ec.doris.kohesio.payload.Update;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/wikibase/update")
public class UpdateController {
    private static final Logger logger = LoggerFactory.getLogger(UpdateController.class);

    @Autowired
    SPARQLQueryService sparqlQueryService;

    @Value("${kohesio.sparqlEndpoint}")
    String sparqlEndpoint;

    public ResponseEntity<JSONObject> updateProject(
            String url,
            Update updatePayload
    ) {
        String id = updatePayload.getId();
        List<MonolingualString> labels = updatePayload.getLabels();
        List<MonolingualString> descriptions = updatePayload.getDescriptions();

        logger.info("Project update by ID: id {} on {}", id, url);

        String queryCheck = "ASK { <"
                + id
                + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> }";
        boolean resultAsk = sparqlQueryService.executeBooleanQuery(
                url,
                queryCheck,
                2
        );
        if (!resultAsk) {
            return new ResponseEntity<>(
                    (JSONObject) (new JSONObject().put("message", "Bad Request - project ID not found")),
                    HttpStatus.BAD_REQUEST
            );
        } else {
            List<UpdateTriple> updateTriples = new ArrayList<>();
            if (labels != null) {
                for (MonolingualString labelObject : labels) {
                    String language = labelObject.getLanguage();
                    String label = labelObject.getText();

                    StringBuilder tripleToDelete = new StringBuilder();
                    StringBuilder tripleToInsert = new StringBuilder();
                    StringBuilder tripleToWhere = new StringBuilder();
                    if (label != null) {
                        tripleToDelete.append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581563> ?label_")
                                .append(language)
                                .append(" . ")
                        ;

                        tripleToWhere
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581563> ?label_")
                                .append(language)
                                .append(" . FILTER (LANG(?label_")
                                .append(language)
                                .append(")")
                                .append(" = \"")
                                .append(language)
                                .append("\") ")
                        ;
                        tripleToInsert
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581563> \"")
                                .append(label)
                                .append("\"@")
                                .append(language)
                                .append(" . ")
                        ;
                        updateTriples.add(
                                new UpdateTriple(
                                        tripleToDelete.toString(),
                                        tripleToInsert.toString(),
                                        tripleToWhere.toString()
                                )
                        );
                    }
                }
            }
            if (descriptions != null) {
                for (MonolingualString descriptionObject : descriptions) {
                    String language = descriptionObject.getLanguage();
                    String description = descriptionObject.getText();

                    StringBuilder tripleToDelete = new StringBuilder();
                    StringBuilder tripleToInsert = new StringBuilder();
                    StringBuilder tripleToWhere = new StringBuilder();
                    if (description != null) {
                        description = description.replace("\"", "\\\"");
                        tripleToDelete
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581562> ?description_")
                                .append(language)
                                .append(" . ")
                        ;
                        tripleToWhere
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581562> ?description_")
                                .append(language)
                                .append(" . FILTER (LANG(?description_")
                                .append(language)
                                .append(")")
                                .append(" = \"")
                                .append(language)
                                .append("\") ")
                        ;
                        tripleToInsert
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P581562> \"")
                                .append(description)
                                .append("\"@")
                                .append(language)
                                .append(" . ")
                        ;

                        updateTriples.add(
                                new UpdateTriple(
                                        tripleToDelete.toString(),
                                        tripleToInsert.toString(),
                                        tripleToWhere.toString()
                                )
                        );
                    }
                }
            }
            if (updateTriples.isEmpty()) {
                return new ResponseEntity<>(
                        (JSONObject) (new JSONObject().put("message", "Bad Request - nothing to update")),
                        HttpStatus.BAD_REQUEST
                );
            }
            for (UpdateTriple updateTriple : updateTriples) {
                String queryDelete = updateTriple.getDeleteQuery();
                String queryInsert = updateTriple.getInsertQuery();
                System.err.println(queryDelete);
                System.err.println(queryInsert);
                sparqlQueryService.executeUpdateQuery(url, queryDelete, 20);
                sparqlQueryService.executeUpdateQuery(url, queryInsert, 20);
            }

            return new ResponseEntity<>(
                    (JSONObject) (new JSONObject().put("message", "entity updated")),
                    HttpStatus.OK
            );
        }
    }

    @PostMapping(value = "/project", produces = "application/json")
    public ResponseEntity<JSONObject> propagateUpdateProject(
            @RequestBody Update updatePayload
    ) throws IOException, ApiException {
        logger.info("Propagate update project {}", updatePayload.getId());
        ApiClient client = ClientBuilder.cluster().build();
        String namespace = new String(
                Files.readAllBytes(
                        Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")
                ),
                Charset.defaultCharset()
        );
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1Api();
        // list all pods in all namespaces
        if ("development".equals(namespace)) {
            // On dev QAnswer is not in the cluster
//            return updateProject(sparqlEndpoint, updatePayload);
            logger.info("You are on development environment, no update will be done");
            return new ResponseEntity<>(
                    (JSONObject) (new JSONObject().put("message", "You are on development environment, no update will be done")),
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        } else {
            V1PodList list = api.listNamespacedPod(
                    namespace,
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
            ResponseEntity<JSONObject> lastResponse = new ResponseEntity<>(
                    (JSONObject) (new JSONObject().put("message", "No running QAnswer found")),
                    HttpStatus.SERVICE_UNAVAILABLE
            );
            for (V1Pod item : list.getItems()) {
                String ip = item.getStatus().getPodIP();
                String phase = item.getStatus().getPhase();
                String port = null;
                for (V1Container container : item.getSpec().getContainers()) {
                    if ("kohesio-qanswer-container".equals(container.getName())) {
                        port = container.getPorts().get(0).getContainerPort().toString();
                        System.out.println("IP: " + ip + " phase: " + phase + " port: " + port);
                        break;
                    }
                }

                if ("Running".equals(phase) && port != null) {
                    String url = "http://" + ip + ":" + port + "/api/endpoint/commission/eu/sparql";
                    lastResponse = updateProject(url, updatePayload);
                }
            }
            return lastResponse;
        }
    }

    private class UpdateTriple {
        String tripleToDelete;
        String tripleToInsert;
        String tripleToWhere;

        public UpdateTriple(String tripleToDelete, String tripleToInsert, String tripleToWhere) {
            this.tripleToDelete = tripleToDelete;
            this.tripleToInsert = tripleToInsert;
            this.tripleToWhere = tripleToWhere;
        }

        public String getDeleteQuery() {
            return "DELETE {" + tripleToDelete + "}"
                    + " WHERE { "
                    + tripleToWhere
                    + " }";
        }

        public String getInsertQuery() {
            return "INSERT DATA {" + tripleToInsert + "}";
        }

        public String getTripleToDelete() {
            return tripleToDelete;
        }

        public void setTripleToDelete(String tripleToDelete) {
            this.tripleToDelete = tripleToDelete;
        }

        public String getTripleToInsert() {
            return tripleToInsert;
        }

        public void setTripleToInsert(String tripleToInsert) {
            this.tripleToInsert = tripleToInsert;
        }

        public String getTripleToWhere() {
            return tripleToWhere;
        }

        public void setTripleToWhere(String tripleToWhere) {
            this.tripleToWhere = tripleToWhere;
        }
    }
}
