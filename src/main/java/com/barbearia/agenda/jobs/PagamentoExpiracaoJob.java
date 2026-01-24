package com.barbearia.agenda.jobs;

import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.model.StatusPagamento;
import com.barbearia.agenda.model.Pagamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.PagamentoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PagamentoExpiracaoJob {

    private final PagamentoRepository pagamentoRepo;
    private final AgendamentoRepository agendamentoRepo;

    public PagamentoExpiracaoJob(PagamentoRepository pagamentoRepo, AgendamentoRepository agendamentoRepo) {
        this.pagamentoRepo = pagamentoRepo;
        this.agendamentoRepo = agendamentoRepo;
    }

    // roda a cada 5m (pode ajustar)
    @Scheduled(fixedDelayString = "5m")
    @Transactional
    public void expirarPendentes() {
        LocalDateTime now = LocalDateTime.now();

        List<Pagamento> expirados = pagamentoRepo.findByStatusAndExpiraEmBefore(StatusPagamento.PENDENTE, now);
        if (expirados.isEmpty()) return;

        for (Pagamento p : expirados) {
            // marca pagamento como EXPIRADO
            p.setStatus(StatusPagamento.EXPIRADO);
            pagamentoRepo.save(p);

            // cancela agendamento se ele ainda estiver pendente e não pago
            Agendamento ag = p.getAgendamento();
            if (ag != null
                    && ag.getStatus() == StatusAgendamento.PAGAMENTO_PENDENTE
                    && !ag.isPago()) {
                ag.setStatus(StatusAgendamento.CANCELADO);
                agendamentoRepo.save(ag);
            }
        }
    }
}
