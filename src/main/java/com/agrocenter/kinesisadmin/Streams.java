package com.agrocenter.kinesisadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

@Configuration
class AwsConfig {
    @Bean KinesisClient kinesis(@Value("${admin-kinesis.region}") String region) {
        return KinesisClient.builder().region(Region.of(region)).build();
    }
}

@RestController
@RequestMapping("/api/kinesis-admin/streams")
class Streams {
    private final KinesisClient aws;
    private final boolean writes;
    Streams(KinesisClient aws, @Value("${admin-kinesis.writes-enabled:false}") boolean writes) { this.aws = aws; this.writes = writes; }
    record StreamView(String nombre, String estado, int shards, int retencionHoras, String modo) {}
    record Creation(@NotBlank @Pattern(regexp="agrocenter-[A-Za-z0-9_.-]{1,100}") String nombre, @Min(1) @Max(10) int shards) {}
    record Change(@NotNull @Min(24) @Max(8760) Integer retencionHoras) {}
    record Scaling(@NotNull @Min(1) @Max(10) Integer shards) {}
    record Action(String mensaje) {}
    @GetMapping List<StreamView> list() {
        return aws.listStreamsPaginator(ListStreamsRequest.builder().build()).streamNames().stream()
            .filter(n -> n.startsWith("agrocenter-")).map(this::details).toList();
    }
    @GetMapping("/{name}") StreamView details(@PathVariable String name) {
        validName(name);
        var s = aws.describeStreamSummary(DescribeStreamSummaryRequest.builder().streamName(name).build()).streamDescriptionSummary();
        return new StreamView(s.streamName(), s.streamStatusAsString(), s.openShardCount(), s.retentionPeriodHours(), s.streamModeDetails() == null ? "PROVISIONED" : s.streamModeDetails().streamModeAsString());
    }
    @PostMapping ResponseEntity<Action> create(@Valid @RequestBody Creation input) {
        writable();
        aws.createStream(CreateStreamRequest.builder().streamName(input.nombre()).shardCount(input.shards()).build());
        return accepted("Creación solicitada. El stream puede permanecer en CREATING; actualiza para consultar su estado.");
    }
    @PatchMapping("/{name}/retencion") ResponseEntity<Action> retention(@PathVariable String name, @Valid @RequestBody Change input) {
        writable(); validName(name); var current = details(name);
        if (!"ACTIVE".equals(current.estado())) throw new ResponseStatusException(HttpStatus.CONFLICT, "El stream debe estar ACTIVE.");
        if (input.retencionHoras() == current.retencionHoras()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La retención no cambió.");
        if (input.retencionHoras() > current.retencionHoras()) aws.increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest.builder().streamName(name).retentionPeriodHours(input.retencionHoras()).build());
        else aws.decreaseStreamRetentionPeriod(DecreaseStreamRetentionPeriodRequest.builder().streamName(name).retentionPeriodHours(input.retencionHoras()).build());
        return accepted("Cambio solicitado. Actualiza para comprobar el estado y la retención efectiva.");
    }
    @DeleteMapping("/{name}") ResponseEntity<Action> delete(@PathVariable String name, @RequestParam String confirmacion) {
        writable(); validName(name);
        if (!name.equals(confirmacion)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La confirmación debe coincidir con el nombre.");
        if (List.of("agrocenter-kinesis-negocio", "agrocenter-kinesis-stock").contains(name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Los streams activos de negocio están protegidos.");
        aws.deleteStream(DeleteStreamRequest.builder().streamName(name).build());
        return accepted("Eliminación solicitada. El stream puede permanecer en DELETING.");
    }
    @PatchMapping("/{name}/shards") ResponseEntity<Action> shards(@PathVariable String name, @Valid @RequestBody Scaling input) {
        writable(); validName(name); var current = details(name);
        if (!"ACTIVE".equals(current.estado()) || !"PROVISIONED".equals(current.modo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Se requiere un stream ACTIVE y provisionado.");
        if (input.shards() == current.shards()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de shards no cambió.");
        aws.updateShardCount(UpdateShardCountRequest.builder().streamName(name).targetShardCount(input.shards()).scalingType(ScalingType.UNIFORM_SCALING).build());
        return accepted("Escalado solicitado. Consulta el estado y shards efectivos más tarde.");
    }
    private void writable() { if (!writes) throw new ResponseStatusException(HttpStatus.CONFLICT, "Operaciones de escritura deshabilitadas por configuración."); }
    private void validName(String name) { if (!name.matches("agrocenter-[A-Za-z0-9_.-]{1,100}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de stream no permitido."); }
    private ResponseEntity<Action> accepted(String msg) { return ResponseEntity.accepted().body(new Action(msg)); }
    @ExceptionHandler(KinesisException.class) ResponseEntity<java.util.Map<String, String>> awsError(KinesisException e) {
        HttpStatus status = e.statusCode() == 403 ? HttpStatus.CONFLICT : e.statusCode() == 404 ? HttpStatus.NOT_FOUND : e.statusCode() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(java.util.Map.of("message", "AWS rechazó la operación (" + e.awsErrorDetails().errorCode() + "). Revisa permisos, estado y límites."));
    }
    @ExceptionHandler(SdkClientException.class) ResponseEntity<java.util.Map<String, String>> clientError(SdkClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of("message", "No fue posible comunicarse con AWS. Consulta el estado antes de reintentar."));
    }
}
