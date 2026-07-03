use super::model::AssetImage;
use primitives::Chain;
use serde::Deserialize;
use std::error::Error;

const JUPITER_API_BASE_URL: &str = "https://api.jup.ag/tokens/v2";

pub struct JupiterProvider {
    client: reqwest::Client,
    minimum_market_cap: f64,
}

#[derive(Clone, Debug, Deserialize)]
struct JupiterToken {
    id: String,
    icon: Option<String>,
    mcap: Option<f64>,
}

impl JupiterProvider {
    pub fn new(minimum_market_cap: f64) -> Self {
        Self {
            client: reqwest::Client::new(),
            minimum_market_cap,
        }
    }

    pub async fn get_verified_asset_images(&self) -> Result<Vec<AssetImage>, Box<dyn Error + Send + Sync>> {
        let tokens = self.get_tokens().await?;
        Ok(self.map_tokens(tokens))
    }

    pub async fn get_verified_asset_images_by_id(&self, token_id: &str) -> Result<Vec<AssetImage>, Box<dyn Error + Send + Sync>> {
        let tokens = self.get_tokens().await?;
        Ok(self.map_tokens(tokens).into_iter().filter(|image| image.token_id == token_id).collect())
    }

    async fn get_tokens(&self) -> Result<Vec<JupiterToken>, Box<dyn Error + Send + Sync>> {
        let request = self.client.get(format!("{JUPITER_API_BASE_URL}/tag")).query(&[("query", "verified")]);
        let response = request.send().await?;
        let status = response.status();
        if !status.is_success() {
            return Err(format!("jupiter request failed: {status}").into());
        }
        Ok(response.json::<Vec<JupiterToken>>().await?)
    }

    fn map_tokens(&self, tokens: Vec<JupiterToken>) -> Vec<AssetImage> {
        tokens
            .into_iter()
            .filter_map(|token| {
                if token.mcap.unwrap_or_default() < self.minimum_market_cap {
                    return None;
                }
                let image_url = token.icon?;
                Some(AssetImage {
                    chain: Chain::Solana,
                    token_id: token.id,
                    image_url,
                })
            })
            .collect()
    }
}
