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
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        List<MonolingualString> descriptionsRaw = updatePayload.getDescriptionsRaw();
        String instagramUsername = updatePayload.getInstagramUsername();
        String twitterUsername = updatePayload.getTwitterUsername();
        String facebookUserId = updatePayload.getFacebookUserId();
        String youtubeVideoId = updatePayload.getYoutubeUserId();
        String imageUrl = updatePayload.getImageUrl();
        MonolingualString imageSummary = updatePayload.getImageSummary();
        String imageCopyright = updatePayload.getImageCopyright();

        logger.info("Project update by ID: id {} on {}", id, url);

        String queryCheck = "ASK { <"
                + id
                + "> <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> }";
        boolean resultAsk = sparqlQueryService.executeBooleanQuery(
                url,
                queryCheck,
                false,
                2
        );
        if (!resultAsk) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bad Request - project ID not found or is not a project"
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

            if (descriptionsRaw != null) {
                for (MonolingualString descriptionRawObject : descriptionsRaw) {
                    String language = descriptionRawObject.getLanguage();
                    String descriptionRaw = descriptionRawObject.getText();

                    StringBuilder tripleToDelete = new StringBuilder();
                    StringBuilder tripleToInsert = new StringBuilder();
                    StringBuilder tripleToWhere = new StringBuilder();
                    if (descriptionRaw != null) {
                        descriptionRaw = descriptionRaw.replace("\"", "\\\"");
                        tripleToDelete
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P589596> ?description_raw_")
                                .append(language)
                                .append(" . ")
                        ;
                        tripleToWhere
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P589596> ?description_raw_")
                                .append(language)
                                .append(" . FILTER (LANG(?description_raw_")
                                .append(language)
                                .append(")")
                                .append(" = \"")
                                .append(language)
                                .append("\") ")
                        ;
                        tripleToInsert
                                .append(" <")
                                .append(id)
                                .append("> <https://linkedopendata.eu/prop/direct/P589596> \"")
                                .append(descriptionRaw)
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

            // for instagram, facebook, twitter, youtube and image
            // if null we skip if "" empty string we delete and if value we delete and setup the new value
            if (instagramUsername != null) {
                generateUpdateTriplesForValue(id, instagramUsername, updateTriples, "P478");
            }
            if (facebookUserId != null) {
                generateUpdateTriplesForValue(id, facebookUserId, updateTriples, "P407");
            }
            if (twitterUsername != null) {
                generateUpdateTriplesForValue(id, twitterUsername, updateTriples, "P241");
            }
            if (youtubeVideoId != null) {
                generateUpdateTriplesForValue(id, youtubeVideoId, updateTriples, "P2210");
            }

            // handle image:
            // we want to keep the qualifier if they exist and aren't updated
            // and we want to update only the qualifier if only that is provided
            if (imageCopyright != null || imageUrl != null || imageSummary != null) {
                generateUpdateTriplesForImage(id, imageUrl, imageCopyright, imageSummary, updateTriples);
            }
            logger.info("Updating {} triples", updateTriples.size());
            if (updateTriples.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request - nothing to update"
                );
            }
            for (UpdateTriple updateTriple : updateTriples) {
                String queryDelete = updateTriple.getDeleteQuery();
                String queryInsert = updateTriple.getInsertQuery();
                if (queryDelete != null && !queryDelete.isEmpty()) {
//                    logger.info("Executing delete query: {}", queryDelete);
                    sparqlQueryService.executeUpdateQuery(url, queryDelete, 20);
                }
                if (queryInsert != null && !queryInsert.isEmpty()) {
//                    logger.info("Executing Insert query: {}", queryInsert);
                    sparqlQueryService.executeUpdateQuery(url, queryInsert, 20);
                }
            }

            return new ResponseEntity<>(
                    (JSONObject) (new JSONObject().put("message", "entity updated")),
                    HttpStatus.OK
            );
        }
    }

    private void generateUpdateTriplesForImage(
            String id,
            String imageUrl,
            String imageCopyright,
            MonolingualString imageSummary,
            List<UpdateTriple> updateTriples
    ) {
        String query = "SELECT ?image ?image_url ?image_summary ?image_copyright WHERE {"
                + " <" + id + "> <https://linkedopendata.eu/prop/P851> ?image ."
                + " ?image <https://linkedopendata.eu/prop/statement/P851> ?image_url."
                + " OPTIONAL{?image <https://linkedopendata.eu/prop/qualifier/P836> ?image_summary.";
        if (imageSummary != null) {
            query += " FILTER(LANG(?image_summary)=\"" + imageSummary.getLanguage() + "\")";
        }
        query += "}"
                + " OPTIONAL{?image <https://linkedopendata.eu/prop/qualifier/P1743> ?image_copyright.}"
                + "}";
        TupleQueryResult result = sparqlQueryService.executeAndCacheQuery(
                sparqlEndpoint,
                query,
                20,
                false,
                "update"
        );
        if (result.hasNext()) {
            while (result.hasNext()) {
                BindingSet querySolution = result.next();
                String oldImageStatement = querySolution.getBinding("image").getValue().stringValue();
                String oldImageUrl = querySolution.getBinding("image_url").getValue().stringValue();
                String oldImageCopyright = querySolution.getBinding("image_copyright").getValue().stringValue();
                MonolingualString oldImageSummary = null;
                if (querySolution.hasBinding("image_summary")) {
                    oldImageSummary = new MonolingualString(
                            ((Literal) querySolution.getBinding("image_summary").getValue()).getLanguage().get(),
                            querySolution.getBinding("image_summary").getValue().stringValue()
                    );
                }

                if (imageUrl != null && !oldImageUrl.equals(imageUrl)) {
                    // update ps:P851 and wdt:P851
                    String tripleImageUrlToDelete = "<" + oldImageStatement + ">"
                            + " <https://linkedopendata.eu/prop/statement/P851> "
                            + "\"" + oldImageUrl + "\" . ";
                    String tripleImageUrlToWhere = "<" + id + ">"
                            + " <https://linkedopendata.eu/prop/P851> "
                            + "<" + oldImageStatement + "> . ";
                    String tripleImageUrlToInsert = null;
                    if (!imageUrl.isEmpty()) {
                        tripleImageUrlToInsert = "<" + oldImageStatement + "> "
                                + "<https://linkedopendata.eu/prop/statement/P851>"
                                + " \"" + imageUrl + "\" . ";
                    }

                    updateTriples.add(
                            new UpdateTriple(
                                    tripleImageUrlToDelete,
                                    tripleImageUrlToInsert,
                                    tripleImageUrlToWhere
                            )
                    );
                    String tripleImageUrlDirectToDelete = "<" + id + ">"
                            + " <https://linkedopendata.eu/prop/direct/P851> "
                            + "\"" + oldImageUrl + "\" . ";
                    String tripleImageUrlDirectToWhere = "<" + id + ">"
                            + " <https://linkedopendata.eu/prop/direct/P851> "
                            + "<" + oldImageUrl + "> . ";
                    String tripleImageUrlDirectToInsert = null;
                    if (!imageUrl.isEmpty()) {
                        tripleImageUrlDirectToInsert = "<" + id + "> "
                                + "<https://linkedopendata.eu/prop/direct/P851>"
                                + " \"" + imageUrl + "\" . ";
                    }
                    updateTriples.add(
                            new UpdateTriple(
                                    tripleImageUrlDirectToDelete,
                                    tripleImageUrlDirectToInsert,
                                    tripleImageUrlDirectToWhere
                            )
                    );
                }
                if (imageSummary != null && (oldImageSummary == null || !oldImageSummary.equals(imageSummary))) {
                    String tripleImageToDelete = null;
                    String tripleImageToWhere = null;
                    String tripleImageToInsert = null;
                    if (oldImageSummary != null) {
                        tripleImageToDelete = "<" + oldImageStatement + ">"
                                + " <https://linkedopendata.eu/prop/qualifier/P836> "
                                + "\"" + oldImageSummary + "\" . ";
                        tripleImageToWhere = "<" + id + ">"
                                + " <https://linkedopendata.eu/prop/P851> "
                                + "<" + oldImageStatement + "> . ";
                    }
                    if (!imageSummary.getText().isEmpty()) {
                        tripleImageToInsert = "<" + oldImageStatement + "> "
                                + "<https://linkedopendata.eu/prop/qualifier/P836>"
                                + " " + imageSummary.toValue() + " . ";
                    }
                    updateTriples.add(
                            new UpdateTriple(
                                    tripleImageToDelete,
                                    tripleImageToInsert,
                                    tripleImageToWhere
                            )
                    );
                }

                if (imageCopyright != null && !oldImageCopyright.equals(imageCopyright)) {
                    String tripleImageToDelete = "<" + oldImageStatement + ">"
                            + " <https://linkedopendata.eu/prop/qualifier/P1743> "
                            + "\"" + oldImageCopyright + "\" . ";
                    String tripleImageToWhere = "<" + id + ">"
                            + " <https://linkedopendata.eu/prop/P851> "
                            + "<" + oldImageStatement + "> . ";
                    String tripleImageToInsert = null;
                    if (!imageCopyright.isEmpty()) {
                        tripleImageToInsert = "<" + oldImageStatement + "> "
                                + "<https://linkedopendata.eu/prop/qualifier/P1743>"
                                + " \"" + imageCopyright + "\" . ";
                    }
                    updateTriples.add(
                            new UpdateTriple(
                                    tripleImageToDelete,
                                    tripleImageToInsert,
                                    tripleImageToWhere
                            )
                    );
                }
            }
        } else {
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request - No image to edit"
                );
            }
            // this mean there wasn't any image before so we have to create all the triples ourselves
            String fakeStatement = id + "-image-P851-" + UUID.randomUUID();
            String tripleImageUrl = "<" + id + "> <https://linkedopendata.eu/prop/direct/P851> \"" + imageUrl + "\" . "
                    + "<" + id + "> <https://linkedopendata.eu/prop/P851> <" + fakeStatement + "> . "
                    + "<" + fakeStatement + "> <https://linkedopendata.eu/prop/statement/P851> \"" + imageUrl + "\" . ";
            if (imageSummary != null && !imageSummary.getText().isEmpty()) {
                tripleImageUrl += "<" + fakeStatement + "> <https://linkedopendata.eu/prop/qualifier/P836> " + imageSummary.toValue() + " . ";
            }
            if (imageCopyright != null && !imageCopyright.isEmpty()) {
                tripleImageUrl += "<" + fakeStatement + "> <https://linkedopendata.eu/prop/qualifier/P1743> \"" + imageCopyright + "\" . ";
            }
            updateTriples.add(
                    new UpdateTriple(
                            null,
                            tripleImageUrl,
                            null
                    )
            );

        }
    }

    private static void generateUpdateTriplesForValue(
            String id,
            String instagramUsername,
            List<UpdateTriple> updateTriples,
            String pid
    ) {
        StringBuilder tripleToDelete = new StringBuilder();
        StringBuilder tripleToInsert = new StringBuilder();
        StringBuilder tripleToWhere = new StringBuilder();

        tripleToDelete
                .append("<")
                .append(id)
                .append("> <https://linkedopendata.eu/prop/direct/")
                .append(pid)
                .append("> ?instagramUsername . ");

        tripleToWhere
                .append("<")
                .append(id)
                .append("> <https://linkedopendata.eu/prop/direct/")
                .append(pid)
                .append("> ?instagramUsername . ");
        if (!instagramUsername.isEmpty()) {
            tripleToInsert
                    .append("<")
                    .append(id)
                    .append("> <https://linkedopendata.eu/prop/direct/P478")
                    .append(pid)
                    .append("> \"")
                    .append(instagramUsername)
                    .append("\" . ")
            ;
        }
        updateTriples.add(
                new UpdateTriple(
                        tripleToDelete.toString(),
                        tripleToInsert.toString(),
                        tripleToWhere.toString()
                )
        );
    }

    @PostMapping(value = "/project", produces = "application/json")
    public ResponseEntity<JSONObject> propagateUpdateProject(
            @RequestBody Update updatePayload
    ) throws IOException, ApiException {
        logger.info("Propagate update project {}", updatePayload.getId());
        logger.info(updatePayload.toString());
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
                        logger.info("IP: {} phase: {} port: {}", ip, phase, port);
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

    private static class UpdateTriple {
        String tripleToDelete;
        String tripleToInsert;
        String tripleToWhere;

        public UpdateTriple(String tripleToDelete, String tripleToInsert, String tripleToWhere) {
            this.tripleToDelete = tripleToDelete;
            this.tripleToInsert = tripleToInsert;
            this.tripleToWhere = tripleToWhere;
        }

        public String getDeleteQuery() {
            if (tripleToDelete == null || tripleToDelete.isEmpty()) {
                return null;
            }
            return "DELETE {" + tripleToDelete + "}"
                    + " WHERE { "
                    + tripleToWhere
                    + " }";
        }

        public String getInsertQuery() {
            if (tripleToInsert == null || tripleToInsert.isEmpty()) {
                return null;
            }
            return "INSERT DATA {" + tripleToInsert + "}";
        }

        public String getTripleToDelete() {
            return tripleToDelete;
        }

        public void setTripleToDelete(String tripleToDelete) {
            if (tripleToDelete.isEmpty()) {
                this.tripleToDelete = null;
            } else {
                this.tripleToDelete = tripleToDelete;
            }
        }

        public String getTripleToInsert() {
            return tripleToInsert;
        }

        public void setTripleToInsert(String tripleToInsert) {
            if (tripleToInsert.isEmpty()) {
                this.tripleToInsert = null;
            } else {
                this.tripleToInsert = tripleToInsert;
            }
        }

        public String getTripleToWhere() {
            return tripleToWhere;
        }

        public void setTripleToWhere(String tripleToWhere) {
            this.tripleToWhere = tripleToWhere;
        }
    }
}
