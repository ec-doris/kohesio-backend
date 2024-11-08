package eu.ec.doris.kohesio.payload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;


public class Update {
    String id;
    List<MonolingualString> labels;
    List<MonolingualString> descriptions;
    List<MonolingualString> descriptionsRaw;
    String instagramUsername;
    String twitterUsername;
    String facebookUserId;
    String youtubeUserId;
    String imageUrl;
    MonolingualString imageSummary;
    String imageCopyright;

    @JsonCreator
    public Update(@JsonProperty("id") String id) {
        this.id = id;
    }

    public List<MonolingualString> getDescriptionsRaw() {
        return descriptionsRaw;
    }

    public void setDescriptionsRaw(List<MonolingualString> descriptionsRaw) {
        this.descriptionsRaw = descriptionsRaw;
    }

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

    public String getInstagramUsername() {
        return instagramUsername;
    }

    public void setInstagramUsername(String instagramUsername) {
        this.instagramUsername = instagramUsername;
    }

    public String getTwitterUsername() {
        return twitterUsername;
    }

    public void setTwitterUsername(String twitterUsername) {
        this.twitterUsername = twitterUsername;
    }

    public String getFacebookUserId() {
        return facebookUserId;
    }

    public void setFacebookUserId(String facebookUserId) {
        this.facebookUserId = facebookUserId;
    }

    public String getYoutubeUserId() {
        return youtubeUserId;
    }

    public void setYoutubeUserId(String youtubeUserId) {
        this.youtubeUserId = youtubeUserId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public MonolingualString getImageSummary() {
        return imageSummary;
    }

    public void setImageSummary(MonolingualString imageSummary) {
        this.imageSummary = imageSummary;
    }

    public String getImageCopyright() {
        return imageCopyright;
    }

    public void setImageCopyright(String imageCopyright) {
        this.imageCopyright = imageCopyright;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "error formating Update";
        }
    }
}
