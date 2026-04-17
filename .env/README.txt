Single place for secrets: .env/local.env (gitignored)

Setup
-----
1. Copy local.env.example to local.env
2. Fill in / keep your real values in local.env only
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
VITE_API_BASE_URL is in local.env. You can also set frontend/.env.development to match.

Security
--------
Rotate any credentials that were ever committed to Git. Prefer strong JWT_SECRET and AUTH_INTERNAL_API_KEY in production.
