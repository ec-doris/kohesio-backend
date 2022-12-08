package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Nut{
    public String uri;
    public List<String> type = new ArrayList<>();
    public HashMap<String, String> name= new HashMap<>();
    public String geoJson="";
    public String country="";
    public String nutsCode = "";
    public String granularity;
    public List<String> narrower = new ArrayList<String>();
}