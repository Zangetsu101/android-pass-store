# Domain Docs

How engineering skills should consume this repo's domain documentation when exploring the codebase.

## Layout

This is a single-context repo.

Read:

- `CONTEXT.md` at the repo root for domain language and rules.
- Relevant ADRs under `docs/adr/` before changing architecture or behavior covered by prior decisions.

There is no `CONTEXT-MAP.md` and no per-context domain-doc layout.

## Use the glossary's vocabulary

When output names a domain concept, use the term as defined in `CONTEXT.md`. Do not drift to synonyms the glossary explicitly avoids.

If the concept needed is not in the glossary, note the gap instead of inventing new terminology silently.

## Flag ADR conflicts

If proposed work contradicts an existing ADR, surface it explicitly rather than silently overriding it.
