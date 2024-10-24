package eu.ec.doris.kohesio.services;

import com.yeo.javasupercluster.SuperCluster;
import eu.ec.doris.kohesio.payload.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSON;
import org.wololo.geojson.GeoJSONFactory;

import java.util.List;

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
        return getCluster(features, 60, 256, 0, 20, 64, bbox, zoom);
    }
    public List<Feature> getCluster(Feature[] features, int radius, int extent, int minzoom, int maxzoom, int nodesize, BoundingBox bbox, int zoom) {
        logger.info("Creating cluster with {} features", features.length);
        SuperCluster superCluster = new SuperCluster(radius, extent, minzoom, maxzoom, nodesize, features);
//        logger.info("BBOX: {} | {} | zoom: {}", bbox.getBounds(), bbox.toWkt(), zoom);
        return superCluster.getClusters(bbox.getBounds(), zoom);
    }

}
