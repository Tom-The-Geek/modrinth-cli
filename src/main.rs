mod modrinth;
mod download_cache;
mod download;
mod install_cache;

use serde::{Serialize, Deserialize};
use toml;
use toml::value::Table;
use structopt::StructOpt;
use std::io::{Write, Read};
use toml::Value;
use crate::modrinth::PackMod;
use crate::download_cache::{DownloadCache, DownloadTarget};
use std::path::Path;
use crate::install_cache::InstallCache;

#[derive(Serialize, Deserialize, Debug)]
struct Pack {
    details: PackMetadata,
    mods: Table
}

impl Pack {
    fn create_empty(game_version: &str, loader: &str) -> Self {
        Pack {
            details: PackMetadata {
                game_version: game_version.to_string(),
                loader: loader.to_string(),
            },
            mods: Table::new()
        }
    }

    fn load(mut file: std::fs::File) -> Self {
        let mut content = String::new();
        file.read_to_string(&mut content).expect("Failed to read metadata file!");
        toml::from_str(&*content).unwrap()
    }

    fn save(self, mut file: std::fs::File) -> Result<(), Box<dyn std::error::Error>> {
        let data = toml::to_string(&self)?;
        file.write_all(data.as_bytes())?;
        Ok(())
    }

    fn add_mod(&mut self, mod_info: PackMod) {
        self.mods.insert(mod_info.slug, Value::String(mod_info.version));
    }
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct PackMetadata {
    game_version: String,
    loader: String,
}

#[derive(StructOpt, Debug)]
#[structopt(name = "modrinth-cli")]
enum Args {
    #[structopt(about = "Initialises a modrinth-mods.toml ready to start making adding mods")]
    Init {
        #[structopt(long, short = "-l", default_value = "fabric")]
        loader: String,

        #[structopt(long, short = "-g")]
        game_version: String,
    },
    #[structopt(about = "Adds mod(s) to the modrinth-mods.toml but does not copy or download any mod files - run sync when you are done adding mods to the pack")]
    Add {
        #[structopt(long, short = "-g", help = "Use the specified game version for resolving the mod versions, rather than the pack default. (useful on snapshots)")]
        force_game_version: Option<String>,
        #[structopt(help = "List of mods to add, by slug or ID. You can use the format mod:version to choose a specific version")]
        mods: Vec<String>,
    },
    #[structopt(about = "Remove mods from synchronises the mods folder with the mods listed in modrinth-mods.toml. Run this after you finish adding or removing mods.")]
    Sync {
        #[structopt(long)]
        no_delete: bool
    },
    #[structopt(about = "Remove mods from modrinth-mods.toml, but doesn't delete any files (run sync to update the mods directory)")]
    Remove {
        #[structopt(help = "List of mods to remove, by slug")]
        mods: Vec<String>,
    },
    #[structopt(about = "Update all or some of the mods in the pack to the latest version")]
    Update {
        #[structopt(long, short = "-g", help = "Use the specified game version for resolving the mod versions, rather than the pack default. (useful on snapshots)")]
        force_game_version: Option<String>,
        #[structopt(help = "List of mods to update, leave empty to attempt to update all")]
        mods: Vec<String>,
    }
}

fn check_pack_exists() -> bool {
    return std::path::Path::new("modrinth-mods.toml").exists()
}

fn init_pack(loader: String, game_version: String) {
    let pack = Pack::create_empty(&*game_version, &*loader);
    if std::path::Path::new("modrinth-mods.toml").exists() {
        println!("Metadata file already exists!");
        return;
    }
    println!("Initialising default pack metadata...");
    let mut file = std::fs::File::create("modrinth-mods.toml").expect("failed to create metadata file!");
    let toml_data = toml::to_string(&pack).unwrap();
    file.write_all(toml_data.as_bytes()).expect("Failed to write metadata");
}

async fn add_mods(mods: Vec<String>, force_game_version: Option<String>) -> Result<(), Box<dyn std::error::Error>> {
    if !check_pack_exists() {
        eprintln!("modrinth-mods.toml does not exist!");
        return Ok(());
    }

    let file = std::fs::File::open("modrinth-mods.toml").expect("Failed to open metadata file!");
    let mut pack = Pack::load(file);
    println!("Loaded metadata: {:?}", pack);

    let game_version = match force_game_version {
        None => pack.details.game_version.clone(),
        Some(game_version) => game_version
    };

    for mod_specifier in mods {
        let version = modrinth::resolve_version(&mod_specifier, &pack.details.loader, &game_version).await?;
        match version {
            Some(version) => {
                println!("Got latest version as {:?} for mod: {}", version, mod_specifier);
                pack.add_mod(version);
            },
            _ => {
                println!("Failed to find latest version of mod: {}", mod_specifier);
            }
        }
    }
    pack.save(std::fs::File::create("modrinth-mods.toml").expect("Failed to open metadata file!")).expect("Failed to save metadata!");

    Ok(())
}

async fn sync_pack(no_delete: bool) -> Result<(), Box<dyn std::error::Error>> {
    if !check_pack_exists() {
        eprintln!("modrinth-mods.toml does not exist!");
        return Ok(());
    }

    let file = std::fs::File::open("modrinth-mods.toml").expect("Failed to open metadata file!");
    let pack = Pack::load(file);
    println!("Loaded metadata: {:?}", pack);

    let mods_dir = Path::new("mods");
    if !mods_dir.exists() {
        println!("Creating mods directory...");
        std::fs::create_dir_all(mods_dir)?;
    }

    let cache = DownloadCache::create(home::home_dir().unwrap().join(".modrinth-cli-cache").as_path()).expect("failed to create cache!");
    let mut installed_mods = InstallCache::load(mods_dir)?;

    let mut installed_mods_vec: Vec<(String, String)> = vec![];
    for (mod_slug, version_id) in &pack.mods {
        installed_mods_vec.push((mod_slug.clone(), version_id.as_str().unwrap().to_string()))
    }
    installed_mods.cleanup(mods_dir, installed_mods_vec, !no_delete)?;

    for (mod_slug, version_id) in &pack.mods {
        let version_id = version_id.as_str().unwrap();
        let version = modrinth::get_mod_version(&*version_id).await?;
        let file = version.files.first();
        match file {
            None => {
                eprintln!("No files found for {}:{}", mod_slug, version_id)
            }
            Some(file) => {
                let output = &mods_dir.join(file.filename.clone());
                if !output.exists() {
                    cache.copy_from_cache(&DownloadTarget::from(&version, file), output).await?;
                    installed_mods.add_mod(mods_dir, &PackMod::new(version.mod_id, mod_slug.clone(), version_id.to_string()), &file.filename)?;
                }
            }
        }
    }

    let mut installed_mods_vec: Vec<(String, String)> = vec![];
    for (mod_slug, version_id) in &pack.mods {
        installed_mods_vec.push((mod_slug.clone(), version_id.as_str().unwrap().to_string()))
    }
    installed_mods.cleanup(mods_dir, installed_mods_vec, !no_delete)?;

    Ok(())
}

async fn remove_mods(mods: Vec<String>) -> Result<(), Box<dyn std::error::Error>> {
    if !check_pack_exists() {
        eprintln!("modrinth-mods.toml does not exist!");
        return Ok(());
    }

    let file = std::fs::File::open("modrinth-mods.toml").expect("Failed to open metadata file!");
    let mut pack = Pack::load(file);
    println!("Loaded metadata: {:?}", pack);

    for mod_to_remove in mods {
        let result = pack.mods.remove(&*mod_to_remove);
        match result {
            None => eprintln!("{} was not found in modrinth-mods.toml!", mod_to_remove),
            Some(_) => println!("Removed {} from the pack", mod_to_remove)
        }
    }

    pack.save(std::fs::File::create("modrinth-mods.toml").expect("Failed to open metadata file!")).expect("Failed to save metadata!");

    Ok(())
}

async fn update_mod(pack: &mut Pack, mod_slug: &String, current_version: &String, game_version: &String) -> Result<(), Box<dyn std::error::Error>> {
    let version = modrinth::resolve_version(mod_slug, &pack.details.loader, game_version).await?;
    match version {
        Some(version) => {
            if &version.version != current_version {
                println!("Updating {} from {} to {}...", mod_slug, current_version, version.version);
                pack.add_mod(version);
            } else {
                println!("{} is up-to-date!", mod_slug);
            }
        },
        None => eprintln!("Mod: {} seems to be missing a matching version, skipping...", mod_slug)
    }

    Ok(())
}

async fn update_mods(mods: Vec<String>, force_game_version: Option<String>) -> Result<(), Box<dyn std::error::Error>> {
    if !check_pack_exists() {
        eprintln!("modrinth-mods.toml does not exist!");
        return Ok(());
    }

    let file = std::fs::File::open("modrinth-mods.toml").expect("Failed to open metadata file!");
    let mut pack = Pack::load(file);
    println!("Loaded metadata: {:?}", pack);

    let game_version = match force_game_version {
        Some(v) => v,
        None => pack.details.game_version.clone()
    };

    if mods.len() == 0 {
        for (mod_slug, version_id) in &pack.mods.clone() {
            update_mod(&mut pack, &mod_slug, &version_id.as_str().unwrap().to_string(), &game_version).await?;
        }
    } else {
        for mod_id in mods {
            let details = modrinth::get_mod_details(&*mod_id).await?;
            let version_id = pack.mods.get(&*details.slug).unwrap().as_str().unwrap().to_string();
            update_mod(&mut pack, &mod_id, &version_id, &game_version).await?;
        }
    }
    Ok(())
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("Hello, world!");

    let args = Args::from_args();

    match args {
        Args::Init { loader, game_version } => init_pack(loader, game_version),
        Args::Add { mods, force_game_version } => add_mods(mods, force_game_version).await?,
        Args::Sync { no_delete } => sync_pack(no_delete).await?,
        Args::Remove { mods } => remove_mods(mods).await?,
        Args::Update { mods, force_game_version } => update_mods(mods, force_game_version).await?,
    }
    Ok(())
}
