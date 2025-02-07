package eu.ec.doris.kohesio.payload;

import java.util.*;

public class Nut{
    public String uri;
    public Set<String> type = new HashSet<>();
    public HashMap<String, String> name= new HashMap<>();
    public String geoJson="";
    public String country="";
    public String nutsCode = "";
    public String granularity;
    public List<String> narrower = new ArrayList<String>();

    @Override
    public String toString() {
        return "Nut{" +
                "uri='" + uri + '\'' +
                ", type=" + type +
                ", name=" + name +
                ", country='" + country + '\'' +
                ", nutsCode='" + nutsCode + '\'' +
                '}';
    }
}