use reqwest::Client;
use serde::Deserialize;
use chrono::{DateTime, Utc};
use std::collections::HashMap;

pub async fn resolve_version(mod_definition: &str, loader: &str, game_version: &str) -> Result<Option<PackMod>, reqwest::Error> {
    return if mod_definition.contains(":") { // User has specified a version ID, check that it actually exists for the specified mod.
        let vec: Vec<&str> = mod_definition.split(":").collect();
        if vec.len() != 2 {
            eprintln!("Invalid version specifier: {} (too many colons)", mod_definition);
            Ok(None)
        } else {
            let mod_details = get_mod_details(vec[0]).await?;
            return if mod_details.versions.contains(&vec[1].to_string()) {
                Ok(Some(PackMod::new(mod_details.id, mod_details.slug, vec[1].to_string())))
            } else {
                eprintln!("Invalid version for mod {}: {}", mod_details.title, vec[1]);
                Ok(None)
            }
        }
    } else { // We need to figure out what version is the latest, as we just have a mod ID/slug
        let details = get_mod_details(mod_definition).await?;
        let versions = get_mod_versions(&*details.id).await?;

        let mut filtered_versions = versions.iter().filter(|current_version| {
            return current_version.loaders.contains(&loader.to_string()) && current_version.game_versions.contains(&game_version.to_string())
        }).collect::<Vec<&ModrinthModVersion>>();

        filtered_versions.sort_by(|&v1, &v2| {
            return v1.date_published.cmp(&v2.date_published)
        });

        return match filtered_versions.last() {
            None => Ok(None),
            Some(version) => Ok(Some(PackMod::new(details.id, details.slug, version.id.clone())))
        }
    }
}

const MODRINTH_API_BASE: &str = "https://api.modrinth.com/api/v1";

#[derive(Deserialize)]
pub struct ModrinthMod {
    pub id: String,
    pub slug: String,
    pub team: String,
    pub title: String,
    pub description: String,
    pub body: String,
    pub body_url: Option<String>,
    pub published: DateTime<Utc>,
    pub updated: DateTime<Utc>,
    pub status: String,
    pub license: License,
    pub client_side: String,
    pub server_side: String,
    pub downloads: u32,
    pub categories: Vec<String>,
    pub versions: Vec<String>,
    pub icon_url: Option<String>,
    pub issues_url: Option<String>,
    pub source_url: Option<String>,
    pub wiki_url: Option<String>,
    pub discord_url: Option<String>,
    pub donation_urls: Option<Vec<DonationLink>>,
}

#[derive(Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum SideType {
    Required,
    Optional,
    Unsupported,
    Unknown,
}

#[derive(Deserialize)]
pub struct License {
    pub id: String,
    pub name: String,
    pub url: Option<String>,
}

#[derive(Deserialize)]
pub struct DonationLink {
    pub id: String,
    pub platform: String,
    pub url: String,
}

#[derive(Deserialize, Debug)]
pub struct ModrinthModVersion {
    pub id: String,
    pub mod_id: String,
    pub author_id: String,
    pub featured: bool,
    pub name: String,
    pub version_number: String,
    pub changelog: Option<String>,
    pub changelog_url: Option<String>,
    pub date_published: DateTime<Utc>,
    pub downloads: u32,
    pub version_type: String,
    pub files: Vec<ModrinthModVersionFile>,
    pub dependencies: Vec<String>,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
}

#[derive(Deserialize, Debug)]
pub struct ModrinthModVersionFile {
    pub hashes: HashMap<String, String>,
    pub url: String,
    pub filename: String,
}

#[derive(Debug)]
pub struct PackMod {
    pub id: String,
    pub slug: String,
    pub version: String,
}

impl PackMod {
    pub fn new(id: String, slug: String, version: String) -> Self {
        Self {
            id, slug, version
        }
    }
}

pub async fn get_mod_details(mod_id: &str) -> Result<ModrinthMod, reqwest::Error> {
    Client::new().get(&format!("{}/mod/{}", MODRINTH_API_BASE, mod_id))
        .send().await?
        .json().await
}

async fn get_mod_versions(mod_id: &str) -> Result<Vec<ModrinthModVersion>, reqwest::Error> {
    Client::new().get(&format!("{}/mod/{}/version", MODRINTH_API_BASE, mod_id))
        .send().await?
        .json().await
}

pub async fn get_mod_version(version_id: &str) -> Result<ModrinthModVersion, reqwest::Error> {
    Client::new().get(&format!("{}/version/{}", MODRINTH_API_BASE, version_id))
        .send().await?
        .json().await
}
