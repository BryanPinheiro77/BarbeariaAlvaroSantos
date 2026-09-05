# Sessão de sete dias

O login emite access token e cookie HttpOnly Secure SameSite=Lax. POST /auth/refresh gira um token opaco de 256 bits, armazenado somente como SHA-256 no banco. A sessão tem limite absoluto de sete dias desde o login; renovar não prorroga o limite. POST /auth/logout revoga o refresh token. Ambos recebem o cookie, sem exigir access token. Origin deve constar em CORS_ALLOWED_ORIGINS (também no login).

Antes do deploy, aplicar db/migrations/001_refresh_sessions.sql no banco. O projeto usa ddl-auto=none; a aplicação não cria tabelas. Configurar JWT_EXPIRATION=900000 para access tokens de 15 minutos (a variável existente de 28800000 prevalece sobre o novo padrão). AUTH_COOKIE_SECURE=false somente em desenvolvimento HTTP local. Sessões antigas sem cookie podem usar o JWT até expirar, depois exigem novo login.

O frontend deve usar proxy same-origin /api (Vercel em produção, Vite em desenvolvimento) para evitar cookies de terceiros entre vercel.app e railway.app. Remover VITE_API_URL absoluto ou configurar /api. Deploy da API precede o frontend. Validar Set-Cookie e Origin no preview autorizado antes de promover.

401 indica autenticação ausente/expirada; 403 indica falta de permissão. O logout revoga o refresh, mas access tokens emitidos permanecem válidos até seu vencimento. Limpeza periódica opcional: DELETE FROM refresh_sessions WHERE expires_at < now(). Worker/cron está fora desta PR.

Aceite: rotação rejeita token anterior; expiração após sete dias exige login; logout impede renovação; access token expirado recebe 401; origem não autorizada recebe 403. Testes unitários usam banco H2 isolado. Nenhuma migração foi aplicada em produção.
