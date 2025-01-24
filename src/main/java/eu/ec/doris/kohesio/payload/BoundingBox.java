package eu.ec.doris.kohesio.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoundingBox {
    private static final Logger logger = LoggerFactory.getLogger(BoundingBox.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Coordinate southWest;
    private Coordinate northEast;

    public static BoundingBox createFromJson(String boundingBoxString) throws JsonProcessingException {
        return objectMapper.readValue(boundingBoxString, BoundingBox.class);
    }

    public static BoundingBox createFromDouble(double[] bbox) throws JsonProcessingException {
        return new BoundingBox(bbox[1], bbox[0], bbox[3], bbox[2]);
    }

    public static BoundingBox createFromString(String boundingBoxString) throws JsonProcessingException {
        if (boundingBoxString.startsWith("{")) {
            return BoundingBox.createFromJson(boundingBoxString);
        }
        String[] parts = boundingBoxString.split(",");
        double[] bbox = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bbox[i] = Double.parseDouble(parts[i]);
        }
        return BoundingBox.createFromDouble(bbox);
    }

    public BoundingBox() {
    }

    public BoundingBox(Envelope envelope) {
        this.southWest = new Coordinate(envelope.getMinY(), envelope.getMinX());
        this.northEast = new Coordinate(envelope.getMaxY(), envelope.getMaxX());
    }

    @JsonCreator
    public BoundingBox(@JsonProperty("_southWest") Coordinate southWest, @JsonProperty("_northEast") Coordinate northEast) {
        this.southWest = southWest;
        this.northEast = northEast;
    }

    public BoundingBox(double south, double west, double north, double east) {
        this.southWest = new Coordinate(south, west);
        this.northEast = new Coordinate(north, east);
    }

    public Coordinate getSouthWest() {
        return southWest;
    }

    public void setSouthWest(Coordinate southWest) {
        this.southWest = southWest;
    }

    public Coordinate getNorthEast() {
        return northEast;
    }

    public double getNorth() {
        return northEast.getLat();
    }

    public double getSouth() {
        return southWest.getLat();
    }

    public double getEast() {
        return northEast.getLng();
    }

    public double getWest() {
        return southWest.getLng();
    }

    public void setNorthEast(Coordinate northEast) {
        this.northEast = northEast;
    }

    public double[] getBounds() {
        return new double[]{
                getWest(),
                getSouth(),
                getEast(),
                getNorth(),
        };
    }

    public String toBounds() {
        return getWest() + "," + getSouth() + "," + getEast() + "," + getNorth();
    }

    public String toWkt() {
        return "POLYGON((" + southWest.getLng() + " " + southWest.getLat() + ","
                + northEast.getLng() + " " + southWest.getLat() + ","
                + northEast.getLng() + " " + northEast.getLat() + ","
                + southWest.getLng() + " " + northEast.getLat() + ","
                + southWest.getLng() + " " + southWest.getLat() + "))";
    }

    public String toLiteral() {
        return "\"" + this.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>";
    }

    public String toString() {
        return toWkt();
    }

    public Geometry toGeometry() {
        org.locationtech.jts.geom.Coordinate[] coordinates = new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(this.getWest(), this.getNorth()),
                new org.locationtech.jts.geom.Coordinate(this.getEast(), this.getNorth()),
                new org.locationtech.jts.geom.Coordinate(this.getEast(), this.getSouth()),
                new org.locationtech.jts.geom.Coordinate(this.getWest(), this.getSouth()),
                new org.locationtech.jts.geom.Coordinate(this.getWest(), this.getNorth())
        };

        return new GeometryFactory().createPolygon(coordinates);
    }
    public int getZoomLevel() {
        double east = this.getEast();
        double west = this.getWest();
        double north = this.getNorth();
        double south = this.getSouth();

        // Handle longitude wrapping and global bounds
        double lngDiff = east - west;
        if (lngDiff < 0) lngDiff += 360;
        if (lngDiff >= 360 || (north - south) >= 180) return 1;

        // Convert to radians for Mercator calculations
        double northRad = Math.toRadians(north);
        double southRad = Math.toRadians(south);

        // Leaflet's Mercator Y coordinate calculation
        double y1 = 0.5 - (Math.log(Math.tan(northRad) + 1/Math.cos(northRad)) / (2 * Math.PI));
        double y2 = 0.5 - (Math.log(Math.tan(southRad) + 1/Math.cos(southRad)) / (2 * Math.PI));
        double latFraction = Math.abs(y1 - y2);

        // Calculate zoom for longitude and latitude separately
        double zoomX = Math.log(360.0 / lngDiff) / Math.log(2);
        double zoomY = Math.log(1.0 / latFraction) / Math.log(2);

        // Use the more constrained zoom (matching Leaflet's fitBounds behavior)
        double zoom = Math.min(zoomX, zoomY);

        // Apply Leaflet's zoom snapping and clamp to valid range
        int finalZoom = (int) Math.ceil(zoom + 0.5); // Round to nearest integer
        finalZoom = Math.max(0, Math.min(finalZoom, 18)); // Standard Leaflet zoom range

        return finalZoom;
    }
}
