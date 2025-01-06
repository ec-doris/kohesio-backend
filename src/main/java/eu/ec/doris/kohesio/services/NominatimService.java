package eu.ec.doris.kohesio.services;

import eu.ec.doris.kohesio.payload.Coordinate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.http.client.HttpClient;
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

import java.io.IOException;
import java.util.Collections;

@Service
public class NominatimService {
    private static final Logger logger = LoggerFactory.getLogger(NominatimService.class);

    public Coordinate getCoordinatesFromTown(String town) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String urlTemplate = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("q", town)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .encode().toUriString();
        logger.info("URL: {}", urlTemplate);

        OkHttpClient httpClient = new OkHttpClient();

        Request request =  new Request.Builder()
                .url(urlTemplate)
                .addHeader("Accept", "application/json")
                .build();
        Response response = httpClient.newCall(request).execute();
        try {
            assert response.body() != null;
            JSONArray jsonArray = (JSONArray) new JSONParser().parse(response.body().charStream());
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
