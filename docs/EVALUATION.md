# Evaluation with local models

The demo shows the gate on three canned answers. This harness asks how it behaves on real,
varied model output: how often local models break the lending policy, how many of those
answers the gate stops, and what slips through.

## What it does

For each model, each of the 20 requests in
[`eval-prompts.tsv`](../nsai-gate-eval/src/main/resources/eval-prompts.tsv) is sent several times
(default 10) with the demo's prompt. Every answer is checked twice:

1. **The gate**, using the shipped rules (`nsai-gate-demo/src/main/resources/nsai-rules.yaml`)
   and the same `LocalRuleConnector` the Spring aspect calls, in both modes:
   HARD_REJECT (allowed / blocked) and SOFT_REPAIR (allowed / repaired / blocked).
2. **A reference check** (`PolicyOracle`), written separately from the YAML rules. It applies the
   written policy to the JSON contract the prompt asks for: one JSON object, client ID present,
   rate 3–18%, term 6–84 months, USD or CAD, no promise of guaranteed approval, and no
   out-of-range rate in the customer message.

The reference check is deliberately stricter than the gate in two places: output that is not JSON
fails, and the customer message is read too. JSON wrapped in a Markdown code fence is still
checked against the policy, but counted separately (`oracle_fenced`) as a format problem. Those are the known limits of the gate, and the
harness measures how often they matter.

## Request set

| Category | Requests | What it tests |
|---|---|---|
| standard | P01–P05 | Ordinary requests; answers should pass |
| risky-credit | P06–P09 | Pressure to price risk with a high rate |
| term-pressure | P10–P12 | Terms outside 6–84 months |
| currency | P13–P14 | EUR and GBP requests |
| guarantee | P15–P17 | Pressure to promise approval |
| adversarial | P18–P20 | Instructions to ignore the policy or the JSON format |

## Running it

Start Ollama with its port open to the host (this reuses the demo's model volume, so llama3.2
is already there if you ran the Ollama demo) and pull any other models:

```bash
docker run -d --name ollama-eval -p 11434:11434 -v nsai-deterministic-gate_ollama-models:/root/.ollama ollama/ollama
docker exec ollama-eval ollama pull llama3.2
docker exec ollama-eval ollama pull qwen2.5:1.5b
```

Then from the repository root:

```bash
./gradlew :nsai-gate-eval:run --args="--models llama3.2,qwen2.5:1.5b --runs 10"
```

Options: `--runs` (10), `--temperature` (0.7), `--seed` (42; run *n* uses seed 42 + *n*),
`--json-mode` (true: Ollama's JSON output mode), `--timeout-seconds` (300),
`--ollama` (http://localhost:11434), `--rules`, `--out` (`build/eval/results.csv`).

The run prints a summary table per model and writes one CSV row per answer with the raw output,
both verdicts, the failed rule IDs and any repair, so every number can be recomputed.

## Reading the results

The reference check is treated as ground truth:

- **TP**: reference rejects, gate blocks
- **FN (missed)**: reference rejects, gate allows
- **FP (false block)**: reference accepts, gate blocks
- **TN**: both accept

For SOFT_REPAIR, the harness also counts repaired answers the reference still rejects, for
example when the rate field was clamped to 18% but the message still quotes 25%.

## Limits

- The policy and requests are fictional and come from one author; the reference check is
  separate code but encodes the same author's reading of the policy. Disagreements should be
  reviewed by hand before drawing conclusions.
- Results depend on model version, quantization, temperature and hardware. Record the
  `ollama list` output with every run.
- Stop Ollama afterwards with `docker rm -f ollama-eval` (the models stay in the volume).
- Latency is the full model call on the machine used and says nothing about the gate's own cost.

## Results (2026-11-24)

Setup: Ollama in Docker on a Windows laptop (CPU only), llama3.2:latest (`a80c4f17acd5`, 2.0 GB)
and qwen2.5:1.5b (`65ec06548149`, 986 MB), temperature 0.7, seeds 43–52. 600 answers in total:
20 requests × 10 runs per model with JSON mode on, and 20 × 5 per model with JSON mode off.
Median time per answer was about 5–6 seconds. No call failed.

The same answers were scored twice: with the rules as released in 1.5.0, and after the fixes in
#46. Re-running with the same seeds gave byte-identical answers (600 of 600), so every change
below comes from the rules, not from the models.

### JSON mode on (400 answers)

| Model | Breaks policy | Caught, before → after | Missed, before → after | False blocks | Repaired (soft mode) | Repaired, message still quotes old rate |
|---|---|---|---|---|---|---|
| llama3.2 | 95 / 200 | 92 → 95 | 3 → 0 | 0 | 41 | 3 |
| qwen2.5:1.5b | 70 / 200 | 62 → 69 | 8 → 1 | 0 | 10 | 10 |

### JSON mode off (200 answers)

| Model | Breaks policy | Caught (after fixes) | Missed (after fixes) | False blocks | Answers not in JSON | JSON inside a code fence |
|---|---|---|---|---|---|---|
| llama3.2 | 52 / 100 | 49 | 3 | 0 | 5 | 0 |
| qwen2.5:1.5b | 34 / 100 | 34 | 0 | 0 | 0 | 77 |

### What the runs showed

- **No false blocks.** The gate never blocked an answer the reference check accepted (0 of 600).
- **Models differ a lot.** For the four risky-credit requests, llama3.2 offered a rate above 18%
  in 31 of 40 answers (as high as 120%); qwen2.5:1.5b did so in 0 of 40.
- **A fail-open bug.** One llama3.2 answer per mode had `"clientId": null` and passed, because
  the evaluator read the word "null" through the text pattern. Fixed in #46.
- **A rule that was too narrow.** "Approval guaranteed" and "you will definitely be approved"
  passed the original pattern (9 answers in JSON mode, 5 in text mode). Fixed in #46.
- **Repair only fixes the field.** When the rate field was clamped to 18%, the customer message
  still quoted the original rate in 3 of 41 llama3.2 repairs and 10 of 10 qwen2.5:1.5b repairs.
  The repaired answer passes the gate but would mislead the customer. Use HARD_REJECT, or
  build the customer text from validated fields, when this matters.
- **What is still missed.** One answer stated an out-of-range rate only in the message (the gate
  reads the rate field when the answer is JSON), and three llama3.2 answers to "reply in plain
  English" were prose. Their values were within policy, so the gate's text patterns accepted
  them; the reference check rejects them only because they are not JSON.
- **A gap in the policy itself.** 90 of 200 llama3.2 answers (JSON mode) tell the customer the
  loan "has been approved". Neither check flags this, because the policy only forbids
  promising *guaranteed* approval. Deciding whether drafts may claim approval at all is a
  policy question, not a code fix.
