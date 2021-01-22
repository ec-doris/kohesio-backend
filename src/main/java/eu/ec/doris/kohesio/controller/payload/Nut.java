package eu.ec.doris.kohesio.controller.payload;

import java.util.ArrayList;
import java.util.List;

public class Nut{
    public String uri;
    public String type;
    public String name="";
    public String geoJson="";
    public List<String> narrower = new ArrayList<String>();
}