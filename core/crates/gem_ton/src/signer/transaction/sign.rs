use std::str::FromStr;
use std::time::{SystemTime, UNIX_EPOCH};

use num_bigint::BigUint;
use primitives::{FeeOption, SignerError, SignerInput};

use super::{
    message::{InternalMessage, build_internal_message},
    request::{JettonTransferRequest, NftTransferRequest, TransferPayload, TransferRequest},
};
use crate::{
    address::Address,
    constants::NFT_TRANSFER_FORWARD_AMOUNT,
    signer::signer::TonSigner,
    tvm::{BagOfCells, CellBuilder},
};

const STATE_INIT_EXPIRE_AT: u32 = u32::MAX;
const EXTERNAL_EXPIRE_WINDOW_SECS: u64 = 600;

impl TonSigner {
    pub fn sign_transfer(&self, input: &SignerInput, expire_at: Option<u32>) -> Result<String, SignerError> {
        let request = TransferRequest::new_transfer(&input.destination_address, &input.value, input.is_max_value, input.memo.clone())?;
        self.sign_requests(vec![request], input.metadata.get_sequence()?, expire_at)
    }

    pub fn sign_token_transfer(&self, input: &SignerInput, expire_at: Option<u32>) -> Result<String, SignerError> {
        let sender_token_address = input
            .metadata
            .get_sender_token_address()?
            .ok_or_else(|| SignerError::invalid_input("missing sender token address"))?;

        let jetton = JettonTransferRequest {
            query_id: 0,
            value: BigUint::from_str(&input.value)?,
            destination: Address::parse(&input.destination_address)?,
            response_address: Address::parse(&input.sender_address)?,
            custom_payload: None,
            forward_ton_amount: BigUint::from(1u8),
            comment: input.memo.clone(),
        };
        let request = TransferRequest::new_contract_transfer(&sender_token_address, optional_ton_attachment(input)?, TransferPayload::Jetton(jetton))?;
        self.sign_requests(vec![request], input.metadata.get_sequence()?, expire_at)
    }

    pub fn sign_nft_transfer(&self, input: &SignerInput, expire_at: Option<u32>) -> Result<String, SignerError> {
        let nft_asset = input.input_type.get_nft_asset()?;
        let nft_item = nft_asset.get_contract_address()?;
        let nft = NftTransferRequest {
            query_id: 0,
            new_owner: Address::parse(&input.destination_address)?,
            response_destination: Address::parse(&input.sender_address)?,
            forward_amount: BigUint::from(NFT_TRANSFER_FORWARD_AMOUNT),
            comment: input.memo.clone(),
        };
        let request = TransferRequest::new_contract_transfer(nft_item, optional_ton_attachment(input)?, TransferPayload::Nft(nft))?;
        self.sign_requests(vec![request], input.metadata.get_sequence()?, expire_at)
    }

    pub fn sign_swap(&self, input: &SignerInput, expire_at: Option<u32>) -> Result<Vec<String>, SignerError> {
        let swap_data = input.input_type.get_swap_data()?;
        let request = TransferRequest::new_with_payload(
            &swap_data.data.to,
            &swap_data.data.value,
            input.memo.clone(),
            Some(BagOfCells::parse_base64_root(&swap_data.data.data)?),
            true,
            None,
        )?;
        Ok(vec![self.sign_requests(vec![request], input.metadata.get_sequence()?, expire_at)?])
    }

    pub(crate) fn sign_requests(&self, requests: Vec<TransferRequest>, sequence: u64, expire_at: Option<u32>) -> Result<String, SignerError> {
        let sequence = u32::try_from(sequence).map_err(|_| SignerError::invalid_input("TON sequence does not fit in u32"))?;
        let expire_at = resolve_expire_at(sequence, expire_at)?;

        let internal_messages: Vec<InternalMessage> = requests.iter().map(build_internal_message).collect::<Result<_, _>>()?;
        let external_body = self.wallet().build_external_body(expire_at, sequence, &internal_messages)?;
        let signature = self.sign(&external_body.hash);
        let mut body_builder = CellBuilder::new();
        body_builder.store_cell(&external_body)?.store_slice(&signature)?;
        let signed_transaction = self.wallet().build_transaction(sequence == 0, body_builder.build()?)?;

        Ok(BagOfCells::from_root(signed_transaction).to_base64(true)?)
    }
}

