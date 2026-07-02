use std::error::Error;

use reqwest::header::{HeaderMap, HeaderValue};

use super::model::JupiterPositionsResponse;

pub struct JupiterClient {
    url: String,
    client: reqwest::Client,
}

impl JupiterClient {
    pub fn new(url: String, key: String) -> Self {
        let mut headers = HeaderMap::new();
        if !key.is_empty() {
            headers.insert("x-api-key", HeaderValue::from_str(&key).unwrap());
        }

        Self {
            url,
            client: reqwest::Client::builder().default_headers(headers).build().unwrap(),
        }
    }

    pub async fn get_wallet_positions(&self, address: &str) -> Result<JupiterPositionsResponse, Box<dyn Error + Send + Sync>> {
        let url = format!("{}/portfolio/v1/positions/{}", self.url, address);
        Ok(self.client.get(url).send().await?.error_for_status()?.json().await?)
    }
}
