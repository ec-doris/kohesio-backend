package eu.ec.doris.kohesio.payload;

import eu.ec.doris.kohesio.services.SPARQLQueryService;
import lombok.Data;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
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

    public void queryNumberProjects(SPARQLQueryService sparqlQueryService, String sparqlEndpoint, String search, int timeout) throws Exception {
        String query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE { ";
//        query += " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";
//        query += "FILTER EXISTS { " + search + " }";
        query += search;
        if ("LAU".equals(this.type)) {
            query += " ?s0 <https://linkedopendata.eu/prop/direct/P581472> <" + this.lid + "> . ";
        } else if ("COUNTRY".equals(this.type)) {
            query += " ?s0 <https://linkedopendata.eu/prop/direct/P32> <" + this.lid + "> . ";
            timeout = 300;
            query = query.replace("?s0 <https://linkedopendata.eu/prop/direct/P127> ?coordinates .", "");
        } else {
            query += " ?s0 <https://linkedopendata.eu/prop/direct/P1845> <" + this.lid + "> . ";
        }
        query += " }";
        TupleQueryResult resultSet = sparqlQueryService.executeAndCacheQuery(
                sparqlEndpoint,
                query,
                timeout,
                "map2"
        );
        if (resultSet == null) {
            numberProjects = -1;
            logger.info("Skipping {} because count failed but we want to test", this.lid);
            return;
        }
        if (resultSet.hasNext()) {
            BindingSet querySolution = resultSet.next();
            numberProjects = ((Literal) querySolution.getBinding("c").getValue()).intValue();
        }
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
