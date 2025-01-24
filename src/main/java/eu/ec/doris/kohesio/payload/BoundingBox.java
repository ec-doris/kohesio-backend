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
        double latDiff = this.getNorth() - this.getSouth();
        double lngDiff = this.getEast() - this.getWest();

        double maxDiff = Math.max(lngDiff, latDiff);
        if (maxDiff < 360 / Math.pow(2, 20)) {
            return 18;
        } else {
            logger.info("zoom partial {}", (-1 * ((Math.log(maxDiff) / Math.log(2)) - (Math.log(360) / Math.log(2)))));
            return Math.max((int) (-1 * ((Math.log(maxDiff) / Math.log(2)) - (Math.log(360) / Math.log(2)))), 1) + 3;
        }
    }

    public int getZoomLevel2() {
        double east = this.getEast();
        double west = this.getWest();
        double north = this.getNorth();
        double south = this.getSouth();

        // Calculate longitude difference, adjusting for antimeridian crossing
        double lngDiff = east - west;
        if (lngDiff < 0) {
            lngDiff += 360;
        }

        // Handle edge case where the bounding box spans the entire globe
        if (lngDiff >= 360 || (north - south) >= 180) {
            return 1; // Minimum zoom
        }

        // Calculate zoom based on longitude
        double zoomX = Math.log(360.0 / lngDiff) / Math.log(2);

        // Calculate Mercator-projected latitude difference
        double yNorth = latToMercator(north);
        double ySouth = latToMercator(south);
        double mercatorLatDiff = Math.abs(yNorth - ySouth);

        // Avoid division by zero in zoom calculation
        if (mercatorLatDiff <= 1e-10) {
            mercatorLatDiff = 1e-10;
        }

        // Calculate zoom based on latitude
        double zoomY = Math.log((2 * Math.PI) / mercatorLatDiff) / Math.log(2);

        // Use the limiting zoom (smaller of the two)
        double zoom = Math.min(zoomX, zoomY);

        // Apply adjustments similar to original code (+3) and clamp values
        int finalZoom = (int) Math.floor(zoom);
        finalZoom = Math.max(finalZoom, 1) + 3; // Ensure minimum zoom of 1+3=4
        finalZoom = Math.min(finalZoom, 18);    // Maximum zoom level

        return finalZoom;
    }

    public int getZoomLevel3() {
        double east = this.getEast();
        double west = this.getWest();
        double north = this.getNorth();
        double south = this.getSouth();

        // Calculate longitude difference, handling antimeridian
        double lngDiff = east - west;
        if (lngDiff < 0) lngDiff += 360;

        // Global coverage edge case
        if (lngDiff >= 360 || (north - south) >= 180) return 1;

        // Viewport adjustment for 2048px width/height (3 = log2(2048/256))
        final double VIEWPORT_ADJUSTMENT = 3;

        // Calculate longitude-based zoom with adjustment
        double zoomX = (Math.log(360.0 / lngDiff) / Math.log(2)) + VIEWPORT_ADJUSTMENT;

        // Calculate latitude-based zoom with Mercator projection
        double yNorth = latToMercator(north);
        double ySouth = latToMercator(south);
        double mercatorLatDiff = Math.abs(yNorth - ySouth);
        if (mercatorLatDiff < 1e-10) mercatorLatDiff = 1e-10;
        double zoomY = (Math.log(2 * Math.PI / mercatorLatDiff) / Math.log(2)) + VIEWPORT_ADJUSTMENT;

        // Use the more constrained zoom (min of X/Y zooms)
        double zoom = Math.min(zoomX, zoomY);

        // Finalize zoom level with clamping
        int finalZoom = (int) Math.floor(zoom);
        finalZoom = Math.max(finalZoom, 1);  // Minimum zoom 1
        finalZoom = Math.min(finalZoom, 18); // Maximum zoom 18

        return finalZoom;
    }

    public int getZoomLevel4() {
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
        int finalZoom = (int) Math.floor(zoom + 0.5); // Round to nearest integer
        finalZoom = Math.max(0, Math.min(finalZoom, 18)); // Standard Leaflet zoom range

        return finalZoom;
    }

    private double latToMercator(double latDegrees) {
        double lat = Math.toRadians(latDegrees);
        return Math.log(Math.tan(lat / 2 + Math.PI / 4));
    }
}
