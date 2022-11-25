package eu.ec.doris.kohesio.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.ec.doris.kohesio.controller.BeneficiaryController;
import eu.ec.doris.kohesio.payload.SimilarWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;

@Service
public class SimilarityService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarityService.class);

    @Value("${similarityServiceUrl}")
    String similarityServiceUrl;

    @Value("${similarityServiceUrl.apiKey}")
    String x_api_key;

    public ExpandedQuery expandQuery(String query, String language) {
        // if the query contains any of the original patterns then we don't expand it and do a normal keyword search
        String[] patterns = {" AND ", " OR ", "NOT ", "*", "\""};
        boolean expand = true;
        for (String pattern : patterns) {
            if (query.contains(pattern)) {
                expand = false;
                break;
            }
        }
        if (expand) {
            String[] words = query.trim().split(" ");
            StringBuilder keywordsBuilder = new StringBuilder();
            keywordsBuilder.append("(");
            for (int i = 0; i < words.length; i++) {
                if (i < words.length - 1) {
                    keywordsBuilder.append(words[i].trim()).append(" AND ");
                } else {
                    keywordsBuilder.append(words[i].trim());
                }
            }

            keywordsBuilder.append(")").append(" OR \"").append(query).append("\"^2");

//            ArrayList<SimilarWord> similarWords = getSimilarWords(query, 10);
            ArrayList<SimilarWord> similarWords = getSimilarWords2(query, language);
            for (SimilarWord word : similarWords) {
                keywordsBuilder.append(" OR \"").append(word.getWord()).append("\"^").append(word.getScore());
            }
            String expandedQuery = keywordsBuilder.toString();

            return new ExpandedQuery().setExpandedQuery(expandedQuery).setKeywords(similarWords);
        } else {
            // else return the original query
            return new ExpandedQuery().setExpandedQuery(query);
        }
    }

    /*
    Query the similarity API and get a list of similar words
     */
    private ArrayList<SimilarWord> getSimilarWords(String text, int number) {
        String url = "https://similarity.cnect.eu/api?text=" + text + "&number=" + number + "&model=kohesio";
        ArrayList<SimilarWord> similarWords = new ArrayList<>();
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                ObjectNode result = (ObjectNode) root;
                ArrayNode hits = (ArrayNode) result.get("similar");
                for (int i = 0; i < hits.size(); i++) {
                    String word = hits.get(i).get("word").textValue();
                    double score = hits.get(i).get("score").doubleValue();
                    similarWords.add(new SimilarWord(word.replace("_", " "), score));
                }
            } else {
                logger.error("Error in HTTP response: " + response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return similarWords;
    }

    private ArrayList<SimilarWord> getSimilarWords2(String text, String language) {
        ArrayList<SimilarWord> similarWords = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode()
                    .put("service", "similarity")
                    .put("text", text)
                    .set("parameters", mapper.createObjectNode().put("lang", language));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("x-api-key", x_api_key);
            HttpEntity<String> request = new HttpEntity<>(mapper.writer().writeValueAsString(payload), headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(this.similarityServiceUrl, request, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                JsonNode root = mapper.readTree(response.getBody());
                ObjectNode result = (ObjectNode) root;
                if ("ok".equals(result.get("message").asText())) {
                    ArrayNode hits = (ArrayNode) result.get("result");
                    for (int i = 0; i < hits.size(); i++) {
                        String word = hits.get(i).asText();
                        similarWords.add(new SimilarWord(word.replace("_", " ")));
                    }
                }
                else{
                    logger.error("Error in HTTP response: " + response);
                }
            } else {
                logger.error("Error in HTTP response: " + response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return similarWords;
    }
}
