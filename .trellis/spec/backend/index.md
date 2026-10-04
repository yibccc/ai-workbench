# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

This directory contains guidelines for backend development. Fill in each file with your project's specific conventions.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Controller / service interfaces / service implementations / mapper layers | Active |
| [Database Guidelines](./database-guidelines.md) | PostgreSQL migrations, MyBatis mapping, time ranges, and project associations | Active |
| [Focus Routines](./focus-routines.md) | Routine occurrences, focus session ledger, settlement, and report evidence | Active |
| [Identity and Isolation](./identity-isolation.md) | Account/session APIs, explicit idle time, owner-scoped SQL and private STOMP | Active |
| [Community Publications](./community-publication.md) | Member snapshots, private drafts/materials, stable receipts and moderation | Active |
| [Private Attachments](./private-attachments.md) | RustFS, exact quotas, reference authorization, durable recovery and safe cleanup | Active |
| [Private Current Resumes](./private-resumes.md) | Singleton Markdown, exact CAS/receipts, original bytes, late-IO cleanup and snapshot contract | Active |
| [Private Text Interviews](./private-interviews.md) | Fixed 2N questions, owner snapshots, SDK manual-only calls, durable jobs and truthful scores | Active |
| [Error Handling](./error-handling.md) | Problem details, validation, conflicts, and safe exception translation | Active |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | To fill |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | To fill |
| [Runtime Integration Contract](./runtime-integration.md) | Local infrastructure, environment variables, status APIs, and probe error behavior | Active |
| [Pagination](./pagination.md) | PageHelper query scope, zero-based API, thread-local cleanup and browser behavior | Active |
| [Report Deletion](./report-deletion.md) | Daily version soft deletion, concurrency, idempotency and retained evidence | Active |
| [Local Delivery](./local-delivery.md) | Windows process ownership, WSL Docker, backup and isolated restore | Active |
| [Linux Deployment](./linux-deployment.md) | Five-service Docker build, private RustFS, app-authenticated ingress and exact STOMP origins | Active |

---

## How to Fill These Guidelines

For each guideline file:

1. Document your project's **actual conventions** (not ideals)
2. Include **code examples** from your codebase
3. List **forbidden patterns** and why
4. Add **common mistakes** your team has made

The goal is to help AI assistants and new team members understand how YOUR project works.

---

**Language**: All documentation should be written in **English**.
