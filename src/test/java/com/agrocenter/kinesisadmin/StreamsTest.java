package com.agrocenter.kinesisadmin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

class StreamsTest {
    @Test void writesDisabledByDefaultCannotCallAws() {
        var aws = mock(KinesisClient.class); var service = new Streams(aws, false);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.create(new Streams.Creation("agrocenter-prueba", 1))).getStatusCode());
        verifyNoInteractions(aws);
    }
    @Test void protectedStreamsCannotBeDeleted() {
        var aws = mock(KinesisClient.class); var service = new Streams(aws, true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.delete("agrocenter-kinesis-stock", "agrocenter-kinesis-stock")).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.delete("agrocenter-prueba", "incorrecto"));
        verifyNoInteractions(aws);
    }
    @Test void creationAndDeletionReturnAccepted() {
        var aws = mock(KinesisClient.class); var service = new Streams(aws, true);
        assertEquals(HttpStatus.ACCEPTED, service.create(new Streams.Creation("agrocenter-prueba", 1)).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, service.delete("agrocenter-prueba", "agrocenter-prueba").getStatusCode());
        verify(aws).createStream(any(CreateStreamRequest.class));
        verify(aws).deleteStream(any(DeleteStreamRequest.class));
    }
    @Test void retentionUsesCorrectAwsOperation() {
        var aws = mock(KinesisClient.class); var service = new Streams(aws, true);
        when(aws.describeStreamSummary(any(DescribeStreamSummaryRequest.class))).thenReturn(DescribeStreamSummaryResponse.builder().streamDescriptionSummary(StreamDescriptionSummary.builder().streamName("agrocenter-prueba").streamStatus(StreamStatus.ACTIVE).openShardCount(1).retentionPeriodHours(24).build()).build());
        assertEquals(HttpStatus.ACCEPTED, service.retention("agrocenter-prueba", new Streams.Change(48)).getStatusCode());
        verify(aws).increaseStreamRetentionPeriod(any(IncreaseStreamRetentionPeriodRequest.class));
    }
}
