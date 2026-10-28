# Research artifacts

- Store reusable native analysis, design inspection, probe sources, frame captures,
  and device-validation evidence under `.local-research/` in this workspace.
  Do not use `/tmp` as the only copy of reusable research.
- Keep `.local-research/` ignored by Git. Do not commit extracted Apple binaries,
  IDA databases, local screenshots, generated probes, or raw tool responses.
- Keep concise implementation references intended for contributors in `docs/`.
  Link their supporting local evidence using workspace-relative paths.
- Write new research scripts and probes to `.local-research/` from the start;
  keep disposable build products separate from source and evidence.
