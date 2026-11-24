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

## Results

_Not yet recorded._
