package eu.ec.doris.kohesio.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.ec.doris.kohesio.payload.SimilarWord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

@Service
public class SimilarityService {

    public String expandQuery(String query){
        String[] words = query.split(" ");
        StringBuilder keywordsBuilder = new StringBuilder();
        keywordsBuilder.append("(");
        for (int i = 0; i < words.length; i++) {
            if(i < words.length -1){
                keywordsBuilder.append(words[i]).append(" AND ");
            }else{
                keywordsBuilder.append(words[i]);
            }
        }

        keywordsBuilder.append(")").append(" OR \"").append(query).append("\"^2");

        ArrayList<SimilarWord> similarWords = getSimilarWords(query);
        for (SimilarWord word: similarWords) {
            keywordsBuilder.append(" OR \"").append(word.getWord()).append("\"^").append(word.getScore());
        }
        String expandedQuery = keywordsBuilder.toString();
        return expandedQuery;
    }
    /*
    Query the similarity API and get a list of similar words
     */
    private ArrayList<SimilarWord> getSimilarWords(String text){
        String url = "http://similarity.cnect.eu:3000/similarity?text="+text;
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
                    double score  = hits.get(i).get("score").doubleValue();
                    similarWords.add(new SimilarWord(word.replace("_"," "),score));
                }
            } else {
                System.err.println("Error in HTTP request!");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return similarWords;
    }

}
