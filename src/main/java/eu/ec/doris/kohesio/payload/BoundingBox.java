package eu.ec.doris.kohesio.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BoundingBox {
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
        return "POLYGON((" + southWest.getLng() + " " + southWest.getLat() + "," +
                northEast.getLng() + " " + southWest.getLat() + "," +
                northEast.getLng() + " " + northEast.getLat() + "," +
                southWest.getLng() + " " + northEast.getLat() + "," +
                southWest.getLng() + " " + southWest.getLat() + "))";
    }

    public String toLiteral() {
        return "\"" + this.toWkt() + "\"^^<http://www.opengis.net/ont/geosparql#wktLiteral>";
    }

    public String toString() {
        return toWkt();
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
}
