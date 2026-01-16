# Barbearia Álvaro Santos 💈✂️  
Sistema de Agendamento e Pagamentos – Back-end API

![Badge](https://img.shields.io/badge/Java-17-red)
![Badge](https://img.shields.io/badge/Spring%20Boot-Backend-brightgreen)
![Badge](https://img.shields.io/badge/PostgreSQL-Database-blue)
![Badge](https://img.shields.io/badge/JWT-Security-orange)
![Badge](https://img.shields.io/badge/Status-Em%20Desenvolvimento-yellow)

---

## 📌 Sobre o Projeto

O **Barbearia Álvaro Santos – Sistema de Agendamento** é um projeto **real**, desenvolvido para atender às necessidades de organização, agendamento e controle de pagamentos de uma barbearia.

O sistema foi pensado para resolver problemas comuns do dia a dia, como:

- Agendamentos feitos de forma desorganizada  
- Falta de controle de horários disponíveis  
- Dificuldade no gerenciamento de clientes  
- Pagamentos sem rastreabilidade  

O projeto está sendo desenvolvido com foco em **uso real em produção**, aplicando boas práticas de arquitetura, segurança e separação de responsabilidades.

---

## 🏗️ Arquitetura

O back-end foi desenvolvido como uma **API REST desacoplada**, seguindo uma arquitetura em camadas:

- **Controllers** – exposição dos endpoints REST  
- **Services** – regras de negócio e validações  
- **Repositories** – acesso ao banco com Spring Data JPA  
- **Models / Entities** – domínio da aplicação  
- **DTOs** – contratos de entrada e saída  
- **Security** – autenticação e autorização com JWT  

O front-end (React) consome essa API de forma independente.

---

## 🚀 Tecnologias Utilizadas

- Java 17  
- Spring Boot  
- Spring Web MVC  
- Spring Data JPA (Hibernate)  
- Spring Security  
- JWT (JSON Web Token)  
- PostgreSQL  
- Maven  
- Lombok  

---

## 🔐 Segurança

- Autenticação baseada em **JWT**
- Controle de acesso por **roles**:
  - `ADMIN`
  - `CLIENTE`
- Sessões **stateless**
- Senhas criptografadas com **BCrypt**
- Credenciais e segredos **fora do repositório**

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

## 🗄️ Banco de Dados

O sistema utiliza **PostgreSQL** com tabelas relacionais para:

- clientes  
- administradores  
- serviços  
- agendamentos  
- pagamentos  

O schema é mantido externamente, com:

```
spring.jpa.hibernate.ddl-auto=none
```

---

## ⚙️ Configuração do Ambiente

Nenhuma credencial sensível é versionada no repositório.

O projeto utiliza separação de configuração:

- `application.properties` → configurações seguras  
- `application-secret.properties` → credenciais (fora do Git)  

### Exemplo (`application-secret.properties`)
```properties
spring.datasource.url=jdbc:postgresql://HOST:5432/DB
spring.datasource.username=USUARIO
spring.datasource.password=SENHA

jwt.secret=SEU_SEGREDO_FORTE
jwt.expiration=28800000

mp.access-token=SEU_TOKEN_MERCADO_PAGO
```

---

## ▶️ Como Executar o Back-end

### Pré-requisitos
- Java 17
- Maven
- PostgreSQL

### Execução
```bash
mvn spring-boot:run
```

A API ficará disponível em:

```
http://localhost:8080
```

---

## 📌 Status do Projeto

🚧 **Em desenvolvimento ativo**  
Projeto real, com melhorias contínuas.

---

## 👨‍💻 Autor

**Bryan Mendes Pinheiro**  
Desenvolvedor Back-end / Full Stack Jr  

- GitHub: https://github.com/BryanPinheiro77  
- LinkedIn: https://www.linkedin.com/in/bryan-mendes-0406b92b5  

---

## 📄 Licença

Este projeto está licenciado sob a licença MIT.  
Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.

