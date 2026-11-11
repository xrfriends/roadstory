# ADR-001: Monorepo 사용

- Status: Accepted
- Date: 2026-08-04

## Context

제품 문서, UX, API, Frontend, Backend, E2E를 함께 변경해야 하며 사람과 Codex가 공통 Context를 사용해야 한다.

## Decision

모든 문서와 구현을 하나의 Monorepo에서 관리한다.

## 이유

- Requirement와 구현을 함께 관리
- Contract 변경의 Atomic Commit
- Codex Context 향상
- End-to-End Traceability

## 영향

장점: 일관성, Cross-stack Refactoring, 중앙 CI  
단점: Repository 증가, Path-based CI와 Ownership 필요

## Guardrail

CODEOWNERS, Path CI, 필요 시 하위 `AGENTS.md`, 순환 Dependency 금지
