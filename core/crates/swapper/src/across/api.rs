use crate::SwapperError;
use alloy_primitives::{B256, U256};
use alloy_sol_types::SolEvent;
use gem_evm::{
    across::contracts::V3SpokePoolInterface::{FundsDeposited, V3FundsDeposited},
    rpc::model::Log,
};

pub(crate) struct FillStatusRelayData {
    pub depositor: B256,
    pub recipient: B256,
    pub exclusive_relayer: B256,
    pub input_token: B256,
    pub output_token: B256,
    pub input_amount: U256,
    pub output_amount: U256,
    pub origin_chain_id: U256,
    pub deposit_id: U256,
    pub fill_deadline: u32,
    pub exclusivity_deadline: u32,
    pub message: alloy_primitives::Bytes,
}

pub(crate) struct ParsedDeposit {
    pub destination_chain_id: u64,
    pub relay_data: FillStatusRelayData,
}

fn decode_event<E: SolEvent>(log: &Log) -> Result<E, SwapperError> {
    let topics = log.topics.iter().map(|topic| decode_topic(topic)).collect::<Result<Vec<_>, _>>()?;
    let data = alloy_primitives::hex::decode(&log.data)?;
    E::decode_raw_log(topics, &data).map_err(SwapperError::from)
}

fn decode_topic(topic: &str) -> Result<B256, SwapperError> {
    let bytes = alloy_primitives::hex::decode(topic).map_err(|_| SwapperError::TransactionError("invalid event topic".into()))?;
    if bytes.len() != 32 {
        return Err(SwapperError::TransactionError("invalid event topic".into()));
    }
    Ok(B256::from_slice(&bytes))
}

fn relay_data_from_v3(event: &V3FundsDeposited, origin_chain_id: u64) -> FillStatusRelayData {
    FillStatusRelayData {
        depositor: event.depositor.into_word(),
        recipient: event.recipient.into_word(),
        exclusive_relayer: event.exclusiveRelayer.into_word(),
        input_token: event.inputToken.into_word(),
        output_token: event.outputToken.into_word(),
        input_amount: event.inputAmount,
        output_amount: event.outputAmount,
        origin_chain_id: U256::from(origin_chain_id),
        deposit_id: U256::from(event.depositId),
        fill_deadline: event.fillDeadline,
        exclusivity_deadline: event.exclusivityDeadline,
        message: event.message.clone(),
    }
}

fn relay_data_from_funds(event: &FundsDeposited, origin_chain_id: u64) -> FillStatusRelayData {
    FillStatusRelayData {
        depositor: event.depositor,
        recipient: event.recipient,
        exclusive_relayer: event.exclusiveRelayer,
        input_token: event.inputToken,
        output_token: event.outputToken,
        input_amount: event.inputAmount,
        output_amount: event.outputAmount,
        origin_chain_id: U256::from(origin_chain_id),
        deposit_id: event.depositId,
        fill_deadline: event.fillDeadline,
        exclusivity_deadline: event.exclusivityDeadline,
        message: event.message.clone(),
    }
}

pub(crate) fn parse_deposit_from_logs(logs: &[Log], receipt_chain_id: u64) -> Result<Option<ParsedDeposit>, SwapperError> {
    for log in logs {
        let Some(topic) = log.topics.first() else {
            continue;
        };
        let topic = decode_topic(topic)?;

        if topic == V3FundsDeposited::SIGNATURE_HASH {
            let event = decode_event::<V3FundsDeposited>(log)?;
            return Ok(Some(ParsedDeposit {
                destination_chain_id: event.destinationChainId.to::<u64>(),
                relay_data: relay_data_from_v3(&event, receipt_chain_id),
            }));
        }

        if topic == FundsDeposited::SIGNATURE_HASH {
            let event = decode_event::<FundsDeposited>(log)?;
            return Ok(Some(ParsedDeposit {
                destination_chain_id: event.destinationChainId.to::<u64>(),
                relay_data: relay_data_from_funds(&event, receipt_chain_id),
            }));
        }
    }

    Ok(None)
}
