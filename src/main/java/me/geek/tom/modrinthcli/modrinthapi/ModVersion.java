package me.geek.tom.modrinthcli.modrinthapi;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ModVersion {
    @SerializedName("id")
    public String id;
    @SerializedName("mod_id")
    public String modId;
    @SerializedName("author_id")
    public String authorId;
    @SerializedName("featured")
    public boolean featured;
    @SerializedName("name")
    public String name;
    @SerializedName("version_number")
    public String versionNumber;
    @SerializedName("changelog")
    public String changelog;
    @SerializedName("changelog_url")
    public Object changelogUrl;
    @SerializedName("date_published")
    public Date datePublished;
    @SerializedName("downloads")
    public int downloads;
    @SerializedName("version_type")
    public String versionType;
    @SerializedName("files")
    public List<File> files = null;
    @SerializedName("dependencies")
    public List<Object> dependencies = null;
    @SerializedName("game_versions")
    public List<String> gameVersions = null;
    @SerializedName("loaders")
    public List<String> loaders = null;

    public static class File {
        @SerializedName("hashes")
        public Map<String, String> hashes;
        @SerializedName("url")
        public String url;
        @SerializedName("filename")
        public String filename;
        @SerializedName("primary")
        public boolean primary;
    }
}
