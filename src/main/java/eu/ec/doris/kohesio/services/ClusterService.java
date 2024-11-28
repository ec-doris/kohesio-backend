package eu.ec.doris.kohesio.services;

import com.yeo.javasupercluster.MainCluster;
import com.yeo.javasupercluster.PointCluster;
import com.yeo.javasupercluster.SuperCluster;
import eu.ec.doris.kohesio.payload.BoundingBox;
import eu.ec.doris.kohesio.payload.Coordinate;
import org.eclipse.rdf4j.query.algebra.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.wololo.geojson.*;

import java.util.*;

@Service
public class ClusterService {
    private static final Logger logger = LoggerFactory.getLogger(ClusterService.class);
//    private static final GeoJSONFactory reader = new GeoJSONFactory();

    public GeoJSON parseGeoJson(String geojson) {
        return GeoJSONFactory.create(geojson);
    }

    public List<Feature> getCluster(String geoJSONText, int radius, int extent, int minzoom, int maxzoom, int nodesize, BoundingBox bbox, int zoom) {
        return getCluster(parseGeoJson(geoJSONText), radius, extent, minzoom, maxzoom, nodesize, bbox, zoom);
    }

    public List<Feature> getCluster(GeoJSON geoJSON, int radius, int extent, int minzoom, int maxzoom, int nodesize, BoundingBox bbox, int zoom) {
        Feature[] features;
        if (geoJSON instanceof Feature) {
            features = new Feature[]{(Feature) geoJSON};
        } else if (geoJSON instanceof FeatureCollection) {
            features = ((FeatureCollection) geoJSON).getFeatures();
        } else {
            throw new IllegalArgumentException("Invalid GeoJSON object");
        }
        return getCluster(features, radius, extent, minzoom, maxzoom, nodesize, bbox, zoom);
    }

    public List<Feature> getClusterWithDefault(Feature[] features, BoundingBox bbox, int zoom) {
        return getCluster(features, 60, 256, 0, 17, 64, bbox, zoom);
    }

    public List<Feature> getCluster(Feature[] features, int radius, int extent, int minzoom, int maxzoom, int nodesize, BoundingBox bbox, int zoom) {
        logger.info("Creating cluster with {} features", features.length);
        SuperCluster superCluster = new SuperCluster(radius, extent, minzoom, maxzoom, nodesize, features);
        return clearDuplicate(superCluster.getClusters(bbox.getBounds(), zoom));
    }

    public List<Feature> getPointsInCluster(List<Feature> features, Coordinate coords, BoundingBox bbox, int zoom) {
        return getPointsInCluster(features.toArray(new Feature[]{}), coords, bbox, zoom);
    }

    public List<Feature> getPointsInCluster(Feature[] features, Coordinate coords, BoundingBox bbox, int zoom) {
        return getPointsInCluster(features, 60, 256, 0, 17, 64, bbox, zoom, coords);
    }

    public List<Feature> getPointsInCluster(Feature[] features, int radius, int extent, int minzoom, int maxzoom, int nodesize, BoundingBox bbox, int zoom, Coordinate coords) {
        logger.info("Getting point in cluster with {} features", features.length);
        SuperCluster superCluster = new SuperCluster(radius, extent, minzoom, maxzoom, nodesize, features);
        List<MainCluster> mcl = superCluster.findClusters(coords.coords(), zoom);
        Set<Feature> points = new HashSet<>();
        mcl.forEach(mainCluster -> {
            points.addAll(superCluster.getPointFromCluster(mainCluster));
        });
        logger.info("Found {} pt(s) at cluster {} with zoom {}", points.size(), coords, zoom);
        return new ArrayList<>(points);
    }

    private List<Feature> clearDuplicate(List<Feature> features) {
        HashMap<String, Feature> results = new HashMap<>();
        features.forEach(feature -> {
            if (!results.containsKey(feature.getGeometry().toString())) {
                results.put(feature.getGeometry().toString(), feature);
            }
        });
        List<Feature> res = new ArrayList<>();
        results.forEach((s, feature) -> res.add(feature));
        return res;
    }
}