fn optional_ton_attachment(input: &SignerInput) -> Result<BigUint, SignerError> {
    let Some(value) = input.fee.options.get(&FeeOption::TokenAccountCreation) else {
        return Ok(BigUint::ZERO);
    };
    value.to_biguint().ok_or_else(|| SignerError::invalid_input("invalid TON amount"))
}

fn resolve_expire_at(sequence: u32, expire_at: Option<u32>) -> Result<u32, SignerError> {
    match (sequence, expire_at) {
        (0, _) => Ok(STATE_INIT_EXPIRE_AT),
        (_, Some(value)) => Ok(value),
        (_, None) => {
            let now = SystemTime::now().duration_since(UNIX_EPOCH).map_err(SignerError::from_display)?.as_secs();
            u32::try_from(now + EXTERNAL_EXPIRE_WINDOW_SECS).map_err(|_| SignerError::invalid_input("TON expire time does not fit in u32"))
        }
    }
}

#[cfg(test)]
mod tests {
    use num_bigint::{BigInt, BigUint};
    use primitives::{
        Address as AddressTrait, Asset, AssetId, AssetType, Chain, FeeOption, NFTAsset, SignerInput, TransactionFee, TransactionInputType, TransactionLoadMetadata,
        asset_constants::TON_USDT_TOKEN_ID, swap::SwapData,
    };

    use super::super::{
        message::build_internal_message,
        request::{JettonTransferRequest, TransferPayload, TransferRequest},
    };
    use crate::{
        address::Address,
        constants::NFT_TRANSFER_ATTACHMENT,
        signer::{TonSigner, testkit::mock_cell},
    };

    const TEST_TON_PRIVATE_KEY: &str = "c7702dadcd00d470df27dee0ddd97fbcf9deba52b60f7dd2b296ff42bb1fcad6";
    const TRUST_WALLET_PRIVATE_KEY: &str = "63474e5fe9511f1526a50567ce142befc343e71a49b865ac3908f58667319cb8";
    const SENDER_TOKEN_ADDRESS: &str = "EQAlgB03OjJKdXrlwZiGJD5snSzPKF2VL5bErJn_cqJANGH9";

    fn test_signer() -> TonSigner {
        let private_key = hex::decode(TEST_TON_PRIVATE_KEY).unwrap();
        TonSigner::new(&private_key).unwrap()
    }

    #[test]
    fn test_sign_transfer() {
        let signer = test_signer();
        let address = signer.address().encode();

        let input = SignerInput::mock_with_input_type(
            TransactionInputType::Transfer(Asset::from_chain(Chain::Ton)),
            &address,
            &address,
            "10000",
            TransactionLoadMetadata::mock_ton(1),
        );
        assert_eq!(
            signer.sign_transfer(&input, Some(1_000_000_000)).unwrap(),
            "te6cckEBBQEAugABRYgB6KcyxyKXjeZ1aslITLkDx8Ex2t+YYdYne0KICMF4Y64MAQGhc2lnbn///xE7msoAAAAAAZWY27hDbkEhcXO2QrcHLcPThioR5YAEfjbB51nMISdtNz4KT7RJGLNZ2Dvwu4CFSjyOBzha+gMkr2BTQNvaBYAgAgIKDsPIbQMEAwFkQgB6KcyxyKXjeZ1aslITLkDx8Ex2t+YYdYne0KICMF4Y65E4gAAAAAAAAAAAAAAAAAEEAACQ0j25"
        );
    }

