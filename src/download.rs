use std::{fs, path::Path};

use reqwest::Url;
use reqwest::{header, Client};
use std::io::Write;


pub async fn download(client: &Client, url: &str, output: &Path) -> std::result::Result<(), Box<dyn std::error::Error>> {
    let url = Url::parse(url)?;

    let mut request = client.get(url.as_str());

    let file = output;

    if file.exists() {
        let size = file.metadata()?.len() - 1;
        request = request.header(header::RANGE, format!("bytes={}-", size));
    }

    let data = request.send().await?.bytes().await?;

    let parent = file.parent().unwrap();
    if !parent.exists() {
        std::fs::create_dir_all(parent)?;
    }

    let mut dest = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&file)?;

    dest.write(&*data)?;

    println!(
        "Download of '{}' has been completed.",
        file.to_str().unwrap()
    );

    Ok(())
}
