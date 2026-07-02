use std::collections::HashMap;

use serde::Deserialize;
use serde_json::Number;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterPositionsResponse {
    pub elements: Vec<JupiterPortfolioElement>,
    pub token_info: Option<HashMap<String, HashMap<String, JupiterTokenInfo>>>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type")]
pub enum JupiterPortfolioElement {
    #[serde(rename = "multiple")]
    Multiple(JupiterMultipleElement),
    #[serde(rename = "liquidity")]
    Liquidity(JupiterLiquidityElement),
    #[serde(rename = "borrowlend")]
    BorrowLend(JupiterBorrowLendElement),
    #[serde(rename = "trade")]
    Trade(JupiterTradeElement),
    #[serde(rename = "leverage")]
    Leverage(JupiterLeverageElement),
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterMultipleElement {
    pub platform_id: String,
    pub label: String,
    pub name: Option<String>,
    pub data: JupiterMultipleData,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterMultipleData {
    pub assets: Vec<JupiterPortfolioAsset>,
    #[serde(rename = "ref")]
    pub reference: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterLiquidityElement {
    pub platform_id: String,
    pub label: String,
    pub name: Option<String>,
    pub data: JupiterLiquidityData,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterLiquidityData {
    pub liquidities: Vec<JupiterLiquidity>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterLiquidity {
    pub assets: Vec<JupiterPortfolioAsset>,
    pub reward_assets: Vec<JupiterPortfolioAsset>,
    pub name: Option<String>,
    #[serde(rename = "ref")]
    pub reference: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterBorrowLendElement {
    pub platform_id: String,
    pub label: String,
    pub name: Option<String>,
    pub data: JupiterBorrowLendData,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterBorrowLendData {
    pub supplied_assets: Vec<JupiterPortfolioAsset>,
    pub borrowed_assets: Vec<JupiterPortfolioAsset>,
    pub reward_assets: Vec<JupiterPortfolioAsset>,
    pub unsettled: Option<JupiterUnsettledAssets>,
    #[serde(rename = "ref")]
    pub reference: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct JupiterUnsettledAssets {
    pub assets: Vec<JupiterPortfolioAsset>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterTradeElement {
    pub platform_id: String,
    pub label: String,
    pub name: Option<String>,
    pub data: JupiterTradeData,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterTradeData {
    pub assets: JupiterTradeAssets,
    #[serde(rename = "ref")]
    pub reference: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct JupiterTradeAssets {
    pub input: Option<JupiterPortfolioAsset>,
    pub output: Option<JupiterPortfolioAsset>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterLeverageElement {
    pub platform_id: String,
    pub label: String,
    pub name: Option<String>,
    pub data: JupiterLeverageData,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterLeverageData {
    pub cross: Option<JupiterCrossLeverage>,
    #[serde(rename = "ref")]
    pub reference: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterCrossLeverage {
    pub collateral_assets: Option<Vec<JupiterPortfolioAsset>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JupiterPortfolioAsset {
    pub data: JupiterPortfolioAssetData,
}

#[derive(Debug, Deserialize)]
pub struct JupiterPortfolioAssetData {
    pub address: Option<String>,
    pub amount: Option<Number>,
}

#[derive(Debug, Deserialize)]
pub struct JupiterTokenInfo {
    pub decimals: u32,
}
