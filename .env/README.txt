Single place for config: .env/local.env

What is on GitHub (javabackend repo)
------------------------------------
The repo includes BOTH:
  - .env/local.env.example   (template only)
  - .env/local.env           (same keys as example; PLACEHOLDER values only)

You asked to push the real .env too. That is not possible with live secrets in the file:
GitHub Push Protection rejects the push (OAuth client IDs/secrets, Azure app secrets,
mail app passwords, etc.). The only ways around that are (a) use placeholder values in
the committed file — what we did — or (b) in GitHub: Security → Secret scanning → allow
each blocked secret (not recommended) or change repo/org rules. We did (a) so the push
succeeds and everyone still gets a ready-to-edit .env/local.env after clone.

On your machine
---------------
Keep your REAL secrets only in .env/local.env locally (do not expect them to match
GitHub). After clone, edit .env/local.env and replace CHANGE_ME / empty OAuth mail fields.

Setup
-----
1. If you cloned fresh: .env/local.env already exists with placeholders; edit it.
   Otherwise copy local.env.example to local.env
2. Fill in real values in local.env only (never commit real secrets unless you accept
   GitHub blocking the push or you use GitHub's explicit allow flow)
3. Before starting Spring Boot, load variables into the shell:

   PowerShell (from jobra-testing-phase-fe/backend/java):
     . .\scripts\load-env.ps1
     cd authservice
     mvn spring-boot:run

   Git Bash / WSL:
     source ./scripts/load-env.sh
     cd authservice && mvn spring-boot:run

   API gateway (separate terminal, after load-env):
     cd api-gateway
     mvn spring-boot:run

IntelliJ / VS Code: use "EnvFile" or Run Configuration → Environment variables → paste from local.env (KEY=VALUE per line).

Frontend (main app under jobra-testing-phase-fe/frontend)
--------
NEXT_PUBLIC_GATEWAY_URL is documented in local.env / local.env.example. Frontend also
uses frontend/.env.local for the same URL (see backend/java/README.md).

Security
--------
Rotate any credentials that were ever committed to Git. Prefer strong JWT_SECRET and AUTH_INTERNAL_API_KEY in production.
