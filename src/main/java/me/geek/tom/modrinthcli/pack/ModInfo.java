package me.geek.tom.modrinthcli.pack;

public class ModInfo {
    public final String modId;
    public final String modSlug;
    public final String versionId;

    public ModInfo(String modId, String modSlug, String versionId) {
        this.modId = modId;
        this.modSlug = modSlug;
        this.versionId = versionId;
    }
}
