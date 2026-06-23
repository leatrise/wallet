use crate::SwapperError;
use alloy_primitives::{B256, Bytes, LogData, U256};
use alloy_sol_types::SolEvent;
use gem_evm::{
    across::contracts::V3SpokePoolInterface::{FundsDeposited, V3FundsDeposited},
    rpc::model::Log,
};

pub(super) struct FillStatusRelayData {
    pub(super) depositor: B256,
    pub(super) recipient: B256,
    pub(super) exclusive_relayer: B256,
    pub(super) input_token: B256,
    pub(super) output_token: B256,
    pub(super) input_amount: U256,
    pub(super) output_amount: U256,
    pub(super) origin_chain_id: U256,
    pub(super) deposit_id: U256,
    pub(super) fill_deadline: u32,
    pub(super) exclusivity_deadline: u32,
    pub(super) message: Bytes,
}

pub(super) struct ParsedDeposit {
    pub(super) destination_chain_id: u64,
    pub(super) relay_data: FillStatusRelayData,
}

fn alloy_log_data(log: &Log) -> Result<LogData, SwapperError> {
    let topics = log.topics.iter().map(|topic| topic.parse()).collect::<Result<Vec<B256>, _>>()?;
    let data = Bytes::from(alloy_primitives::hex::decode(&log.data)?);
    LogData::new(topics, data).ok_or_else(|| SwapperError::TransactionError("invalid event topics".into()))
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

pub(super) fn parse_deposit_from_logs(logs: &[Log], receipt_chain_id: u64) -> Result<Option<ParsedDeposit>, SwapperError> {
    for log in logs {
        let Some(topic) = log.topics.first().map(|topic| topic.parse::<B256>()).transpose()? else {
            continue;
        };

        if topic == V3FundsDeposited::SIGNATURE_HASH {
            let log_data = alloy_log_data(log)?;
            let event = V3FundsDeposited::decode_log_data(&log_data)?;
            return Ok(Some(ParsedDeposit {
                destination_chain_id: event.destinationChainId.to::<u64>(),
                relay_data: relay_data_from_v3(&event, receipt_chain_id),
            }));
        }

        if topic == FundsDeposited::SIGNATURE_HASH {
            let log_data = alloy_log_data(log)?;
            let event = FundsDeposited::decode_log_data(&log_data)?;
            return Ok(Some(ParsedDeposit {
                destination_chain_id: event.destinationChainId.to::<u64>(),
                relay_data: relay_data_from_funds(&event, receipt_chain_id),
            }));
        }
    }

    Ok(None)
}
