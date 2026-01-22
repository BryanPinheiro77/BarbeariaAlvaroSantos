# Barbearia Álvaro Santos 💈✂️  
Sistema de Agendamento, Pagamentos e Notificações – API REST (Back-end)

![Badge](https://img.shields.io/badge/Java-21-red)
![Badge](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen)
![Badge](https://img.shields.io/badge/PostgreSQL-Supabase-blue)
![Badge](https://img.shields.io/badge/JWT-Security-orange)
![Badge](https://img.shields.io/badge/Deploy-Railway%20%7C%20Vercel-2ea44f)
![Badge](https://img.shields.io/badge/Status-Em%20Produ%C3%A7%C3%A3o-success)

---

## 🌐 Aplicação em produção (Front-end)

A aplicação (painel + cliente) está publicada em:

- https://barbearia-alvaro-santos-front-end.vercel.app

> Observação: este repositório contém a **API**. O front-end é mantido em repositório separado e consome esta API.

---

## 📌 Sobre o Projeto

**Barbearia Álvaro Santos – Sistema de Agendamento** é um projeto **real**, desenvolvido para organizar o fluxo de atendimentos, facilitar o agendamento online e controlar pagamentos.

Problemas que o sistema resolve:

- Agendamentos sem padrão/organização  
- Falta de controle de horários disponíveis  
- Gestão de serviços e preços sem rastreabilidade  
- Pagamentos sem acompanhamento (pendente/confirmado/cancelado)  
- Notificações e confirmações (WhatsApp) sem automação  

O sistema foi construído com foco em **produção**, aplicando boas práticas de arquitetura, segurança e separação de responsabilidades.

---

## 🧩 Componentes do Sistema

- **Back-end (este repositório / API REST)**: Spring Boot
- **Front-end**: React + Vite (consome a API de forma independente)
- **Pagamentos**: Mercado Pago (Checkout + Webhook)
- **Notificações**: WhatsApp via **WAHA** (opcional, recomendado em produção)
- **Banco**: PostgreSQL (Supabase)

---

## 🏗️ Arquitetura

API REST em camadas:

- **Controllers** – endpoints REST  
- **Services** – regras de negócio, validações e orquestração  
- **Repositories** – acesso ao banco com Spring Data JPA  
- **Models / Entities** – domínio  
- **DTOs** – contratos de entrada e saída  
- **Security** – autenticação e autorização com JWT  

---

## 🚀 Tecnologias Utilizadas

- Java 21  
- Spring Boot 4  
- Spring Web MVC  
- Spring Data JPA (Hibernate)  
- Spring Security  
- JWT (JSON Web Token)  
- PostgreSQL (Supabase)  
- Maven  
- Lombok  

---

## ☁️ Deploy (Produção)

- **Back-end**: Railway  
- **Banco de dados**: Supabase Postgres  
- **Front-end**: Vercel  
- **WAHA (WhatsApp)**: Railway (serviço separado) ou VPS/Container

### Observação (Supabase / Railway)

Se o teu ambiente de deploy estiver em IPv4, a **Direct connection** do Supabase pode falhar (“Not IPv4 compatible”).  
Em produção, recomenda-se usar **Session Pooler** do Supabase para obter estabilidade de conexão.

---

## 🔐 Segurança

- Autenticação baseada em **JWT**
- Controle de acesso por **roles**:
  - `ADMIN`
  - `CLIENTE`
- Sessões **stateless**
- Senhas criptografadas com **BCrypt**
- Credenciais e segredos **fora do repositório** (via ENV no Railway / `.properties` local)

---

## ✨ Funcionalidades Principais

### 👤 Usuários
- Login de administrador e cliente
- Autorização por perfil (Admin/Cliente)

### 📅 Agendamentos
- Criação de agendamentos pelo cliente
- Cálculo de horários disponíveis
- Seleção de múltiplos serviços
- Cancelamento e conclusão de agendamentos
- Listagens por cliente, data, intervalo e status

### ✂️ Serviços (Admin)
- CRUD de serviços (criar, editar, ativar/desativar, excluir)
- Serviços ativos disponíveis para clientes
- Duração e preço por serviço

### ⏱️ Horários / Agenda (Admin)
- Gestão de horários e disponibilidade
- Regras de disponibilidade por dia/intervalo

### 💳 Pagamentos
- Integração com **Mercado Pago**
- Webhook para confirmação automática (status)
- Controle e consulta de status do pagamento
- Confirmação manual (Admin) quando necessário

### 💬 WhatsApp (WAHA) – Opcional
- Envio de mensagens de confirmação/atualizações para o cliente
- Recomendado em produção com WAHA rodando como serviço separado  
  (o back consome a API do WAHA via `WAHA_BASE_URL`)

---

## 🔗 Endpoints (principais)

> Observação: os endpoints abaixo são um resumo do núcleo do sistema.

**Públicos**
- `POST /auth/login`
- `POST /clientes/registrar`
- `GET  /servicos/ativos`
- `POST /pagamentos/webhook` (e variações sob `/pagamentos/webhook/**`)
- `GET  /agendamentos/horarios-disponiveis`

**Cliente (ROLE_CLIENTE)**
- `POST /pagamentos/criar`
- `GET  /pagamentos/*`
- `GET  /pagamentos/agendamentos/**`
- `GET/POST/PATCH/DELETE /agendamentos/**` (conforme regras do sistema)

**Admin (ROLE_ADMIN)**
- `GET/POST/PUT/PATCH/DELETE /servicos/**`
- Endpoints administrativos sob `/admin/**` (ex.: horários, relatórios e operações do painel)
- `GET  /pagamentos`
- `PATCH /pagamentos/*/confirmar-manual`
- `PATCH /pagamentos/*/cancelar`
- `/pagamentos/mock/**` (ambiente de teste)

---

## ⚙️ Configuração do Ambiente

Nenhuma credencial sensível é versionada no repositório.

### Variáveis de ambiente (Produção / Railway)

As variáveis abaixo refletem o `application.properties`:

**Servidor**
- `PORT` (Railway injeta automaticamente; local default `8080`)

**Banco**
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DB_POOL_SIZE` (opcional, ex.: `5`)

**JPA / Logs (opcional)**
- `JPA_SHOW_SQL` (em PROD recomendado: `false`)
- `JPA_FORMAT_SQL` (em PROD recomendado: `false`)

**Segurança**
- `JWT_SECRET`
- `JWT_EXPIRATION` (opcional, default: `28800000`)

**Mercado Pago**
- `MP_ACCESS_TOKEN`
- `MP_NOTIFICATION_URL` (URL pública do back para webhooks do Mercado Pago)
- `APP_FRONTEND_URL` (URL do front em produção; usado em redirects/links)

**CORS**
- `CORS_ALLOWED_ORIGINS`  
  Ex.: `http://localhost:5173,https://barbearia-alvaro-santos-front-end.vercel.app`

**WhatsApp (WAHA)**
- `WAHA_BASE_URL` (URL do WAHA; interno ou público)
- `WAHA_API_KEY` (API key configurada no WAHA)
- `WAHA_SESSION` (nome da sessão usada pelo WAHA, ex.: `default`)

> Dica: em Railway, se WAHA e back estiverem no mesmo projeto, você pode usar o **host interno** do WAHA.
> Caso contrário, use a URL pública do serviço.

### Exemplo (Supabase Session Pooler)
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`
- `SPRING_DATASOURCE_USERNAME=postgres.<ref-do-projeto>`
- `SPRING_DATASOURCE_PASSWORD=<sua-senha>`

---

## ▶️ Como Executar Localmente

### Pré-requisitos
- Java 21
- Maven
- PostgreSQL (local) **ou** Supabase

### Config (local)
Crie um arquivo **`application-secret.properties`** (não versionado) e configure as chaves:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sua_base
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

MP_ACCESS_TOKEN=SEU_TOKEN_TESTE_OU_PROD
JWT_SECRET=uma_chave_forte_aqui

# opcional
CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_FRONTEND_URL=http://localhost:5173
MP_NOTIFICATION_URL=http://localhost:8080/pagamentos/webhook

# WAHA (opcional)
WAHA_BASE_URL=http://localhost:3000
WAHA_API_KEY=sua_api_key
WAHA_SESSION=default
```

### Execução
```bash
mvn spring-boot:run
```

A API ficará disponível em:
```
http://localhost:8080
```

---

## 🗄️ Banco de Dados

O schema é mantido externamente, com:

```properties
spring.jpa.hibernate.ddl-auto=none
```

---

## 🧭 Próximas Melhorias

- Otimizações de performance (índices, evitar N+1, DTOs enxutos)
- Melhorias de observabilidade (logs estruturados, métricas)

---

## 👨‍💻 Autor

**Bryan Mendes Pinheiro**  
- GitHub: https://github.com/BryanPinheiro77  
- LinkedIn: https://www.linkedin.com/in/bryan-mendes-0406b92b5  

---

## 📄 Licença

Este projeto está licenciado sob a licença MIT.  
Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.
