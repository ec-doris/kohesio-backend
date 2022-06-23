package eu.ec.doris.kohesio.payload;

import java.util.ArrayList;
import java.util.List;

public class Nut{
    public String uri;
    public List<String> type = new ArrayList<>();
    public String name="";
    public String geoJson="";
    public String country="";
    public String granularity;
    public List<String> narrower = new ArrayList<String>();
}