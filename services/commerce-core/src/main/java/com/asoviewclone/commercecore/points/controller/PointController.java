package com.asoviewclone.commercecore.points.controller;

import com.asoviewclone.commercecore.points.service.PointService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class PointController {

  private final PointService pointService;

  public PointController(PointService pointService) {
    this.pointService = pointService;
  }

  @GetMapping("/me/points")
  public Map<String, Long> getMyPoints(@AuthenticationPrincipal AuthenticatedUser user) {
    return Map.of("balance", pointService.getBalance(user.userId()));
  }
}
