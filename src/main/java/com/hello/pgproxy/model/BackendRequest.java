package com.hello.pgproxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackendRequest {
    private UUID id;
    private Long amount_cents;
    private Long verification;
}