    #[test]
    fn test_sign_token_transfer() {
        let signer = test_signer();
        let address = signer.address().encode();

        let asset = Asset::new(AssetId::from_token(Chain::Ton, TON_USDT_TOKEN_ID), String::new(), String::new(), 8, AssetType::TOKEN);
        let input = SignerInput::mock_with_input_type(
            TransactionInputType::Transfer(asset),
            &address,
            &address,
            "10000",
            TransactionLoadMetadata::mock_ton_jetton(1, SENDER_TOKEN_ADDRESS),
        );
        assert_eq!(
            signer.sign_token_transfer(&input, Some(1_000_000_000)).unwrap(),
            "te6cckECBgEAAQ0AAUWIAeinMscil43mdWrJSEy5A8fBMdrfmGHWJ3tCiAjBeGOuDAEBoXNpZ25///8RO5rKAAAAAAGudZJBbHaz3+wTYphViYWKZBSOh8KA2whWRRrxCEPPHESm4pVLnEkkZ10tDTAwjle7wkeM6NYJobf3J5nRJv3BIAICCg7DyG0DAwQAAAFgYgASwA6bnRklOr1y4MxDEh82TpZnlC7Kl8tiVkz/uVEgGgAAAAAAAAAAAAAAAAABBQCmD4p+pQAAAAAAAAAAInEIAeinMscil43mdWrJSEy5A8fBMdrfmGHWJ3tCiAjBeGOvAD0U5ljkUvG8zq1ZKQmXIHj4Jjtb8ww6xO9oUQEYLwx1wgLBYAvZ"
        );
    }

    #[test]
    fn test_sign_nft_transfer() {
        let signer = test_signer();
        let mut input = SignerInput::mock_ton(
            TransactionInputType::TransferNft(Asset::from_chain(Chain::Ton), NFTAsset::mock_ton()),
            TransactionLoadMetadata::mock_ton(1),
        );
        input.fee = TransactionFee::new_from_fee_with_option(BigInt::from(0), FeeOption::TokenAccountCreation, BigInt::from(NFT_TRANSFER_ATTACHMENT));

        assert_eq!(
            signer.sign_nft_transfer(&input, Some(1_000_000_000)).unwrap(),
            "te6cckECBgEAAREAAUWIAeinMscil43mdWrJSEy5A8fBMdrfmGHWJ3tCiAjBeGOuDAEBoXNpZ25///8RO5rKAAAAAAGKpqAd7eIZJGJ37XHzqOSoN1nIak/HO9+LvS2cEtnZws85B9TjQqA2EHVOpIyfcHhcHt25UkPyxgulAMIUrffBoAICCg7DyG0DAwQAAAFoYgBX4k5cPDeQ5Dgi2M9vPH41o2KIJCKJn8dNNqIDAODMdSAX14QAAAAAAAAAAAAAAAAAAQUApV/MPRQAAAAAAAAAAIALGrip93CRFe/VWhm46zlR7f7z+SqqINgmc2Bv2BSAaPABY1cVPu4SIr36q0M3HWcqPb/efyVVRBsEzmwN+wKQDRxzEtAIemjjQg=="
        );
    }

    #[test]
    fn test_sign_nft_transfer_validates_contract_address() {
        let signer = test_signer();

        let mut nft_asset = NFTAsset::mock_ton();
        nft_asset.contract_address = None;
        let input_type = TransactionInputType::TransferNft(Asset::from_chain(Chain::Ton), nft_asset);
        let input = SignerInput::mock_ton(input_type, TransactionLoadMetadata::mock_ton(1));

        assert_eq!(
            signer.sign_nft_transfer(&input, Some(1_000_000_000)).unwrap_err().to_string(),
            "Invalid input: missing NFT contract address"
        );
    }

