# Barbearia Álvaro Santos 💈✂️  
Sistema de Agendamento e Pagamentos – Back-end (API REST)

![Badge](https://img.shields.io/badge/Java-21-red)
![Badge](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen)
![Badge](https://img.shields.io/badge/PostgreSQL-Supabase-blue)
![Badge](https://img.shields.io/badge/JWT-Security-orange)
![Badge](https://img.shields.io/badge/Status-Em%20Desenvolvimento-yellow)

---

## 📌 Sobre o Projeto

 **Barbearia Álvaro Santos – Sistema de Agendamento** é um projeto **real**, desenvolvido para atender às necessidades de organização, agendamento e controle de pagamentos de uma barbearia.

O sistema foi pensado para resolver problemas comuns do dia a dia, como:

- Agendamentos feitos de forma desorganizada  
- Falta de controle de horários disponíveis  
- Dificuldade no gerenciamento de clientes  
- Pagamentos sem rastreabilidade  

O projeto está sendo desenvolvido com foco em **uso real em produção**, aplicando boas práticas de arquitetura, segurança e separação de responsabilidades.

---

## 🏗️ Arquitetura

API REST em camadas:

- **Controllers** – endpoints REST  
- **Services** – regras de negócio e validações  
- **Repositories** – acesso ao banco com Spring Data JPA  
- **Models / Entities** – domínio  
- **DTOs** – contratos de entrada e saída  
- **Security** – autenticação e autorização com JWT  

O front-end (React/Vite) consome esta API de forma independente.

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

### Observação (Supabase / Railway)

Se o teu ambiente de deploy estiver em IPv4, a **Direct connection** do Supabase pode falhar (mensagem “Not IPv4 compatible”).  
Em produção, recomenda-se usar **Session Pooler** do Supabase para obter estabilidade de conexão.

---

## 🔐 Segurança

- Autenticação baseada em **JWT**
- Controle de acesso por **roles**:
  - `ADMIN`
  - `CLIENTE`
- Sessões **stateless**
- Senhas criptografadas com **BCrypt**
- Credenciais e segredos **fora do repositório** (via ENV)

---

## ✨ Funcionalidades Principais

### 👤 Usuários
- Login de administrador e cliente
- Autorização por perfil

### 📅 Agendamentos
- Criação de agendamentos pelo cliente
- Cálculo automático de horários disponíveis
- Seleção de múltiplos serviços
- Cancelamento e conclusão de agendamentos
- Listagens por cliente, data, intervalo e status

### ✂️ Serviços
- Cadastro e gerenciamento de serviços (Admin)
- Serviços ativos disponíveis para clientes
- Duração e preço por serviço

### 💳 Pagamentos
- Integração com **Mercado Pago**
- Webhook para confirmação automática
- Controle de status do pagamento
- Confirmação manual (Admin)

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
- `/admin/**`
- `/servicos/**`
- `GET  /pagamentos`
- `PATCH /pagamentos/*/confirmar-manual`
- `PATCH /pagamentos/*/cancelar`
- `/pagamentos/mock/**` (ambiente de teste)

---

## ⚙️ Configuração do Ambiente

Nenhuma credencial sensível é versionada no repositório.

### Variáveis de ambiente (Produção / Railway)

**Banco**
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DB_POOL_SIZE` (opcional, ex.: `5`)

**Segurança**
- `JWT_SECRET`
- `JWT_EXPIRATION` (opcional, default: `28800000`)

**Mercado Pago**
- `MP_ACCESS_TOKEN`

**CORS**
- `CORS_ALLOWED_ORIGINS`  
  Ex.: `http://localhost:5173,https://SEU-FRONT.vercel.app`

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

## 🧭 Roadmap

- Integração WhatsApp: confirmação e lembrete de agendamento
- Melhorias de performance: paginação, DTOs enxutos, evitar N+1, índices no Postgres

---

## 👨‍💻 Autor

**Bryan Mendes Pinheiro**  
- GitHub: https://github.com/BryanPinheiro77  
- LinkedIn: https://www.linkedin.com/in/bryan-mendes-0406b92b5  

---

## 📄 Licença

Este projeto está licenciado sob a licença MIT.  
Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.
