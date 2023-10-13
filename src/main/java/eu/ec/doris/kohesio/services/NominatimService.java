package eu.ec.doris.kohesio.services;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NominatimService {
    public class Coordinates {
        String latitude;
        String longitude;

        public Coordinates(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLatitude() {
            return latitude;
        }

        public void setLatitude(String latitude) {
            this.latitude = latitude;
        }

        public String getLongitude() {
            return longitude;
        }

        public void setLongitude(String longitude) {
            this.longitude = longitude;
        }
    }

    public Coordinates getCoordinatesFromTown(String town) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String urlTemplate = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("q", town)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .encode().toUriString();
        System.out.println(urlTemplate);

        HttpEntity<String> response = new RestTemplate().exchange(
                urlTemplate,
                HttpMethod.GET, entity, String.class
        );
        System.out.println(response.getBody());
        try {
            JSONArray JsonArray = (JSONArray) new JSONParser().parse(response.getBody());
            System.out.println(((JSONObject) JsonArray.get(0)).get("lat"));
            System.out.println(((JSONObject) JsonArray.get(0)).get("lon"));

            return new Coordinates(
                    ((JSONObject) JsonArray.get(0)).get("lat").toString(),
                    ((JSONObject) JsonArray.get(0)).get("lon").toString()
            );
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
