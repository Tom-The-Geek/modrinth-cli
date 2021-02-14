package me.geek.tom.modrinthcli.pack;

import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;
import me.geek.tom.modrinthcli.exception.VersionNotFoundException;
import me.geek.tom.modrinthcli.installcache.InstalledModsCacheFile;
import me.geek.tom.modrinthcli.modrinthapi.ModDetails;
import me.geek.tom.modrinthcli.modrinthapi.ModVersion;
import me.geek.tom.modrinthcli.util.cache.CachedDownloader;
import me.geek.tom.modrinthcli.util.Logger;
import me.geek.tom.modrinthcli.util.VersionPair;
import me.geek.tom.modrinthcli.util.cache.DownloadTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static me.geek.tom.modrinthcli.ModrinthCli.MODRINTH;

public class Pack {
    private final Path dir;
    private final FileConfig packFileConfig;
    private final PackFile packFile;
    private final ObjectConverter converter;
    private final InstalledModsCacheFile installedModsCache;

    public Pack(Path dir, boolean noDelete) throws IOException {
        this.dir = dir;
        this.packFileConfig = loadPackFile();
        this.converter = new ObjectConverter();
        this.packFile = this.converter.toObject(this.packFileConfig, PackFile::new);
        this.installedModsCache = InstalledModsCacheFile.load(this, !noDelete);
    }

    public void addMod(String id, String version) {
        this.packFile.mods.set(id, version);
    }

    public Path getModsDir() throws IOException {
        Path mods = this.dir.resolve("mods");
        if (!Files.exists(mods)) {
            Files.createDirectories(mods);
        }
        return mods;
    }

    public void save() throws IOException {
        this.save(false, false);
    }

    public void save(boolean sync, boolean delete) throws IOException {
        if (sync) {
            this.installedModsCache.cleanup(this.getModsDir(), this.packFile.getInstalledMods(), delete);
        }

        this.converter.toConfig(this.packFile, this.packFileConfig);
        this.packFileConfig.save();
    }

    public PackFile getPackFile() {
        return this.packFile;
    }

    private FileConfig loadPackFile() {
        Path file = this.dir.resolve("modrinth-mods.toml");
        FileConfig config = FileConfig.of(file);
        config.load();
        return config;
    }

    public void addMod(String modId, String forcedGameVersion, boolean download) throws Throwable {
        String gameVersion;
        if (!forcedGameVersion.isEmpty()) gameVersion = forcedGameVersion;
        else gameVersion = packFile.details.gameVersion;

        ModInfo mod = resolveMod(modId, gameVersion);

        this.addMod(mod.modSlug, mod.versionId);
        if (download) installMod(mod.modSlug, mod.versionId);
    }

    public void updateMod(String modId, String forcedGameVersion) throws Throwable {
        String gameVersion;
        if (!forcedGameVersion.isEmpty()) gameVersion = forcedGameVersion;
        else gameVersion = packFile.details.gameVersion;

        ModInfo latestVersion = getLatestVersion(modId, gameVersion, packFile.details.loader);
        String installedVersion = getCurrentInstalledVersion(latestVersion.modSlug);
        if (!latestVersion.versionId.equals(installedVersion)) {
            Logger.info("Updating " + latestVersion.modSlug + " from " + installedVersion + " to " + latestVersion);
            this.addMod(latestVersion.modSlug, latestVersion.versionId);
        } else {
            Logger.info(latestVersion.modSlug + " is up-to-date!");
        }
    }

    public void updateAll(String forcedGameVersion) throws Throwable {
        for (VersionPair mod : this.packFile.getInstalledMods()) {
            this.updateMod(mod.getModSlug(), forcedGameVersion);
        }
    }

    private ModInfo resolveMod(String modId, String gameVersion) throws Throwable {
        ModInfo mod;
        if (modId.contains(":")) {
            String[] pts = modId.split(":", 2);
            modId = pts[0];
            String versionId = pts[1];
            ModDetails details = getModDetails(modId);
            if (!details.versions.contains(versionId)) throw new VersionNotFoundException("Version " + versionId + " does not exist for mod: " + details.title);
            mod = new ModInfo(details.id, details.slug, versionId);
        } else {
            mod = this.getLatestVersion(modId, gameVersion, packFile.details.loader);
        }
        return mod;
    }

    public String getCurrentInstalledVersion(String modSlug) {
        return this.packFile.mods.get(modSlug);
    }

    public void remove(VersionPair mod) {
        this.getPackFile().mods.remove(mod.getModSlug());
    }

    public void installMod(String slug, String version) throws Throwable {
        ModVersion modVersion = Logger.requesting(MODRINTH.getVersion(version), "Getting version information: " + slug + "...");
        ModVersion.File file = modVersion.files.get(0);
        Path output = getModsDir().resolve(file.filename);
        if (!Files.exists(output)) {
            CachedDownloader.INSTANCE.download(new DownloadTarget(modVersion.modId, modVersion.id, file.hashes.get("sha1"), file.url), output);
            this.installedModsCache.addMod(getModsDir(), slug, modVersion.modId, modVersion.id, file.filename);
        }
    }

    public ModInfo getLatestVersion(String modId, String gameVersion, String loader) throws Throwable {
        ModDetails mod = getModDetails(modId);
        assert mod != null; // these are to make the IDE shutup.
        List<ModVersion> versions = Logger.requesting(MODRINTH.getModVersions(mod.id), "Getting versions for: " + mod.slug + "...");
        assert versions != null;
        List<ModVersion> matchingVersions = versions.stream()
                .filter(version -> version.gameVersions.contains(gameVersion))
                .filter(version -> version.loaders.contains(loader))
                .sorted(Comparator.comparingLong((ModVersion v) -> v.datePublished.getTime()).reversed())
                .collect(Collectors.toList());

        if (matchingVersions.isEmpty()) {
            throw new VersionNotFoundException("No versions found for " + modId + " fitting gameVersion: " + gameVersion + " and loader: " + loader + "!");
        }

        ModVersion version = matchingVersions.get(0);
        if (version.files.isEmpty()) {
            throw new VersionNotFoundException("Version " + version.name + " for mod: " + mod.title + " has no files!");
        }

        return new ModInfo(mod.id, mod.slug, version.id);
    }

    private ModDetails getModDetails(String modId) throws Throwable {
        return Logger.requesting(MODRINTH.getMod(modId), "Getting mod details: " + modId + "...");
    }
}
