package eu.ec.doris.kohesio.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BoundingBox {
    private Coordinate southWest;
    private Coordinate northEast;

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
