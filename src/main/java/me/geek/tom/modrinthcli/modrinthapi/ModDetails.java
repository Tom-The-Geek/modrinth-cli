package me.geek.tom.modrinthcli.modrinthapi;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

public class ModDetails {
    @SerializedName("id")
    public String id;
    @SerializedName("slug")
    public String slug;
    @SerializedName("team")
    public String team;
    @SerializedName("title")
    public String title;
    @SerializedName("description")
    public String description;
    @SerializedName("body")
    public String body;
    @SerializedName("body_url")
    public String bodyUrl;
    @SerializedName("published")
    public Date published;
    @SerializedName("updated")
    public Date updated;
    @SerializedName("status")
    public String status;
    @SerializedName("license")
    public License license;
    @SerializedName("client_side")
    public String clientSide;
    @SerializedName("server_side")
    public String serverSide;
    @SerializedName("downloads")
    public int downloads;
    @SerializedName("categories")
    public List<String> categories = null;
    @SerializedName("versions")
    public List<String> versions = null;
    @SerializedName("icon_url")
    public String iconUrl;
    @SerializedName("issues_url")
    public String issuesUrl;
    @SerializedName("source_url")
    public String sourceUrl;
    @SerializedName("wiki_url")
    public Object wikiUrl;
    @SerializedName("discord_url")
    public String discordUrl;
    @SerializedName("donation_urls")
    public List<Object> donationUrls = null;

    @Override
    public String toString() {
        return "ModDetails{" +
                "id='" + id + '\'' +
                ", slug='" + slug + '\'' +
                ", team='" + team + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", body='" + body + '\'' +
                ", bodyUrl='" + bodyUrl + '\'' +
                ", published=" + published +
                ", updated=" + updated +
                ", status='" + status + '\'' +
                ", license=" + license +
                ", clientSide='" + clientSide + '\'' +
                ", serverSide='" + serverSide + '\'' +
                ", downloads=" + downloads +
                ", categories=" + categories +
                ", versions=" + versions +
                ", iconUrl='" + iconUrl + '\'' +
                ", issuesUrl='" + issuesUrl + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", wikiUrl=" + wikiUrl +
                ", discordUrl='" + discordUrl + '\'' +
                ", donationUrls=" + donationUrls +
                '}';
    }

    public static class License {
        @SerializedName("id")
        public String id;
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;
    }
}
