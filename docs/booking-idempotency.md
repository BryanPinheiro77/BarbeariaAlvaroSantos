# Agendamentos sem duplicidade

Aplicar db/migrations/002_booking_requests.sql antes do deploy. A migração é transacional e falha se já houver sobreposição ou horários inválidos. Não apaga duplicados existentes: listar e resolver com o responsável antes de reaplicar. Não reaplicar depois de concluída. Não depende da migração de refresh token.

POST /agendamentos e POST /admin/agendamentos aceitam Idempotency-Key opcional (16–128 caracteres alfanuméricos, hífen/underscore). Frontend envia UUID por tentativa lógica e conserva a chave em retries. Mesma chave/usuário/payload devolve o mesmo agendamento; mudar payload com a chave existente retorna 409. Replay não envia outra confirmação WhatsApp. Chave ausente permanece compatível com frontend antigo; a proteção de sobreposição continua ativa.

Lock transacional PostgreSQL por chave e por dia serializa criação de cliente e admin entre instâncias. A restrição EXCLUDE no banco também impede sobreposição por outros escritores. Intervalos adjacentes são permitidos; cancelados liberam horário. A agenda atual é única. Reservas atravessando meia-noite são rejeitadas. Idempotência engloba criação de agendamento; cobranças no provedor de pagamento são operação separada.

Aceite: duas requisições com a mesma chave resultam no mesmo ID; duas chaves para intervalo sobreposto resultam em um sucesso e um 409; rollback não deixa chave presa; tentativas não dependem da memória de uma JVM. Teste PostgreSQL: BOOKING_TEST_DB_URL=jdbc:postgresql://localhost:PORT/test BOOKING_TEST_DB_USER=test BOOKING_TEST_DB_PASSWORD=test bash mvnw -Dtest=BookingRequestsTest test. Usar banco descartável: o teste cria e remove somente seu schema aleatório.

Deploy backend antes da PR de frontend correspondente. Worker/cron permanece fora do escopo. A chamada WAHA ainda ocorre durante a transação existente; não se promete entrega exatamente uma vez diante de falha entre envio externo e commit.
