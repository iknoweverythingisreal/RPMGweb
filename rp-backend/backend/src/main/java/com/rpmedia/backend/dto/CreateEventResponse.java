package com.rpmedia.backend.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateEventResponse {
  private Long id;
  private List<String> warnings;
}