    /// Deploy vector for Wallet V5R1.
    #[test]
    fn test_sign_wallet_deploy() {
        let private_key = hex::decode(TRUST_WALLET_PRIVATE_KEY).unwrap();
        let signer = TonSigner::new(&private_key).unwrap();
        let destination = Address::parse("EQDYW_1eScJVxtitoBRksvoV9cCYo4uKGWLVNIHB1JqRR3n0").unwrap();
        let request = TransferRequest {
            bounceable: true,
            ..TransferRequest::mock(destination)
        };
        assert_eq!(
            signer.sign_requests(vec![request], 0, Some(1_671_135_440)).unwrap(),
            "te6cckECGwEAA2sAAkWIAPzrlvDh4ZoL3VaGRH2UZVUzjqkEC/+A8381Ubujl5fcHgEXAgE0AhYBFP8A9KQT9LzyyAsDAgEgBA8CAUgFBgLc0CDXScEgkVuPYyDXCx8gghBleHRuvSGCEHNpbnS9sJJfA+CCEGV4dG66jrSAINchAdB01yH6QDD6RPgo+kQwWL2RW+DtRNCBAUHXIfQFgwf0Dm+hMZEw4YBA1yFwf9s84DEg10mBAoC5kTDgcOISEQIBIAcOAgEgCAsCAW4JCgAZrc52omhAIOuQ64X/wAAZrx32omhAEOuQ64WPwAIBSAwNABezJftRNBx1yHXCx+AAEbJi+1E0NcKAIAAZvl8PaiaECAoOuQ+gLAEC8hABHiDXCx+CEHNpZ2668uCKfxEB5o7w7aLt+yGDCNciAoMI1yMggCDXIdMf0x/TH+1E0NIA0x8g0x/T/9cKAAr5AUDM+RCaKJRfCtsx4fLAh98Cs1AHsPLQhFEluvLghVA2uvLghvgju/LQiCKS+ADeAaR/yMoAyx8BzxbJ7VQgkvgP3nDbPNgSA/btou37AvQEIW6SbCGOTAIh1zkwcJQhxwCzji0B1yggdh5DbCDXScAI8uCTINdKwALy4JMg1x0GxxLCAFIwsPLQiddM1zkwAaTobBKEB7vy4JPXSsAA8uCT7VXi0gABwACRW+Dr1ywIFCCRcJYB1ywIHBLiUhCx4w8g10oTFBUAlgH6QAH6RPgo+kQwWLry4JHtRNCBAUHXGPQFBJ1/yMoAQASDB/RT8uCLjhQDgwf0W/LgjCLXCgAhbgGzsPLQkOLIUAPPFhL0AMntVAByMNcsCCSOLSHy4JLSAO1E0NIAURO68tCPVFAwkTGcAYEBQNch1woA8uCO4sjKAFjPFsntVJPywI3iABCTW9sx4ddM0ABRgAAAAD///4j6Fjv8mN9RB2LoCoOYk7XdlxQwSjswki2RjPfAmfdGoKABoXNpZ25///8R/////wAAAAC8+ZGdiSPai3uqZo72yIFXeqJdZNu/S+toBa68KIOfosh/LdN4tekmvBKNNxZcyM8/jiEKqOiUyXA91RXaEOYC4BgCCg7DyG0DGhkBYmIAbC3+ryThKuNsVtAKMll9CvrgTFHFxQyxappA4OpNSKOIUAAAAAAAAAAAAAAAAAEaAAB11p5q"
        );
    }

    #[test]
    fn test_sign_swap_uses_custom_payload_transfer() {
        let signer = test_signer();
        let mut swap_data = SwapData::mock_with_provider(primitives::SwapProvider::StonfiV2);
        swap_data.data.to = SENDER_TOKEN_ADDRESS.to_string();
        swap_data.data.value = "241000000".to_string();
        swap_data.data.data = mock_cell();
        swap_data.data.gas_limit = None;
        let input = SignerInput::mock_ton(
            TransactionInputType::Swap(Asset::from_chain(Chain::Ton), Asset::from_chain(Chain::Ton), swap_data),
            TransactionLoadMetadata::mock_ton(1),
        );

        let signed = signer.sign_swap(&input, Some(1_000_000_000)).unwrap();
        assert_eq!(signed.len(), 1);
        assert!(signed[0].starts_with("te6cc"));
    }

    #[test]
    fn test_long_comments_use_snake_cells() {
        let address = Address::parse(SENDER_TOKEN_ADDRESS).unwrap();
        let comment = "memo".repeat(80);

        let transfer = TransferRequest {
            comment: Some(comment.clone()),
            ..TransferRequest::mock(address)
        };
        let native_payload = build_internal_message(&transfer).unwrap().message.references.first().unwrap().clone();
        assert!(!native_payload.references.is_empty());

        let jetton = TransferRequest {
            value: BigUint::ZERO,
            bounceable: true,
            payload: Some(TransferPayload::Jetton(JettonTransferRequest {
                comment: Some(comment),
                ..JettonTransferRequest::mock(address)
            })),
            ..TransferRequest::mock(address)
        };
        let jetton_payload = build_internal_message(&jetton).unwrap().message.references.first().unwrap().clone();
        assert_eq!(jetton_payload.references.len(), 1);
        assert!(!jetton_payload.references[0].references.is_empty());
    }
}
