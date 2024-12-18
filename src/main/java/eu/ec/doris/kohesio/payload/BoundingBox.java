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

    //    public String getCenterBBox(BoundingBox bbox) throws ParseException {
//        Point point = wktReader.read(this.geo).getCentroid();
//        Geometry bboxGeom = wktReader.read(bbox.toWkt());
//        Point center = bboxGeom.getCentroid();
//        Geometry line = wktReader.read("LINESTRING(" + point.getX() + " " + point.getY() + "," + center.getX() + " " + center.getY() + ")");
//        // find the intersection between the center of the bbox and the point
//        Geometry intersection = line.intersection(bboxGeom);
//        logger.info("Intersection: {}", intersection.toText());
//
//        Coordinate coordinate = intersection.getCentroid().getCoordinate();
//        // lat,lng
//        return coordinate.x + "," + coordinate.y;
//    }
    public int getZoomLevel() {
        double latDiff = this.getNorth() - this.getSouth();
        double lngDiff = this.getEast() - this.getWest();

        double maxDiff = Math.max(lngDiff, latDiff);
        if (maxDiff < 360 / Math.pow(2, 20)) {
            return 18;
        } else {
            return Math.max((int) (-1 * ((Math.log(maxDiff) / Math.log(2)) - (Math.log(360) / Math.log(2)))), 1) + 3;
        }
    }
//    private static final int WORLD_DIM_HEIGHT = 256;
//    private static final int WORLD_DIM_WIDTH = 256;
//    private static final int MAX_ZOOM = 18;
//
//    private double latRad(double lat) {
//        double sin = Math.sin(lat * Math.PI / 180);
//        double radX2 = Math.log((1 + sin) / (1 - sin)) / 2;
//        return Math.max(Math.min(radX2, Math.PI), -Math.PI) / 2;
//    }
//
//    private double zoom(double mapPx, double worldPx, double fraction) {
//        return Math.floor(Math.log(mapPx / worldPx / fraction) / Math.log(2));
//    }
//    public int guessZoom() {
//        Coordinate ne = this.getNorthEast();
//        Coordinate sw = this.getSouthWest();
//
//        double latFraction = (latRad(ne.getLat()) - latRad(sw.getLat())) / Math.PI;
//
//        double lngDiff = ne.getLng() - sw.getLng();
//        double lngFraction = ((lngDiff < 0) ? (lngDiff + 360) : lngDiff) / 360;
//
//        double latZoom = zoom(mapDim.height, WORLD_DIM_HEIGHT, latFraction);
//        double lngZoom = zoom(mapDim.width, WORLD_DIM_WIDTH, lngFraction);
//
//        return (int) Math.min(Math.min(latZoom, lngZoom), MAX_ZOOM);
//    }
//
//    public int calculateZoom() {
//        // Earth’s circumference in meters at the equator
//        final double EARTH_CIRCUMFERENCE = 40075017;
//
//        // Map tile size in pixels (default is 256 for many mapping APIs)
//        final int TILE_SIZE = 256;
//
//        // Calculate the width and height of the bounding box in meters
//        double latDifference = Math.abs(this.getNorth() - this.getSouth());
//        double lngDifference = Math.abs(this.getEast() - this.getWest());
//
//        // Calculate the resolution needed in meters per pixel
//        double latResolution = (EARTH_CIRCUMFERENCE / Math.pow(2, 0)) * latDifference / 360.0;
//        double lngResolution = (EARTH_CIRCUMFERENCE / Math.pow(2, 0)) * lngDifference / 360.0;
//
//        // Adjust the resolutions to fit the map dimensions
//        double latZoom = Math.log(800. / TILE_SIZE / latResolution) / Math.log(2);
//        double lngZoom = Math.log(640. / TILE_SIZE / lngResolution) / Math.log(2);
//
//        // Use the smaller zoom level to ensure the bounding box fits in the map
//        int zoomLevel = (int) Math.floor(Math.min(latZoom, lngZoom));
//        System.err.println(zoomLevel);
//        return Math.max(0, zoomLevel); // Ensure zoom level is non-negative
//    }
}
