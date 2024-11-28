package eu.ec.doris.kohesio.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.Objects;

public class Coordinate {
    private static final double TOLERANCE = 1e-9;
    double lat;
    double lng;

    @JsonCreator
    public Coordinate(@JsonProperty("lat") double lat, @JsonProperty("lng") double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Coordinate(double[] coords) {
        this.lat = coords[1];
        this.lng = coords[0];
    }

    public Coordinate(String coordinateString) {
        String[] strings = coordinateString.split(",");
        this.lat = Double.parseDouble(strings[1]);
        this.lng = Double.parseDouble(strings[0]);
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public double[] coords() {
        return new double[]{this.lng, this.lat};
    }

    @Override
    public String toString() {
        return "Coordinate{" +
                "lat=" + lat +
                ", lng=" + lng +
                '}';
    }

    //    @Override
//    public boolean equals(Object o) {
//        if (o == null || getClass() != o.getClass()) return false;
//        Coordinate that = (Coordinate) o;
//        return Double.compare(lat, that.lat) == 0 && Double.compare(lng, that.lng) == 0;
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(lat, lng);
//    }



    // new equals and hash because of some floating point error
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return Math.abs(this.lat - that.lat) < TOLERANCE &&
                Math.abs(this.lng - that.lng) < TOLERANCE;
    }

    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(Math.round(lat / TOLERANCE) * TOLERANCE);
        long lngBits = Double.doubleToLongBits(Math.round(lng / TOLERANCE) * TOLERANCE);
        return Objects.hash(latBits, lngBits);
    }

    public org.locationtech.jts.geom.Coordinate toJts() {
        return new org.locationtech.jts.geom.Coordinate(this.getLat(), this.getLng());
    }

    public org.locationtech.jts.geom.Point toPoint() {
        return new GeometryFactory().createPoint(
                new org.locationtech.jts.geom.Coordinate(
                        this.getLat(),
                        this.getLng()
                )
        );
    }

    public String toWkt() {
        return this.toPoint().toText();
    }

    public String toLiteral() {
        return "\"" + this.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>";
    }
}
