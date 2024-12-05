/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.yeo.javasupercluster;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * @author yeozkaya@gmail.com
 */
public class MainCluster {

    protected double x;
    protected double y;
    protected int zoom;
    protected Integer numPoints;
    protected int parentId;
    protected Integer index;
    protected Integer id;
    protected Properties properties;
    protected Integer clusterIndex;

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public Integer getNumPoints() {
        return numPoints;
    }

    public void setNumPoints(Integer numPoints) {
        this.numPoints = numPoints;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Integer getClusterIndex() {
        return clusterIndex;
    }

    public void setClusterIndex(Integer clusterIndex) {
        this.clusterIndex = clusterIndex;
    }

    @Override
    public String toString() {
        return "MainCluster{" +
                "x=" + x +
                ", y=" + y +
                ", zoom=" + zoom +
                ", numPoints=" + numPoints +
                ", parentId=" + parentId +
                ", index=" + index +
                ", id=" + id +
                ", properties=" + properties +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MainCluster cluster = (MainCluster) o;
        return Double.compare(x, cluster.x) == 0
                && Double.compare(y, cluster.y) == 0
                && zoom == cluster.zoom
                && parentId == cluster.parentId
                && Objects.equals(numPoints, cluster.numPoints)
                && Objects.equals(index, cluster.index)
                && Objects.equals(id, cluster.id)
                && Objects.equals(properties, cluster.properties)
                && Objects.equals(clusterIndex, cluster.clusterIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, zoom, numPoints, parentId, index, id, properties, clusterIndex);
    }
}
