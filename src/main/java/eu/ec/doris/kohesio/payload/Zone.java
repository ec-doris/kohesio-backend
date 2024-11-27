package eu.ec.doris.kohesio.payload;

import eu.ec.doris.kohesio.controller.MapController;
import eu.ec.doris.kohesio.services.SPARQLQueryService;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

public class Zone {
    private static final WKTReader wktReader = new WKTReader();
    private static final Logger logger = LoggerFactory.getLogger(Zone.class);

    String uri;
    String lid;
    String geo;
    String type;
    int numberProjects;

    public Zone(String uri, String lid, String geo, String type) {
        this.uri = uri;
        this.lid = lid;
        this.geo = geo;
        this.type = type;
        this.numberProjects = 0;
    }

    public String getUri() {
        return uri;
    }

    public String getLid() {
        return lid;
    }

    public String getGeo() {
        return geo;
    }

    public String getType() {
        return type;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setLid(String lid) {
        this.lid = lid;
    }

    public void setGeo(String geo) {
        this.geo = geo;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getNumberProjects() {
        return numberProjects;
    }

    public void setNumberProjects(int numberProjects) {
        this.numberProjects = numberProjects;
    }

    public void queryNumberProjects(SPARQLQueryService sparqlQueryService, String sparqlEndpoint, String search, int timeout) throws Exception {
        String query = "SELECT (COUNT(DISTINCT ?s0) AS ?c ) WHERE { ";
//                    + " ?s0 <https://linkedopendata.eu/prop/direct/P35> <https://linkedopendata.eu/entity/Q9934> . ";
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
