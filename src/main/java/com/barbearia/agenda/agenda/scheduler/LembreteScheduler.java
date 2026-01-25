package com.barbearia.agenda.agenda.scheduler;

import com.barbearia.agenda.integration.WahaClient;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LembreteScheduler {

    private final AgendamentoRepository agendamentoRepo;
    private final WahaClient wahaClient;

    public LembreteScheduler(AgendamentoRepository agendamentoRepo,
                             WahaClient wahaClient) {
        this.agendamentoRepo = agendamentoRepo;
        this.wahaClient = wahaClient;
    }

    @Scheduled(fixedDelay = 60000) // 1 minuto
    @Transactional
    public void verificarLembretes() {

        List<Agendamento> agendamentos = agendamentoRepo.findPendentesParaLembrete();
        LocalDateTime agora = LocalDateTime.now();

        for (Agendamento ag : agendamentos) {

            if (ag.getLembreteMinutos() == null || ag.getLembreteMinutos() <= 0) {
                continue;
            }

            LocalDateTime horarioAgendamento = LocalDateTime.of(
                    ag.getData(),
                    ag.getHorarioInicio()
            );

            LocalDateTime horarioLembrete = horarioAgendamento.minusMinutes(
                    ag.getLembreteMinutos()
            );

            // janela segura de disparo
            if (agora.isAfter(horarioLembrete) && agora.isBefore(horarioAgendamento)) {

                try {
                    String mensagem = "⏰ Lembrete de agendamento\n\n" +
                            "Olá, " + ag.getCliente().getNome() + "!\n" +
                            "Seu horário na barbearia é em " + ag.getLembreteMinutos() + " minutos.\n\n" +
                            "📅 Data: " + ag.getData() + "\n" +
                            "⏰ Horário: " + ag.getHorarioInicio() + "\n\n" +
                            "Te esperamos 💈";

                    boolean enviado = wahaClient.sendText(
                            ag.getCliente().getTelefone(),
                            mensagem
                    );

                    // ✅ AQUI marca no banco
                    if (enviado) {
                        ag.setEnviadoLembrete(true);
                        agendamentoRepo.save(ag);
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao enviar lembrete:");
                    e.printStackTrace();
                }
            }
        }
    }
}
