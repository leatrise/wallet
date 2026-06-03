use std::fs;

use gem_tracing::tracing::{debug, info, warn};
use rig::tool::ToolDyn;

use crate::Result;
use crate::completion::CompletionBackend;
use crate::config::Settings;

const SUPERVISOR_FILE: &str = "supervisor.md";
const PASS: &str = "PASS";
const REWRITE: &str = "REWRITE:";
const SILENCE: &str = "SILENCE";

pub struct ReplySupervisor {
    backend: CompletionBackend,
}

#[derive(Debug, PartialEq, Eq)]
enum Decision {
    Pass,
    Rewrite(String),
    Silence,
    Invalid,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum SupervisedReply {
    Send(String),
    Silence,
}

impl ReplySupervisor {
    pub fn build(settings: &Settings) -> Result<Option<Self>> {
        let preamble_path = settings.agent_dir().join(SUPERVISOR_FILE);
        if !preamble_path.exists() {
            return Ok(None);
        }
        let preamble = fs::read_to_string(&preamble_path)?;
        let backend = CompletionBackend::build(settings, &preamble, Vec::<Box<dyn ToolDyn>>::new())?;
        Ok(Some(Self { backend }))
    }

    pub async fn review_reply(&self, conversation_id: u64, context: &str, reply: &str) -> SupervisedReply {
        let decision = self.decide(conversation_id, "review", context, reply).await;
        log_decision(conversation_id, "review", &decision, reply);
        match decision {
            Decision::Pass | Decision::Invalid => SupervisedReply::Send(reply.to_string()),
            Decision::Rewrite(reply) => SupervisedReply::Send(reply),
            Decision::Silence => SupervisedReply::Silence,
        }
    }

    pub async fn repair_reply(&self, conversation_id: u64, context: &str, draft: &str) -> SupervisedReply {
        let decision = self.decide(conversation_id, "repair", context, draft).await;
        log_decision(conversation_id, "repair", &decision, draft);
        match decision {
            Decision::Pass => SupervisedReply::Send(draft.to_string()),
            Decision::Rewrite(reply) => SupervisedReply::Send(reply),
            Decision::Silence | Decision::Invalid => SupervisedReply::Silence,
        }
    }

    async fn decide(&self, conversation_id: u64, mode: &'static str, context: &str, reply: &str) -> Decision {
        let prompt = review_prompt(mode, context, reply);
        let output = match self.backend.prompt_text(&prompt).await {
            Ok(output) => output,
            Err(e) => {
                warn!(error = %e, conversation_id, "reply supervisor failed");
                return Decision::Invalid;
            }
        };

        parse_output(&output)
    }
}

impl Decision {
    fn verdict(&self) -> &'static str {
        match self {
            Decision::Pass => "pass",
            Decision::Rewrite(_) => "rewrite",
            Decision::Silence => "silence",
            Decision::Invalid => "invalid",
        }
    }
}

fn review_prompt(mode: &str, context: &str, reply: &str) -> String {
    format!("Mode: {mode}\n\nConversation context:\n{context}\n\n---\nDraft:\n{reply}\n---")
}

fn parse_output(output: &str) -> Decision {
    let body = output.trim();
    if body.eq_ignore_ascii_case(PASS) {
        return Decision::Pass;
    }
    if body.eq_ignore_ascii_case(SILENCE) {
        return Decision::Silence;
    }

    let rewrite = if let Some(rest) = body.strip_prefix(REWRITE) {
        rest
    } else if let Some(rest) = body.strip_prefix("rewrite:") {
        rest
    } else {
        debug!(output_chars = body.chars().count(), "reply supervisor output was invalid");
        return Decision::Invalid;
    };

    let rewrite = rewrite.trim();
    if rewrite.is_empty() {
        debug!("reply supervisor requested rewrite without content");
        return Decision::Invalid;
    }

    Decision::Rewrite(rewrite.to_string())
}

fn log_decision(conversation_id: u64, mode: &'static str, decision: &Decision, draft: &str) {
    match decision {
        Decision::Rewrite(reply) => {
            info!(
                conversation_id,
                verdict = decision.verdict(),
                mode,
                reply_chars = draft.chars().count(),
                suggested_chars = reply.chars().count(),
                "supervisor reviewed chatwoot reply"
            );
        }
        _ => {
            info!(
                conversation_id,
                verdict = decision.verdict(),
                mode,
                reply_chars = draft.chars().count(),
                "supervisor reviewed chatwoot reply"
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{Decision, parse_output, review_prompt};

    #[test]
    fn parses_pass() {
        assert_eq!(parse_output("PASS"), Decision::Pass);
        assert_eq!(parse_output("  pass  "), Decision::Pass);
    }

    #[test]
    fn parses_rewrite() {
        assert_eq!(parse_output("REWRITE: clean text"), Decision::Rewrite("clean text".to_string()));
        assert_eq!(parse_output("REWRITE:\nclean text"), Decision::Rewrite("clean text".to_string()));
    }

    #[test]
    fn parses_invalid() {
        assert_eq!(parse_output("REWRITE:"), Decision::Invalid);
        assert_eq!(parse_output("maybe"), Decision::Invalid);
    }

    #[test]
    fn parses_silence() {
        assert_eq!(parse_output("SILENCE"), Decision::Silence);
        assert_eq!(parse_output("  silence  "), Decision::Silence);
    }

    #[test]
    fn builds_prompt() {
        let prompt = review_prompt("repair", "history", "reply");
        assert!(prompt.contains("Mode: repair"));
        assert!(prompt.contains("Conversation context:\nhistory"));
        assert!(prompt.contains("Draft:\nreply"));
    }
}
