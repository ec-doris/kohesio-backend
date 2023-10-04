package eu.ec.doris.kohesio.payload;

import java.util.List;

public class Update {

    String id;
    List<MonolingualString> labels;
    List<MonolingualString> descriptions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<MonolingualString> getLabels() {
        return labels;
    }

    public void setLabels(List<MonolingualString> labels) {
        this.labels = labels;
    }

    public List<MonolingualString> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(List<MonolingualString> descriptions) {
        this.descriptions = descriptions;
    }
}
