package me.geek.tom.modrinthcli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.geek.tom.modrinthcli.modrinthapi.ModrinthApi;
import me.geek.tom.modrinthcli.pack.Pack;
import me.geek.tom.modrinthcli.util.Logger;
import me.geek.tom.modrinthcli.util.VersionPair;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModrinthCli {
    public static final OkHttpClient OKHTTP = new OkHttpClient();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final ModrinthApi MODRINTH = ModrinthApi.get();

    private void init(InitArgs args) throws IOException {
        Path metadata = Paths.get(".", "modrinth-mods.toml");
        if (Files.exists(metadata)) {
            Logger.error("There is already metadata in the current directory!");
            return;
        }

        // TODO: Not do this
        Files.write(metadata, Arrays.asList(
                "[details]",
                "gameVersion = \"" + args.gameVersion + "\"",
                "loader = \"" + args.loader + "\"",
                "",
                "[mods]",
                ""
        ));

        Logger.success("Wrote default metadata to " + metadata);
    }

    private void add(AddArgs args) throws Throwable {
        Path metadata = Paths.get(".", "modrinth-mods.toml");
        if (!Files.exists(metadata)) {
            Logger.error("There is no metadata in the current directory!");
            return;
        }

        Pack pack = new Pack(Paths.get("."), false);

        for (String mod : args.mods) {
            Logger.info("Installing: " + mod);
            pack.addMod(mod, args.forceGameVersion, args.download);
            Logger.success("Installed: " + mod);
        }

        Logger.info("Saving pack file...");
        pack.save();
    }

    private void sync(SyncArgs args) throws Throwable {
        Path metadata = Paths.get(".", "modrinth-mods.toml");
        if (!Files.exists(metadata)) {
            Logger.error("There is no metadata in the current directory!");
            return;
        }

        Pack pack = new Pack(Paths.get("."), args.noDelete);
        for (VersionPair mod : pack.getPackFile().getInstalledMods()) {
            pack.installMod(mod.getModSlug(), mod.getVersionId());
        }

        pack.save(true, !args.noDelete);
    }

    private void remove(RemoveArgs args) throws IOException {
        Path metadata = Paths.get(".", "modrinth-mods.toml");
        if (!Files.exists(metadata)) {
            Logger.error("There is no metadata in the current directory!");
            return;
        }

        Pack pack = new Pack(Paths.get("."), false);

        for (VersionPair mod : pack.getPackFile().getInstalledMods()) {
            if (args.mods.contains(mod.getModSlug())) {
                args.mods.remove(mod.getModSlug());
                pack.remove(mod);
                Logger.info("Removed: " + mod.getModSlug());
            }
        }

        for (String mod : args.mods) {
            Logger.error(mod + " is not installed!");
        }

        pack.save(args.sync, !args.noDelete);
    }

    private void update(UpdateArgs args) throws Throwable {
        Path metadata = Paths.get(".", "modrinth-mods.toml");
        if (!Files.exists(metadata)) {
            Logger.error("There is no metadata in the current directory!");
            return;
        }

        Pack pack = new Pack(Paths.get("."), false);
        if (args.mods.isEmpty()) {
            pack.updateAll(args.forceGameVersion);
        } else {
            for (String mod : args.mods) {
                pack.updateMod(mod, args.forceGameVersion);
            }
        }

        pack.save(!args.noSync, !args.noDelete);

        if (!args.noSync) {
            SyncArgs syncArgs = new SyncArgs();
            syncArgs.noDelete = args.noDelete;
            this.sync(syncArgs);
        }
    }

    public void run(String[] args) throws Throwable {
        InitArgs initArgs = new InitArgs();
        AddArgs addArgs = new AddArgs();
        SyncArgs syncArgs = new SyncArgs();
        RemoveArgs removeArgs = new RemoveArgs();
        UpdateArgs updateArgs = new UpdateArgs();
        JCommander jCommander = JCommander.newBuilder()
                .addCommand("init", initArgs)
                .addCommand("add", addArgs)
                .addCommand("sync", syncArgs)
                .addCommand("remove", removeArgs)
                .addCommand("update", updateArgs)
                .build();

        jCommander.parse(args);
        if (jCommander.getParsedCommand() == null) {
            jCommander.usage();
            return;
        }

        switch (jCommander.getParsedCommand()) {
            case "init":
                if (initArgs.gameVersion == null) {
                    jCommander.usage();
                    return;
                }
                this.init(initArgs);
                break;
            case "add":
                this.add(addArgs);
                break;
            case "sync":
                this.sync(syncArgs);
                break;
            case "remove":
                this.remove(removeArgs);
                break;
            case "update":
                this.update(updateArgs);
                break;
            default:
                jCommander.usage();
        }
    }

    public static void main(String[] args) {
        try {
            new ModrinthCli().run(args);
        } catch (Throwable e) {
            Logger.error("An error occurred: " + e.getClass().getSimpleName() + ": " + e.getLocalizedMessage());
            e.printStackTrace();
        } finally {
            OKHTTP.dispatcher().executorService().shutdown();
        }
    }

    @Parameters(commandDescription = "Updates some or all installed mods")
    public static class UpdateArgs {
        @Parameter(description = "List of mods to update, by slug.")
        public List<String> mods = new ArrayList<>();

        @Parameter(names = { "--force-game-version", "-G" }, description = "Download the latest version for a different version of the game.")
        public String forceGameVersion = "";

        @Parameter(names = { "--no-sync", "-s" }, description = "Do not run sync after updating")
        public boolean noSync = false;

        @Parameter(names = { "--no-delete", "-n" }, description = "Do not delete mods from the mods folder. Only applies if --sync is also set")
        public boolean noDelete = false;
    }

    @Parameters(commandDescription = "Removes mods")
    public static class RemoveArgs {
        @Parameter(names = { "--sync", "-s" }, description = "Equivalent to running sync after removing the mods")
        public boolean sync = false;

        @Parameter(names = { "--no-delete", "-n" }, description = "Do not delete mods from the mods folder. Only applies if --sync is also set")
        public boolean noDelete = false;

        @Parameter(description = "List of mods to remove, by slug.")
        public List<String> mods = new ArrayList<>();
    }

    @Parameters(commandDescription = "Syncs the list mods specified in modrinth-mods.toml with the mods folder (installs them)")
    public static class SyncArgs {
        @Parameter(names = { "--no-delete", "-n" }, description = "Do not delete mods from the mods folder")
        public boolean noDelete = false;
    }

    @Parameters(commandDescription = "Add mods")
    public static class AddArgs {
        @Parameter(names = { "--force-game-version", "-G" }, description = "Download the latest version for a different version of the game.")
        public String forceGameVersion = "";

        @Parameter(names = { "--download", "-s" }, description = "Download the mod mod after adding it to modrinth-mods.toml")
        public boolean download = false;

        @Parameter(description = "List of mods to add, by slug or ID. " +
                "You can use the format mod:version to install a specific version")
        public List<String> mods = new ArrayList<>();
    }

    @Parameters(commandDescription = "Initialise metadata files")
    public static class InitArgs {
        @Parameter(names = { "--loader", "-l" })
        public String loader = "fabric";

        @Parameter(names = { "--game-version", "-g" })
        public String gameVersion;
    }
}
