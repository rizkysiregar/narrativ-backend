package com.rizkysiregar.feedzmore.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "fcm_broadcast_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FcmBroadcastEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    private String messageName;
    private String title;
    private String body;

}
