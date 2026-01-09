package com.barbearia.agenda.controller;

import com.barbearia.agenda.dto.*;
import com.barbearia.agenda.model.Agendamento;
import com.barbearia.agenda.model.Cliente;
import com.barbearia.agenda.model.Servico;
import com.barbearia.agenda.model.StatusAgendamento;
import com.barbearia.agenda.repository.AgendamentoRepository;
import com.barbearia.agenda.repository.ClienteRepository;
import com.barbearia.agenda.repository.ServicoRepository;
import com.barbearia.agenda.repository.HorarioBarbeiroRepository;
import com.barbearia.agenda.service.AgendamentoService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/agendamentos")
public class AgendamentoController {

    private final AgendamentoRepository agendamentoRepo;
    private final ClienteRepository clienteRepo;
    private final ServicoRepository servicoRepo;
    private final HorarioBarbeiroRepository horarioRepo;
    private final AgendamentoService agendamentoService;

    public AgendamentoController(
            AgendamentoRepository agendamentoRepo,
            ClienteRepository clienteRepo,
            ServicoRepository servicoRepo,
            HorarioBarbeiroRepository horarioRepo,
            AgendamentoService agendamentoService

    ) {
        this.agendamentoRepo = agendamentoRepo;
        this.clienteRepo = clienteRepo;
        this.servicoRepo = servicoRepo;
        this.horarioRepo = horarioRepo;
        this.agendamentoService = agendamentoService;
    }

    // ====================================================================
    // 1️⃣ CRIAR AGENDAMENTO
    // ====================================================================
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody AgendamentoCreateRequest req,
            Authentication auth
    ) {
        if (auth == null) {
            return ResponseEntity.status(401).body("Usuário não autenticado");
        }

        String email = auth.getName();
        Agendamento a = agendamentoService.criar(req, email);
        return ResponseEntity.ok(toResponse(a));
    }




    // ====================================================================
    // ⭐ 2️⃣ HORÁRIOS DISPONÍVEIS INTELIGENTES (para vários serviços)
    // ====================================================================
    @PostMapping("/horarios-disponiveis")
    public ResponseEntity<HorariosDisponiveisResponse> listarHorariosDisponiveis(
            @RequestBody HorariosDisponiveisRequest req
    ) {
        LocalDate dia = LocalDate.parse(req.data());

        // 1) soma total da duração dos serviços selecionados
        int duracaoTotal = calcularDuracaoTotal(req.servicosIds());

        // 2) horários-base cadastrados pelo barbeiro
        List<LocalTime> horariosBase = horarioRepo.findByAtivoTrue()
                .stream()
                .map(h -> h.getHorario())
                .toList();

        // 3) filtra horários onde o agendamento CABE
        List<String> horariosLivres = horariosBase.stream()
                .filter(horario -> cabeNesseHorario(dia, horario, duracaoTotal))
                .map(LocalTime::toString)
                .toList();

        HorariosDisponiveisResponse resp = new HorariosDisponiveisResponse(
                req.data(),
                horariosLivres
        );

        return ResponseEntity.ok(resp);
    }

    // ====================================================================
    // 3️⃣ LISTAR TODOS OS AGENDAMENTOS
    // ====================================================================
    @GetMapping
    public ResponseEntity<List<AgendamentoResponse>> listarTodos() {

        List<AgendamentoResponse> lista = agendamentoRepo.findAll()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ====================================================================
    // 4️⃣ LISTAR AGENDAMENTOS POR DIA
    // ====================================================================
    @GetMapping("/dia/{data}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorDia(@PathVariable String data) {

        List<AgendamentoResponse> lista = agendamentoRepo
                .findByData(LocalDate.parse(data))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ====================================================================
    // 5️⃣ FILTRAR ENTRE DUAS DATAS
    // ====================================================================
    @GetMapping("/intervalo")
    public ResponseEntity<List<AgendamentoResponse>> listarPorIntervalo(
            @RequestParam String inicio,
            @RequestParam String fim
    ) {
        LocalDate i = LocalDate.parse(inicio);
        LocalDate f = LocalDate.parse(fim);

        List<AgendamentoResponse> lista = agendamentoRepo
                .findByDataBetween(i, f)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ====================================================================
    // 6️⃣ FILTRAR POR STATUS
    // ====================================================================
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AgendamentoResponse>> listarPorStatus(@PathVariable String status) {

        List<AgendamentoResponse> lista = agendamentoRepo
                .findByStatus(StatusAgendamento.valueOf(status.toUpperCase()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ====================================================================
    // 7️⃣ LISTAR POR CLIENTE
    // ====================================================================
    @GetMapping("/meus")
    public ResponseEntity<?> listarMeus(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body("Usuário não autenticado");
        }

        String email = auth.getName();

        List<AgendamentoResponse> lista = agendamentoService.listarMeus(email)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(lista);
    }

    // ====================================================================
    // 8️⃣ CANCELAR
    // ====================================================================
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        return agendamentoRepo.findById(id)
                .map(a -> {
                    a.setStatus(StatusAgendamento.CANCELADO);
                    agendamentoRepo.save(a);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ====================================================================
    // 9️⃣ CONCLUIR
    // ====================================================================
    @PatchMapping("/{id}/concluir")
    public ResponseEntity<?> concluir(@PathVariable Long id) {
        return agendamentoRepo.findById(id)
                .map(a -> {
                    a.setStatus(StatusAgendamento.CONCLUIDO);
                    agendamentoRepo.save(a);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ====================================================================
    // 🔧 MÉTODOS AUXILIARES
    // ====================================================================

    private int calcularDuracaoTotal(List<Long> servicosIds) {
        return servicosIds.stream()
                .map(id -> servicoRepo.findById(id).orElse(null))
                .filter(s -> s != null)
                .mapToInt(s -> s.getDuracaoMinutos())
                .sum();
    }

    private boolean cabeNesseHorario(LocalDate data, LocalTime inicio, int duracaoTotal) {

        LocalTime fim = inicio.plusMinutes(duracaoTotal);

        boolean conflito = agendamentoRepo
                .existsByDataAndHorarioInicioLessThanAndHorarioFimGreaterThan(
                        data, fim, inicio
                );

        return !conflito;
    }

    private AgendamentoResponse toResponse(Agendamento a) {

        var servicosDto = a.getServicos().stream()
                .map(link -> new AgendamentoServico(
                        link.getServico().getId(),
                        link.getServico().getNome(),
                        link.getServico().getDuracaoMinutos(),
                        link.getServico().getPreco()
                ))
                .toList();

        return new AgendamentoResponse(
                a.getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),

                servicosDto,

                a.getData(),
                a.getHorarioInicio(),
                a.getHorarioFim(),

                a.getFormaPagamentoTipo(),
                a.getFormaPagamentoModo(),
                a.getLembreteMinutos(),
                a.getStatus(),

                a.isPago()
        );
    }


}
