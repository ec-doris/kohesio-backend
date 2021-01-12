package eu.ec.doris.kohesio.controller.geoIp;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.util.SocketUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class GeoIp {

    DatabaseReader dbReader;

    GeoIp(@Value("${geoLite.path}") String geoLitePath) throws IOException {
        InputStream input = new FileInputStream(new File(geoLitePath+"/GeoLite2-City.mmdb"));
        dbReader = new DatabaseReader.Builder(input)
                .build();
    }

    public Coordinates compute(String ip) throws IOException, GeoIp2Exception {
        InetAddress ipAddress = InetAddress.getByName(ip);
        CityResponse response = dbReader.city(ipAddress);

        System.out.println(response.getCity());
        System.out.println(response.getCountry());

        String latitude =
                response.getLocation().getLatitude().toString();
        String longitude =
                response.getLocation().getLongitude().toString();
        Coordinates coordinates = new Coordinates(latitude,longitude);
        return coordinates;
    }

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
}
