use primitives::{Address as AddressTrait, SignerError};

use super::message::TonSignMessageData;
use crate::{
    address::Address,
    signer::signer::{TonSignResult, TonSigner},
};

impl TonSigner {
    pub fn sign_personal(&self, data: &[u8], timestamp: u64) -> Result<TonSignResult, SignerError> {
        let message_data = TonSignMessageData::from_bytes(data)?;
        Address::ensure_matches(Some(message_data.address.as_str()), &self.address().encode())?;
        let digest = message_data.hash_with_address(timestamp, self.address())?;

        Ok(TonSignResult {
            signature: self.sign(&digest).to_vec(),
            public_key: self.public_key().to_vec(),
            timestamp,
        })
    }
}

#[cfg(test)]
mod tests {
    use crate::{
        address::base64_to_hex_address,
        signer::{
            TonSigner,
            sign_data::{TonSignDataPayload, TonSignMessageData},
            testkit::{TEST_PUBLIC_KEY, mock_cell, mock_signer, mock_signer_address},
        },
    };

    #[test]
    fn test_sign_ton_personal() {
        let payload = TonSignDataPayload::Text { text: "Hello TON".to_string() };
        let message_data = TonSignMessageData::new(payload, "example.com".to_string(), mock_signer_address());
        let data = message_data.to_bytes();

        let result = mock_signer().sign_personal(&data, 1234567890).unwrap();

        assert_eq!(
            hex::encode(&result.signature),
            "bafd1ae238f45f4df9c4c8f0a46c9dd2f9dade285c821634a7bd5bf9d0b9531d51ee45c3c7f53842521b1285a6913849b2553b6739a69e167b707ac92c104c04"
        );
        assert_eq!(hex::encode(&result.public_key), TEST_PUBLIC_KEY);
        assert_eq!(result.timestamp, 1234567890);
    }

    #[test]
    fn test_sign_ton_personal_accepts_raw_address() {
        let payload = TonSignDataPayload::Text { text: "Hello TON".to_string() };
        let address = base64_to_hex_address(&mock_signer_address()).unwrap();
        let message_data = TonSignMessageData::new(payload, "example.com".to_string(), address);
        let data = message_data.to_bytes();

        let result = mock_signer().sign_personal(&data, 1234567890).unwrap();

        assert_eq!(
            hex::encode(&result.signature),
            "bafd1ae238f45f4df9c4c8f0a46c9dd2f9dade285c821634a7bd5bf9d0b9531d51ee45c3c7f53842521b1285a6913849b2553b6739a69e167b707ac92c104c04"
        );
    }

    #[test]
    fn test_sign_ton_personal_rejects_invalid_key() {
        assert!(TonSigner::new(&[0u8; 16]).is_err());
    }

    #[test]
    fn test_sign_ton_personal_cell() {
        let payload = TonSignDataPayload::Cell {
            schema: "comment#00000000 text:SnakeData = InMsgBody;".to_string(),
            cell: mock_cell(),
        };
        let message_data = TonSignMessageData::new(payload, "example.com".to_string(), mock_signer_address());
        let data = message_data.to_bytes();

        let result = mock_signer().sign_personal(&data, 1234567890).unwrap();

        assert_eq!(
            hex::encode(&result.signature),
            "579c852645b0a746fcf8ff450d709c94680291b922dfffce517329ef520b1b9bb7867d41e97b2f2b0bf367fec0e98473fc5ead6ae7aa0fe1edb9979b647c2004"
        );
        assert_eq!(result.timestamp, 1234567890);
    }

    #[test]
    fn test_sign_ton_personal_rejects_mismatched_address() {
        let payload = TonSignDataPayload::Text { text: "Hello TON".to_string() };
        let message_data = TonSignMessageData::new(
            payload,
            "example.com".to_string(),
            "0:0000000000000000000000000000000000000000000000000000000000000000".to_string(),
        );
        let data = message_data.to_bytes();

        let result = mock_signer().sign_personal(&data, 1234567890);

        assert_eq!(result.err().unwrap().to_string(), "Invalid input: TON from does not match signer address");
    }
}
