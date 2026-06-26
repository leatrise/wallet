mod data;
mod hash_impl;
mod parse;

pub use hash_impl::{hash_typed_data, validate_eip712_domain_binding};

#[cfg(test)]
mod tests {
    use super::*;
    use hex::FromHex;

    #[test]
    fn hash_matches_reference_vector() {
        let json = include_str!("../../testdata/eip712_reference_vector.json");

        let our_hash = hash_typed_data(json).expect("hash succeeds");
        let expected = <[u8; 32]>::from_hex("be609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2").unwrap();
        assert_eq!(our_hash, expected);
    }

    #[test]
    fn hash_hyperliquid_with_colon_type() {
        let json = include_str!("../../../gem_hypercore/testdata/hl_eip712_approve_agent.json");
        let digest = hash_typed_data(json).expect("hash succeeds");
        let expected = <[u8; 32]>::from_hex("480af9fd3cdc70c2f8a521388be13620d16a0f643d9cffdfbb65cd019cc27537").unwrap();
        assert_eq!(digest, expected);
    }

    #[test]
    fn hash_handles_arrays_and_nested_types() {
        let json = include_str!("../../testdata/eip712_arrays_nested.json");

        let digest = hash_typed_data(json).expect("hash succeeds");
        let expected = <[u8; 32]>::from_hex("6acbc18af9d2decca3d38571c2f595b1ebb1b93e9e7b046632df71f6ceb217f9").unwrap();
        assert_eq!(digest, expected);
    }

    #[test]
    fn hash_rejects_missing_message() {
        let json = include_str!("../../testdata/eip712_missing_message.json");

        let err = hash_typed_data(json).expect_err("missing message returns error");
        assert!(err.to_string().contains("missing message"));
    }

    #[test]
    fn hash_supports_signed_integers() {
        let json = include_str!("../../testdata/eip712_signed_integers.json");

        let digest = hash_typed_data(json).expect("hash succeeds");
        let expected = <[u8; 32]>::from_hex("c6bed7e6a1ec9d2737b1d7bbca1e966eff59e74e21d8e20a66351b2db82cfc6a").unwrap();
        assert_eq!(digest, expected);
    }

    #[test]
    fn hash_differs_across_chain_ids() {
        let ethereum_json = include_str!("../../testdata/eip712_canonical_chain_id_1.json");
        let polygon_json = include_str!("../../testdata/eip712_canonical_chain_id_137.json");
        assert_ne!(hash_typed_data(ethereum_json).unwrap(), hash_typed_data(polygon_json).unwrap());
    }

    #[test]
    fn hash_rejects_domain_field_not_bound_by_schema() {
        let undeclared = include_str!("../../testdata/eip712_domain_undeclared_field.json");
        assert!(hash_typed_data(undeclared).unwrap_err().to_string().contains("verifyingContract"));

        let missing_type = include_str!("../../testdata/eip712_domain_missing_type.json");
        assert!(hash_typed_data(missing_type).unwrap_err().to_string().contains("verifyingContract"));

        let partial = include_str!("../../testdata/eip712_domain_partial_declared.json");
        hash_typed_data(partial).expect("fully declared partial domain should hash");
    }

    #[test]
    fn hash_rejects_excessive_type_nesting() {
        let self_referential = include_str!("../../testdata/eip712_self_referential_type.json");
        assert!(hash_typed_data(self_referential).unwrap_err().to_string().contains("depth"));

        let chain_len = 200usize;
        let mut type_defs = vec![r#""EIP712Domain":[{"name":"chainId","type":"uint256"}]"#.to_string()];
        for index in 0..chain_len {
            let field_type = if index + 1 < chain_len { format!("T{}", index + 1) } else { "uint256".to_string() };
            type_defs.push(format!(r#""T{index}":[{{"name":"f","type":"{field_type}"}}]"#));
        }
        let deep_chain = format!(r#"{{"types":{{{}}},"primaryType":"T0","domain":{{"chainId":1}},"message":{{}}}}"#, type_defs.join(","));
        let err = hash_typed_data(&deep_chain).expect_err("deep type chain must be rejected");
        assert!(err.to_string().contains("depth"), "{err}");
    }

    #[test]
    fn hash_rejects_unbound_chain_id() {
        let missing_schema_field = include_str!("../../../gem_evm/testdata/eip712_domain_chain_id_without_schema_field.json");
        assert!(hash_typed_data(missing_schema_field).unwrap_err().to_string().contains("chainId"));

        let schema_without_domain_value = include_str!("../../../gem_evm/testdata/eip712_schema_chain_id_without_domain_value.json");
        assert!(hash_typed_data(schema_without_domain_value).unwrap_err().to_string().contains("missing chainId"));

        let null_domain_chain_id = include_str!("../../../gem_evm/testdata/eip712_domain_chain_id_null_value.json");
        assert!(hash_typed_data(null_domain_chain_id).unwrap_err().to_string().contains("chainId"));
    }
}
