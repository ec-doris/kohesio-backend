package eu.ec.doris.kohesio.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
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
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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


    @PostMapping(value = "/projectUpdate", produces = "application/json")
    public ResponseEntity<JSONObject> updateProject(
            @org.springframework.web.bind.annotation.RequestBody Update updatePayload
    ) throws Exception {
        String id = updatePayload.getId();
        List<MonolingualString> labels = updatePayload.getLabels();
        List<MonolingualString> descriptions = updatePayload.getDescriptions();

        logger.info("Project update by ID: id {}", id);
        for (MonolingualString label : labels) {
            logger.info("label {}, language {}", label.getText(), label.getLanguage());
        }
        for (MonolingualString desccription : descriptions) {
            logger.info("description {}, language {}", desccription.getText(), desccription.getLanguage());
        }

        String queryCheck = "ASK { <"
                + id
                + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> }";
        boolean resultAsk = sparqlQueryService.executeBooleanQuery(sparqlEndpoint, queryCheck, 2);
        if (!resultAsk) {
            JSONObject result = new JSONObject();
            result.put("message", "Bad Request - project ID not found");
            return new ResponseEntity<JSONObject>(result, HttpStatus.BAD_REQUEST);
        } else {

            StringBuilder tripleToDelete = new StringBuilder();
            StringBuilder tripleToInsert = new StringBuilder();
            StringBuilder tripleToWhere = new StringBuilder();
            for (MonolingualString labelObject : labels) {
                String language = labelObject.getLanguage();
                String label = labelObject.getText();

                if (label != null) {
                    tripleToDelete.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581563> ?o . ");
                    tripleToWhere.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581563> ?o . FILTER (LANG(?o) = \"").append(language).append("\") ");
                    tripleToInsert.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581563> \"").append(label).append("\"@").append(language).append(" . ");
                }
            }
            for (MonolingualString descriptionObject : descriptions) {
                String language = descriptionObject.getLanguage();
                String description = descriptionObject.getText();

                if (description != null) {
                    tripleToDelete.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581562> ?o . ");
                    tripleToWhere.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581562> ?o . FILTER (LANG(?o) = \"").append(language).append("\") ");
                    tripleToInsert.append(" <").append(id).append("> <https://linkedopendata.eu/prop/direct/P581562> \"").append(description).append("\"@").append(language).append(" . ");
                }
            }
            if ((tripleToDelete.length() == 0) || (tripleToInsert.length() == 0)) {
                JSONObject result = new JSONObject();
                result.put("message", "Bad Request - nothing to update");
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            String queryDelete = "DELETE {" + tripleToDelete + "}"
                    + " WHERE { "
                    + "<" + id + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934>. "
                    + tripleToWhere
                    + " }";
            String queryInsert = " INSERT DATA {" + tripleToInsert + "}";

            System.err.println(queryDelete);
            System.err.println(queryInsert);
//            sparqlQueryService.executeUpdateQuery(sparqlEndpoint, queryDelete, 20);
//            sparqlQueryService.executeUpdateQuery(sparqlEndpoint, queryInsert, 20);

            JSONObject result = new JSONObject();
            result.put("message", "entity updated");
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    @PostMapping(value = "/project", produces = "application/json")
    public void propagateUpdateProject(
            @org.springframework.web.bind.annotation.RequestBody Update updatePayload
    ) throws IOException, ApiException {
        logger.info("Propagate update project");
        ApiClient client = ClientBuilder.cluster().build();
        String namespace = new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")), Charset.defaultCharset());
        Configuration.setDefaultApiClient(client);
        System.out.println(namespace);
        CoreV1Api api = new CoreV1Api();
        // list all pods in all namespaces
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
        List<Response> responses = new ArrayList<>();
        for (V1Pod item : list.getItems()) {
            String ip = item.getStatus().getPodIP();
            String phase = item.getStatus().getPhase();
            String port = null;
            for (V1Container container : item.getSpec().getContainers()) {
                if (
                        "kohesio-backend-container".equals(container.getName()) &&
                                !"openjdk:11-jre-slim".equals(container.getImage())
                ) {
                    port = container.getPorts().get(0).getContainerPort().toString();
                    System.out.println("IP: " + ip + " phase: " + phase + " port: " + port);
                    break;
                }
            }

            if ("Running".equals(phase) && port != null) {
                String url = "http://" + ip + ":" + port + "/wikibase/update/projectUpdate";
                OkHttpClient httpClient = client.getHttpClient();
                ObjectMapper objectMapper = new ObjectMapper();
                RequestBody requestBody = RequestBody.create(
                        okhttp3.MediaType.parse("application/json"),
                        objectMapper.writeValueAsString(updatePayload)
                );
                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build();
                Call call = httpClient.newCall(request);
                Response response = call.execute();
                responses.add(response);
            }
        }
        for (Response response : responses) {
//            System.out.println(response.code());
            if (response.code() != 200) {
                throw new RuntimeException("Error while propagating update");
            }
        }
    }
}
