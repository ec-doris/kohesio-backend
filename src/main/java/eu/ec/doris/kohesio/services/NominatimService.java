package eu.ec.doris.kohesio.services;

import eu.ec.doris.kohesio.payload.BoundingBox;
import eu.ec.doris.kohesio.payload.Coordinate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Service
public class NominatimService {
    private static final Logger logger = LoggerFactory.getLogger(NominatimService.class);

    private JSONArray makeQuery(String town) throws IOException, ParseException {
        logger.info("Querying Nominatim for {}", town);
        String urlTemplate = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                .queryParam("q", town)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .encode().toUriString();
//        logger.info("URL: {}", urlTemplate);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(urlTemplate)
                .addHeader("Accept", "application/json")
                .build();
        okhttp3.Response response = null;
        response = new okhttp3.OkHttpClient().newCall(request).execute();
        if (response.code() == 200) {
            return (JSONArray) new JSONParser().parse(response.body().charStream());
        }
        return null;
    }

    public BoundingBox getBboxFromTown(String town) throws IOException, ParseException {
        JSONArray jsonArray = makeQuery(town);
        if (jsonArray != null && jsonArray.isEmpty()) {
            return null;
        }
        JSONArray bbox = (JSONArray) ((JSONObject) jsonArray.get(0)).get("boundingbox");
        return new BoundingBox(
                Double.parseDouble((String) bbox.get(0)),
                Double.parseDouble((String) bbox.get(2)),
                Double.parseDouble((String) bbox.get(1)),
                Double.parseDouble((String) bbox.get(3))
        );
    }

    public Coordinate getCoordinatesFromTown(String town) throws IOException, ParseException {
        JSONArray jsonArray = makeQuery(town);
        if (jsonArray != null && jsonArray.isEmpty()) {
            return null;
        }
        return new Coordinate(
                Double.parseDouble(((JSONObject) jsonArray.get(0)).get("lat").toString()),
                Double.parseDouble(((JSONObject) jsonArray.get(0)).get("lon").toString())
        );
    }
}
