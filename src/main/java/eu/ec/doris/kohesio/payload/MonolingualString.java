package eu.ec.doris.kohesio.payload;

import java.util.Objects;

public class MonolingualString {
    String language;
    String text;

    public MonolingualString() {
    }

    public MonolingualString(String language, String text) {
        this.language = language;
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String toValue() {
        if (language == null || language.isEmpty()) {
            return "\"" + text + "\"";
        }
        return "\"" + text + "\"@" + language;
    }

    @Override
    public String toString() {
        return toValue();
    }

    public boolean equals(MonolingualString obj) {
        return Objects.equals(text, obj.text) && Objects.equals(language, obj.language);
    }
}
