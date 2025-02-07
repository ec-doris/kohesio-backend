package eu.ec.doris.kohesio.payload;

import lombok.Data;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

@Data
public class Zone {
    private static final WKTReader wktReader = new WKTReader();
    private static final Logger logger = LoggerFactory.getLogger(Zone.class);

    String uri;
    String lid;
    String geo;
    String type;
    Integer numberProjects;

    public Zone(String uri, String lid, String geo, String type, Integer numberProjects) {
        this.uri = uri;
        this.lid = lid;
        this.geo = geo;
        this.type = type;
        this.numberProjects = numberProjects;
    }
    public Zone(String uri, String lid, String geo, String type) {
        this(uri, lid, geo, type, null);
    }

    public Integer getNumberProjects() {
        return numberProjects;
    }

    public void setNumberProjects(Integer numberProjects) {
        this.numberProjects = numberProjects;
    }

    public String getCenter() throws org.locationtech.jts.io.ParseException {
        org.locationtech.jts.geom.Coordinate coordinate = wktReader.read(this.geo).getCentroid().getCoordinate();
        // lat,lng
        return coordinate.x + "," + coordinate.y;
    }

    public String getCenterBBox(BoundingBox bbox) throws ParseException, org.locationtech.jts.io.ParseException {
        org.locationtech.jts.geom.Point point = wktReader.read(this.geo).getCentroid();
        Geometry bboxGeom = wktReader.read(bbox.toWkt());
        org.locationtech.jts.geom.Point center = bboxGeom.getCentroid();
        Geometry line = wktReader.read(
                "LINESTRING("
                        + point.getX() + " "
                        + point.getY() + ","
                        + center.getX() + " "
                        + center.getY() + ")"
        );
        // find the intersection between the center of the bbox and the point
        Geometry intersection = line.intersection(bboxGeom);
        logger.info("Intersection: {}", intersection.toText());

        Coordinate coordinate = intersection.getCentroid().getCoordinate();
        // lat,lng
        return coordinate.x + "," + coordinate.y;
    }

    @Override
    public String toString() {
        return "Zone{" +
                "uri='" + uri + '\'' +
                ", lid='" + lid + '\'' +
//                ", geo='" + geo + '\'' +
                ", type='" + type + '\'' +
                ", numberProjects=" + numberProjects +
                '}';
    }
}
