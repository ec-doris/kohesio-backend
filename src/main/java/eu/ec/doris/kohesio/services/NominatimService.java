package eu.ec.doris.kohesio.services;

import eu.ec.doris.kohesio.payload.Coordinate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NominatimService {
    private static final Logger logger = LoggerFactory.getLogger(NominatimService.class);

    public Coordinate getCoordinatesFromTown(String town) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String urlTemplate = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("q", town)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .encode().toUriString();
        logger.info("URL: {}", urlTemplate);

        HttpEntity<String> response = new RestTemplate().exchange(
                urlTemplate,
                HttpMethod.GET, entity, String.class
        );
        logger.info("Response: {}", response.getBody());
        try {
            JSONArray jsonArray = (JSONArray) new JSONParser().parse(response.getBody());
            if (jsonArray.isEmpty()) {
                return null;
            }
            Coordinate coordinate = new Coordinate(
                    Double.parseDouble(((JSONObject) jsonArray.get(0)).get("lat").toString()),
                    Double.parseDouble(((JSONObject) jsonArray.get(0)).get("lon").toString())
            );
            logger.info("Coordinate of the location asked: {}", coordinate);
            return coordinate;
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
